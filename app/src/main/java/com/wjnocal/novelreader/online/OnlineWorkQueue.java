package com.wjnocal.novelreader.online;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** One long-lived job per worker: no whole-book future queue or head-of-line wait. */
final class OnlineWorkQueue {
    interface Job { void run(int index, OnlineDownloadCancelToken token) throws Exception; }

    static void run(int size, int concurrency, OnlineDownloadCancelToken parent, Job job) throws Exception {
        if (size == 0) return;
        int workers = Math.max(1, Math.min(Math.min(8, concurrency), size));
        OnlineDownloadCancelToken token = new OnlineDownloadCancelToken();
        Runnable cancel = token::cancel;
        if (parent != null) parent.register(cancel);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        ExecutorCompletionService<Void> completed = new ExecutorCompletionService<>(pool);
        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger next = new AtomicInteger();
        try {
            for (int i = 0; i < workers; i++) {
                futures.add(completed.submit(() -> {
                    for (int index; (index = next.getAndIncrement()) < size;) {
                        token.throwIfCancelled();
                        job.run(index, token);
                    }
                    return null;
                }));
            }
            for (int i = 0; i < workers; i++) {
                while (true) {
                    OnlineHttpClient.checkCancelled(parent);
                    Future<Void> finished = completed.poll(100, TimeUnit.MILLISECONDS);
                    if (finished != null) { finished.get(); break; }
                }
            }
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        } finally {
            token.cancel();
            for (Future<Void> future : futures) future.cancel(true);
            pool.shutdownNow();
            // Workers must release files and sockets before caller cleans its directory.
            boolean interrupted = Thread.interrupted();
            try { pool.awaitTermination(5, TimeUnit.SECONDS); }
            finally {
                if (interrupted) Thread.currentThread().interrupt();
                if (parent != null) parent.unregister(cancel);
            }
        }
    }
}
