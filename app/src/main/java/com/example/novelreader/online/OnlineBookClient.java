package com.example.novelreader.online;

import android.content.Context;

import com.example.novelreader.data.AppDatabase;
import com.example.novelreader.data.BookEntity;
import com.example.novelreader.data.ChapterEntity;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

public class OnlineBookClient {
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";
    public static final String FORMAT_TXT = "txt";
    public static final String FORMAT_EPUB = "epub";

    private final Context context;
    private final AppDatabase database;
    private final OnlineSourceRepository sourceRepository;
    private final Random random = new Random();

    public OnlineBookClient(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(context);
        this.sourceRepository = new OnlineSourceRepository(context);
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
        int completed = 0;
        for (OnlineBookSource source : sources) {
            notifySearchProgress(progress, "正在搜索：" + source.name, completed, sources.size());
            allResults.addAll(searchSource(source, cleanKeyword));
            completed++;
            notifySearchProgress(progress, "已完成：" + source.name, completed, sources.size());
        }
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
        OnlineBookInfo bookInfo = parseBookInfo(source, result);
        throwIfCancelled(cancelToken);
        if (database.bookDao().getByTitle(bookInfo.title) != null) {
            throw new IllegalStateException("书架里已存在《" + bookInfo.title + "》");
        }

        notifyProgress(progress, "正在读取目录...");
        List<OnlineChapterRef> toc = parseToc(source, result.url);
        throwIfCancelled(cancelToken);
        if (toc.isEmpty()) {
            throw new IllegalStateException("目录为空，无法下载");
        }

        File bookDir = new File(context.getFilesDir(), "books/online_" + System.currentTimeMillis());
        ensureDir(bookDir);
        File originalFile = new File(bookDir, safeFileName(bookInfo.title) + "." + format);

        File chaptersDir = new File(bookDir, "chapters");
        ensureDir(chaptersDir);
        long bookId = -1L;
        List<String> downloadedContents = new ArrayList<>();
        try {
            long now = System.currentTimeMillis();
            BookEntity book = new BookEntity();
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
            bookId = database.bookDao().insert(book);

            List<String> downloaded = downloadChapterContents(source, toc, progress, cancelToken);
            List<ChapterEntity> chapters = new ArrayList<>();
            for (int i = 0; i < toc.size(); i++) {
                throwIfCancelled(cancelToken);
                OnlineChapterRef ref = toc.get(i);
                String content = downloaded.get(i);
                downloadedContents.add(content);
                File chapterFile = new File(chaptersDir, String.format(Locale.US, "%04d.txt", i));
                writeText(chapterFile, content);

                ChapterEntity chapter = new ChapterEntity();
                chapter.bookId = bookId;
                chapter.chapterIndex = i;
                chapter.title = ref.title;
                chapter.contentPath = chapterFile.getAbsolutePath();
                chapters.add(chapter);
            }
            throwIfCancelled(cancelToken);
            notifyProgress(progress, "正在生成 " + format.toUpperCase(Locale.US) + " 文件...");
            if (FORMAT_EPUB.equals(format)) {
                writeEpub(originalFile, bookInfo, toc, downloadedContents);
            } else {
                writeTxt(originalFile, bookInfo, source, result.url, toc, downloadedContents);
            }
            throwIfCancelled(cancelToken);
            database.chapterDao().insertAll(chapters);
            notifyProgress(progress, "下载完成：《" + bookInfo.title + "》");
            return bookId;
        } catch (Exception e) {
            if (bookId > 0) {
                BookEntity inserted = database.bookDao().getById(bookId);
                if (inserted != null) {
                    database.bookDao().delete(inserted);
                }
            }
            deleteRecursively(bookDir);
            throw e;
        }
    }

    private List<String> downloadChapterContents(OnlineBookSource source, List<OnlineChapterRef> toc, OnlineDownloadProgress progress, OnlineDownloadCancelToken cancelToken) throws Exception {
        int concurrency = Math.max(1, Math.min(source.crawl.concurrency, toc.size()));
        if (concurrency == 1) {
            List<String> contents = new ArrayList<>();
            for (int i = 0; i < toc.size(); i++) {
                throwIfCancelled(cancelToken);
                OnlineChapterRef ref = toc.get(i);
                notifyProgress(progress, "正在下载 " + (i + 1) + "/" + toc.size() + "：" + ref.title);
                contents.add(downloadChapterContent(source, ref, cancelToken));
            }
            return contents;
        }

        notifyProgress(progress, "并发下载中：0/" + toc.size() + "，线程 " + concurrency);
        List<String> contents = new ArrayList<>(Collections.nCopies(toc.size(), ""));
        AtomicInteger completed = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        List<Future<ChapterDownload>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < toc.size(); i++) {
                int index = i;
                OnlineChapterRef ref = toc.get(i);
                futures.add(pool.submit(() -> {
                    throwIfCancelled(cancelToken);
                    String content = downloadChapterContent(source, ref, cancelToken);
                    int done = completed.incrementAndGet();
                    notifyProgress(progress, "并发下载中：" + done + "/" + toc.size() + "：" + ref.title);
                    return new ChapterDownload(index, content);
                }));
            }
            for (Future<ChapterDownload> future : futures) {
                throwIfCancelled(cancelToken);
                ChapterDownload download = future.get();
                contents.set(download.index, download.content);
            }
            return contents;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<OnlineBookResult> searchSource(OnlineBookSource source, String keyword) {
        List<OnlineBookResult> results = new ArrayList<>();
        if (source.search.disabled || source.search.result == null || source.search.result.trim().isEmpty()) {
            return results;
        }
        try {
            Document document = requestSearchDocument(source, keyword);
            Set<String> visitedPages = new HashSet<>();
            List<Document> pages = new ArrayList<>();
            pages.add(document);
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                Document page = pages.get(pageIndex);
                String pageUrl = page.location();
                if (!pageUrl.isEmpty() && !visitedPages.add(pageUrl)) {
                    continue;
                }
                Elements elements = page.select(source.search.result);
                for (Element element : elements) {
                    String bookName = selectValue(element, source.search.bookName, true);
                    String url = selectValue(element, source.search.bookName, false);
                    if (bookName.isEmpty() || url.isEmpty()) {
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
                    if (!visitedPages.contains(nextUrl)) {
                        pages.add(requestDocument(nextUrl, source));
                    }
                }
            }
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
        for (Element element : document.select(source.search.nextPage)) {
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

    private Document requestSearchDocument(OnlineBookSource source, String keyword) throws Exception {
        String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
        String url = source.search.url.replace("%s", encodedKeyword);
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(source.baseUrl)
                .timeout(source.crawl.timeoutMillis)
                .ignoreHttpErrors(true)
                .ignoreContentType(true);
        if ("post".equalsIgnoreCase(source.search.method)) {
            Iterator<String> keys = source.search.data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = source.search.data.optString(key);
                connection.data(key, "%s".equals(value) ? keyword : value);
            }
            return connection.method(Connection.Method.POST).post();
        }
        return connection.get();
    }

    private OnlineBookInfo parseBookInfo(OnlineBookSource source, OnlineBookResult result) throws Exception {
        Document document = requestDocument(result.url, source);
        OnlineBookInfo info = new OnlineBookInfo();
        info.title = fallback(selectValue(document, source.book.bookName, true), deriveTitleFromDocument(document, result.bookName));
        info.author = cleanAuthor(fallback(selectValue(document, source.book.author, true), result.author));
        info.category = fallback(selectValue(document, source.book.category, true), result.category);
        info.intro = selectValue(document, source.book.intro, true);
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

    private List<OnlineChapterRef> parseToc(OnlineBookSource source, String bookUrl) throws Exception {
        List<OnlineChapterRef> chapters = new ArrayList<>();
        String firstTocUrl = resolveTocUrl(source, bookUrl);
        Set<String> visitedPages = new HashSet<>();
        List<String> tocPages = new ArrayList<>();
        tocPages.add(firstTocUrl);
        for (int pageIndex = 0; pageIndex < tocPages.size(); pageIndex++) {
            String tocUrl = tocPages.get(pageIndex);
            if (!visitedPages.add(tocUrl)) {
                continue;
            }
            Document document = requestDocument(tocUrl, source);
            Elements elements = document.select(source.toc.item);
            appendChapterRefs(source, chapters, elements);
            for (String nextUrl : extractTocPageUrls(document, source)) {
                if (!visitedPages.contains(nextUrl) && !tocPages.contains(nextUrl)) {
                    tocPages.add(nextUrl);
                }
            }
        }
        return chapters;
    }

    private void appendChapterRefs(OnlineBookSource source, List<OnlineChapterRef> chapters, Elements elements) {
        if (source.toc.desc) {
            for (int i = elements.size() - 1; i >= 0; i--) {
                addChapterRef(chapters, elements.get(i));
            }
        } else {
            for (Element element : elements) {
                addChapterRef(chapters, element);
            }
        }
    }

    private List<String> extractTocPageUrls(Document document, OnlineBookSource source) {
        List<String> urls = new ArrayList<>();
        if (source.toc.nextPage == null || source.toc.nextPage.trim().isEmpty()) {
            return urls;
        }
        for (Element element : document.select(source.toc.nextPage)) {
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

    private void addChapterRef(List<OnlineChapterRef> chapters, Element element) {
        String title = OnlineBookResult.clean(element.text());
        String url = element.absUrl("href");
        if (!title.isEmpty() && !url.isEmpty()) {
            for (OnlineChapterRef existing : chapters) {
                if (url.equals(existing.url)) {
                    return;
                }
            }
            OnlineChapterRef chapter = new OnlineChapterRef();
            chapter.title = title;
            chapter.url = url;
            chapters.add(chapter);
        }
    }

    private String downloadChapterContent(OnlineBookSource source, OnlineChapterRef ref, OnlineDownloadCancelToken cancelToken) throws Exception {
        Exception lastError = null;
        int attempts = Math.max(1, source.crawl.maxRetries + 1);
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                throwIfCancelled(cancelToken);
                sleepBetweenRequests(source);
                throwIfCancelled(cancelToken);
                Document document = requestDocument(ref.url, source);
                throwIfCancelled(cancelToken);
                String title = fallback(selectValue(document, source.chapter.title, true), ref.title);
                String body = extractChapterBody(document, source.chapter);
                if (body.isEmpty()) {
                    throw new IllegalStateException("正文为空");
                }
                return title + "\n\n" + body;
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                lastError = e;
                sleepBetweenRequests(source);
            }
        }
        throw lastError == null ? new IllegalStateException("章节下载失败") : lastError;
    }

    private Document requestDocument(String url, OnlineBookSource source) throws Exception {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .referrer(source.baseUrl)
                .timeout(source.crawl.timeoutMillis)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .get();
    }

    private String extractChapterBody(Document document, OnlineBookSource.ChapterRule rule) {
        Elements elements = document.select(rule.content);
        if (elements.isEmpty()) {
            return "";
        }
        if (rule.filterTag != null && !rule.filterTag.trim().isEmpty()) {
            elements.select(rule.filterTag).remove();
        }
        StringBuilder builder = new StringBuilder();
        String html = elements.html()
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
        Elements elements = scope.select(query.selector);
        if (elements.isEmpty()) {
            return "";
        }
        if (query.attribute != null) {
            return OnlineBookResult.clean(elements.first().absUrl(query.attribute));
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

    private void sleepBetweenRequests(OnlineBookSource source) throws InterruptedException {
        int min = Math.max(0, source.crawl.minIntervalMillis);
        int max = Math.max(min + 1, source.crawl.maxIntervalMillis);
        Thread.sleep(min + random.nextInt(max - min));
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
        if (cancelToken != null) {
            cancelToken.throwIfCancelled();
        }
    }

    private String normalizeFormat(String format) {
        return FORMAT_EPUB.equalsIgnoreCase(format) ? FORMAT_EPUB : FORMAT_TXT;
    }

    private void writeTxt(File file, OnlineBookInfo bookInfo, OnlineBookSource source, String url, List<OnlineChapterRef> toc, List<String> contents) throws Exception {
        StringBuilder builder = new StringBuilder();
        builder.append(bookInfo.title).append("\n");
        builder.append(bookInfo.author).append("\n");
        builder.append("source=").append(source.name).append("\n");
        builder.append("url=").append(url).append("\n\n");
        for (int i = 0; i < contents.size(); i++) {
            builder.append(toc.get(i).title).append("\n\n");
            builder.append(contents.get(i)).append("\n\n");
        }
        writeText(file, builder.toString());
    }

    private void writeEpub(File file, OnlineBookInfo bookInfo, List<OnlineChapterRef> toc, List<String> contents) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
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
                String id = "chapter" + i;
                String href = "chapters/" + id + ".xhtml";
                manifest.append("<item id=\"").append(id).append("\" href=\"").append(href).append("\" media-type=\"application/xhtml+xml\"/>");
                spine.append("<itemref idref=\"").append(id).append("\"/>");
                nav.append("<li><a href=\"").append(href).append("\">").append(escapeXml(toc.get(i).title)).append("</a></li>");
                putZipEntry(zip, "OEBPS/" + href, chapterXhtml(bookInfo.title, toc.get(i).title, contents.get(i)));
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

    private static class OnlineBookInfo {
        String title;
        String author;
        String category;
        String intro;
    }

    private static class OnlineChapterRef {
        String title;
        String url;
    }

    private static class ChapterDownload {
        final int index;
        final String content;

        ChapterDownload(int index, String content) {
            this.index = index;
            this.content = content;
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
