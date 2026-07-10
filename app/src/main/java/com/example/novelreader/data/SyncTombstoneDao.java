package com.example.novelreader.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SyncTombstoneDao {
    @Query("SELECT * FROM sync_tombstones")
    List<SyncTombstoneEntity> getAll();

    @Query("SELECT * FROM sync_tombstones WHERE key = :key LIMIT 1")
    SyncTombstoneEntity getByKey(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(SyncTombstoneEntity tombstone);
}
