package com.wjnocal.novelreader.online;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

/** Completed chapters survive a failed/cancelled download; never cache partial pages. */
final class OnlineChapterCache {
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private static final long MAX_AGE = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_CHAPTER_BYTES = 4 * 1024 * 1024;
    private final File directory;

    OnlineChapterCache(File directory) {
        this.directory = directory;
        if (!directory.isDirectory()) directory.mkdirs();
        prune();
    }

    static String key(OnlineBookSource source, String url, String title) throws Exception {
        OnlineBookSource.ChapterRule r = source.chapter;
        String identity = "chapter-v2\n" + source.id + "\n" + url + "\n" + title + "\n"
                + r.title + "\n" + r.content + "\n" + r.filterTxt + "\n" + r.filterTag + "\n"
                + r.nextPage + "\n" + r.nextPageInJs + "\n" + r.nextChapterLink;
        return hex(digest(identity.getBytes(StandardCharsets.UTF_8)));
    }

    synchronized String read(String key) {
        File file = new File(directory, key + ".cache");
        if (!file.isFile() || System.currentTimeMillis() - file.lastModified() > MAX_AGE) return null;
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            int size = input.readInt();
            if (size <= 0 || size > MAX_CHAPTER_BYTES || file.length() != size + 36L) return null;
            byte[] expected = new byte[32];
            input.readFully(expected);
            byte[] text = new byte[size];
            input.readFully(text);
            if (!Arrays.equals(expected, digest(text))) return null;
            return new String(text, StandardCharsets.UTF_8);
        } catch (Exception ignored) { return null; }
    }

    synchronized void write(String key, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_CHAPTER_BYTES) return;
        File temporary = new File(directory, key + ".tmp");
        File complete = new File(directory, key + ".cache");
        try {
            try (DataOutputStream output = new DataOutputStream(new FileOutputStream(temporary))) {
                output.writeInt(bytes.length);
                output.write(digest(bytes));
                output.write(bytes);
            }
            if (!temporary.renameTo(complete)) temporary.delete();
        } catch (Exception ignored) { temporary.delete(); }
    }

    synchronized void prune() {
        File[] files = directory.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        long retained = 0;
        long now = System.currentTimeMillis();
        for (File file : files) {
            if (!file.isFile()) continue;
            retained += file.length();
            if (file.getName().endsWith(".tmp") || now - file.lastModified() > MAX_AGE || retained > MAX_BYTES) {
                retained -= file.length();
                file.delete();
            }
        }
    }

    private static byte[] digest(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }
    private static String hex(byte[] data) {
        StringBuilder result = new StringBuilder();
        for (byte b : data) result.append(String.format(java.util.Locale.US, "%02x", b & 255));
        return result.toString();
    }
}
