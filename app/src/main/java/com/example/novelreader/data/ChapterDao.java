package com.example.novelreader.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    List<ChapterEntity> getForBook(long bookId);

    @Query("SELECT * FROM chapters WHERE bookId = :bookId AND chapterIndex = :chapterIndex LIMIT 1")
    ChapterEntity getByIndex(long bookId, int chapterIndex);

    @Insert
    void insertAll(List<ChapterEntity> chapters);

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    void deleteForBook(long bookId);
}
