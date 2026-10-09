package com.wjnocal.novelreader.online;

import com.wjnocal.novelreader.data.BookEntity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

public class OnlineProgressiveDownloadTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void parallelCompletionsPublishOnlyContiguousChaptersWithoutDuplicates() throws Exception {
        List<String> commits = new ArrayList<>();
        OnlineChapterPublisher publisher = new OnlineChapterPublisher(4, 0, (from, to) -> commits.add(from + ":" + to));
        publisher.completed(2);
        assertTrue(commits.isEmpty());
        publisher.completed(0);
        assertEquals(Arrays.asList("0:1"), commits);
        publisher.completed(3);
        publisher.completed(1);
        publisher.completed(0);
        assertEquals(Arrays.asList("0:1", "1:4"), commits);
    }

    @Test public void failedCommitDoesNotAdvanceReadablePrefix() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<String> commits = new ArrayList<>();
        OnlineChapterPublisher publisher = new OnlineChapterPublisher(2, 0, (from, to) -> {
            if (attempts.getAndIncrement() == 0) throw new IOException("database busy");
            commits.add(from + ":" + to);
        });
        assertThrows(IOException.class, () -> publisher.completed(0));
        publisher.completed(1);
        assertEquals(Arrays.asList("0:2"), commits);
    }

    @Test public void firstChapterBecomesReadableWhileLaterRequestsAreStillBlocked() throws Exception {
        CountDownLatch firstReadable = new CountDownLatch(1);
        CountDownLatch releaseLater = new CountDownLatch(1);
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                    if (!"/0".equals(request.getPath())) releaseLater.await(5, TimeUnit.SECONDS);
                    return chapter(request.getPath());
                }
            });
            server.start();
            File directory = temporary.newFolder();
            List<Integer> published = new ArrayList<>();
            OnlineChapterPublisher publisher = new OnlineChapterPublisher(3, 0, (from, to) -> {
                for (int i = from; i < to; i++) {
                    assertTrue(Files.readString(new File(directory, String.format("%04d.txt", i)).toPath()).contains("正文"));
                    published.add(i);
                }
                firstReadable.countDown();
            });
            CompletableFuture<List<File>> download = CompletableFuture.supplyAsync(() -> {
                try {
                    return new OnlineBookClient(new OnlineHttpClient()).downloadChapterContents(source(server, 3),
                            toc(server, 3), directory, new OnlineChapterCache(temporary.newFolder()), null, null,
                            0, publisher::completed);
                } catch (Exception e) { throw new CompletionException(e); }
            });
            try {
                assertTrue(firstReadable.await(3, TimeUnit.SECONDS));
                assertFalse(download.isDone());
                assertEquals(Arrays.asList(0), published);
            } finally { releaseLater.countDown(); }
            assertEquals(3, download.get(5, TimeUnit.SECONDS).size());
            assertEquals(Arrays.asList(0, 1, 2), published);
        }
    }

    @Test public void cancellationKeepsPublishedChapterAndResumeDoesNotOverwriteIt() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override public MockResponse dispatch(RecordedRequest request) { return chapter(request.getPath()); }
            });
            server.start();
            File directory = temporary.newFolder();
            OnlineChapterCache cache = new OnlineChapterCache(temporary.newFolder());
            OnlineBookClient client = new OnlineBookClient(new OnlineHttpClient());
            OnlineDownloadCancelToken token = new OnlineDownloadCancelToken();
            AtomicInteger readable = new AtomicInteger();
            OnlineChapterPublisher publisher = new OnlineChapterPublisher(3, 0, (from, to) -> {
                readable.set(to);
                token.cancel();
            });
            assertThrows(CancellationException.class, () -> client.downloadChapterContents(source(server, 1),
                    toc(server, 3), directory, cache, null, token, 0, publisher::completed));
            assertEquals(1, readable.get());
            File first = new File(directory, "0000.txt");
            assertTrue(first.isFile());
            Files.write(first.toPath(), "已发布章节不应重写".getBytes(StandardCharsets.UTF_8));
            OnlineChapterPublisher resumed = new OnlineChapterPublisher(3, 1, (from, to) -> {
                assertEquals(readable.get(), from);
                readable.set(to);
            });
            client.downloadChapterContents(source(server, 2), toc(server, 3), directory, cache,
                    null, null, 1, resumed::completed);
            assertEquals(3, readable.get());
            assertEquals(3, server.getRequestCount());
            assertEquals("已发布章节不应重写", Files.readString(first.toPath()));
        }
    }

    @Test public void resumeMarkerSurvivesRestartAndRejectsOtherSourcesOrChangedCatalogs() throws Exception {
        BookEntity book = new BookEntity();
        book.fileType = "online-txt";
        book.storageDirPath = temporary.newFolder().getAbsolutePath();
        List<OnlineBookClient.OnlineChapterRef> toc = new ArrayList<>();
        OnlineBookClient.OnlineChapterRef ref = new OnlineBookClient.OnlineChapterRef();
        ref.url = "https://example.test/1";
        ref.title = "第一章";
        toc.add(ref);
        String signature = OnlineDownloadState.signature("a", "https://example.test/book", "txt", toc);
        OnlineDownloadState.begin(book, signature);
        assertTrue(OnlineDownloadState.isIncomplete(book));
        assertTrue(OnlineDownloadState.canResume(book, signature));
        assertFalse(OnlineDownloadState.canResume(book, OnlineDownloadState.signature("b", "https://example.test/book", "txt", toc)));
        ref.url = "https://example.test/changed";
        assertFalse(OnlineDownloadState.canResume(book, OnlineDownloadState.signature("a", "https://example.test/book", "txt", toc)));
        OnlineDownloadState.complete(book);
        assertFalse(OnlineDownloadState.isIncomplete(book));
    }

    private static MockResponse chapter(String path) {
        return new MockResponse().setBody("<h1>章节" + path + "</h1><div id='content'><p>正文第一段</p><p>正文第二段</p></div>");
    }

    private static OnlineBookSource source(MockWebServer server, int concurrency) {
        OnlineBookSource source = new OnlineBookSource();
        source.id = "progressive-test";
        source.baseUrl = server.url("/").toString();
        source.crawl.concurrency = concurrency;
        source.crawl.minIntervalMillis = 0;
        source.crawl.maxIntervalMillis = 0;
        source.crawl.maxRetries = 0;
        source.chapter.title = "h1";
        source.chapter.content = "#content";
        return source;
    }

    private static List<OnlineBookClient.OnlineChapterRef> toc(MockWebServer server, int count) {
        List<OnlineBookClient.OnlineChapterRef> toc = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            OnlineBookClient.OnlineChapterRef ref = new OnlineBookClient.OnlineChapterRef();
            ref.url = server.url("/" + i).toString();
            ref.title = "章节" + i;
            toc.add(ref);
        }
        return toc;
    }
}
