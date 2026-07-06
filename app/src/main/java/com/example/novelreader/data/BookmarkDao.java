package com.example.novelreader.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY chapterIndex ASC, pageStartOffset ASC")
    List<BookmarkEntity> getForBook(long bookId);

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND chapterIndex = :chapterIndex AND pageStartOffset = :pageStartOffset LIMIT 1")
    BookmarkEntity getAtPage(long bookId, int chapterIndex, int pageStartOffset);

    @Query("SELECT COUNT(*) FROM bookmarks WHERE bookId = :bookId")
    int countForBook(long bookId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BookmarkEntity bookmark);

    @Delete
    void delete(BookmarkEntity bookmark);

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    void deleteForBook(long bookId);
}
