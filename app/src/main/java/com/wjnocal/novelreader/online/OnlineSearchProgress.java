package com.wjnocal.novelreader.online;

public interface OnlineSearchProgress {
    void onProgress(String message, int completed, int total);
}
