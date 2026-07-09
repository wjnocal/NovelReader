package com.example.novelreader.online;

import org.json.JSONObject;

public class OnlineBookSource {
    public String id;
    public String name;
    public String baseUrl;
    public boolean enabled = true;
    public boolean needProxy;
    public String comment;
    public SearchRule search = new SearchRule();
    public BookRule book = new BookRule();
    public TocRule toc = new TocRule();
    public ChapterRule chapter = new ChapterRule();
    public CrawlRule crawl = new CrawlRule();

    public static class SearchRule {
        public boolean disabled;
        public String url;
        public String method = "get";
        public JSONObject data = new JSONObject();
        public String result;
        public String bookName;
        public String author;
        public String category;
        public String latestChapter;
        public String lastUpdateTime;
        public String status;
        public String nextPage;
    }

    public static class BookRule {
        public String url;
        public String bookName;
        public String author;
        public String intro;
        public String category;
        public String latestChapter;
        public String lastUpdateTime;
        public String status;
    }

    public static class TocRule {
        public String url;
        public String baseUri;
        public String item;
        public String nextPage;
        public boolean desc;
    }

    public static class ChapterRule {
        public String title;
        public String content;
        public boolean paragraphTagClosed;
        public String filterTxt;
        public String filterTag;
    }

    public static class CrawlRule {
        public int timeoutMillis = 15000;
        public int minIntervalMillis = 500;
        public int maxIntervalMillis = 1200;
        public int maxRetries = 2;
        public int concurrency = 3;
    }
}
