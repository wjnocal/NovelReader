package com.example.novelreader.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface ReaderSettingsDao {
    @Query("SELECT * FROM reader_settings WHERE id = 1 LIMIT 1")
    ReaderSettingsEntity get();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(ReaderSettingsEntity settings);
}
