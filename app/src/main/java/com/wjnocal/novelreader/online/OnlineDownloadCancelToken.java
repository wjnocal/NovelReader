package com.wjnocal.novelreader.online;

import java.util.concurrent.CancellationException;

public class OnlineDownloadCancelToken {
    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void throwIfCancelled() {
        if (cancelled) {
            throw new CancellationException("下载已取消");
        }
    }
}
