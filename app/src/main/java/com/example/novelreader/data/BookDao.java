package com.example.novelreader.data;

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

    @Query("SELECT * FROM books WHERE title = :title LIMIT 1")
    BookEntity getByTitle(String title);

    @Query("SELECT COUNT(*) FROM books WHERE finishedAt > 0")
    int countFinished();

    @Query("SELECT COUNT(*) FROM books WHERE finishedAt = 0")
    int countReading();

    @Query("UPDATE books SET category = :newCategory, updatedAt = :updatedAt WHERE category = :oldCategory")
    void renameCategory(String oldCategory, String newCategory, long updatedAt);

    @Query("UPDATE books SET category = '未分类', updatedAt = :updatedAt WHERE category = :category")
    void clearCategory(String category, long updatedAt);

    @Insert
    long insert(BookEntity book);

    @Update
    void update(BookEntity book);

    @Delete
    void delete(BookEntity book);
}
