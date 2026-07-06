package com.example.novelreader.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ImportRecordDao {
    @Query("SELECT * FROM import_records ORDER BY updatedAt DESC")
    List<ImportRecordEntity> getAll();

    @Query("SELECT * FROM import_records WHERE id = :id LIMIT 1")
    ImportRecordEntity getById(long id);

    @Insert
    long insert(ImportRecordEntity record);

    @Update
    void update(ImportRecordEntity record);

    @Query("DELETE FROM import_records")
    void deleteAll();
}
