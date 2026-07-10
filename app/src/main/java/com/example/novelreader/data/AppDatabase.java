package com.example.novelreader.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                BookEntity.class,
                ChapterEntity.class,
                ReaderSettingsEntity.class,
                BookmarkEntity.class,
                NoteEntity.class,
                DailyReadingEntity.class,
                FolderMetaEntity.class,
                ImportRecordEntity.class,
                SyncTombstoneEntity.class,
                ReadingEventEntity.class
        },
        version = 9,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN currentPageIndex INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE books ADD COLUMN currentPageStartOffset INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE reader_settings ADD COLUMN readMode INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE TABLE IF NOT EXISTS bookmarks ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "bookId INTEGER NOT NULL, "
                    + "chapterIndex INTEGER NOT NULL, "
                    + "pageIndex INTEGER NOT NULL, "
                    + "pageStartOffset INTEGER NOT NULL, "
                    + "chapterTitle TEXT, "
                    + "summary TEXT, "
                    + "createdAt INTEGER NOT NULL, "
                    + "FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId ON bookmarks(bookId)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bookmarks_bookId_chapterIndex_pageStartOffset "
                    + "ON bookmarks(bookId, chapterIndex, pageStartOffset)");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN chapterSplitMode INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN pinnedAt INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN category TEXT DEFAULT '未分类'");
            database.execSQL("ALTER TABLE books ADD COLUMN description TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN coverPath TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN finishedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE TABLE IF NOT EXISTS notes ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "bookId INTEGER NOT NULL, "
                    + "chapterIndex INTEGER NOT NULL, "
                    + "pageIndex INTEGER NOT NULL, "
                    + "pageStartOffset INTEGER NOT NULL, "
                    + "chapterTitle TEXT, "
                    + "selectedText TEXT, "
                    + "noteText TEXT, "
                    + "color INTEGER NOT NULL, "
                    + "createdAt INTEGER NOT NULL, "
                    + "FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_notes_bookId ON notes(bookId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_notes_bookId_chapterIndex_pageStartOffset "
                    + "ON notes(bookId, chapterIndex, pageStartOffset)");
            database.execSQL("CREATE TABLE IF NOT EXISTS daily_reading ("
                    + "date TEXT NOT NULL, "
                    + "readingMillis INTEGER NOT NULL, "
                    + "PRIMARY KEY(date))");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS folder_meta ("
                    + "name TEXT NOT NULL, "
                    + "pinnedAt INTEGER NOT NULL, "
                    + "updatedAt INTEGER NOT NULL, "
                    + "PRIMARY KEY(name))");
        }
    };

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS import_records ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "uri TEXT, "
                    + "displayName TEXT, "
                    + "status TEXT, "
                    + "errorMessage TEXT, "
                    + "bookId INTEGER NOT NULL, "
                    + "createdAt INTEGER NOT NULL, "
                    + "updatedAt INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE reader_settings ADD COLUMN fontMode INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE reader_settings ADD COLUMN paragraphSpacingDp REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE reader_settings ADD COLUMN firstLineIndentEm REAL NOT NULL DEFAULT 2");
            database.execSQL("ALTER TABLE reader_settings ADD COLUMN pageMarginDp INTEGER NOT NULL DEFAULT 22");
            database.execSQL("ALTER TABLE reader_settings ADD COLUMN pageTurnMode INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN syncId TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE books ADD COLUMN syncContentHash TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE books ADD COLUMN syncUpdatedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE bookmarks ADD COLUMN syncId TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE bookmarks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE notes ADD COLUMN syncId TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE notes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE folder_meta ADD COLUMN syncId TEXT NOT NULL DEFAULT ''");
            database.execSQL("CREATE TABLE IF NOT EXISTS sync_tombstones (key TEXT NOT NULL, entityType TEXT, syncId TEXT, deletedAt INTEGER NOT NULL, PRIMARY KEY(key))");
            database.execSQL("CREATE TABLE IF NOT EXISTS reading_events (syncId TEXT NOT NULL, date TEXT, readingMillis INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(syncId))");
        }
    };

    public abstract BookDao bookDao();

    public abstract ChapterDao chapterDao();

    public abstract ReaderSettingsDao readerSettingsDao();

    public abstract BookmarkDao bookmarkDao();

    public abstract NoteDao noteDao();

    public abstract DailyReadingDao dailyReadingDao();

    public abstract FolderMetaDao folderMetaDao();

    public abstract ImportRecordDao importRecordDao();

    public abstract SyncTombstoneDao syncTombstoneDao();

    public abstract ReadingEventDao readingEventDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "novel_reader.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build();
                }
            }
        }
        return instance;
    }
}
