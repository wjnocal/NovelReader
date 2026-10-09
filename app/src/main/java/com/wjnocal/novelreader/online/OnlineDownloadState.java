package com.wjnocal.novelreader.online;

import com.wjnocal.novelreader.data.BookEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import okio.ByteString;

/** Persistent marker keeps partial downloads readable and prevents incomplete cloud uploads. */
public final class OnlineDownloadState {
    private static File marker(BookEntity book) {
        return new File(book.storageDirPath, ".online-download");
    }

    public static boolean isIncomplete(BookEntity book) {
        return book != null && book.storageDirPath != null && book.fileType != null
                && book.fileType.startsWith("online-") && marker(book).isFile();
    }

    static String signature(String sourceId, String url, String format,
                            List<OnlineBookClient.OnlineChapterRef> toc) {
        StringBuilder identity = new StringBuilder(sourceId).append('\n').append(url).append('\n').append(format);
        for (OnlineBookClient.OnlineChapterRef chapter : toc) {
            identity.append('\n').append(chapter.url).append('\n').append(chapter.title);
        }
        return ByteString.encodeUtf8(identity.toString()).sha256().hex();
    }

    static boolean canResume(BookEntity book, String signature) throws IOException {
        if (!isIncomplete(book)) return false;
        byte[] bytes = new byte[64];
        try (FileInputStream input = new FileInputStream(marker(book))) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) return false;
                offset += count;
            }
            return input.read() == -1 && signature.equals(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    static void begin(BookEntity book, String signature) throws IOException {
        try (FileOutputStream output = new FileOutputStream(marker(book))) {
            output.write(signature.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }

    static void complete(BookEntity book) throws IOException {
        File marker = marker(book);
        if (marker.exists() && !marker.delete()) throw new IOException("无法更新下载完成状态");
    }
}
