package com.wjnocal.novelreader.online;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

public class OnlineDownloadTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private OnlineBookSource source(MockWebServer server) {
        OnlineBookSource source = new OnlineBookSource();
        source.id = "test";
        source.baseUrl = server.url("/").toString();
        source.crawl.minIntervalMillis = 0;
        source.crawl.maxIntervalMillis = 0;
        source.crawl.maxRetries = 0;
        source.chapter.title = "h1";
        source.chapter.content = "#content";
        source.chapter.nextPage = "#next";
        source.toc.item = "#toc a";
        return source;
    }

    private MockResponse chapter(int index) {
        return new MockResponse().setBody("<h1>第" + index + "章</h1><div id=content><p>第一段" + index + "</p><p>第二段</p></div>");
    }

    private List<OnlineBookClient.OnlineChapterRef> toc(MockWebServer server, int count) {
        List<OnlineBookClient.OnlineChapterRef> refs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OnlineBookClient.OnlineChapterRef ref = new OnlineBookClient.OnlineChapterRef();
            ref.title = "第" + i + "章";
            ref.url = server.url("/" + i + ".html").toString();
            refs.add(ref);
        }
        return refs;
    }

    @Test public void allBundledRulesAreValidAndIdsUnique() throws Exception {
        JSONArray sources = new JSONArray(Files.readString(new File("src/main/assets/online_sources.json").toPath()));
        java.util.Set<String> ids = new java.util.HashSet<>();
        Document document = Jsoup.parse("<body></body>");
        for (int i = 0; i < sources.length(); i++) {
            JSONObject rule = sources.getJSONObject(i);
            OnlineBookSource source = OnlineSourceRepository.parseSource(rule);
            assertTrue(ids.add(source.id));
            assertTrue(source.crawl.concurrency >= 1);
            for (String section : new String[]{"search", "book", "toc", "chapter"}) {
                JSONObject object = rule.getJSONObject(section);
                for (String key : new String[]{"result", "bookName", "author", "item", "nextPage", "title", "content", "filterTag"}) {
                    String selector = object.optString(key).split("@(?:js|java):", 2)[0].trim();
                    if (selector.isEmpty()) continue;
                    if (selector.startsWith("/") || selector.startsWith("(")) document.selectXpath(selector);
                    else document.select(selector);
                }
            }
            if (!source.chapter.filterTxt.isEmpty()) java.util.regex.Pattern.compile(source.chapter.filterTxt);
        }
        assertEquals(25, ids.size());
    }

    @Test public void base64AndShuffledParagraphsAreRestored() {
        OnlineBookClient client = new OnlineBookClient(new OnlineHttpClient());
        OnlineBookSource.ChapterRule rule = new OnlineBookSource.ChapterRule();
        rule.content = "#content@java:base64.decode()";
        String encoded = Base64.getEncoder().encodeToString("<p>第一段</p><p>第二段</p>".getBytes(StandardCharsets.UTF_8));
        Document doc = Jsoup.parse("<div id=content><script>document.writeln(qsbs.bb('" + encoded + "'));</script></div>");
        assertEquals("第一段\n\n第二段", client.extractChapterBody(doc, rule));
        rule.content = "#content@js:data-id";
        doc = Jsoup.parse("<div id=content><dd data-id=2><p>第二段</p></dd><dd data-id=1><p>第一段</p></dd></div>");
        assertEquals("第一段\n\n第二段", client.extractChapterBody(doc, rule));
    }

    @Test public void page10IsIncludedButNextChapterIsNot() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            server.enqueue(new MockResponse().setBody("<h1>第一章</h1><div id=content><p>甲</p></div><a id=next href='/1_10.html'>下一章</a>"));
            server.enqueue(new MockResponse().setBody("<h1>第一章</h1><div id=content><p>乙</p></div><a id=next href='/2.html'>下一章</a>"));
            OnlineBookClient client = new OnlineBookClient(new OnlineHttpClient());
            String text = client.downloadChapterContent(source, toc(server, 2).get(1), null);
            assertEquals("第一章\n\n甲\n\n乙", text);
            assertEquals(2, server.getRequestCount());
            assertEquals("/1_10.html", server.takeRequest(1, TimeUnit.SECONDS).getPath().equals("/1.html")
                    ? server.takeRequest(1, TimeUnit.SECONDS).getPath() : "unexpected");
        }
    }

    @Test public void tocReusesDetailAndDeduplicatesLargeCatalog() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            StringBuilder html = new StringBuilder("<div id=toc>");
            for (int i = 0; i < 10000; i++) html.append("<a href='/").append(i).append(".html'>章节</a><a href='/").append(i).append(".html'>重复</a>");
            html.append("<a href='javascript:bad()'>无效</a></div>");
            OnlineBookClient client = new OnlineBookClient(new OnlineHttpClient());
            String url = server.url("/").toString();
            assertEquals(10000, client.parseToc(source, url, Jsoup.parse(html.toString(), url), null).size());
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test public void paginatedTocSchedulesEachPageOnce() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            source.toc.nextPage = "select option";
            String url = server.url("/book").toString();
            Document first = Jsoup.parse("<div id=toc><a href='/1.html'>第一章</a></div><select><option value='/book'>1</option><option value='/book2'>2</option><option value='/book2'>2</option></select>", url);
            server.enqueue(new MockResponse().setBody("<div id=toc><a href='/2.html'>第二章</a></div><select><option value='/book'>1</option><option value='/book2'>2</option></select>"));
            assertEquals(2, new OnlineBookClient(new OnlineHttpClient()).parseToc(source, url, first, null).size());
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test public void wxsyHiddenTocEntriesAreDiscarded() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            source.id = "wxsy";
            source.toc.item = "ul.section-list.ycxsid a";
            String url = server.url("/book").toString();
            Document doc = Jsoup.parse("<style>.section-list.ycxsid>li:nth-child(1){display:none}.section-list.ycxsid>li:nth-last-child(1){display:none}</style><ul class='section-list ycxsid'><li><a href='/fake1'>伪</a></li><li><a href='/real'>正文</a></li><li><a href='/fake2'>伪</a></li></ul>", url);
            List<OnlineBookClient.OnlineChapterRef> chapters = new OnlineBookClient(new OnlineHttpClient()).parseToc(source, url, doc, null);
            assertEquals(1, chapters.size());
            assertEquals(server.url("/real").toString(), chapters.get(0).url);
        }
    }

    @Test public void failedSecondPageNeverCachesTruncatedChapter() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            List<OnlineBookClient.OnlineChapterRef> toc = toc(server, 1);
            server.enqueue(new MockResponse().setBody("<h1>第一章</h1><div id=content>第一页</div><a id=next href='/0_2.html'>下一页</a>"));
            server.enqueue(new MockResponse().setResponseCode(503));
            OnlineChapterCache cache = new OnlineChapterCache(temporary.newFolder());
            try {
                new OnlineBookClient(new OnlineHttpClient()).downloadChapterContents(source, toc, temporary.newFolder(), cache, null, null);
                fail();
            } catch (OnlineHttpClient.HttpFailure expected) { assertEquals(503, expected.status); }
            assertNull(cache.read(OnlineChapterCache.key(source, toc.get(0).url, toc.get(0).title)));
        }
    }

    @Test public void silentJsNextChapterIsNotMergedIntoPreviousChapter() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            source.crawl.concurrency = 1;
            source.chapter.nextPageInJs = "script@js:r=r.match(/nextpage = \"(.*?)\"/)[1]";
            server.enqueue(new MockResponse().setBody("<h1>第一章</h1><div id=content>第一章正文</div><script>nextpage = \"/1.html\"</script>"));
            server.enqueue(chapter(1));
            List<File> files = new OnlineBookClient(new OnlineHttpClient()).downloadChapterContents(source, toc(server, 2), temporary.newFolder(), new OnlineChapterCache(temporary.newFolder()), null, null);
            assertFalse(Files.readString(files.get(0).toPath()).contains("第二段"));
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test public void refererCharsetAndConnectionReuse() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            server.enqueue(new MockResponse().setHeader("Content-Type", "text/html; charset=GBK")
                    .setBody(new okio.Buffer().write("<h1>中文</h1>".getBytes(java.nio.charset.Charset.forName("GBK")))));
            server.enqueue(chapter(2));
            OnlineHttpClient client = new OnlineHttpClient();
            assertEquals("中文", client.get(server.url("/1").toString(), source, null).select("h1").text());
            client.get(server.url("/2").toString(), source, null);
            RecordedRequest first = server.takeRequest(1, TimeUnit.SECONDS);
            RecordedRequest second = server.takeRequest(1, TimeUnit.SECONDS);
            assertEquals(source.baseUrl.replaceFirst("/+$", ""), first.getHeader("Referer"));
            assertEquals(1, second.getSequenceNumber());
        }
    }

    @Test public void retries429ButFails403Immediately() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            source.crawl.maxRetries = 2;
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "0"));
            server.enqueue(chapter(1));
            OnlineHttpClient client = new OnlineHttpClient();
            assertEquals("第1章", client.get(server.url("/1").toString(), source, null).select("h1").text());
            server.enqueue(new MockResponse().setResponseCode(403));
            try { client.get(server.url("/2").toString(), source, null); fail(); }
            catch (OnlineHttpClient.HttpFailure failure) { assertEquals(403, failure.status); }
            assertEquals(3, server.getRequestCount());
            assertEquals(5000, OnlineHttpClient.retryDelay("5", 0));
        }
    }

    @Test public void jsonpPreservesQuotedHtmlBeforeParsing() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            source.id = "quanben5";
            String html = "<div class=pic_txt_list><h3><a href='/n/test/'>测试书</a></h3><p class=info><span>作者</span></p></div>";
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/javascript; charset=utf-8")
                    .setBody("search(" + new JSONObject().put("content", html) + ");"));
            Document result = new OnlineHttpClient().search(server.url("/search").toString(), source, null, null);
            assertEquals("测试书", result.select(".pic_txt_list h3 a").text());
            assertEquals(server.url("/n/test/").toString(), result.select("a").first().absUrl("href"));
        }
    }

    @Test public void searchPaginationPreservesResultsAndDeduplicatesBooks() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            source.search.url = server.url("/search?q=%s").toString();
            source.search.result = ".book";
            source.search.bookName = "a";
            source.search.nextPage = ".pages a";
            server.enqueue(new MockResponse().setBody("<div class=book><a href='/book1'>甲</a></div><div class=pages><a href='/page2'>2</a><a href='/page2'>重复</a></div>"));
            server.enqueue(new MockResponse().setBody("<div class=book><a href='/book1'>甲</a></div><div class=book><a href='/book2'>乙</a></div><div class=pages><a href='/page2'>2</a></div>"));
            List<OnlineBookResult> results = new OnlineBookClient(new OnlineHttpClient()).searchSource(source, "书", null);
            assertEquals(2, results.size());
            assertEquals("甲", results.get(0).bookName);
            assertEquals("乙", results.get(1).bookName);
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test public void cancellationAbortsSocketWithoutWaitingForTimeout() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            server.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE));
            OnlineDownloadCancelToken token = new OnlineDownloadCancelToken();
            CompletableFuture<Boolean> request = CompletableFuture.supplyAsync(() -> {
                try { new OnlineHttpClient().get(server.url("/slow").toString(), source, token); return false; }
                catch (CancellationException expected) { return true; }
                catch (Exception other) { throw new RuntimeException(other); }
            });
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
            token.cancel();
            assertTrue(request.get(2, TimeUnit.SECONDS));
        }
    }

    @Test public void boundedWorkersPreserveOrderAndReuseCompletedCache() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    int index = Integer.parseInt(request.getPath().replaceAll("\\D", ""));
                    return chapter(index).setBodyDelay(index % 3 * 10, TimeUnit.MILLISECONDS);
                }
            });
            OnlineBookSource source = source(server);
            source.crawl.concurrency = 4;
            OnlineBookClient client = new OnlineBookClient(new OnlineHttpClient());
            List<OnlineBookClient.OnlineChapterRef> toc = toc(server, 16);
            OnlineChapterCache cache = new OnlineChapterCache(temporary.newFolder());
            List<File> files = client.downloadChapterContents(source, toc, temporary.newFolder(), cache, null, null);
            for (int i = 0; i < files.size(); i++) assertTrue(Files.readString(files.get(i).toPath()).startsWith("第" + i + "章\n"));
            assertEquals(16, server.getRequestCount());
            client.downloadChapterContents(source, toc, temporary.newFolder(), cache, null, null);
            assertEquals(16, server.getRequestCount());
        }
    }

    @Test public void queueFailsFastAndDoesNotRunThousandsOfPendingJobs() throws Exception {
        AtomicInteger started = new AtomicInteger();
        long start = System.nanoTime();
        try {
            OnlineWorkQueue.run(10000, 4, null, (index, token) -> {
                started.incrementAndGet();
                if (index == 1) throw new IllegalStateException("fixture failure");
                while (true) { token.throwIfCancelled(); Thread.sleep(20); }
            });
            fail();
        } catch (IllegalStateException expected) { assertEquals("fixture failure", expected.getMessage()); }
        assertTrue(started.get() <= 4);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2000);
    }

    @Test public void retryAfterDelayIsActuallyRespected() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            source.crawl.maxRetries = 1;
            server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "1"));
            server.enqueue(chapter(1));
            long start = System.nanoTime();
            new OnlineHttpClient().get(server.url("/1").toString(), source, null);
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 950);
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test public void restartingFailedBookFetchesOnlyMissingChapters() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            source.crawl.concurrency = 1;
            List<OnlineBookClient.OnlineChapterRef> refs = toc(server, 2);
            OnlineBookClient client = new OnlineBookClient(new OnlineHttpClient());
            OnlineChapterCache cache = new OnlineChapterCache(temporary.newFolder());
            server.enqueue(chapter(0));
            server.enqueue(new MockResponse().setResponseCode(403));
            try { client.downloadChapterContents(source, refs, temporary.newFolder(), cache, null, null); fail(); }
            catch (OnlineHttpClient.HttpFailure expected) { assertEquals(403, expected.status); }
            assertNotNull(cache.read(OnlineChapterCache.key(source, refs.get(0).url, refs.get(0).title)));
            server.enqueue(chapter(1));
            List<File> files = client.downloadChapterContents(source, refs, temporary.newFolder(), cache, null, null);
            assertEquals(2, files.size());
            assertEquals(3, server.getRequestCount());
        }
    }

    @Test public void corruptOrOutdatedRuleCacheIsNotReused() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            OnlineBookSource source = source(server);
            File directory = temporary.newFolder();
            OnlineChapterCache cache = new OnlineChapterCache(directory);
            String key = OnlineChapterCache.key(source, "https://example.org/1", "第一章");
            cache.write(key, "正文");
            assertEquals("正文", cache.read(key));
            source.chapter.filterTxt = "new filter";
            assertNull(cache.read(OnlineChapterCache.key(source, "https://example.org/1", "第一章")));
            Files.write(new File(directory, key + ".cache").toPath(), new byte[]{1,2,3});
            assertNull(cache.read(key));
        }
    }

    @Test public void txtAndEpubExportsKeepChapterOrderAndParagraphs() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            List<File> files = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                File file = temporary.newFile();
                Files.writeString(file.toPath(), "第" + i + "章\n\n甲 & 乙\n\n第二段");
                files.add(file);
            }
            OnlineBookClient client = new OnlineBookClient(new OnlineHttpClient());
            OnlineBookClient.OnlineBookInfo info = new OnlineBookClient.OnlineBookInfo();
            info.title = "测试书"; info.author = "作者";
            File txt = temporary.newFile();
            client.writeTxt(txt, info, source(server), "url", toc(server, 2), files, null);
            String text = Files.readString(txt.toPath());
            assertEquals(1, text.split("第0章", -1).length - 1);
            assertTrue(text.indexOf("第0章") < text.indexOf("第1章"));
            File epub = temporary.newFile();
            client.writeEpub(epub, info, toc(server, 2), files, null);
            try (ZipFile zip = new ZipFile(epub)) {
                assertEquals("application/epub+zip", new String(zip.getInputStream(zip.getEntry("mimetype")).readAllBytes(), StandardCharsets.UTF_8));
                String chapter = new String(zip.getInputStream(zip.getEntry("OEBPS/chapters/chapter0.xhtml")).readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(chapter.contains("<p>甲 &amp; 乙</p>"));
                assertTrue(chapter.contains("<p>第二段</p>"));
            }
        }
    }

    @Test public void pacingOverlapsNetworkLatencyBenchmark() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) {
                    return chapter(1).setBodyDelay(80, TimeUnit.MILLISECONDS);
                }
            });
            OnlineBookSource source = source(server);
            source.crawl.concurrency = 1;
            source.crawl.minIntervalMillis = 80;
            source.crawl.maxIntervalMillis = 80;
            OnlineBookClient client = new OnlineBookClient(new OnlineHttpClient());
            List<OnlineBookClient.OnlineChapterRef> refs = toc(server, 10);
            long start = System.nanoTime();
            for (OnlineBookClient.OnlineChapterRef ref : refs) {
                Thread.sleep(80); // Exact old delay-before-request behavior.
                Jsoup.connect(ref.url).get();
            }
            long before = System.nanoTime() - start;
            start = System.nanoTime();
            client.downloadChapterContents(source, refs, temporary.newFolder(), new OnlineChapterCache(temporary.newFolder()), null, null);
            long after = System.nanoTime() - start;
            System.out.printf("PACING_BENCHMARK old=%.0fms new=%.0fms speedup=%.2fx%n", before / 1e6, after / 1e6, (double) before / after);
            assertTrue("New pacing should overlap request latency", after < before * 0.85);
        }
    }
}
