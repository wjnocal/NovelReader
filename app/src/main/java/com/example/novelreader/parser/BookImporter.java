package com.example.novelreader.parser;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.example.novelreader.data.AppDatabase;
import com.example.novelreader.data.BookEntity;
import com.example.novelreader.data.ChapterEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BookImporter {
    private final Context context;
    private final AppDatabase database;

    public BookImporter(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(context);
    }

    public long importBook(Uri uri) throws Exception {
        String displayName = getDisplayName(uri);
        String lowerName = displayName.toLowerCase(Locale.US);
        if (!lowerName.endsWith(".txt") && !lowerName.endsWith(".epub")) {
            throw new IllegalArgumentException("仅支持 TXT 和 EPUB 文件");
        }

        File importsDir = new File(context.getFilesDir(), "imports");
        ensureDir(importsDir);
        File tempFile = new File(importsDir, System.currentTimeMillis() + "_" + safeFileName(displayName));
        copyUriToFile(uri, tempFile);

        ParsedBook parsedBook;
        if (lowerName.endsWith(".txt")) {
            parsedBook = new TxtParser().parse(tempFile, displayName);
        } else {
            parsedBook = new EpubParser().parse(tempFile, displayName);
        }

        File bookDir = new File(context.getFilesDir(), "books/" + System.currentTimeMillis());
        ensureDir(bookDir);
        File originalFile = new File(bookDir, safeFileName(displayName));
        if (!tempFile.renameTo(originalFile)) {
            copyFile(tempFile, originalFile);
            tempFile.delete();
        }
        String coverPath = null;
        if (parsedBook.coverPath != null && !parsedBook.coverPath.trim().isEmpty()) {
            File parsedCover = new File(parsedBook.coverPath);
            if (parsedCover.exists()) {
                File coverFile = new File(bookDir, "cover" + extensionOf(parsedCover.getName()));
                copyFile(parsedCover, coverFile);
                parsedCover.delete();
                coverPath = coverFile.getAbsolutePath();
            }
        }

        long now = System.currentTimeMillis();
        BookEntity book = new BookEntity();
        book.title = parsedBook.title;
        book.author = parsedBook.author;
        book.fileType = parsedBook.fileType;
        book.category = "未分类";
        book.description = parsedBook.description;
        book.coverPath = coverPath;
        book.originalFilePath = originalFile.getAbsolutePath();
        book.storageDirPath = bookDir.getAbsolutePath();
        book.totalChapters = parsedBook.chapters.size();
        book.currentChapterIndex = 0;
        book.scrollY = 0;
        book.createdAt = now;
        book.updatedAt = now;
        book.syncId = UUID.randomUUID().toString();
        book.syncUpdatedAt = now;

        long bookId = -1;
        try {
            bookId = database.bookDao().insert(book);
            List<ChapterEntity> chapters = new ArrayList<>();
            File chaptersDir = new File(bookDir, "chapters");
            ensureDir(chaptersDir);
            for (int i = 0; i < parsedBook.chapters.size(); i++) {
                ParsedChapter parsedChapter = parsedBook.chapters.get(i);
                File chapterFile = new File(chaptersDir, String.format(Locale.US, "%04d.txt", i));
                writeText(chapterFile, parsedChapter.content);

                ChapterEntity chapter = new ChapterEntity();
                chapter.bookId = bookId;
                chapter.chapterIndex = i;
                chapter.title = parsedChapter.title;
                chapter.contentPath = chapterFile.getAbsolutePath();
                chapters.add(chapter);
            }
            database.chapterDao().insertAll(chapters);
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

    public void rebindBook(long bookId, Uri uri) throws Exception {
        BookEntity existing = database.bookDao().getById(bookId);
        if (existing == null) {
            throw new IllegalArgumentException("书籍不存在");
        }
        String displayName = getDisplayName(uri);
        String lowerName = displayName.toLowerCase(Locale.US);
        if (!lowerName.endsWith(".txt") && !lowerName.endsWith(".epub")) {
            throw new IllegalArgumentException("仅支持 TXT 和 EPUB 文件");
        }

        File importsDir = new File(context.getFilesDir(), "imports");
        ensureDir(importsDir);
        File tempFile = new File(importsDir, System.currentTimeMillis() + "_" + safeFileName(displayName));
        copyUriToFile(uri, tempFile);

        ParsedBook parsedBook;
        if (lowerName.endsWith(".txt")) {
            parsedBook = new TxtParser().parse(tempFile, displayName);
        } else {
            parsedBook = new EpubParser().parse(tempFile, displayName);
        }

        File oldBookDir = existing.storageDirPath == null || existing.storageDirPath.trim().isEmpty() ? null : new File(existing.storageDirPath);
        File bookDir = new File(context.getFilesDir(), "books/" + bookId + "_rebound_" + System.currentTimeMillis());
        ensureDir(bookDir);
        File originalFile = new File(bookDir, safeFileName(displayName));
        if (!tempFile.renameTo(originalFile)) {
            copyFile(tempFile, originalFile);
            tempFile.delete();
        }

        String coverPath = null;
        if (parsedBook.coverPath != null && !parsedBook.coverPath.trim().isEmpty()) {
            File parsedCover = new File(parsedBook.coverPath);
            if (parsedCover.exists()) {
                File coverFile = new File(bookDir, "cover" + extensionOf(parsedCover.getName()));
                copyFile(parsedCover, coverFile);
                parsedCover.delete();
                coverPath = coverFile.getAbsolutePath();
            }
        }

        List<ChapterEntity> chapters = new ArrayList<>();
        File chaptersDir = new File(bookDir, "chapters");
        ensureDir(chaptersDir);
        for (int i = 0; i < parsedBook.chapters.size(); i++) {
            ParsedChapter parsedChapter = parsedBook.chapters.get(i);
            File chapterFile = new File(chaptersDir, String.format(Locale.US, "%04d.txt", i));
            writeText(chapterFile, parsedChapter.content);

            ChapterEntity chapter = new ChapterEntity();
            chapter.bookId = bookId;
            chapter.chapterIndex = i;
            chapter.title = parsedChapter.title;
            chapter.contentPath = chapterFile.getAbsolutePath();
            chapters.add(chapter);
        }

        try {
            database.chapterDao().deleteForBook(bookId);
            database.chapterDao().insertAll(chapters);
            existing.author = parsedBook.author;
            existing.fileType = parsedBook.fileType;
            existing.description = parsedBook.description;
            existing.coverPath = coverPath;
            existing.originalFilePath = originalFile.getAbsolutePath();
            existing.storageDirPath = bookDir.getAbsolutePath();
            existing.totalChapters = parsedBook.chapters.size();
            existing.currentChapterIndex = Math.max(0, Math.min(existing.currentChapterIndex, existing.totalChapters - 1));
            existing.currentPageIndex = 0;
            existing.currentPageStartOffset = 0;
            existing.scrollY = 0;
            existing.updatedAt = System.currentTimeMillis();
            existing.syncContentHash = "";
            existing.syncUpdatedAt = existing.updatedAt;
            database.bookDao().update(existing);
            if (oldBookDir != null && oldBookDir.exists()) {
                deleteRecursively(oldBookDir);
            }
        } catch (Exception e) {
            deleteRecursively(bookDir);
            throw e;
        }
    }

    private String getDisplayName(Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }
        }
        String lastPath = uri.getLastPathSegment();
        return lastPath == null || lastPath.trim().isEmpty() ? "imported_book" : lastPath;
    }

    private void copyUriToFile(Uri uri, File target) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) {
                throw new IllegalArgumentException("无法读取选择的文件");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void copyFile(File source, File target) throws Exception {
        try (InputStream input = new java.io.FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static void writeText(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void ensureDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建目录：" + dir.getAbsolutePath());
        }
    }

    private static String safeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".img";
    }

    private static void deleteRecursively(File file) {
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
}
