package com.wjnocal.novelreader.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reader_settings")
public class ReaderSettingsEntity {
    @PrimaryKey
    public int id = 1;
    public float textSizeSp = 19f;
    public float lineSpacingMultiplier = 1.55f;
    public int themeMode = 0;
    public int readMode = 0;
    public int fontMode = 0;
    public float paragraphSpacingDp = 0f;
    public float firstLineIndentEm = 2f;
    public int pageMarginDp = 22;
    public int pageTurnMode = 0;
}
