package com.example.novelreader.online;

public interface OnlineSearchProgress {
    void onProgress(String message, int completed, int total);
}
