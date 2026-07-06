package com.example.novelreader.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FolderMetaDao {
    @Query("SELECT * FROM folder_meta")
    List<FolderMetaEntity> getAll();

    @Query("SELECT * FROM folder_meta WHERE name = :name LIMIT 1")
    FolderMetaEntity getByName(String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(FolderMetaEntity folder);

    @Query("DELETE FROM folder_meta WHERE name = :name")
    void deleteByName(String name);
}
