package com.wjnocal.novelreader.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(
        tableName = "bookmarks",
        foreignKeys = @ForeignKey(
                entity = BookEntity.class,
                parentColumns = "id",
                childColumns = "bookId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("bookId"),
                @Index(value = {"bookId", "chapterIndex", "pageStartOffset"}, unique = true)
        }
)
public class BookmarkEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long bookId;
    public int chapterIndex;
    public int pageIndex;
    public int pageStartOffset;
    public String chapterTitle;
    public String summary;
    public long createdAt;
    @NonNull
    public String syncId = "";
    public long updatedAt;
}
