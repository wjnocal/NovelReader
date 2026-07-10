package com.example.novelreader.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "folder_meta")
public class FolderMetaEntity {
    @PrimaryKey
    @NonNull
    public String name = "";
    public long pinnedAt;
    public long updatedAt;
    @NonNull
    public String syncId = "";
}
