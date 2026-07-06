package com.example.novelreader.parser;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TxtParser {
    public static final int SPLIT_DEFAULT = 0;
    public static final int SPLIT_CHINESE_CHAPTER = 1;
    public static final int SPLIT_ARABIC_CHAPTER = 2;
    public static final int SPLIT_ARABIC_MARK = 3;
    public static final int SPLIT_CHAPTER_ARABIC = 4;
    public static final int SPLIT_CHINESE_SECTION = 5;
    public static final int SPLIT_CHINESE_VOLUME = 6;
    public static final int SPLIT_PROLOGUE = 7;

    private static final String CHINESE_NUMERAL = "一二三四五六七八九十百千万零〇两";

    public ParsedBook parse(File file, String fallbackTitle) throws IOException {
        return parse(file, fallbackTitle, SPLIT_DEFAULT);
    }

    public ParsedBook parse(File file, String fallbackTitle, int splitMode) throws IOException {
        String text = TextFileReader.readText(file)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        ParsedBook book = new ParsedBook();
        book.title = stripExtension(fallbackTitle);
        book.author = "本地 TXT";
        book.fileType = "txt";

        Matcher matcher = patternForMode(splitMode).matcher(text);
        int chapterStart = -1;
        String chapterTitle = null;
        int index = 1;

        while (matcher.find()) {
            if (chapterTitle != null) {
                addChapter(book, chapterTitle, text.substring(chapterStart, matcher.start()).trim(), index++);
            } else if (matcher.start() > 0) {
                String preface = text.substring(0, matcher.start()).trim();
                if (!preface.isEmpty()) {
                    addChapter(book, "序章", preface, index++);
                }
            }
            chapterTitle = matcher.group(1).trim();
            chapterStart = matcher.end();
        }

        if (chapterTitle != null) {
            addChapter(book, chapterTitle, text.substring(chapterStart).trim(), index);
        }
        if (book.chapters.isEmpty()) {
            addChapter(book, book.title, text, 1);
        }
        return book;
    }

    public static String labelForMode(int splitMode) {
        switch (splitMode) {
            case SPLIT_CHINESE_CHAPTER:
                return "第X章（第一章）";
            case SPLIT_ARABIC_CHAPTER:
                return "第x章（第1章）";
            case SPLIT_ARABIC_MARK:
                return "x、（1、）";
            case SPLIT_CHAPTER_ARABIC:
                return "chapter x（chapter1）";
            case SPLIT_CHINESE_SECTION:
                return "第X节/第x节";
            case SPLIT_CHINESE_VOLUME:
                return "第X卷/第x卷";
            case SPLIT_PROLOGUE:
                return "序章/楔子/后记";
            default:
                return "默认（第X章/第x章）";
        }
    }

    private static Pattern patternForMode(int splitMode) {
        String number = "\\d" + CHINESE_NUMERAL;
        switch (splitMode) {
            case SPLIT_CHINESE_CHAPTER:
                return Pattern.compile("(?m)^\\s*(第[" + CHINESE_NUMERAL + "]+章[^\\r\\n]*)\\s*$");
            case SPLIT_ARABIC_CHAPTER:
                return Pattern.compile("(?m)^\\s*(第\\d+章[^\\r\\n]*)\\s*$");
            case SPLIT_ARABIC_MARK:
                return Pattern.compile("(?m)^\\s*(\\d+、[^\\r\\n]*)\\s*$");
            case SPLIT_CHAPTER_ARABIC:
                return Pattern.compile("(?im)^\\s*((?:chapter|chap\\.?|ch\\.?)[\\s.]*\\d+[^\\r\\n]*)\\s*$");
            case SPLIT_CHINESE_SECTION:
                return Pattern.compile("(?m)^\\s*(第[" + number + "]+节[^\\r\\n]*)\\s*$");
            case SPLIT_CHINESE_VOLUME:
                return Pattern.compile("(?m)^\\s*(第[" + number + "]+卷[^\\r\\n]*)\\s*$");
            case SPLIT_PROLOGUE:
                return Pattern.compile("(?m)^\\s*((?:序章|序言|楔子|引子|正文|番外|后记)[^\\r\\n]*)\\s*$");
            default:
                return Pattern.compile("(?m)^\\s*(第[" + number + "]+章[^\\r\\n]*)\\s*$");
        }
    }

    private static void addChapter(ParsedBook book, String title, String content, int index) {
        String cleanTitle = title == null || title.trim().isEmpty() ? "第 " + index + " 章" : title.trim();
        String cleanContent = content == null ? "" : content.trim();
        if (cleanContent.isEmpty()) {
            cleanContent = cleanTitle;
        }
        book.chapters.add(new ParsedChapter(cleanTitle, cleanContent));
    }

    private static String stripExtension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot > 0) {
            return name.substring(0, dot);
        }
        return name == null || name.trim().isEmpty() ? "未命名 TXT" : name;
    }
}
