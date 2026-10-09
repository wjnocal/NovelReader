package com.wjnocal.novelreader.online;

import java.util.concurrent.CancellationException;
import java.util.HashSet;
import java.util.Set;

public class OnlineDownloadCancelToken {
    private volatile boolean cancelled;
    private final Set<Runnable> callbacks = new HashSet<>();

    public void cancel() {
        Runnable[] pending;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true;
            pending = callbacks.toArray(new Runnable[0]);
            callbacks.clear();
        }
        for (Runnable callback : pending) callback.run();
    }

    public void register(Runnable callback) {
        synchronized (this) {
            if (!cancelled) {
                callbacks.add(callback);
                return;
            }
        }
        callback.run();
    }

    public synchronized void unregister(Runnable callback) {
        callbacks.remove(callback);
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void throwIfCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("下载已取消");
        }
    }
}
