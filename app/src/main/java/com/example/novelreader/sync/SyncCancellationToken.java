package com.example.novelreader.sync;

public class SyncCancellationToken {
    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public void throwIfCancelled() throws InterruptedException {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("同步已取消");
        }
    }
}
