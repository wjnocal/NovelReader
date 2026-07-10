package com.example.novelreader.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ReadingEventDao {
    @Query("SELECT * FROM reading_events")
    List<ReadingEventEntity> getAll();

    @Query("SELECT * FROM reading_events WHERE syncId = :syncId LIMIT 1")
    ReadingEventEntity getBySyncId(String syncId);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(ReadingEventEntity event);
}
