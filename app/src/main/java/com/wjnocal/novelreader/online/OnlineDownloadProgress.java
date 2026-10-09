package com.wjnocal.novelreader.online;

public interface OnlineDownloadProgress {
    void onProgress(String message);

    default void onBookAvailable(long bookId) { }
}
