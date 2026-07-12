package com.wjnocal.novelreader.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Records deletions long enough for another device to receive them. */
@Entity(tableName = "sync_tombstones")
public class SyncTombstoneEntity {
    @PrimaryKey
    @NonNull
    public String key = "";
    public String entityType = "";
    public String syncId = "";
    public long deletedAt;
}
