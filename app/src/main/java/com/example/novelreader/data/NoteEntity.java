package com.example.novelreader.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "notes",
        foreignKeys = @ForeignKey(
                entity = BookEntity.class,
                parentColumns = "id",
                childColumns = "bookId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("bookId"), @Index(value = {"bookId", "chapterIndex", "pageStartOffset"})}
)
public class NoteEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long bookId;
    public int chapterIndex;
    public int pageIndex;
    public int pageStartOffset;
    public String chapterTitle;
    public String selectedText;
    public String noteText;
    public int color;
    public long createdAt;
}
