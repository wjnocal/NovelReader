package com.example.novelreader.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "books")
public class BookEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String title;
    public String author;
    public String fileType;
    public String category = "未分类";
    public String description;
    public String coverPath;
    public String originalFilePath;
    public String storageDirPath;
    public int totalChapters;
    public int currentChapterIndex;
    public int scrollY;
    public int currentPageIndex;
    public int currentPageStartOffset;
    public int chapterSplitMode;
    public long pinnedAt;
    public long finishedAt;
    public long createdAt;
    public long updatedAt;
}
