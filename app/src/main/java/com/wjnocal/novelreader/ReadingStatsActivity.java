package com.wjnocal.novelreader;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.wjnocal.novelreader.data.AppDatabase;
import com.wjnocal.novelreader.data.DailyReadingEntity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReadingStatsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "reader_stats";
    private static final String KEY_TOTAL_READING_MILLIS = "totalReadingMillis";
    private static final int MODE_DAY = 0;
    private static final int MODE_WEEK = 1;
    private static final int MODE_MONTH = 2;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private SharedPreferences prefs;
    private LinearLayout content;
    private LinearLayout chartContainer;
    private final List<DailyReadingEntity> allDaily = new ArrayList<>();
    private int chartMode = MODE_DAY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = AppDatabase.getInstance(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        setContentView(createContentView());
        hideSystemBars();
        loadStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private ScrollView createContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFFF8F5EF);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(18), dp(22), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        return scrollView;
    }

    private void loadStats() {
        content.removeAllViews();
        addHeader();
        addLoading();
        executor.execute(() -> {
            List<DailyReadingEntity> recent = database.dailyReadingDao().getRecent(7);
            List<DailyReadingEntity> all = database.dailyReadingDao().getAll();
            int finished = database.bookDao().countFinished();
            int reading = database.bookDao().countReading();
            long totalMillis = prefs.getLong(KEY_TOTAL_READING_MILLIS, 0L);
            runOnUiThread(() -> renderStats(recent, all, finished, reading, totalMillis));
        });
    }

    private void renderStats(List<DailyReadingEntity> recent, List<DailyReadingEntity> all, int finished, int reading, long totalMillis) {
        allDaily.clear();
        allDaily.addAll(all);
        content.removeAllViews();
        addHeader();

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setBackground(cardBackground(0xCCFFFFFF, dp(28)));
        summary.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        summaryParams.setMargins(0, dp(16), 0, dp(20));
        content.addView(summary, summaryParams);

        addSummaryRow(summary, "今日阅读", formatMinutes(getTodayMillis(recent)));
        addSummaryRow(summary, "累计阅读", formatMinutes(totalMillis));
        addSummaryRow(summary, "连续阅读", countReadingStreak(recent) + " 天");
        addSummaryRow(summary, "在读书籍", reading + " 本");
        addSummaryRow(summary, "已读完", finished + " 本");

        addChartSection();
    }

    private void addHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView back = createText("‹", 38f, 0xFF111111, false);
        back.setGravity(android.view.Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(50)));

        TextView title = createText("阅读统计", 30f, 0xFF111111, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(6), 0, 0, 0);
        bar.addView(title, titleParams);
        content.addView(bar);
    }

    private void addLoading() {
        TextView loading = createText("正在整理阅读记录…", 16f, 0xFF666666, false);
        loading.setPadding(0, dp(26), 0, 0);
        content.addView(loading);
    }

    private void addSummaryRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView labelView = createText(label, 16f, 0xFF666666, false);
        TextView valueView = createText(value, 22f, 0xFF111111, true);
        valueView.setGravity(android.view.Gravity.END);
        row.addView(labelView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row);
    }

    private void addChartSection() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView section = createText("阅读趋势", 22f, 0xFF111111, true);
        header.addView(section, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(createModeTabs());
        content.addView(header);

        chartContainer = new LinearLayout(this);
        chartContainer.setOrientation(LinearLayout.VERTICAL);
        chartContainer.setBackground(cardBackground(0xCCFFFFFF, dp(28)));
        chartContainer.setPadding(dp(14), dp(18), dp(14), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(14), 0, 0);
        content.addView(chartContainer, params);
        updateChart();
    }

    private LinearLayout createModeTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(android.view.Gravity.CENTER);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(cardBackground(0xAAFFFFFF, dp(22)));
        tabs.addView(createModeTab("按天", MODE_DAY));
        tabs.addView(createModeTab("按周", MODE_WEEK));
        tabs.addView(createModeTab("按月", MODE_MONTH));
        return tabs;
    }

    private TextView createModeTab(String text, int mode) {
        TextView tab = createText(text, 15f, chartMode == mode ? 0xFFB64B4B : 0xFF111111, true);
        tab.setGravity(android.view.Gravity.CENTER);
        tab.setPadding(dp(13), dp(7), dp(13), dp(7));
        tab.setBackground(cardModeBackground(chartMode == mode));
        tab.setOnClickListener(v -> {
            if (chartMode == mode) {
                return;
            }
            chartMode = mode;
            loadStats();
        });
        return tab;
    }

    private void updateChart() {
        chartContainer.removeAllViews();
        List<ChartEntry> entries = buildChartEntries(chartMode);
        if (entries.isEmpty()) {
            TextView empty = createText("暂无记录", 16f, 0xFF666666, false);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(62), 0, dp(62));
            chartContainer.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return;
        }
        BarChartView chart = new BarChartView(this);
        chart.setEntries(entries);
        chartContainer.addView(chart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(240)
        ));
    }

    private List<ChartEntry> buildChartEntries(int mode) {
        if (allDaily.isEmpty()) {
            return Collections.emptyList();
        }
        switch (mode) {
            case MODE_WEEK:
                return buildWeekEntries();
            case MODE_MONTH:
                return buildMonthEntries();
            case MODE_DAY:
            default:
                return buildDayEntries();
        }
    }

    private List<ChartEntry> buildDayEntries() {
        List<ChartEntry> entries = new ArrayList<>();
        int start = Math.max(0, allDaily.size() - 7);
        for (int i = start; i < allDaily.size(); i++) {
            DailyReadingEntity day = allDaily.get(i);
            String label = day.date == null ? "" : day.date.substring(Math.max(0, day.date.length() - 5));
            entries.add(new ChartEntry(label, day.readingMillis));
        }
        return entries;
    }

    private List<ChartEntry> buildWeekEntries() {
        List<ChartEntry> entries = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar currentWeek = null;
        long millis = 0L;
        for (DailyReadingEntity day : allDaily) {
            Calendar week = calendarFor(day.date, dateFormat);
            if (week == null) {
                continue;
            }
            week.setFirstDayOfWeek(Calendar.MONDAY);
            week.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            clearTime(week);
            if (currentWeek == null || !sameDay(currentWeek, week)) {
                if (currentWeek != null) {
                    entries.add(new ChartEntry(monthDayLabel(currentWeek), millis));
                }
                currentWeek = (Calendar) week.clone();
                millis = 0L;
            }
            millis += day.readingMillis;
        }
        if (currentWeek != null) {
            entries.add(new ChartEntry(monthDayLabel(currentWeek), millis));
        }
        return tail(entries, 8);
    }

    private List<ChartEntry> buildMonthEntries() {
        List<ChartEntry> entries = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar currentMonth = null;
        long millis = 0L;
        for (DailyReadingEntity day : allDaily) {
            Calendar month = calendarFor(day.date, dateFormat);
            if (month == null) {
                continue;
            }
            month.set(Calendar.DAY_OF_MONTH, 1);
            clearTime(month);
            if (currentMonth == null || currentMonth.get(Calendar.YEAR) != month.get(Calendar.YEAR)
                    || currentMonth.get(Calendar.MONTH) != month.get(Calendar.MONTH)) {
                if (currentMonth != null) {
                    entries.add(new ChartEntry(monthLabel(currentMonth), millis));
                }
                currentMonth = (Calendar) month.clone();
                millis = 0L;
            }
            millis += day.readingMillis;
        }
        if (currentMonth != null) {
            entries.add(new ChartEntry(monthLabel(currentMonth), millis));
        }
        return tail(entries, 8);
    }

    private List<ChartEntry> tail(List<ChartEntry> entries, int limit) {
        if (entries.size() <= limit) {
            return entries;
        }
        return new ArrayList<>(entries.subList(entries.size() - limit, entries.size()));
    }

    private TextView createText(String text, float sizeSp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(sizeSp);
        textView.setTextColor(color);
        textView.setIncludeFontPadding(true);
        if (bold) {
            textView.setTypeface(null, Typeface.BOLD);
        }
        return textView;
    }

    private GradientDrawable cardBackground(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private GradientDrawable cardModeBackground(boolean selected) {
        GradientDrawable drawable = cardBackground(selected ? 0xFFFFFFFF : 0x00FFFFFF, dp(18));
        if (selected) {
            drawable.setStroke(dp(1), 0x22B64B4B);
        }
        return drawable;
    }

    private long getTodayMillis(List<DailyReadingEntity> recent) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
        for (DailyReadingEntity day : recent) {
            if (today.equals(day.date)) {
                return day.readingMillis;
            }
        }
        return 0L;
    }

    private int countReadingStreak(List<DailyReadingEntity> recent) {
        Set<String> activeDays = new HashSet<>();
        for (DailyReadingEntity day : recent) {
            if (day.readingMillis > 0) {
                activeDays.add(day.date);
            }
        }
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        int streak = 0;
        while (activeDays.contains(format.format(calendar.getTime()))) {
            streak++;
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    private Calendar calendarFor(String date, SimpleDateFormat format) {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(format.parse(date));
            return calendar;
        } catch (ParseException e) {
            return null;
        }
    }

    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private boolean sameDay(Calendar left, Calendar right) {
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
    }

    private String monthDayLabel(Calendar calendar) {
        return new SimpleDateFormat("MM-dd", Locale.US).format(calendar.getTime());
    }

    private String monthLabel(Calendar calendar) {
        return new SimpleDateFormat("yy-MM", Locale.US).format(calendar.getTime());
    }

    private String formatMinutes(long millis) {
        long minutes = Math.max(0L, millis / 60000L);
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private static class ChartEntry {
        final String label;
        final long millis;

        ChartEntry(String label, long millis) {
            this.label = label;
            this.millis = millis;
        }
    }

    private class BarChartView extends View {
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private List<ChartEntry> entries = Collections.emptyList();

        BarChartView(android.content.Context context) {
            super(context);
            barPaint.setColor(0xFFB64B4B);
            textPaint.setColor(0xFF555555);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(dp(12));
            axisPaint.setColor(0x1F111111);
            axisPaint.setStrokeWidth(dp(1));
        }

        void setEntries(List<ChartEntry> entries) {
            this.entries = entries == null ? Collections.emptyList() : entries;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (entries.isEmpty()) {
                return;
            }
            int left = dp(8);
            int right = getWidth() - dp(8);
            int top = dp(20);
            int bottom = getHeight() - dp(42);
            canvas.drawLine(left, bottom, right, bottom, axisPaint);

            long max = 0L;
            for (ChartEntry entry : entries) {
                max = Math.max(max, entry.millis);
            }
            if (max <= 0L) {
                max = 60000L;
            }

            float slot = (right - left) / (float) entries.size();
            float barWidth = Math.min(dp(30), slot * 0.52f);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            for (int i = 0; i < entries.size(); i++) {
                ChartEntry entry = entries.get(i);
                float centerX = left + slot * i + slot / 2f;
                float percent = Math.max(0.04f, entry.millis / (float) max);
                float barHeight = (bottom - top) * percent;
                rect.set(centerX - barWidth / 2f, bottom - barHeight, centerX + barWidth / 2f, bottom);
                canvas.drawRoundRect(rect, dp(8), dp(8), barPaint);
                canvas.drawText(shortMinutes(entry.millis), centerX, rect.top - dp(7), textPaint);
                canvas.drawText(entry.label, centerX, getHeight() - dp(14) - metrics.ascent / 2f, textPaint);
            }
        }

        private String shortMinutes(long millis) {
            long minutes = Math.max(0L, millis / 60000L);
            if (minutes < 60) {
                return minutes + "m";
            }
            return (minutes / 60) + "h";
        }
    }
}
