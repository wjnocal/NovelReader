package com.wjnocal.novelreader.sync;

import android.content.Context;
import android.net.Uri;

import com.wjnocal.novelreader.UserProfile;
import com.wjnocal.novelreader.data.AppDatabase;
import com.wjnocal.novelreader.data.BookEntity;
import com.wjnocal.novelreader.data.BookmarkEntity;
import com.wjnocal.novelreader.data.ChapterEntity;
import com.wjnocal.novelreader.data.DailyReadingEntity;
import com.wjnocal.novelreader.data.FolderMetaEntity;
import com.wjnocal.novelreader.data.NoteEntity;
import com.wjnocal.novelreader.data.ReadingEventEntity;
import com.wjnocal.novelreader.data.ReaderSettingsEntity;
import com.wjnocal.novelreader.data.SyncTombstoneEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/** Merges Room data and complete book payloads with a user-owned WebDAV directory. */
public class SyncRepository {
    private static final String ROOT = "NovelReaderSync/";
    private static final String MANIFEST = ROOT + "manifest.json";
    private static final ReentrantLock SYNC_LOCK = new ReentrantLock();
    private final Context context;
    private final AppDatabase database;

    public interface Listener {
        void onProgress(String message, int percent);
    }

    public SyncRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(this.context);
    }

    public void testConnection(SyncPreferences.Config config) throws Exception {
        new WebDavClient(config, new SyncCancellationToken()).testConnection();
    }

    public void sync(SyncCancellationToken cancellation, Listener listener) throws Exception {
        if (!SyncPreferences.isEnabled(context)) {
            throw new IllegalStateException("请先保存 WebDAV 配置");
        }
        if (!SYNC_LOCK.tryLock()) {
            throw new IllegalStateException("已有同步任务正在进行");
        }
        try {
            SyncPreferences.setStatus(context, "正在同步");
            SyncPreferences.Config config = SyncPreferences.get(context);
            WebDavClient client = new WebDavClient(config, cancellation);
            progress(listener, "连接 WebDAV 服务器", 4);
            client.testConnection();
            client.ensureDirectory(ROOT + "objects/");
            client.ensureDirectory(ROOT + "avatars/");
            client.ensureDirectory(ROOT + "tmp/");
            client.ensureDirectory(ROOT + "history/");

            for (int attempt = 0; attempt < 2; attempt++) {
                cancellation.throwIfCancelled();
                progress(listener, "整理本机同步数据", 10);
                prepareLocalState();
                String etag = client.etag(MANIFEST);
                byte[] remoteBytes = client.getBytes(MANIFEST);
                JSONObject remote = remoteBytes == null ? new JSONObject() : new JSONObject(new String(remoteBytes, StandardCharsets.UTF_8));
                List<ProgressConflict> conflicts = findProgressConflicts(remote);
                if (!conflicts.isEmpty()) {
                    throw new ProgressConflictException(conflicts);
                }

                progress(listener, "合并服务器数据", 24);
                mergeRemote(client, remote, cancellation, listener);
                progress(listener, "上传书籍和头像", 56);
                JSONObject manifest = buildManifest(client, cancellation, listener);
                byte[] output = manifest.toString().getBytes(StandardCharsets.UTF_8);
                if (remoteBytes != null) {
                    client.putAtomic(ROOT + "history/previous-manifest.json", remoteBytes, null);
                }
                try {
                    progress(listener, "发布同步清单", 92);
                    client.putAtomic(MANIFEST, output, etag);
                    long now = System.currentTimeMillis();
                    SyncPreferences.setLastSync(context, now);
                    SyncPreferences.setStatus(context, "同步完成 " + formatTime(now));
                    progress(listener, "同步完成", 100);
                    return;
                } catch (WebDavClient.ConcurrentSyncException conflict) {
                    if (attempt == 1) {
                        throw new IllegalStateException("同步冲突，请稍后重试");
                    }
                }
            }
        } catch (ProgressConflictException e) {
            SyncPreferences.setStatus(context, "等待选择同名书籍的阅读进度");
            throw e;
        } catch (InterruptedException e) {
            SyncPreferences.setStatus(context, "同步已取消");
            throw new IllegalStateException("同步已取消");
        } catch (Exception e) {
            SyncPreferences.setStatus(context, "同步失败：" + safeMessage(e));
            throw e;
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    public static void requestAutomatic(Context context) {
        SyncScheduler.request(context);
    }

    public static void recordDeletion(Context context, String type, String syncId, long deletedAt) {
        if (syncId == null || syncId.trim().isEmpty()) {
            return;
        }
        AppDatabase database = AppDatabase.getInstance(context.getApplicationContext());
        SyncTombstoneEntity tombstone = new SyncTombstoneEntity();
        tombstone.entityType = type;
        tombstone.syncId = syncId;
        tombstone.key = type + ":" + syncId;
        tombstone.deletedAt = deletedAt;
        database.syncTombstoneDao().save(tombstone);
        requestAutomatic(context);
    }

    public static void recordReadingEvent(Context context, String date, long millis) {
        if (millis <= 0L || date == null || date.isEmpty()) {
            return;
        }
        ReadingEventEntity event = new ReadingEventEntity();
        event.syncId = UUID.randomUUID().toString();
        event.date = date;
        event.readingMillis = millis;
        event.createdAt = System.currentTimeMillis();
        AppDatabase.getInstance(context.getApplicationContext()).readingEventDao().insert(event);
        requestAutomatic(context);
    }

    public static final class ProgressConflict {
        public final String localSyncId;
        public final String remoteSyncId;
        public final String title;
        public final int localChapterIndex;
        public final int remoteChapterIndex;
        public final int remoteScrollY;
        public final int remotePageIndex;
        public final int remotePageStartOffset;
        public final long remoteFinishedAt;

        private ProgressConflict(BookEntity local, JSONObject remote) {
            localSyncId = local.syncId;
            remoteSyncId = remote.optString("syncId", "");
            title = remote.optString("title", local.title == null ? "" : local.title);
            localChapterIndex = local.currentChapterIndex;
            remoteChapterIndex = remote.optInt("currentChapterIndex", 0);
            remoteScrollY = remote.optInt("scrollY", 0);
            remotePageIndex = remote.optInt("currentPageIndex", 0);
            remotePageStartOffset = remote.optInt("currentPageStartOffset", 0);
            remoteFinishedAt = remote.optLong("finishedAt", 0L);
        }
    }

    public static final class ProgressConflictException extends Exception {
        private final List<ProgressConflict> conflicts;

        private ProgressConflictException(List<ProgressConflict> conflicts) {
            super("发现同名书籍，请选择保留的阅读进度");
            this.conflicts = new ArrayList<>(conflicts);
        }

        public List<ProgressConflict> getConflicts() {
            return conflicts;
        }
    }

    /** Resolves a same-title collision by retaining one local book and merging user data into it. */
    public void resolveProgressConflict(ProgressConflict conflict, boolean keepRemoteProgress) {
        database.runInTransaction(() -> {
            BookEntity local = database.bookDao().getBySyncId(conflict.localSyncId);
            if (local == null) {
                throw new IllegalStateException("本机书籍已不存在");
            }
            BookEntity downloadedRemote = database.bookDao().getBySyncId(conflict.remoteSyncId);
            if (downloadedRemote != null && downloadedRemote.id != local.id) {
                mergeBookUserData(downloadedRemote, local);
                BookArchive.deleteRecursively(empty(downloadedRemote.storageDirPath) ? null : new File(downloadedRemote.storageDirPath));
                database.bookDao().delete(downloadedRemote);
            }
            if (keepRemoteProgress) {
                local.currentChapterIndex = conflict.remoteChapterIndex;
                local.scrollY = conflict.remoteScrollY;
                local.currentPageIndex = conflict.remotePageIndex;
                local.currentPageStartOffset = conflict.remotePageStartOffset;
                local.finishedAt = conflict.remoteFinishedAt;
            }
            long now = System.currentTimeMillis();
            local.syncId = conflict.remoteSyncId;
            local.updatedAt = now;
            local.syncUpdatedAt = now;
            database.bookDao().update(local);
        });
    }

    private void mergeBookUserData(BookEntity from, BookEntity into) {
        for (BookmarkEntity bookmark : database.bookmarkDao().getForBook(from.id)) {
            BookmarkEntity existing = database.bookmarkDao().getAtPage(into.id, bookmark.chapterIndex, bookmark.pageStartOffset);
            if (existing == null) {
                bookmark.bookId = into.id;
                database.bookmarkDao().update(bookmark);
            } else {
                database.bookmarkDao().delete(bookmark);
            }
        }
        for (NoteEntity note : database.noteDao().getForBook(from.id)) {
            note.bookId = into.id;
            database.noteDao().update(note);
        }
    }

    private List<ProgressConflict> findProgressConflicts(JSONObject remote) {
        List<ProgressConflict> conflicts = new ArrayList<>();
        JSONArray books = remote.optJSONArray("books");
        if (books == null) {
            return conflicts;
        }
        List<BookEntity> localBooks = database.bookDao().getAll();
        for (int i = 0; i < books.length(); i++) {
            JSONObject remoteBook = books.optJSONObject(i);
            if (remoteBook == null) {
                continue;
            }
            String remoteSyncId = remoteBook.optString("syncId", "");
            String remoteTitle = remoteBook.optString("title", "").trim();
            if (empty(remoteSyncId) || empty(remoteTitle)) {
                continue;
            }
            for (BookEntity local : localBooks) {
                if (!remoteSyncId.equals(local.syncId)
                        && remoteTitle.equals(safeTitle(local.title))) {
                    conflicts.add(new ProgressConflict(local, remoteBook));
                    break;
                }
            }
        }
        return conflicts;
    }

    private void prepareLocalState() {
        long now = System.currentTimeMillis();
        database.runInTransaction(() -> {
            for (BookEntity book : database.bookDao().getAll()) {
                boolean changed = false;
                if (empty(book.syncId)) {
                    book.syncId = UUID.randomUUID().toString();
                    changed = true;
                }
                if (book.syncUpdatedAt <= 0L) {
                    book.syncUpdatedAt = book.updatedAt > 0L ? book.updatedAt : now;
                    changed = true;
                }
                if (changed) {
                    database.bookDao().update(book);
                }
            }
            for (BookmarkEntity bookmark : database.bookmarkDao().getAll()) {
                if (empty(bookmark.syncId) || bookmark.updatedAt <= 0L) {
                    bookmark.syncId = empty(bookmark.syncId) ? UUID.randomUUID().toString() : bookmark.syncId;
                    bookmark.updatedAt = bookmark.createdAt > 0L ? bookmark.createdAt : now;
                    database.bookmarkDao().update(bookmark);
                }
            }
            for (NoteEntity note : database.noteDao().getAll()) {
                if (empty(note.syncId) || note.updatedAt <= 0L) {
                    note.syncId = empty(note.syncId) ? UUID.randomUUID().toString() : note.syncId;
                    note.updatedAt = note.createdAt > 0L ? note.createdAt : now;
                    database.noteDao().update(note);
                }
            }
            for (FolderMetaEntity folder : database.folderMetaDao().getAll()) {
                if (empty(folder.syncId)) {
                    folder.syncId = UUID.randomUUID().toString();
                    database.folderMetaDao().save(folder);
                }
            }
            for (DailyReadingEntity daily : database.dailyReadingDao().getAll()) {
                String legacyId = "legacy-" + daily.date;
                if (database.readingEventDao().getBySyncId(legacyId) == null) {
                    ReadingEventEntity event = new ReadingEventEntity();
                    event.syncId = legacyId;
                    event.date = daily.date;
                    event.readingMillis = daily.readingMillis;
                    event.createdAt = now;
                    database.readingEventDao().insert(event);
                }
            }
        });
        if (SyncPreferences.nameUpdatedAt(context) <= 0L) {
            SyncPreferences.markNameChanged(context);
        }
        if (new File(context.getFilesDir(), "avatars/current_avatar.png").isFile()
                && SyncPreferences.avatarUpdatedAt(context) <= 0L) {
            SyncPreferences.markAvatarChanged(context);
        }
        if (SyncPreferences.settingsUpdatedAt(context) <= 0L) {
            SyncPreferences.markSettingsChanged(context);
        }
    }

    private void mergeRemote(WebDavClient client, JSONObject remote, SyncCancellationToken cancellation, Listener listener) throws Exception {
        applyRemoteTombstones(remote.optJSONArray("tombstones"));
        JSONArray books = remote.optJSONArray("books");
        if (books != null) {
            for (int i = 0; i < books.length(); i++) {
                cancellation.throwIfCancelled();
                applyRemoteBook(client, books.getJSONObject(i), cancellation);
                progress(listener, "恢复服务器书籍 " + (i + 1) + "/" + books.length(), 25 + Math.min(25, (i + 1) * 25 / Math.max(1, books.length())));
            }
        }
        applyRemoteBookmarks(remote.optJSONArray("bookmarks"));
        applyRemoteNotes(remote.optJSONArray("notes"));
        applyRemoteFolders(remote.optJSONArray("folders"));
        applyRemoteProfile(client, remote.optJSONObject("profile"), cancellation);
        applyRemoteSettings(remote.optJSONObject("readerSettings"));
        applyRemoteReadingEvents(remote.optJSONArray("readingEvents"));
    }

    private void applyRemoteTombstones(JSONArray items) {
        if (items == null) {
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = item.optString("type", "");
            String syncId = item.optString("syncId", "");
            long deletedAt = item.optLong("deletedAt", 0L);
            if (empty(type) || empty(syncId) || deletedAt <= 0L) {
                continue;
            }
            SyncTombstoneEntity local = database.syncTombstoneDao().getByKey(type + ":" + syncId);
            if (local != null && local.deletedAt >= deletedAt) {
                continue;
            }
            if ("book".equals(type)) {
                BookEntity book = database.bookDao().getBySyncId(syncId);
                if (book != null && book.syncUpdatedAt <= deletedAt) {
                    BookArchive.deleteRecursively(empty(book.storageDirPath) ? null : new File(book.storageDirPath));
                    database.bookDao().delete(book);
                }
            } else if ("bookmark".equals(type)) {
                BookmarkEntity itemToDelete = database.bookmarkDao().getBySyncId(syncId);
                if (itemToDelete != null && itemToDelete.updatedAt <= deletedAt) {
                    database.bookmarkDao().delete(itemToDelete);
                }
            } else if ("note".equals(type)) {
                NoteEntity itemToDelete = database.noteDao().getBySyncId(syncId);
                if (itemToDelete != null && itemToDelete.updatedAt <= deletedAt) {
                    database.noteDao().delete(itemToDelete);
                }
            } else if ("folder".equals(type)) {
                FolderMetaEntity folder = database.folderMetaDao().getBySyncId(syncId);
                if (folder != null && folder.updatedAt <= deletedAt) {
                    database.folderMetaDao().deleteByName(folder.name);
                }
            }
            SyncTombstoneEntity saved = new SyncTombstoneEntity();
            saved.entityType = type;
            saved.syncId = syncId;
            saved.key = type + ":" + syncId;
            saved.deletedAt = deletedAt;
            database.syncTombstoneDao().save(saved);
        }
    }

    private void applyRemoteBook(WebDavClient client, JSONObject item, SyncCancellationToken cancellation) throws Exception {
        String syncId = item.optString("syncId", "");
        long updatedAt = item.optLong("updatedAt", 0L);
        if (empty(syncId) || isDeleted("book", syncId, updatedAt)) {
            return;
        }
        BookEntity local = database.bookDao().getBySyncId(syncId);
        if (local != null && local.syncUpdatedAt > updatedAt) {
            return;
        }
        String contentHash = item.optString("contentHash", "");
        BookArchive.RestoredPayload payload = null;
        if (!empty(contentHash) && (local == null || !contentHash.equals(local.syncContentHash))) {
            cancellation.throwIfCancelled();
            File cacheDir = new File(context.getFilesDir(), "sync-cache");
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw new IllegalStateException("无法创建本地同步目录");
            }
            File temp = File.createTempFile("remote-book-", ".zip", cacheDir);
            try {
                client.download(ROOT + "objects/" + contentHash + ".zip", temp);
                if (!contentHash.equals(BookArchive.sha256(temp))) {
                    throw new IllegalArgumentException("服务器书籍文件校验失败");
                }
                payload = BookArchive.restore(context, temp, syncId);
            } finally {
                temp.delete();
            }
        }
        if (local == null) {
            local = new BookEntity();
            local.syncId = syncId;
        } else if (payload != null && !empty(local.storageDirPath)) {
            BookArchive.deleteRecursively(new File(local.storageDirPath));
        }
        copyBook(item, local);
        local.syncId = syncId;
        local.syncContentHash = contentHash;
        local.syncUpdatedAt = updatedAt;
        if (payload != null) {
            local.storageDirPath = payload.storageDirPath;
            local.originalFilePath = payload.originalFilePath;
            local.coverPath = payload.coverPath;
            local.totalChapters = payload.chapters.size();
        }
        if (local.id == 0L) {
            long id = database.bookDao().insert(local);
            local.id = id;
            if (payload != null) {
                for (ChapterEntity chapter : payload.chapters) {
                    chapter.bookId = id;
                }
                database.chapterDao().insertAll(payload.chapters);
            }
        } else {
            if (payload != null) {
                database.chapterDao().deleteForBook(local.id);
                for (ChapterEntity chapter : payload.chapters) {
                    chapter.bookId = local.id;
                }
                database.chapterDao().insertAll(payload.chapters);
            }
            database.bookDao().update(local);
        }
    }

    private void applyRemoteBookmarks(JSONArray items) {
        if (items == null) return;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String syncId = item.optString("syncId", "");
            long updatedAt = item.optLong("updatedAt", 0L);
            if (empty(syncId) || isDeleted("bookmark", syncId, updatedAt)) continue;
            BookmarkEntity local = database.bookmarkDao().getBySyncId(syncId);
            if (local != null && local.updatedAt > updatedAt) continue;
            BookEntity book = database.bookDao().getBySyncId(item.optString("bookSyncId", ""));
            if (book == null) continue;
            if (local == null) local = new BookmarkEntity();
            local.bookId = book.id;
            local.chapterIndex = item.optInt("chapterIndex", 0);
            local.pageIndex = item.optInt("pageIndex", 0);
            local.pageStartOffset = item.optInt("pageStartOffset", 0);
            local.chapterTitle = item.optString("chapterTitle", "");
            local.summary = item.optString("summary", "");
            local.createdAt = item.optLong("createdAt", updatedAt);
            local.syncId = syncId;
            local.updatedAt = updatedAt;
            if (local.id == 0L) database.bookmarkDao().insert(local); else database.bookmarkDao().update(local);
        }
    }

    private void applyRemoteNotes(JSONArray items) {
        if (items == null) return;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String syncId = item.optString("syncId", "");
            long updatedAt = item.optLong("updatedAt", 0L);
            if (empty(syncId) || isDeleted("note", syncId, updatedAt)) continue;
            NoteEntity local = database.noteDao().getBySyncId(syncId);
            if (local != null && local.updatedAt > updatedAt) continue;
            BookEntity book = database.bookDao().getBySyncId(item.optString("bookSyncId", ""));
            if (book == null) continue;
            if (local == null) local = new NoteEntity();
            local.bookId = book.id;
            local.chapterIndex = item.optInt("chapterIndex", 0);
            local.pageIndex = item.optInt("pageIndex", 0);
            local.pageStartOffset = item.optInt("pageStartOffset", 0);
            local.chapterTitle = item.optString("chapterTitle", "");
            local.selectedText = item.optString("selectedText", "");
            local.noteText = item.optString("noteText", "");
            local.color = item.optInt("color", 0x66FFE08A);
            local.createdAt = item.optLong("createdAt", updatedAt);
            local.syncId = syncId;
            local.updatedAt = updatedAt;
            if (local.id == 0L) database.noteDao().insert(local); else database.noteDao().update(local);
        }
    }

    private void applyRemoteFolders(JSONArray items) {
        if (items == null) return;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String syncId = item.optString("syncId", "");
            long updatedAt = item.optLong("updatedAt", 0L);
            if (empty(syncId) || isDeleted("folder", syncId, updatedAt)) continue;
            FolderMetaEntity local = database.folderMetaDao().getBySyncId(syncId);
            if (local != null && local.updatedAt > updatedAt) continue;
            String name = item.optString("name", "").trim();
            if (name.isEmpty() || "未分类".equals(name)) continue;
            if (local != null && !name.equals(local.name)) database.folderMetaDao().deleteByName(local.name);
            FolderMetaEntity sameName = database.folderMetaDao().getByName(name);
            if (sameName != null && (local == null || sameName != local)) local = sameName;
            if (local == null) local = new FolderMetaEntity();
            local.name = name;
            local.pinnedAt = item.optLong("pinnedAt", 0L);
            local.updatedAt = updatedAt;
            local.syncId = syncId;
            database.folderMetaDao().save(local);
        }
    }

    private void applyRemoteProfile(WebDavClient client, JSONObject profile, SyncCancellationToken cancellation) throws Exception {
        if (profile == null) return;
        long remoteNameAt = profile.optLong("nameUpdatedAt", 0L);
        if (remoteNameAt > SyncPreferences.nameUpdatedAt(context)) {
            UserProfile.saveNameFromSync(context, profile.optString("name", "默认用户"), remoteNameAt);
        }
        String hash = profile.optString("avatarHash", "");
        long remoteAvatarAt = profile.optLong("avatarUpdatedAt", 0L);
        if (remoteAvatarAt > SyncPreferences.avatarUpdatedAt(context) && empty(hash)) {
            UserProfile.clearAvatarFromSync(context, remoteAvatarAt);
            SyncPreferences.setAvatarHash(context, "");
        } else if (!empty(hash) && remoteAvatarAt > SyncPreferences.avatarUpdatedAt(context)) {
            cancellation.throwIfCancelled();
            File avatar = new File(context.getFilesDir(), "avatars/current_avatar.png");
            client.download(ROOT + "avatars/" + hash + ".png", avatar);
            if (!hash.equals(BookArchive.sha256(avatar))) {
                throw new IllegalArgumentException("服务器头像文件校验失败");
            }
            UserProfile.saveAvatarFromSync(context, Uri.fromFile(avatar), remoteAvatarAt);
            SyncPreferences.setAvatarHash(context, hash);
        }
    }

    private void applyRemoteSettings(JSONObject remote) {
        if (remote == null) return;
        long updatedAt = remote.optLong("updatedAt", 0L);
        if (updatedAt <= SyncPreferences.settingsUpdatedAt(context)) return;
        ReaderSettingsEntity settings = database.readerSettingsDao().get();
        if (settings == null) settings = new ReaderSettingsEntity();
        settings.textSizeSp = (float) remote.optDouble("textSizeSp", settings.textSizeSp);
        settings.lineSpacingMultiplier = (float) remote.optDouble("lineSpacingMultiplier", settings.lineSpacingMultiplier);
        settings.themeMode = remote.optInt("themeMode", settings.themeMode);
        settings.readMode = remote.optInt("readMode", settings.readMode);
        settings.fontMode = remote.optInt("fontMode", settings.fontMode);
        settings.paragraphSpacingDp = (float) remote.optDouble("paragraphSpacingDp", settings.paragraphSpacingDp);
        settings.firstLineIndentEm = (float) remote.optDouble("firstLineIndentEm", settings.firstLineIndentEm);
        settings.pageMarginDp = remote.optInt("pageMarginDp", settings.pageMarginDp);
        settings.pageTurnMode = remote.optInt("pageTurnMode", settings.pageTurnMode);
        database.readerSettingsDao().save(settings);
        SyncPreferences.setSettingsUpdatedAt(context, updatedAt);
    }

    private void applyRemoteReadingEvents(JSONArray items) {
        if (items == null) return;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || empty(item.optString("syncId", ""))) continue;
            ReadingEventEntity event = new ReadingEventEntity();
            event.syncId = item.optString("syncId", "");
            event.date = item.optString("date", "");
            event.readingMillis = item.optLong("readingMillis", 0L);
            event.createdAt = item.optLong("createdAt", 0L);
            database.readingEventDao().insert(event);
        }
        rebuildDailyReading();
    }

    private JSONObject buildManifest(WebDavClient client, SyncCancellationToken cancellation, Listener listener) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("deviceId", SyncPreferences.deviceId(context));
        root.put("updatedAt", System.currentTimeMillis());
        root.put("profile", buildProfile(client, cancellation));
        root.put("readerSettings", buildSettings());
        JSONArray books = new JSONArray();
        List<BookEntity> localBooks = database.bookDao().getAll();
        for (int i = 0; i < localBooks.size(); i++) {
            cancellation.throwIfCancelled();
            BookEntity book = localBooks.get(i);
            ensureBookPayload(client, book, cancellation);
            books.put(bookJson(book));
            progress(listener, "准备书籍 " + (i + 1) + "/" + localBooks.size(), 56 + Math.min(26, (i + 1) * 26 / Math.max(1, localBooks.size())));
        }
        root.put("books", books);
        root.put("bookmarks", bookmarksJson());
        root.put("notes", notesJson());
        root.put("folders", foldersJson());
        root.put("readingEvents", readingEventsJson());
        root.put("tombstones", tombstonesJson());
        return root;
    }

    private JSONObject buildProfile(WebDavClient client, SyncCancellationToken cancellation) throws Exception {
        JSONObject profile = new JSONObject();
        profile.put("name", UserProfile.name(context));
        profile.put("nameUpdatedAt", SyncPreferences.nameUpdatedAt(context));
        File avatar = new File(context.getFilesDir(), "avatars/current_avatar.png");
        String hash = "";
        if (avatar.isFile()) {
            cancellation.throwIfCancelled();
            hash = BookArchive.sha256(avatar);
            if (!client.exists(ROOT + "avatars/" + hash + ".png")) {
                client.putAtomic(ROOT + "avatars/" + hash + ".png", avatar, null);
            }
            SyncPreferences.setAvatarHash(context, hash);
        }
        profile.put("avatarHash", hash);
        profile.put("avatarUpdatedAt", SyncPreferences.avatarUpdatedAt(context));
        return profile;
    }

    private JSONObject buildSettings() throws Exception {
        ReaderSettingsEntity settings = database.readerSettingsDao().get();
        if (settings == null) settings = new ReaderSettingsEntity();
        JSONObject item = new JSONObject();
        item.put("updatedAt", SyncPreferences.settingsUpdatedAt(context));
        item.put("textSizeSp", settings.textSizeSp);
        item.put("lineSpacingMultiplier", settings.lineSpacingMultiplier);
        item.put("themeMode", settings.themeMode);
        item.put("readMode", settings.readMode);
        item.put("fontMode", settings.fontMode);
        item.put("paragraphSpacingDp", settings.paragraphSpacingDp);
        item.put("firstLineIndentEm", settings.firstLineIndentEm);
        item.put("pageMarginDp", settings.pageMarginDp);
        item.put("pageTurnMode", settings.pageTurnMode);
        return item;
    }

    private void ensureBookPayload(WebDavClient client, BookEntity book, SyncCancellationToken cancellation) throws Exception {
        if (empty(book.syncContentHash)) {
            List<ChapterEntity> chapters = database.chapterDao().getForBook(book.id);
            BookArchive.Archive archive = BookArchive.create(context, book, chapters);
            try {
                cancellation.throwIfCancelled();
                if (!client.exists(ROOT + "objects/" + archive.sha256 + ".zip")) {
                    client.putAtomic(ROOT + "objects/" + archive.sha256 + ".zip", archive.file, null);
                }
                book.syncContentHash = archive.sha256;
                database.bookDao().update(book);
            } finally {
                archive.file.delete();
            }
        }
    }

    private JSONObject bookJson(BookEntity book) throws Exception {
        JSONObject item = new JSONObject();
        item.put("syncId", book.syncId);
        item.put("contentHash", book.syncContentHash);
        item.put("updatedAt", book.syncUpdatedAt);
        item.put("title", book.title);
        item.put("author", book.author);
        item.put("fileType", book.fileType);
        item.put("category", book.category);
        item.put("description", book.description);
        item.put("totalChapters", book.totalChapters);
        item.put("currentChapterIndex", book.currentChapterIndex);
        item.put("scrollY", book.scrollY);
        item.put("currentPageIndex", book.currentPageIndex);
        item.put("currentPageStartOffset", book.currentPageStartOffset);
        item.put("chapterSplitMode", book.chapterSplitMode);
        item.put("pinnedAt", book.pinnedAt);
        item.put("finishedAt", book.finishedAt);
        item.put("createdAt", book.createdAt);
        return item;
    }

    private JSONArray bookmarksJson() throws Exception {
        JSONArray output = new JSONArray();
        for (BookmarkEntity bookmark : database.bookmarkDao().getAll()) {
            BookEntity book = database.bookDao().getById(bookmark.bookId);
            if (book == null) continue;
            JSONObject item = new JSONObject();
            item.put("syncId", bookmark.syncId); item.put("bookSyncId", book.syncId);
            item.put("chapterIndex", bookmark.chapterIndex); item.put("pageIndex", bookmark.pageIndex);
            item.put("pageStartOffset", bookmark.pageStartOffset); item.put("chapterTitle", bookmark.chapterTitle);
            item.put("summary", bookmark.summary); item.put("createdAt", bookmark.createdAt); item.put("updatedAt", bookmark.updatedAt);
            output.put(item);
        }
        return output;
    }

    private JSONArray notesJson() throws Exception {
        JSONArray output = new JSONArray();
        for (NoteEntity note : database.noteDao().getAll()) {
            BookEntity book = database.bookDao().getById(note.bookId);
            if (book == null) continue;
            JSONObject item = new JSONObject();
            item.put("syncId", note.syncId); item.put("bookSyncId", book.syncId);
            item.put("chapterIndex", note.chapterIndex); item.put("pageIndex", note.pageIndex);
            item.put("pageStartOffset", note.pageStartOffset); item.put("chapterTitle", note.chapterTitle);
            item.put("selectedText", note.selectedText); item.put("noteText", note.noteText); item.put("color", note.color);
            item.put("createdAt", note.createdAt); item.put("updatedAt", note.updatedAt);
            output.put(item);
        }
        return output;
    }

    private JSONArray foldersJson() throws Exception {
        JSONArray output = new JSONArray();
        for (FolderMetaEntity folder : database.folderMetaDao().getAll()) {
            JSONObject item = new JSONObject();
            item.put("syncId", folder.syncId); item.put("name", folder.name);
            item.put("pinnedAt", folder.pinnedAt); item.put("updatedAt", folder.updatedAt); output.put(item);
        }
        return output;
    }

    private JSONArray readingEventsJson() throws Exception {
        JSONArray output = new JSONArray();
        for (ReadingEventEntity event : database.readingEventDao().getAll()) {
            JSONObject item = new JSONObject();
            item.put("syncId", event.syncId); item.put("date", event.date);
            item.put("readingMillis", event.readingMillis); item.put("createdAt", event.createdAt); output.put(item);
        }
        return output;
    }

    private JSONArray tombstonesJson() throws Exception {
        JSONArray output = new JSONArray();
        for (SyncTombstoneEntity tombstone : database.syncTombstoneDao().getAll()) {
            JSONObject item = new JSONObject();
            item.put("type", tombstone.entityType); item.put("syncId", tombstone.syncId);
            item.put("deletedAt", tombstone.deletedAt); output.put(item);
        }
        return output;
    }

    private boolean isDeleted(String type, String syncId, long changedAt) {
        SyncTombstoneEntity tombstone = database.syncTombstoneDao().getByKey(type + ":" + syncId);
        return tombstone != null && tombstone.deletedAt >= changedAt;
    }

    private void copyBook(JSONObject item, BookEntity book) {
        book.title = item.optString("title", ""); book.author = item.optString("author", "");
        book.fileType = item.optString("fileType", "txt"); book.category = item.optString("category", "未分类");
        book.description = item.optString("description", ""); book.totalChapters = item.optInt("totalChapters", 0);
        book.currentChapterIndex = item.optInt("currentChapterIndex", 0); book.scrollY = item.optInt("scrollY", 0);
        book.currentPageIndex = item.optInt("currentPageIndex", 0); book.currentPageStartOffset = item.optInt("currentPageStartOffset", 0);
        book.chapterSplitMode = item.optInt("chapterSplitMode", 0); book.pinnedAt = item.optLong("pinnedAt", 0L);
        book.finishedAt = item.optLong("finishedAt", 0L); book.createdAt = item.optLong("createdAt", System.currentTimeMillis());
        book.updatedAt = item.optLong("updatedAt", book.createdAt);
    }

    private void rebuildDailyReading() {
        Map<String, Long> totals = new HashMap<>();
        long totalReadingMillis = 0L;
        for (ReadingEventEntity event : database.readingEventDao().getAll()) {
            if (!empty(event.date) && event.readingMillis > 0L) {
                totals.put(event.date, (totals.containsKey(event.date) ? totals.get(event.date) : 0L) + event.readingMillis);
                totalReadingMillis += event.readingMillis;
            }
        }
        database.dailyReadingDao().deleteAll();
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            DailyReadingEntity daily = new DailyReadingEntity(); daily.date = entry.getKey(); daily.readingMillis = entry.getValue(); database.dailyReadingDao().save(daily);
        }
        context.getSharedPreferences("reader_stats", Context.MODE_PRIVATE).edit()
                .putLong("totalReadingMillis", totalReadingMillis).apply();
    }

    private static void progress(Listener listener, String message, int percent) { if (listener != null) listener.onProgress(message, percent); }
    private static boolean empty(String value) { return value == null || value.trim().isEmpty(); }
    private static String safeTitle(String value) { return value == null ? "" : value.trim(); }
    private static String safeMessage(Exception e) { return e.getMessage() == null ? "未知错误" : e.getMessage(); }
    private static String formatTime(long time) { return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(time)); }
}
