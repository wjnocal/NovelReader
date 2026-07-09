package com.example.novelreader.online;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class OnlineSourceRepository {
    private static final String SOURCE_FILE = "online_sources.json";

    private final Context context;

    public OnlineSourceRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<OnlineBookSource> loadEnabledSources() throws Exception {
        List<OnlineBookSource> sources = new ArrayList<>();
        for (OnlineBookSource source : loadAllSources()) {
            if (source.enabled) {
                sources.add(source);
            }
        }
        return sources;
    }

    public List<OnlineBookSource> loadAllSources() throws Exception {
        List<OnlineBookSource> sources = new ArrayList<>();
        JSONArray array = new JSONArray(readAsset(SOURCE_FILE));
        for (int i = 0; i < array.length(); i++) {
            OnlineBookSource source = parseSource(array.getJSONObject(i));
            sources.add(source);
        }
        return sources;
    }

    public OnlineBookSource findById(String sourceId) throws Exception {
        for (OnlineBookSource source : loadEnabledSources()) {
            if (source.id.equals(sourceId)) {
                return source;
            }
        }
        throw new IllegalArgumentException("书源不存在：" + sourceId);
    }

    public OnlineBookSource findEnabledSourceForUrl(String url) throws Exception {
        return findEnabledSourceForUrl(url, null);
    }

    public OnlineBookSource findEnabledSourceForUrl(String url, Set<String> sourceIds) throws Exception {
        String target = url == null ? "" : url.trim();
        for (OnlineBookSource source : loadEnabledSources()) {
            if (sourceIds != null && !sourceIds.isEmpty() && !sourceIds.contains(source.id)) {
                continue;
            }
            if (target.startsWith(source.baseUrl)) {
                return source;
            }
        }
        return null;
    }

    private OnlineBookSource parseSource(JSONObject object) {
        OnlineBookSource source = new OnlineBookSource();
        source.id = object.optString("id");
        source.name = object.optString("name");
        source.baseUrl = object.optString("baseUrl");
        source.enabled = object.optBoolean("enabled", true);
        source.needProxy = object.optBoolean("needProxy", false);
        source.comment = object.optString("comment");

        JSONObject search = object.optJSONObject("search");
        if (search != null) {
            source.search.disabled = search.optBoolean("disabled", false);
            source.search.url = search.optString("url");
            source.search.method = search.optString("method", "get");
            source.search.data = search.optJSONObject("data") == null ? new JSONObject() : search.optJSONObject("data");
            source.search.result = search.optString("result");
            source.search.bookName = search.optString("bookName");
            source.search.author = search.optString("author");
            source.search.category = search.optString("category");
            source.search.latestChapter = search.optString("latestChapter");
            source.search.lastUpdateTime = search.optString("lastUpdateTime");
            source.search.status = search.optString("status");
            source.search.nextPage = search.optString("nextPage");
        }

        JSONObject book = object.optJSONObject("book");
        if (book != null) {
            source.book.url = book.optString("url");
            source.book.bookName = book.optString("bookName");
            source.book.author = book.optString("author");
            source.book.intro = book.optString("intro");
            source.book.category = book.optString("category");
            source.book.latestChapter = book.optString("latestChapter");
            source.book.lastUpdateTime = book.optString("lastUpdateTime");
            source.book.status = book.optString("status");
        }

        JSONObject toc = object.optJSONObject("toc");
        if (toc != null) {
            source.toc.url = toc.optString("url");
            source.toc.baseUri = toc.optString("baseUri");
            source.toc.item = toc.optString("item");
            source.toc.nextPage = toc.optString("nextPage");
            source.toc.desc = toc.optBoolean("isDesc", false);
        }

        JSONObject chapter = object.optJSONObject("chapter");
        if (chapter != null) {
            source.chapter.title = chapter.optString("title");
            source.chapter.content = chapter.optString("content");
            source.chapter.paragraphTagClosed = chapter.optBoolean("paragraphTagClosed", false);
            source.chapter.filterTxt = chapter.optString("filterTxt");
            source.chapter.filterTag = chapter.optString("filterTag");
        }

        JSONObject crawl = object.optJSONObject("crawl");
        if (crawl != null) {
            source.crawl.timeoutMillis = crawl.optInt("timeoutMillis", source.crawl.timeoutMillis);
            source.crawl.minIntervalMillis = crawl.optInt("minIntervalMillis", crawl.optInt("minInterval", source.crawl.minIntervalMillis));
            source.crawl.maxIntervalMillis = crawl.optInt("maxIntervalMillis", crawl.optInt("maxInterval", source.crawl.maxIntervalMillis));
            source.crawl.maxRetries = crawl.optInt("maxRetries", source.crawl.maxRetries);
            source.crawl.concurrency = crawl.optInt("concurrency", source.crawl.concurrency);
        }
        return source;
    }

    private String readAsset(String fileName) throws Exception {
        try (InputStream input = context.getAssets().open(fileName)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
