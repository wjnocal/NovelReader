package com.wjnocal.novelreader.online;

/** Publishes only a contiguous prefix, even when parallel requests finish out of order. */
final class OnlineChapterPublisher {
    interface Commit { void append(int from, int to) throws Exception; }

    private final boolean[] ready;
    private final Commit commit;
    private int published;

    OnlineChapterPublisher(int count, int published, Commit commit) {
        this.ready = new boolean[count];
        this.published = published;
        this.commit = commit;
    }

    synchronized void completed(int index) throws Exception {
        if (index < published) return;
        ready[index] = true;
        int end = published;
        while (end < ready.length && ready[end]) end++;
        if (end > published) {
            commit.append(published, end);
            published = end;
        }
    }
}
