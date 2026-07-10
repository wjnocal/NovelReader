package com.example.novelreader.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Immutable reading-duration event, used so synced daily totals never double count. */
@Entity(tableName = "reading_events")
public class ReadingEventEntity {
    @PrimaryKey
    @NonNull
    public String syncId = "";
    public String date = "";
    public long readingMillis;
    public long createdAt;
}
