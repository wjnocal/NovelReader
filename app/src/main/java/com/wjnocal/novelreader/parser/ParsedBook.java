package com.wjnocal.novelreader.parser;

import java.util.ArrayList;
import java.util.List;

public class ParsedBook {
    public String title;
    public String author;
    public String fileType;
    public String description;
    public String coverPath;
    public final List<ParsedChapter> chapters = new ArrayList<>();
}
