package com.wjnocal.novelreader.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BookDao {
    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    List<BookEntity> getAll();

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    BookEntity getById(long bookId);

    @Query("SELECT * FROM books WHERE syncId = :syncId LIMIT 1")
    BookEntity getBySyncId(String syncId);

    @Query("SELECT * FROM books WHERE title = :title LIMIT 1")
    BookEntity getByTitle(String title);

    @Query("SELECT COUNT(*) FROM books WHERE finishedAt > 0")
    int countFinished();

    @Query("SELECT COUNT(*) FROM books WHERE finishedAt = 0")
    int countReading();

    @Query("UPDATE books SET category = :newCategory, updatedAt = :updatedAt, syncUpdatedAt = :updatedAt WHERE category = :oldCategory")
    void renameCategory(String oldCategory, String newCategory, long updatedAt);

    @Query("UPDATE books SET category = '未分类', updatedAt = :updatedAt, syncUpdatedAt = :updatedAt WHERE category = :category")
    void clearCategory(String category, long updatedAt);

    @Query("UPDATE books SET currentChapterIndex = 0, scrollY = 0, currentPageIndex = 0, currentPageStartOffset = 0")
    void clearReadingProgress();

    @Query("UPDATE books SET currentChapterIndex = :chapterIndex, scrollY = :scrollY, currentPageIndex = :pageIndex, "
            + "currentPageStartOffset = :pageStartOffset, updatedAt = :updatedAt, syncUpdatedAt = :updatedAt WHERE id = :bookId")
    void updateReadingProgress(long bookId, int chapterIndex, int scrollY, int pageIndex, int pageStartOffset, long updatedAt);

    @Query("UPDATE books SET finishedAt = :finishedAt, updatedAt = :finishedAt, syncUpdatedAt = :finishedAt WHERE id = :bookId")
    void markFinished(long bookId, long finishedAt);

    @Insert
    long insert(BookEntity book);

    @Update
    void update(BookEntity book);

    @Delete
    void delete(BookEntity book);
}
