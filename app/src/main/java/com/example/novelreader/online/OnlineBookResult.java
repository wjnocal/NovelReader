package com.example.novelreader.online;

public class OnlineBookResult {
    public String sourceId;
    public String sourceName;
    public String bookName;
    public String author;
    public String category;
    public String latestChapter;
    public String lastUpdateTime;
    public String status;
    public String url;

    public String displayAuthor() {
        return clean(author).isEmpty() ? "未知作者" : clean(author);
    }

    public String displayLatest() {
        String latest = clean(latestChapter);
        return latest.isEmpty() ? "最新章节未知" : latest;
    }

    public static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}
