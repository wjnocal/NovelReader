package com.example.novelreader.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "daily_reading")
public class DailyReadingEntity {
    @PrimaryKey
    @NonNull
    public String date = "";
    public long readingMillis;
}
