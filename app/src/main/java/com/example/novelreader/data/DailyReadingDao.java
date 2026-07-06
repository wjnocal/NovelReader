package com.example.novelreader.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DailyReadingDao {
    @Query("SELECT * FROM daily_reading WHERE date = :date LIMIT 1")
    DailyReadingEntity getByDate(String date);

    @Query("SELECT * FROM daily_reading ORDER BY date DESC LIMIT :limit")
    List<DailyReadingEntity> getRecent(int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(DailyReadingEntity entity);
}
