package com.wjnocal.novelreader.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY chapterIndex ASC, pageStartOffset ASC, createdAt DESC")
    List<NoteEntity> getForBook(long bookId);

    @Query("SELECT COUNT(*) FROM notes WHERE bookId = :bookId")
    int countForBook(long bookId);

    @Query("SELECT * FROM notes")
    List<NoteEntity> getAll();

    @Query("SELECT * FROM notes WHERE syncId = :syncId LIMIT 1")
    NoteEntity getBySyncId(String syncId);

    @Insert
    long insert(NoteEntity note);

    @Update
    void update(NoteEntity note);

    @Delete
    void delete(NoteEntity note);

    @Query("DELETE FROM notes WHERE bookId = :bookId")
    void deleteForBook(long bookId);
}
