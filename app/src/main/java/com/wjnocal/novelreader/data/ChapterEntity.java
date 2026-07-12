package com.wjnocal.novelreader.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "chapters",
        foreignKeys = @ForeignKey(
                entity = BookEntity.class,
                parentColumns = "id",
                childColumns = "bookId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("bookId"), @Index(value = {"bookId", "chapterIndex"}, unique = true)}
)
public class ChapterEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long bookId;
    public int chapterIndex;
    public String title;
    public String contentPath;
}
