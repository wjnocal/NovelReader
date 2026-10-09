package com.wjnocal.novelreader.online;

import android.content.Context;

import com.wjnocal.novelreader.data.AppDatabase;
import com.wjnocal.novelreader.data.BookEntity;
import com.wjnocal.novelreader.data.ChapterEntity;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.UUID;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.FormBody;
import okio.ByteString;

public class OnlineBookClient {
    public static final String FORMAT_TXT = "txt";
    public static final String FORMAT_EPUB = "epub";

    private final Context context;
    private final AppDatabase database;
    private final OnlineSourceRepository sourceRepository;
    private final Random random = new Random();
    private final OnlineHttpClient http;

    public OnlineBookClient(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(context);
        this.sourceRepository = new OnlineSourceRepository(context);
        this.http = new OnlineHttpClient();
    }

    // Pure parser/transport test seam; library operations use the Context constructor.
    OnlineBookClient(OnlineHttpClient http) {
        this.context = null;
        this.database = null;
        this.sourceRepository = null;
        this.http = http;
    }

    public List<OnlineBookResult> search(String keyword) throws Exception {
        return search(keyword, null, null);
    }

    public List<OnlineBookResult> search(String keyword, Set<String> sourceIds, OnlineSearchProgress progress) throws Exception {
        String cleanKeyword = OnlineBookResult.clean(keyword);
        if (cleanKeyword.isEmpty()) {
            return new ArrayList<>();
        }
        List<OnlineBookResult> allResults = new ArrayList<>();
        List<OnlineBookSource> sources = new ArrayList<>();
        for (OnlineBookSource source : sourceRepository.loadEnabledSources()) {
            if (sourceIds == null || sourceIds.isEmpty() || sourceIds.contains(source.id)) {
                sources.add(source);
            }
        }
        List<List<OnlineBookResult>> bySource = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) bySource.add(null);
        AtomicInteger completed = new AtomicInteger();
        OnlineWorkQueue.run(sources.size(), 4, null, (index, token) -> {
            OnlineBookSource source = sources.get(index);
            List<OnlineBookResult> found = searchSource(source, cleanKeyword, token);
            synchronized (bySource) {
                bySource.set(index, found);
                notifySearchProgress(progress, "已完成：" + source.name, completed.incrementAndGet(), sources.size());
            }
        });
        // Stable source ordering regardless of network completion order.
        for (List<OnlineBookResult> found : bySource) allResults.addAll(found);
        return allResults;
    }

    public long downloadToLibrary(OnlineBookResult result, OnlineDownloadProgress progress) throws Exception {
        return downloadToLibrary(result, FORMAT_TXT, progress);
    }

    public long downloadToLibrary(OnlineBookResult result, String format, OnlineDownloadProgress progress) throws Exception {
        return downloadToLibrary(result, format, progress, null);
    }

    public long downloadToLibrary(OnlineBookResult result, String format, OnlineDownloadProgress progress, OnlineDownloadCancelToken cancelToken) throws Exception {
        OnlineBookSource source = sourceRepository.findById(result.sourceId);
        return downloadToLibrary(source, result, normalizeFormat(format), progress, cancelToken);
    }

    public long downloadUrlToLibrary(String bookUrl, OnlineDownloadProgress progress) throws Exception {
        return downloadUrlToLibrary(bookUrl, FORMAT_TXT, progress);
    }

    public long downloadUrlToLibrary(String bookUrl, String format, OnlineDownloadProgress progress) throws Exception {
        return downloadUrlToLibrary(bookUrl, format, progress, null);
    }

    public long downloadUrlToLibrary(String bookUrl, String format, OnlineDownloadProgress progress, OnlineDownloadCancelToken cancelToken) throws Exception {
        return downloadUrlToLibrary(bookUrl, format, progress, cancelToken, null);
    }

    public long downloadUrlToLibrary(String bookUrl, String format, OnlineDownloadProgress progress, OnlineDownloadCancelToken cancelToken, Set<String> sourceIds) throws Exception {
        OnlineBookSource source = sourceRepository.findEnabledSourceForUrl(bookUrl, sourceIds);
        if (source == null) {
            throw new IllegalArgumentException("没有匹配这个网址的可用书源");
        }
        OnlineBookResult result = new OnlineBookResult();
        result.sourceId = source.id;
        result.sourceName = source.name;
        result.bookName = "在线书籍";
        result.url = bookUrl;
        return downloadToLibrary(source, result, normalizeFormat(format), progress, cancelToken);
    }

    private long downloadToLibrary(OnlineBookSource source, OnlineBookResult result, String format, OnlineDownloadProgress progress, OnlineDownloadCancelToken cancelToken) throws Exception {
        throwIfCancelled(cancelToken);
        notifyProgress(progress, "正在读取详情：《" + result.bookName + "》");
        Document detail = http.get(result.url, source, cancelToken);
        OnlineBookInfo bookInfo = parseBookInfo(source, result, detail);
        throwIfCancelled(cancelToken);
        BookEntity existing = database.bookDao().getByTitle(bookInfo.title);
        if (existing != null && !OnlineDownloadState.isIncomplete(existing)) {
            throw new IllegalStateException("书架里已存在《" + bookInfo.title + "》");
        }

        notifyProgress(progress, "正在读取目录...");
        List<OnlineChapterRef> toc = parseToc(source, result.url, detail, cancelToken);
        throwIfCancelled(cancelToken);
        if (toc.isEmpty()) {
            throw new IllegalStateException("目录为空，无法下载");
        }

        String signature = OnlineDownloadState.signature(source.id, result.url, format, toc);
        if (existing != null && !OnlineDownloadState.canResume(existing, signature)) {
            throw new IllegalStateException("书架已有同名未完成下载，请选择原来的书源、下载格式和目录继续下载");
        }
        File bookDir = existing == null
                ? new File(context.getFilesDir(), "books/online_" + UUID.randomUUID())
                : new File(existing.storageDirPath);
        ensureDir(bookDir);
        File originalFile = existing == null
                ? new File(bookDir, safeFileName(bookInfo.title) + "." + format)
                : new File(existing.originalFilePath);

        File chaptersDir = new File(bookDir, "chapters");
        ensureDir(chaptersDir);
        final long[] bookId = {existing == null ? -1L : existing.id};
        OnlineChapterCache cache = new OnlineChapterCache(new File(context.getCacheDir(), "online_chapters"));
        try {
            long now = System.currentTimeMillis();
            BookEntity book = existing == null ? new BookEntity() : existing;
            if (existing == null) {
                book.title = bookInfo.title;
                book.author = bookInfo.author;
                book.fileType = "online-" + format;
                book.category = OnlineBookResult.clean(bookInfo.category).isEmpty() ? "未分类" : bookInfo.category;
                book.description = bookInfo.intro;
                book.originalFilePath = originalFile.getAbsolutePath();
                book.storageDirPath = bookDir.getAbsolutePath();
                book.totalChapters = toc.size();
                book.currentChapterIndex = 0;
                book.scrollY = 0;
                book.createdAt = now;
                book.updatedAt = now;
                book.syncId = UUID.randomUUID().toString();
                book.syncUpdatedAt = now;
                OnlineDownloadState.begin(book, signature);
            }
            List<ChapterEntity> saved = existing == null ? new ArrayList<>() : database.chapterDao().getForBook(bookId[0]);
            for (int i = 0; i < saved.size(); i++) {
                if (saved.get(i).chapterIndex != i || !new File(saved.get(i).contentPath).isFile()) {
                    throw new IllegalStateException("已下载章节文件缺失，请删除这本书后重新下载");
                }
            }
            if (!saved.isEmpty() && progress != null) progress.onBookAvailable(bookId[0]);
            OnlineChapterPublisher publisher = new OnlineChapterPublisher(toc.size(), saved.size(), (from, to) -> {
                throwIfCancelled(cancelToken);
                List<ChapterEntity> added = new ArrayList<>();
                boolean firstPublication = bookId[0] <= 0;
                long committedId = bookId[0];
                database.beginTransaction();
                try {
                    if (firstPublication) committedId = database.bookDao().insert(book);
                    for (int i = from; i < to; i++) {
                        ChapterEntity chapter = new ChapterEntity();
                        chapter.bookId = committedId;
                        chapter.chapterIndex = i;
                        chapter.title = toc.get(i).title;
                        chapter.contentPath = new File(chaptersDir, String.format(Locale.US, "%04d.txt", i)).getAbsolutePath();
                        added.add(chapter);
                    }
                    database.chapterDao().insertAll(added);
                    database.setTransactionSuccessful();
                } finally { database.endTransaction(); }
                bookId[0] = committedId;
                if (firstPublication && progress != null) progress.onBookAvailable(committedId);
            });
            List<File> downloaded = downloadChapterContents(source, toc, chaptersDir, cache, progress,
                    cancelToken, saved.size(), publisher::completed);
            throwIfCancelled(cancelToken);
            notifyProgress(progress, "正在生成 " + format.toUpperCase(Locale.US) + " 文件...");
            File pendingExport = new File(bookDir, originalFile.getName() + ".part");
            if (FORMAT_EPUB.equals(format)) {
                writeEpub(pendingExport, bookInfo, toc, downloaded, cancelToken);
            } else {
                writeTxt(pendingExport, bookInfo, source, result.url, toc, downloaded, cancelToken);
            }
            throwIfCancelled(cancelToken);
            if (!pendingExport.renameTo(originalFile)) throw new java.io.IOException("无法保存下载文件");
            if (database.bookDao().getById(bookId[0]) == null) throw new CancellationException("书籍已删除");
            OnlineDownloadState.complete(book);
            notifyProgress(progress, "下载完成：《" + bookInfo.title + "》");
            return bookId[0];
        } catch (Exception e) {
            // Published chapters remain readable after cancellation or a network failure.
            if (bookId[0] <= 0 || database.bookDao().getById(bookId[0]) == null) {
                deleteRecursively(bookDir);
            }
            throw e;
        } finally {
            cache.prune();
        }
    }

    List<File> downloadChapterContents(OnlineBookSource source, List<OnlineChapterRef> toc, File directory,
                                      OnlineChapterCache cache, OnlineDownloadProgress progress,
                                      OnlineDownloadCancelToken cancelToken) throws Exception {
        return downloadChapterContents(source, toc, directory, cache, progress, cancelToken, 0, null);
    }

    interface ChapterReady { void onReady(int index) throws Exception; }

    List<File> downloadChapterContents(OnlineBookSource source, List<OnlineChapterRef> toc, File directory,
                                      OnlineChapterCache cache, OnlineDownloadProgress progress,
                                      OnlineDownloadCancelToken cancelToken, int alreadyPublished,
                                      ChapterReady ready) throws Exception {
        int concurrency = Math.max(1, Math.min(8, Math.min(source.crawl.concurrency, toc.size())));
        notifyProgress(progress, "并发下载中：" + alreadyPublished + "/" + toc.size() + "，线程 " + concurrency);
        List<File> files = new ArrayList<>();
        for (int i = 0; i < toc.size(); i++) files.add(new File(directory, String.format(Locale.US, "%04d.txt", i)));
        AtomicInteger completed = new AtomicInteger(alreadyPublished);
        Set<String> chapterUrls = new HashSet<>();
        for (OnlineChapterRef ref : toc) chapterUrls.add(ref.url);
        OnlineWorkQueue.run(toc.size() - alreadyPublished, concurrency, cancelToken, (pendingIndex, token) -> {
            int index = pendingIndex + alreadyPublished;
            OnlineChapterRef ref = toc.get(index);
            String key = OnlineChapterCache.key(source, ref.url, ref.title);
            String content = cache.read(key);
            if (content == null) {
                content = downloadChapterContent(source, ref, token, chapterUrls);
                token.throwIfCancelled();
                cache.write(key, content);
            }
            token.throwIfCancelled();
            writeText(files.get(index), content);
            token.throwIfCancelled();
            if (ready != null) ready.onReady(index);
            synchronized (completed) {
                int done = completed.incrementAndGet();
                notifyProgress(progress, "并发下载中：" + done + "/" + toc.size() + "：" + ref.title);
            }
        });
        return files;
    }

    List<OnlineBookResult> searchSource(OnlineBookSource source, String keyword, OnlineDownloadCancelToken token) {
        List<OnlineBookResult> results = new ArrayList<>();
        if (source.search.disabled || source.search.result == null || source.search.result.trim().isEmpty()) {
            return results;
        }
        try {
            Document document = requestSearchDocument(source, keyword, token);
            Set<String> visitedPages = new HashSet<>();
            Set<String> seenBooks = new HashSet<>();
            Set<String> scheduledPages = new HashSet<>();
            List<String> pendingPages = new ArrayList<>();
            for (int pageIndex = 0; pageIndex == 0 || pageIndex <= pendingPages.size(); pageIndex++) {
                throwIfCancelled(token);
                Document page = pageIndex == 0 ? document : http.search(pendingPages.get(pageIndex - 1), source, null, token);
                String pageUrl = page.location();
                if (!pageUrl.isEmpty() && !visitedPages.add(pageUrl)) {
                    continue;
                }
                Elements elements = page.select(source.search.result);
                for (Element element : elements) {
                    String bookName = selectValue(element, source.search.bookName, true);
                    String url = selectValue(element, source.search.bookName, false);
                    if (bookName.isEmpty() || !isHttpUrl(url) || !seenBooks.add(url)) {
                        continue;
                    }
                    OnlineBookResult result = new OnlineBookResult();
                    result.sourceId = source.id;
                    result.sourceName = source.name;
                    result.bookName = bookName;
                    result.url = url;
                    result.author = cleanAuthor(selectValue(element, source.search.author, true));
                    result.category = selectValue(element, source.search.category, true);
                    result.latestChapter = selectValue(element, source.search.latestChapter, true);
                    result.lastUpdateTime = selectValue(element, source.search.lastUpdateTime, true);
                    result.status = selectValue(element, source.search.status, true);
                    results.add(result);
                }
                for (String nextUrl : extractSearchPageUrls(page, source)) {
                    if (isHttpUrl(nextUrl) && isSameHost(pageUrl, nextUrl)
                            && !visitedPages.contains(nextUrl) && scheduledPages.add(nextUrl) && pendingPages.size() < 100) {
                        pendingPages.add(nextUrl);
                    }
                }
            }
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Exception ignored) {
            // A single source failing should not prevent searching the remaining sources.
        }
        return results;
    }

    private List<String> extractSearchPageUrls(Document document, OnlineBookSource source) {
        List<String> urls = new ArrayList<>();
        if (source.search.nextPage == null || source.search.nextPage.trim().isEmpty()) {
            return urls;
        }
        for (Element element : selectElements(document, source.search.nextPage)) {
            String url = element.absUrl("href");
            if (url.isEmpty()) {
                url = element.absUrl("value");
            }
            if (!url.isEmpty() && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    private Document requestSearchDocument(OnlineBookSource source, String keyword, OnlineDownloadCancelToken token) throws Exception {
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
        String url = source.search.url.startsWith("@js:")
                ? buildDynamicSearchUrl(source, keyword)
                : source.search.url.replace("%s", encodedKeyword);
        FormBody.Builder form = new FormBody.Builder();
        if ("post".equalsIgnoreCase(source.search.method)) {
            Iterator<String> keys = source.search.data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = source.search.data.optString(key);
                form.add(key, value.replace("%s", keyword));
            }
            return http.search(url, source, form.build(), token);
        }
        return http.search(url, source, null, token);
    }

    private String buildDynamicSearchUrl(OnlineBookSource source, String keyword) throws Exception {
        if (!"quanben5".equals(source.id)) {
            throw new IllegalArgumentException("暂不支持这个书源的动态搜索地址");
        }
        String alphabet = "PXhw7UT1B0a9kQDKZsjIASmOezxYG4CHo5Jyfg2b8FLpEvRr3WtVnlqMidu6cN";
        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()).replace("+", "%20");
        StringBuilder encrypted = new StringBuilder();
        for (int i = 0; i < encoded.length(); i++) {
            char value = encoded.charAt(i);
            int index = alphabet.indexOf(value);
            char shifted = index < 0 ? value : alphabet.charAt((index + 3) % alphabet.length());
            encrypted.append(alphabet.charAt(random.nextInt(alphabet.length())))
                    .append(shifted)
                    .append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return "https://quanben5.com/?c=book&a=search.json&callback=search&keywords="
                + encoded + "&b=" + URLEncoder.encode(encrypted.toString(), StandardCharsets.UTF_8.name());
    }

    private OnlineBookInfo parseBookInfo(OnlineBookSource source, OnlineBookResult result, Document document) {
        OnlineBookInfo info = new OnlineBookInfo();
        info.title = fallback(selectValue(document, source.book.bookName, true), fallback(meta(document, "og:novel:book_name"), deriveTitleFromDocument(document, result.bookName)));
        info.author = cleanAuthor(fallback(selectValue(document, source.book.author, true), fallback(meta(document, "og:novel:author"), result.author)));
        info.category = fallback(selectValue(document, source.book.category, true), fallback(meta(document, "og:novel:category"), result.category));
        info.intro = fallback(selectValue(document, source.book.intro, true), meta(document, "og:description"));
        if (info.title.isEmpty()) {
            throw new IllegalStateException("详情页书名为空");
        }
        if (info.author.isEmpty()) {
            info.author = "未知作者";
        }
        return info;
    }

    private String deriveTitleFromDocument(Document document, String fallback) {
        String title = OnlineBookResult.clean(document.title());
        if (title.isEmpty()) {
            return fallback;
        }
        return title.replaceFirst("[_\\-—|｜].*$", "").trim();
    }

    List<OnlineChapterRef> parseToc(OnlineBookSource source, String bookUrl, Document detail, OnlineDownloadCancelToken token) throws Exception {
        List<OnlineChapterRef> chapters = new ArrayList<>();
        Set<String> chapterUrls = new HashSet<>();
        String firstTocUrl = resolveTocUrl(source, bookUrl);
        Set<String> visitedPages = new HashSet<>();
        Set<String> scheduledPages = new HashSet<>();
        List<String> tocPages = new ArrayList<>();
        tocPages.add(firstTocUrl);
        scheduledPages.add(firstTocUrl);
        for (int pageIndex = 0; pageIndex < tocPages.size(); pageIndex++) {
            throwIfCancelled(token);
            String tocUrl = tocPages.get(pageIndex);
            if (!visitedPages.add(tocUrl)) {
                continue;
            }
            Document document = detail != null && tocUrl.equals(bookUrl) ? detail : http.get(tocUrl, source, token);
            applyTocTransform(document, source);
            Elements elements = selectElements(document, source.toc.item);
            appendChapterRefs(source, chapters, chapterUrls, elements);
            for (String nextUrl : extractTocPageUrls(document, source)) {
                if (isHttpUrl(nextUrl) && isSameHost(tocUrl, nextUrl) && scheduledPages.add(nextUrl)) {
                    if (tocPages.size() >= 1000) throw new IllegalStateException("目录分页过多");
                    tocPages.add(nextUrl);
                }
            }
        }
        return chapters;
    }

    private void appendChapterRefs(OnlineBookSource source, List<OnlineChapterRef> chapters, Set<String> urls, Elements elements) {
        if (source.toc.desc) {
            for (int i = elements.size() - 1; i >= 0; i--) {
                addChapterRef(chapters, urls, elements.get(i));
            }
        } else {
            for (Element element : elements) {
                addChapterRef(chapters, urls, element);
            }
        }
    }

    private List<String> extractTocPageUrls(Document document, OnlineBookSource source) {
        List<String> urls = new ArrayList<>();
        if (source.toc.nextPage == null || source.toc.nextPage.trim().isEmpty()) {
            return urls;
        }
        for (Element element : selectElements(document, source.toc.nextPage)) {
            String url = element.absUrl("href");
            if (url.isEmpty()) {
                url = element.absUrl("value");
            }
            if (!url.isEmpty() && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    private String resolveTocUrl(OnlineBookSource source, String bookUrl) {
        String tocUrl = OnlineBookResult.clean(source.toc.url);
        String baseUri = OnlineBookResult.clean(source.toc.baseUri);
        if (tocUrl.isEmpty() && baseUri.isEmpty()) {
            return bookUrl;
        }
        String id = extractBookId(source, bookUrl);
        if (id.isEmpty()) {
            return bookUrl;
        }
        String pattern = tocUrl.isEmpty() ? baseUri : tocUrl;
        return String.format(Locale.US, pattern, id);
    }

    private String extractBookId(OnlineBookSource source, String bookUrl) {
        String pattern = OnlineBookResult.clean(source.book.url);
        if (pattern.isEmpty() || !pattern.contains("(")) {
            return "";
        }
        try {
            Matcher matcher = Pattern.compile(pattern).matcher(bookUrl);
            return matcher.find() && matcher.groupCount() >= 1 ? matcher.group(1) : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private void addChapterRef(List<OnlineChapterRef> chapters, Set<String> urls, Element element) {
        String title = OnlineBookResult.clean(element.text());
        String url = element.absUrl("href");
        if (!title.isEmpty() && isHttpUrl(url) && urls.add(url)) {
            OnlineChapterRef chapter = new OnlineChapterRef();
            chapter.title = title;
            chapter.url = url;
            chapters.add(chapter);
        }
    }

    String downloadChapterContent(OnlineBookSource source, OnlineChapterRef ref, OnlineDownloadCancelToken cancelToken) throws Exception {
        return downloadChapterContent(source, ref, cancelToken, java.util.Collections.emptySet());
    }

    private String downloadChapterContent(OnlineBookSource source, OnlineChapterRef ref,
                                         OnlineDownloadCancelToken cancelToken, Set<String> chapterUrls) throws Exception {
        Exception lastError = null;
        int attempts = Math.max(1, source.crawl.maxRetries + 1);
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                String title = "";
                StringBuilder chapterBuilder = new StringBuilder();
                Set<String> visitedPages = new HashSet<>();
                List<String> pageUrls = new ArrayList<>();
                pageUrls.add(ref.url);
                for (int pageIndex = 0; pageIndex < pageUrls.size(); pageIndex++) {
                    throwIfCancelled(cancelToken);
                    String pageUrl = pageUrls.get(pageIndex);
                    if (!visitedPages.add(pageUrl)) {
                        continue;
                    }
                    Document document = http.get(pageUrl, source, cancelToken);
                    throwIfCancelled(cancelToken);
                    if (title.isEmpty()) {
                        title = fallback(selectValue(document, source.chapter.title, true), ref.title)
                                .replaceFirst("\\(\\d+/\\d+\\)$", "").trim();
                    }
                    List<String> nextPages = extractChapterPageUrls(document, source, pageUrl, ref.url);
                    String body = extractChapterBody(document, source.chapter);
                    if (body.isEmpty()) throw new IllegalStateException("正文为空：" + pageUrl);
                    if (body.matches("(?s).*(访问太频繁|访问过于频繁|请\\d+秒过后刷新|error code: 1015).*")) {
                        throw new IllegalStateException("网站限流：" + pageUrl);
                    }
                    if (!body.isEmpty()) {
                        if (chapterBuilder.length() > 0) {
                            chapterBuilder.append("\n\n");
                        }
                        chapterBuilder.append(body);
                    }
                    for (String nextUrl : nextPages) {
                        if (!chapterUrls.contains(nextUrl) && !visitedPages.contains(nextUrl) && !pageUrls.contains(nextUrl)) {
                            pageUrls.add(nextUrl);
                        }
                    }
                    if (pageUrls.size() > 50) {
                        throw new IllegalStateException("章节分页过多，可能误入下一章");
                    }
                }
                String body = normalizeParagraphs(chapterBuilder.toString());
                if (body.isEmpty()) {
                    throw new IllegalStateException("正文为空");
                }
                title = title.isEmpty() ? ref.title : title;
                return title + "\n\n" + body;
            } catch (CancellationException e) {
                throw e;
            } catch (OnlineHttpClient.HttpFailure e) {
                // HTTP retries and Retry-After were already handled by the transport.
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationException("下载已取消");
            } catch (Exception e) {
                lastError = e;
                if (attempt + 1 < attempts) {
                    long backoff = e.getMessage() != null && e.getMessage().contains("网站限流")
                            ? 30_000 : OnlineHttpClient.retryDelay(null, attempt);
                    for (long waited = 0; waited < backoff; waited += 100) {
                        throwIfCancelled(cancelToken);
                        Thread.sleep(Math.min(100, backoff - waited));
                    }
                }
            }
        }
        throw lastError == null ? new IllegalStateException("章节下载失败") : lastError;
    }

    private List<String> extractChapterPageUrls(Document document, OnlineBookSource source, String currentUrl, String firstUrl) {
        List<String> urls = new ArrayList<>();
        String nextPageInJs = OnlineBookResult.clean(source.chapter.nextPageInJs);
        if (!nextPageInJs.isEmpty()) {
            String nextUrl = resolveNextUrlFromJsRule(document, nextPageInJs, currentUrl);
            if (shouldFollowChapterPage(document, source, nextUrl, "", firstUrl, currentUrl, false)) {
                urls.add(nextUrl);
                return urls;
            }
        }
        String nextPageRule = OnlineBookResult.clean(source.chapter.nextPage);
        if (!nextPageRule.isEmpty()) {
            Elements nextElements = selectElements(document, nextPageRule);
            for (Element element : nextElements) {
                addChapterPageUrl(urls, element, source, currentUrl, firstUrl, false);
            }
            return urls;
        }
        for (Element element : document.select("a")) {
            String text = OnlineBookResult.clean(element.text());
            if ((text.contains("下一页") || text.contains("继续阅读")) && !text.contains("下一章")) {
                addChapterPageUrl(urls, element, source, currentUrl, firstUrl, true);
            }
        }
        return urls;
    }

    private void addChapterPageUrl(List<String> urls, Element element, OnlineBookSource source, String currentUrl, String firstUrl, boolean requireSameChapterShape) {
        String url = element.absUrl("href");
        if (url.isEmpty()) {
            url = element.absUrl("value");
        }
        String text = OnlineBookResult.clean(element.text());
        if (shouldFollowChapterPage(element.ownerDocument(), source, url, text, firstUrl, currentUrl, requireSameChapterShape) && !urls.contains(url)) {
            urls.add(url);
        }
    }

    private boolean shouldFollowChapterPage(Document document, OnlineBookSource source, String nextUrl, String nextText, String firstUrl, String currentUrl, boolean requireSameChapterShape) {
        if (!isHttpUrl(nextUrl) || nextUrl.equals(currentUrl)) {
            return false;
        }
        String cleanNextText = OnlineBookResult.clean(nextText);
        if (isLastChapterPage(nextUrl, cleanNextText, source)) {
            return false;
        }
        if (requireSameChapterShape) {
            return isLikelySameChapterPage(firstUrl, currentUrl, nextUrl);
        }
        return isSameHost(firstUrl, nextUrl) || (document != null && isSameHost(document.baseUri(), nextUrl));
    }

    private boolean isLastChapterPage(String nextUrl, String nextText, OnlineBookSource source) {
        String nextChapterLink = OnlineBookResult.clean(source.chapter.nextChapterLink);
        if (!nextChapterLink.isEmpty()) {
            try {
                if (nextUrl.matches(nextChapterLink)) {
                    return true;
                }
            } catch (Exception ignored) {
                // Keep downloading if a custom regex is invalid instead of failing the whole chapter.
            }
        }
        return !nextUrl.matches(".*[-_]\\d+\\.html?")
                && nextText.matches(".*(下一章|没有了|>>|书末页).*");
    }

    private String resolveNextUrlFromJsRule(Document document, String rule, String currentUrl) {
        String[] parts = splitJsRule(rule);
        if (parts[0].isEmpty() || parts[1].isEmpty()) {
            return "";
        }
        Elements elements = selectElements(document, parts[0]);
        if (elements.isEmpty()) {
            return "";
        }
        String input = elements.size() == 1 ? elements.first().html() : elements.html();
        String extracted = extractByJsMatch(input, parts[1]);
        if (extracted.isEmpty()) {
            extracted = extractByJsMatch(document.html(), parts[1]);
        }
        if (extracted.isEmpty()) {
            return "";
        }
        return resolveUrl(currentUrl, extracted);
    }

    private String[] splitJsRule(String rule) {
        String clean = rule == null ? "" : rule.trim();
        int jsIndex = clean.indexOf("@js:");
        if (jsIndex < 0) {
            return new String[]{clean, ""};
        }
        return new String[]{clean.substring(0, jsIndex).trim(), clean.substring(jsIndex + 4).trim()};
    }

    private String extractByJsMatch(String input, String script) {
        String regex = extractJsRegexLiteral(script);
        if (regex.isEmpty()) {
            return "";
        }
        try {
            Matcher matcher = Pattern.compile(regex, Pattern.DOTALL).matcher(input);
            if (matcher.find() && matcher.groupCount() >= 1) {
                return OnlineBookResult.clean(matcher.group(1));
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private String extractJsRegexLiteral(String script) {
        int matchIndex = script.indexOf(".match(/");
        if (matchIndex < 0) {
            matchIndex = script.indexOf("match(/");
        }
        if (matchIndex < 0) {
            return "";
        }
        int start = script.indexOf('/', matchIndex);
        if (start < 0) {
            return "";
        }
        boolean escaping = false;
        for (int i = start + 1; i < script.length(); i++) {
            char value = script.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (value == '\\') {
                escaping = true;
                continue;
            }
            if (value == '/') {
                return script.substring(start + 1, i).replace("\\/", "/");
            }
        }
        return "";
    }

    private String resolveUrl(String baseUrl, String targetUrl) {
        try {
            return URI.create(baseUrl).resolve(targetUrl).toString();
        } catch (Exception ignored) {
            return targetUrl;
        }
    }

    private boolean isSameHost(String leftUrl, String rightUrl) {
        try {
            return sameHost(URI.create(leftUrl), URI.create(rightUrl));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isLikelySameChapterPage(String firstUrl, String currentUrl, String nextUrl) {
        try {
            URI first = URI.create(firstUrl);
            URI current = URI.create(currentUrl);
            URI next = URI.create(nextUrl);
            if (nextUrl.equals(currentUrl) || !sameHost(first, next)) {
                return false;
            }
            String firstPath = first.getPath() == null ? "" : first.getPath();
            String nextPath = next.getPath() == null ? "" : next.getPath();
            String base = firstPath.replaceFirst("(\\.html?|/)?$", "");
            if (!base.isEmpty() && nextPath.matches(Pattern.quote(base) + "([_\\-]\\d+|/\\d+)?\\.html?")) {
                return true;
            }
            String currentPath = current.getPath() == null ? "" : current.getPath();
            return sameDirectory(firstPath, nextPath) && sameFileStem(firstPath, currentPath, nextPath);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean sameHost(URI first, URI next) {
        String firstHost = first.getHost() == null ? "" : first.getHost();
        String nextHost = next.getHost() == null ? "" : next.getHost();
        return firstHost.equalsIgnoreCase(nextHost);
    }

    private boolean sameDirectory(String firstPath, String nextPath) {
        int firstSlash = firstPath.lastIndexOf('/');
        int nextSlash = nextPath.lastIndexOf('/');
        String firstDir = firstSlash >= 0 ? firstPath.substring(0, firstSlash + 1) : "";
        String nextDir = nextSlash >= 0 ? nextPath.substring(0, nextSlash + 1) : "";
        return firstDir.equals(nextDir);
    }

    private boolean sameFileStem(String firstPath, String currentPath, String nextPath) {
        String firstStem = fileStem(firstPath).replaceFirst("[_\\-]\\d+$", "");
        String currentStem = fileStem(currentPath).replaceFirst("[_\\-]\\d+$", "");
        String nextStem = fileStem(nextPath).replaceFirst("[_\\-]\\d+$", "");
        return !firstStem.isEmpty() && firstStem.equals(currentStem) && firstStem.equals(nextStem);
    }

    private String fileStem(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.replaceFirst("\\.html?$", "");
    }

    private boolean isHttpUrl(String url) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ignored) { return false; }
    }

    private String meta(Document document, String property) {
        return selectValue(document, "meta[property=\"" + property + "\"], meta[name=\"" + property + "\"]", true);
    }

    String extractChapterBody(Document document, OnlineBookSource.ChapterRule rule) {
        Elements elements = selectElements(document, rule.content).clone();
        if (elements.isEmpty()) {
            return "";
        }
        if (rule.filterTag != null && !rule.filterTag.trim().isEmpty()) {
            selectElements(elements, rule.filterTag).remove();
        }
        StringBuilder builder = new StringBuilder();
        String html = transformChapterHtml(elements.html(), rule)
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|dd|li|section|article)>", "\n")
                .replaceAll("(?i)<(p|div|dd|li|section|article)(\\s[^>]*)?>", "\n");
        String rawText = Jsoup.parse(html.replace("\n", " __NOVEL_LINE__ ")).text()
                .replaceAll("\\s*__NOVEL_LINE__\\s*", "\n");
        String[] lines = rawText.split("\\r?\\n+");
        for (String line : lines) {
            appendParagraph(builder, line);
        }
        String text = builder.toString().trim();
        if (rule.filterTxt != null && !rule.filterTxt.trim().isEmpty()) {
            text = text.replaceAll(rule.filterTxt, "");
        }
        return normalizeParagraphs(text);
    }

    private String transformChapterHtml(String html, OnlineBookSource.ChapterRule rule) {
        String transformed = html == null ? "" : html;
        if (transformed.contains("document.writeln") || (rule.content != null && rule.content.contains("base64.decode"))) {
            // Replace the whole script, not only its call: text left inside a script
            // is deliberately ignored by Jsoup and would silently disappear.
            Matcher matcher = Pattern.compile("(?:<script[^>]*>\\s*)?document\\.writeln\\(qsbs\\.bb\\(['\"]([^'\"]+)['\"]\\)\\);?\\s*(?:</script>)?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(transformed);
            StringBuffer output = new StringBuffer();
            while (matcher.find()) {
                String decoded = decodeBase64Utf8(matcher.group(1));
                matcher.appendReplacement(output, Matcher.quoteReplacement(decoded));
            }
            matcher.appendTail(output);
            transformed = output.toString();
        }
        if (transformed.contains("data-id=") && rule.content != null && rule.content.contains("data-id")) {
            Matcher matcher = Pattern.compile("(?is)<dd\\s+[^>]*data-id=['\"]?(\\d+)['\"]?[^>]*>(.*?)</dd>").matcher(transformed);
            List<DataIdBlock> blocks = new ArrayList<>();
            while (matcher.find()) {
                blocks.add(new DataIdBlock(Integer.parseInt(matcher.group(1)), matcher.group(2)));
            }
            if (!blocks.isEmpty()) {
                blocks.sort((left, right) -> Integer.compare(left.id, right.id));
                StringBuilder ordered = new StringBuilder();
                for (DataIdBlock block : blocks) ordered.append(block.html);
                transformed = ordered.toString();
            }
        }
        return transformed;
    }

    private String decodeBase64Utf8(String encoded) {
        try {
            ByteString decoded = ByteString.decodeBase64(encoded);
            return decoded == null ? "" : decoded.utf8();
        } catch (Exception ignored) {
            return encoded;
        }
    }

    private void applyTocTransform(Document document, OnlineBookSource source) {
        if (!"wxsy".equals(source.id)) {
            return;
        }
        String html = document.html();
        int hiddenFromStart = countMatches(html, "\\.section-list\\.ycxsid\\s*>\\s*li:nth-child\\(\\d+\\)\\s*\\{\\s*display\\s*:\\s*none");
        int hiddenFromEnd = countMatches(html, "\\.section-list\\.ycxsid\\s*>\\s*li:nth-last-child\\(\\d+\\)\\s*\\{\\s*display\\s*:\\s*none");
        for (Element list : document.select("ul.section-list.ycxsid")) {
            Elements items = list.children();
            for (int i = 0; i < hiddenFromStart && !items.isEmpty(); i++) {
                items.first().remove();
                items = list.children();
            }
            for (int i = 0; i < hiddenFromEnd && !items.isEmpty(); i++) {
                items.last().remove();
                items = list.children();
            }
        }
    }

    private int countMatches(String value, String regex) {
        int count = 0;
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value);
        while (matcher.find()) count++;
        return count;
    }

    private void appendParagraph(StringBuilder builder, String value) {
        String clean = OnlineBookResult.clean(value);
        if (clean.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(clean);
    }

    private String normalizeParagraphs(String text) {
        String[] lines = text.split("\\r?\\n+");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            appendParagraph(builder, line);
        }
        return builder.toString();
    }

    private String selectValue(Element scope, String rule, boolean preferText) {
        if (rule == null || rule.trim().isEmpty()) {
            return "";
        }
        SelectorQuery query = SelectorQuery.parse(rule);
        Elements elements = selectElements(scope, query.selector);
        if (elements.isEmpty()) {
            return "";
        }
        if (query.attribute != null) {
            String value = "href".equals(query.attribute) || "src".equals(query.attribute)
                    ? elements.first().absUrl(query.attribute) : elements.first().attr(query.attribute);
            return OnlineBookResult.clean(value);
        }
        if (!preferText) {
            String href = elements.first().absUrl("href");
            if (!href.isEmpty()) {
                return href;
            }
        }
        String metaContent = elements.first().attr("content");
        if (!metaContent.isEmpty()) {
            return OnlineBookResult.clean(metaContent);
        }
        return OnlineBookResult.clean(elements.text());
    }

    private Elements selectElements(Element scope, String rule) {
        if (scope == null || rule == null || rule.trim().isEmpty()) {
            return new Elements();
        }
        String selector = splitJsRule(rule)[0];
        int javaIndex = selector.indexOf("@java:");
        if (javaIndex >= 0) {
            selector = selector.substring(0, javaIndex).trim();
        }
        if (selector.isEmpty()) {
            return new Elements();
        }
        try {
            if (selector.matches("^(/|//|\\(/).*")) {
                return scope.selectXpath(selector);
            }
            return scope.select(selector);
        } catch (Exception ignored) {
            return new Elements();
        }
    }

    private Elements selectElements(Elements scopes, String rule) {
        Elements selected = new Elements();
        if (scopes == null) {
            return selected;
        }
        for (Element scope : scopes) {
            selected.addAll(selectElements(scope, rule));
        }
        return selected;
    }

    private String fallback(String preferred, String fallback) {
        String cleanPreferred = OnlineBookResult.clean(preferred);
        return cleanPreferred.isEmpty() ? OnlineBookResult.clean(fallback) : cleanPreferred;
    }

    private String cleanAuthor(String value) {
        return OnlineBookResult.clean(value)
                .replaceFirst("^(作者|作 者)[:：\\s]*", "")
                .replaceFirst("^作者", "")
                .trim();
    }

    private void notifyProgress(OnlineDownloadProgress progress, String message) {
        if (progress != null) {
            progress.onProgress(message);
        }
    }

    private void notifySearchProgress(OnlineSearchProgress progress, String message, int completed, int total) {
        if (progress != null) {
            progress.onProgress(message, completed, total);
        }
    }

    private void throwIfCancelled(OnlineDownloadCancelToken cancelToken) {
        OnlineHttpClient.checkCancelled(cancelToken);
    }

    private String normalizeFormat(String format) {
        return FORMAT_EPUB.equalsIgnoreCase(format) ? FORMAT_EPUB : FORMAT_TXT;
    }

    void writeTxt(File file, OnlineBookInfo bookInfo, OnlineBookSource source, String url, List<OnlineChapterRef> toc, List<File> contents, OnlineDownloadCancelToken token) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(bookInfo.title + "\n" + bookInfo.author + "\nsource=" + source.name + "\nurl=" + url + "\n\n");
            for (File chapter : contents) {
                throwIfCancelled(token);
                // Chapter files already start with their title; do not duplicate it.
                writer.write(readText(chapter));
                writer.write("\n\n");
            }
        }
    }

    void writeEpub(File file, OnlineBookInfo bookInfo, List<OnlineChapterRef> toc, List<File> contents, OnlineDownloadCancelToken token) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            putStoredMimetype(zip);
            putZipEntry(zip, "META-INF/container.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                    + "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
                    + "</rootfiles></container>");
            StringBuilder manifest = new StringBuilder();
            StringBuilder spine = new StringBuilder();
            StringBuilder nav = new StringBuilder();
            nav.append("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\"><head><title>")
                    .append(escapeXml(bookInfo.title)).append("</title></head><body><nav epub:type=\"toc\"><ol>");
            for (int i = 0; i < contents.size(); i++) {
                throwIfCancelled(token);
                String id = "chapter" + i;
                String href = "chapters/" + id + ".xhtml";
                manifest.append("<item id=\"").append(id).append("\" href=\"").append(href).append("\" media-type=\"application/xhtml+xml\"/>");
                spine.append("<itemref idref=\"").append(id).append("\"/>");
                nav.append("<li><a href=\"").append(href).append("\">").append(escapeXml(toc.get(i).title)).append("</a></li>");
                putZipEntry(zip, "OEBPS/" + href, chapterXhtml(bookInfo.title, toc.get(i).title, readText(contents.get(i))));
            }
            nav.append("</ol></nav></body></html>");
            putZipEntry(zip, "OEBPS/nav.xhtml", nav.toString());
            putZipEntry(zip, "OEBPS/content.opf", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"bookid\" version=\"3.0\">"
                    + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"bookid\">" + System.currentTimeMillis()
                    + "</dc:identifier><dc:title>" + escapeXml(bookInfo.title) + "</dc:title><dc:creator>" + escapeXml(bookInfo.author)
                    + "</dc:creator><dc:language>zh-CN</dc:language></metadata><manifest><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
                    + manifest + "</manifest><spine>" + spine + "</spine></package>");
        }
    }

    private void putStoredMimetype(ZipOutputStream zip) throws Exception {
        byte[] data = "application/epub+zip".getBytes(StandardCharsets.UTF_8);
        ZipEntry entry = new ZipEntry("mimetype");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private void putZipEntry(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String chapterXhtml(String bookTitle, String chapterTitle, String content) {
        StringBuilder body = new StringBuilder();
        body.append("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>")
                .append(escapeXml(chapterTitle)).append("</title></head><body><h1>")
                .append(escapeXml(chapterTitle)).append("</h1>");
        for (String paragraph : content.split("\\r?\\n+")) {
            String clean = OnlineBookResult.clean(paragraph);
            if (!clean.isEmpty() && !clean.equals(bookTitle) && !clean.equals(chapterTitle)) {
                body.append("<p>").append(escapeXml(clean)).append("</p>");
            }
        }
        body.append("</body></html>");
        return body.toString();
    }

    private String escapeXml(String value) {
        return OnlineBookResult.clean(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String safeFileName(String name) {
        String clean = OnlineBookResult.clean(name).replaceAll("[\\\\/:*?\"<>|]", "_");
        return clean.isEmpty() ? "online_book" : clean;
    }

    private void writeText(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String readText(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) != -1;) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void ensureDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建目录：" + dir.getAbsolutePath());
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    static class OnlineBookInfo {
        String title;
        String author;
        String category;
        String intro;
    }

    static class OnlineChapterRef {
        String title;
        String url;
    }

    private static class DataIdBlock {
        final int id;
        final String html;

        DataIdBlock(int id, String html) {
            this.id = id;
            this.html = html;
        }
    }

    private static class SelectorQuery {
        String selector;
        String attribute;

        static SelectorQuery parse(String rule) {
            SelectorQuery query = new SelectorQuery();
            String clean = rule.trim();
            int at = clean.lastIndexOf('@');
            if (at > 0 && at < clean.length() - 1 && clean.indexOf(']', at) == -1) {
                String attr = clean.substring(at + 1).trim();
                if ("href".equals(attr) || "src".equals(attr) || "content".equals(attr) || "value".equals(attr)) {
                    query.selector = clean.substring(0, at).trim();
                    query.attribute = attr;
                    return query;
                }
            }
            query.selector = clean;
            return query;
        }
    }
}
