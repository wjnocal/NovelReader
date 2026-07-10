package com.example.novelreader.sync;

import android.content.Context;

import com.example.novelreader.data.BookEntity;
import com.example.novelreader.data.ChapterEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Creates portable book payloads without persisting device-specific paths. */
public final class BookArchive {
    private BookArchive() {
    }

    public static final class Archive {
        public File file;
        public String sha256;
    }

    public static final class RestoredPayload {
        public String originalFilePath;
        public String coverPath;
        public String storageDirPath;
        public List<ChapterEntity> chapters = new ArrayList<>();
    }

    public static Archive create(Context context, BookEntity book, List<ChapterEntity> chapters) throws Exception {
        File cacheDir = new File(context.getFilesDir(), "sync-cache");
        ensureDir(cacheDir);
        File archive = File.createTempFile("book-" + safe(book.syncId) + "-", ".zip", cacheDir);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            JSONObject writtenMeta = new JSONObject();
            writtenMeta.put("format", 1);
            writtenMeta.put("original", putFile(zip, book.originalFilePath, "original"));
            writtenMeta.put("cover", putFile(zip, book.coverPath, "cover"));
            JSONArray chapterArray = new JSONArray();
            for (ChapterEntity chapter : chapters) {
                JSONObject item = new JSONObject();
                item.put("index", chapter.chapterIndex);
                item.put("title", chapter.title == null ? "" : chapter.title);
                item.put("path", putFile(zip, chapter.contentPath,
                        "chapters/" + String.format(Locale.US, "%05d", chapter.chapterIndex)));
                chapterArray.put(item);
            }
            writtenMeta.put("chapters", chapterArray);
            zip.putNextEntry(new ZipEntry("book.json"));
            zip.write(writtenMeta.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Archive result = new Archive();
        result.file = archive;
        result.sha256 = sha256(archive);
        return result;
    }

    public static RestoredPayload restore(Context context, File archive, String bookSyncId) throws Exception {
        File booksDir = new File(context.getFilesDir(), "books");
        ensureDir(booksDir);
        File target = new File(booksDir, "sync_" + safe(bookSyncId) + "_" + System.currentTimeMillis());
        ensureDir(target);
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry metaEntry = zip.getEntry("book.json");
            if (metaEntry == null) {
                throw new IllegalArgumentException("同步书籍包缺少元数据");
            }
            JSONObject meta;
            try (InputStream input = zip.getInputStream(metaEntry)) {
                meta = new JSONObject(new String(readAll(input), StandardCharsets.UTF_8));
            }
            RestoredPayload result = new RestoredPayload();
            result.storageDirPath = target.getAbsolutePath();
            result.originalFilePath = extractEntry(zip, meta.optString("original", ""), target);
            result.coverPath = extractEntry(zip, meta.optString("cover", ""), target);
            JSONArray chapters = meta.optJSONArray("chapters");
            if (chapters != null) {
                for (int i = 0; i < chapters.length(); i++) {
                    JSONObject item = chapters.getJSONObject(i);
                    ChapterEntity chapter = new ChapterEntity();
                    chapter.chapterIndex = item.optInt("index", i);
                    chapter.title = item.optString("title", "");
                    chapter.contentPath = extractEntry(zip, item.optString("path", ""), target);
                    if (chapter.contentPath == null || chapter.contentPath.isEmpty()) {
                        throw new IllegalArgumentException("同步书籍包章节缺失");
                    }
                    result.chapters.add(chapter);
                }
            }
            return result;
        } catch (Exception e) {
            deleteRecursively(target);
            throw e;
        }
    }

    private static String putFile(ZipOutputStream zip, String path, String prefix) throws Exception {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }
        File file = new File(path);
        if (!file.isFile()) {
            return "";
        }
        String entryName = "payload/" + prefix + "/" + safe(file.getName());
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            copy(input, zip);
        }
        zip.closeEntry();
        return entryName;
    }

    private static String extractEntry(ZipFile zip, String entryName, File target) throws Exception {
        if (entryName == null || entryName.isEmpty()) {
            return "";
        }
        if (!entryName.startsWith("payload/")) {
            throw new IllegalArgumentException("同步书籍包路径不安全");
        }
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            throw new IllegalArgumentException("同步书籍包文件缺失");
        }
        File output = new File(target, entryName.substring("payload/".length()));
        String root = target.getCanonicalPath() + File.separator;
        if (!output.getCanonicalPath().startsWith(root)) {
            throw new IllegalArgumentException("同步书籍包路径不安全");
        }
        File parent = output.getParentFile();
        ensureDir(parent);
        try (InputStream input = zip.getInputStream(entry); OutputStream stream = new FileOutputStream(output)) {
            copy(input, stream);
        }
        return output.getAbsolutePath();
    }

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder value = new StringBuilder();
        for (byte b : digest.digest()) {
            value.append(String.format(Locale.US, "%02x", b));
        }
        return value.toString();
    }

    public static void deleteRecursively(File file) {
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

    private static void ensureDir(File dir) {
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建同步目录");
        }
    }

    private static void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    private static String safe(String value) {
        return (value == null ? "" : value).replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
