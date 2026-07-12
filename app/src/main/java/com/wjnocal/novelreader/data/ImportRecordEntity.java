package com.wjnocal.novelreader.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "import_records")
public class ImportRecordEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String uri;
    public String displayName;
    public String status;
    public String errorMessage;
    public long bookId;
    public long createdAt;
    public long updatedAt;
}
