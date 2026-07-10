package com.example.novelreader.sync;

import android.content.Context;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Foreground-process automatic sync with a short debounce for frequent reader writes. */
final class SyncScheduler {
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private static final Object LOCK = new Object();
    private static long generation;

    private SyncScheduler() {
    }

    static void request(Context context) {
        Context appContext = context.getApplicationContext();
        if (!SyncPreferences.isEnabled(appContext)) {
            return;
        }
        final long requestId;
        synchronized (LOCK) {
            requestId = ++generation;
        }
        EXECUTOR.schedule(() -> {
            synchronized (LOCK) {
                if (requestId != generation) {
                    return;
                }
            }
            try {
                new SyncRepository(appContext).sync(new SyncCancellationToken(), null);
            } catch (Exception ignored) {
                // The persisted sync status is shown on the WebDAV screen; the next foreground event retries.
            }
        }, 5, TimeUnit.SECONDS);
    }
}
