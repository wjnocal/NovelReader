package com.wjnocal.novelreader.ui;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

public class TextPaginator {
    private TextPaginator() {
    }

    public static List<PageInfo> paginate(
            CharSequence text,
            TextPaint paint,
            int width,
            int height,
            float lineSpacingExtra,
            float lineSpacingMultiplier
    ) {
        List<PageInfo> pages = new ArrayList<>();
        if (text == null || text.length() == 0 || width <= 0 || height <= 0) {
            pages.add(new PageInfo(0, 0));
            return pages;
        }

        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
                .setIncludePad(false)
                .build();

        int lineCount = layout.getLineCount();
        int startLine = 0;
        while (startLine < lineCount) {
            int top = layout.getLineTop(startLine);
            int endLine = startLine + 1;
            while (endLine < lineCount && layout.getLineBottom(endLine) - top <= height) {
                endLine++;
            }
            int start = layout.getLineStart(startLine);
            int end = layout.getLineEnd(Math.max(startLine, endLine - 1));
            pages.add(new PageInfo(start, Math.min(end, text.length())));
            startLine = endLine;
        }

        if (pages.isEmpty()) {
            pages.add(new PageInfo(0, text.length()));
        }
        return pages;
    }

    public static int findPageByOffset(List<PageInfo> pages, int offset) {
        if (pages == null || pages.isEmpty()) {
            return 0;
        }
        int safeOffset = Math.max(0, offset);
        for (int i = 0; i < pages.size(); i++) {
            PageInfo page = pages.get(i);
            if (safeOffset >= page.start && safeOffset < page.end) {
                return i;
            }
        }
        return safeOffset >= pages.get(pages.size() - 1).end ? pages.size() - 1 : 0;
    }
}
