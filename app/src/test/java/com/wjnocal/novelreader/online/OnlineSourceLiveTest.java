package com.wjnocal.novelreader.online;

import org.json.JSONArray;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** Opt-in, small read-only probe. Website failures are reported independently. */
public class OnlineSourceLiveTest {
    @Test public void probeCurrentSites() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("novelreader.liveProbe"));
        JSONArray rules = new JSONArray(Files.readString(new File("src/main/assets/online_sources.json").toPath()));
        for (int i = 0; i < rules.length(); i++) {
            OnlineBookSource source = OnlineSourceRepository.parseSource(rules.getJSONObject(i));
            if (!java.util.Arrays.asList(System.getProperty("novelreader.probeSources").split(",")).contains(source.id)) continue;
            source.crawl.timeoutMillis = 15000;
            source.crawl.maxRetries = 0;
            long start = System.nanoTime();
            try {
                OnlineHttpClient http = new OnlineHttpClient();
                OnlineBookClient client = new OnlineBookClient(http);
                Document home = http.get(source.baseUrl, source, null);
                String bookUrl = "";
                for (Element link : home.select("a[href]")) {
                    String url = link.absUrl("href");
                    String pattern = source.book.url == null || source.book.url.isEmpty()
                            ? "https?://www\\.shuhaige\\.net/\\d+/" : source.book.url;
                    if (url.matches(pattern)) { bookUrl = url; break; }
                }
                if (bookUrl.isEmpty()) throw new IllegalStateException("Homepage reachable, no matching book link");
                Document detail = http.get(bookUrl, source, null);
                List<OnlineBookClient.OnlineChapterRef> chapters = client.parseToc(source, bookUrl, detail, null);
                if (chapters.isEmpty()) throw new IllegalStateException("Catalog empty");
                String content = client.downloadChapterContent(source, chapters.get(0), null);
                if (content.length() < 100) throw new IllegalStateException("Chapter suspiciously short");
                System.out.printf("LIVE_PROBE source=%s result=OK chapters=%d firstChapterChars=%d elapsedMs=%.0f book=%s%n",
                        source.id, chapters.size(), content.length(), (System.nanoTime() - start) / 1e6, bookUrl);
            } catch (Exception error) {
                System.out.printf("LIVE_PROBE source=%s result=UNAVAILABLE reason=%s elapsedMs=%.0f%n",
                        source.id, error.toString(), (System.nanoTime() - start) / 1e6);
            }
        }
    }
}
