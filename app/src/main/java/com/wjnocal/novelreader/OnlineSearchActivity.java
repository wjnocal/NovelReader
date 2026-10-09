package com.wjnocal.novelreader;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.wjnocal.novelreader.online.OnlineBookClient;
import com.wjnocal.novelreader.online.OnlineBookResult;
import com.wjnocal.novelreader.online.OnlineBookSource;
import com.wjnocal.novelreader.online.OnlineDownloadService;
import com.wjnocal.novelreader.online.OnlineSourceRepository;
import com.wjnocal.novelreader.ui.ReaderActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OnlineSearchActivity extends AppCompatActivity {
    private static final int COLOR_PAGE = 0xFFF4F0EA;
    private static final int COLOR_SURFACE = 0xFFFFFCF8;
    private static final int COLOR_SURFACE_STRONG = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFF211F1D;
    private static final int COLOR_MUTED = 0xFF77716B;
    private static final int COLOR_LINE = 0x1F5E5149;
    private static final int COLOR_ACCENT = 0xFFB95A52;
    private static final int COLOR_ACCENT_DARK = 0xFF97463F;
    private static final int COLOR_ACCENT_SOFT = 0xFFFFE9E4;
    private static final int COLOR_DISABLED = 0xFF9A928B;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final List<OnlineBookSource> allSources = new ArrayList<>();
    private final Map<String, CheckBox> sourceChecks = new LinkedHashMap<>();

    private EditText keywordInput;
    private RadioGroup searchModeGroup;
    private RadioButton keywordModeButton;
    private RadioButton urlModeButton;
    private RadioGroup formatGroup;
    private RadioButton txtFormatButton;
    private ProgressBar searchProgress;
    private TextView progressText;
    private LinearLayout sourceList;
    private LinearLayout resultList;
    private TextView resultSectionLabel;
    private ScrollView pageScrollView;
    private Button searchButton;
    private int checkedSearchModeId = View.NO_ID;
    private int checkedFormatId = View.NO_ID;
    private Dialog currentDownloadDialog;
    private TextView currentDownloadProgress;
    private TextView currentDownloadReadButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        hideSystemBars();
        applyTopSafeArea(pageScrollView);
        loadSources();
    }

    @Override
    protected void onStart() {
        super.onStart();
        hideSystemBars();
        OnlineDownloadService.setListener(new OnlineDownloadService.Listener() {
            @Override
            public void onDownloadStatus(String message) {
                runOnUiThread(() -> updateDownloadProgress(message));
            }

            @Override
            public void onBookAvailable(long bookId) {
                runOnUiThread(() -> showDownloadReadable(bookId));
            }

            @Override
            public void onDownloadFinished(long bookId) {
                runOnUiThread(() -> showDownloadFinished(bookId));
            }

            @Override
            public void onDownloadCancelled() {
                runOnUiThread(() -> showDownloadStopped("下载已取消" + retainedChaptersMessage()));
            }

            @Override
            public void onDownloadFailed(String message) {
                runOnUiThread(() -> showDownloadStopped("下载失败：" + message + retainedChaptersMessage()));
            }
        });
    }

    @Override
    protected void onStop() {
        OnlineDownloadService.setListener(null);
        super.onStop();
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

    private View createContentView() {
        pageScrollView = new ScrollView(this);
        pageScrollView.setFillViewport(true);
        pageScrollView.setClipToPadding(false);
        pageScrollView.setBackgroundColor(COLOR_PAGE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_PAGE);
        root.setPadding(dpToPx(16), dpToPx(18), dpToPx(16), dpToPx(14));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        TextView back = textButton("‹ 返回");
        back.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText("在线搜书");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(25f);
        title.setTypeface(null, Typeface.BOLD);
        TextView browser = textButton("浏览器");
        browser.setOnClickListener(v -> startActivity(BrowserActivity.createIntent(this, keywordInput == null ? "" : keywordInput.getText().toString().trim())));
        topBar.addView(back, new LinearLayout.LayoutParams(dpToPx(82), dpToPx(46)));
        topBar.addView(title, new LinearLayout.LayoutParams(0, dpToPx(44), 1f));
        topBar.addView(browser, new LinearLayout.LayoutParams(dpToPx(72), dpToPx(46)));
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        topParams.setMargins(0, 0, 0, dpToPx(10));
        root.addView(topBar, topParams);

        keywordInput = new EditText(this);
        keywordInput.setSingleLine(true);
        keywordInput.setHint("输入书名、作者或详情页网址");
        keywordInput.setTextColor(COLOR_TEXT);
        keywordInput.setHintTextColor(0xFF9A928B);
        keywordInput.setTextSize(16f);
        keywordInput.setMinHeight(dpToPx(54));
        keywordInput.setPadding(dpToPx(16), 0, dpToPx(16), 0);
        keywordInput.setBackground(roundedBackground(COLOR_SURFACE_STRONG, 16, 0x1A7C6B60, 1));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, 0, 0, dpToPx(12));
        root.addView(keywordInput, inputParams);

        LinearLayout controls = surfacePanel();
        TextView modeLabel = sectionLabel("搜索方式");
        controls.addView(modeLabel);
        searchModeGroup = new RadioGroup(this);
        searchModeGroup.setOrientation(RadioGroup.HORIZONTAL);
        keywordModeButton = radioButton("书名搜索", true);
        urlModeButton = radioButton("网址下载", false);
        searchModeGroup.addView(keywordModeButton);
        searchModeGroup.addView(urlModeButton);
        checkedSearchModeId = keywordModeButton.getId();
        makeRadioGroupClearable(searchModeGroup, true);
        controls.addView(searchModeGroup);

        TextView formatLabel = sectionLabel("下载格式");
        controls.addView(formatLabel);
        formatGroup = new RadioGroup(this);
        formatGroup.setOrientation(RadioGroup.HORIZONTAL);
        txtFormatButton = radioButton("TXT", true);
        formatGroup.addView(txtFormatButton);
        formatGroup.addView(radioButton("EPUB", false));
        checkedFormatId = txtFormatButton.getId();
        makeRadioGroupClearable(formatGroup, false);
        controls.addView(formatGroup);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        searchButton = new Button(this);
        searchButton.setText("开始");
        styleActionButton(searchButton, true);
        searchButton.setOnClickListener(v -> startAction());
        Button selectAllButton = new Button(this);
        selectAllButton.setText("全选");
        styleActionButton(selectAllButton, false);
        selectAllButton.setOnClickListener(v -> setAllSearchSourcesChecked(true));
        Button clearButton = new Button(this);
        clearButton.setText("清空");
        styleActionButton(clearButton, false);
        clearButton.setOnClickListener(v -> setAllSearchSourcesChecked(false));
        actionRow.addView(searchButton, actionButtonParams(true));
        actionRow.addView(selectAllButton, actionButtonParams(true));
        actionRow.addView(clearButton, actionButtonParams(false));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.setMargins(0, dpToPx(12), 0, 0);
        controls.addView(actionRow, actionParams);

        searchProgress = new ProgressBar(this);
        searchProgress.setIndeterminate(true);
        searchProgress.setVisibility(View.GONE);
        controls.addView(searchProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(32)
        ));
        progressText = new TextView(this);
        progressText.setTextColor(COLOR_MUTED);
        progressText.setTextSize(14f);
        progressText.setVisibility(View.GONE);
        progressText.setPadding(dpToPx(2), dpToPx(4), dpToPx(2), 0);
        controls.addView(progressText);
        root.addView(controls, sectionParams(0, 0, 0, dpToPx(12)));

        root.addView(sectionLabel("支持的网点"));
        ScrollView sourceScroll = new ScrollView(this);
        sourceScroll.setFillViewport(false);
        sourceScroll.setClipToPadding(false);
        sourceScroll.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(2));
        sourceScroll.setBackground(roundedBackground(COLOR_SURFACE_STRONG, 16, COLOR_LINE, 1));
        sourceScroll.setNestedScrollingEnabled(true);
        sourceScroll.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(
                    event.getActionMasked() == MotionEvent.ACTION_DOWN
                            || event.getActionMasked() == MotionEvent.ACTION_MOVE
            );
            return false;
        });
        sourceList = new LinearLayout(this);
        sourceList.setOrientation(LinearLayout.VERTICAL);
        sourceScroll.addView(sourceList);
        root.addView(sourceScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(184)
        ));

        resultSectionLabel = sectionLabel("搜索结果");
        root.addView(resultSectionLabel);
        LinearLayout resultPanel = new LinearLayout(this);
        resultPanel.setOrientation(LinearLayout.VERTICAL);
        resultPanel.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
        resultPanel.setMinimumHeight(dpToPx(180));
        resultPanel.setBackground(roundedBackground(COLOR_SURFACE_STRONG, 16, COLOR_LINE, 1));
        resultList = new LinearLayout(this);
        resultList.setOrientation(LinearLayout.VERTICAL);
        resultPanel.addView(resultList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(resultPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        pageScrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return pageScrollView;
    }

    private void applyTopSafeArea(View rootView) {
        int initialLeft = rootView.getPaddingLeft();
        int initialTop = rootView.getPaddingTop();
        int initialRight = rootView.getPaddingRight();
        int initialBottom = rootView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, windowInsets) -> {
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int safeTop = Math.max(cutout.top, systemBars.top);
            view.setPadding(initialLeft, initialTop + safeTop, initialRight, initialBottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }

    private void loadSources() {
        executor.execute(() -> {
            try {
                List<OnlineBookSource> sources = new OnlineSourceRepository(this).loadAllSources();
                runOnUiThread(() -> {
                    allSources.clear();
                    allSources.addAll(sources);
                    renderSourceList();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "读取书源失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void renderSourceList() {
        sourceList.removeAllViews();
        sourceChecks.clear();
        for (OnlineBookSource source : allSources) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(8), dpToPx(5), dpToPx(8), dpToPx(5));
            row.setBackground(roundedBackground(source.enabled ? COLOR_SURFACE : 0xFFF1EDE7, 12, 0x0F000000, 1));
            CheckBox check = new CheckBox(this);
            check.setText(source.name + " · " + sourceStateText(source));
            check.setTextColor(source.enabled ? COLOR_TEXT : COLOR_DISABLED);
            check.setTextSize(15f);
            check.setChecked(source.enabled);
            check.setEnabled(source.enabled);
            check.setButtonTintList(controlTint());
            sourceChecks.put(source.id, check);
            row.addView(check, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));
            TextView openButton = textButton("官网");
            openButton.setTextSize(13f);
            openButton.setTextColor(source.enabled ? COLOR_ACCENT : COLOR_DISABLED);
            openButton.setBackground(roundedBackground(source.enabled ? COLOR_ACCENT_SOFT : 0xFFEDE8E2, 999, source.enabled ? 0x33B95A52 : 0x1A000000, 1));
            openButton.setOnClickListener(v -> openSourceInBrowser(source));
            row.addView(openButton, new LinearLayout.LayoutParams(
                    dpToPx(56),
                    dpToPx(34)
            ));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 0, 0, dpToPx(7));
            sourceList.addView(row, rowParams);
        }
    }

    private String sourceStateText(OnlineBookSource source) {
        String proxy = source.needProxy ? " · 需代理" : "";
        if (!source.enabled) {
            return "暂不可用" + proxy;
        }
        if (source.search.disabled || source.search.result == null || source.search.result.trim().isEmpty()) {
            return "仅网址下载" + proxy;
        }
        return "可搜索" + proxy;
    }

    private void openSourceInBrowser(OnlineBookSource source) {
        String url = source.baseUrl == null ? "" : source.baseUrl.trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "站点地址为空", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(BrowserActivity.createIntent(this, url));
    }

    private void setAllSearchSourcesChecked(boolean checked) {
        for (CheckBox check : sourceChecks.values()) {
            if (check.isEnabled()) {
                check.setChecked(checked);
            }
        }
    }

    private void startAction() {
        String value = keywordInput.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!keywordModeButton.isChecked() && !urlModeButton.isChecked()) {
            Toast.makeText(this, "请选择搜索方式", Toast.LENGTH_SHORT).show();
            return;
        }
        if (urlModeButton.isChecked()) {
            startUrlDownload(value);
        } else {
            startSearch(value);
        }
    }

    private void startSearch(String keyword) {
        Set<String> selectedSources = selectedSearchSourceIds();
        if (selectedSources.isEmpty()) {
            Toast.makeText(this, "请选择至少一个可搜索书源", Toast.LENGTH_SHORT).show();
            return;
        }
        setBusy(true, "准备搜索...");
        resultList.removeAllViews();
        executor.execute(() -> {
            try {
                List<OnlineBookResult> results = new OnlineBookClient(this).search(keyword, selectedSources,
                        (message, completed, total) -> runOnUiThread(() -> {
                            progressText.setText(message + "  " + completed + "/" + total);
                        }));
                List<ResultGroup> groups = mergeResults(keyword, results);
                runOnUiThread(() -> {
                    setBusy(false, "");
                    renderResults(groups);
                    scrollToResults();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false, "");
                    Toast.makeText(this, "搜索失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private Set<String> selectedSearchSourceIds() {
        Set<String> selected = new HashSet<>();
        for (OnlineBookSource source : allSources) {
            CheckBox check = sourceChecks.get(source.id);
            if (check != null && check.isEnabled() && check.isChecked() && isSearchable(source)) {
                selected.add(source.id);
            }
        }
        return selected;
    }

    private Set<String> selectedEnabledSourceIds() {
        Set<String> selected = new HashSet<>();
        for (Map.Entry<String, CheckBox> entry : sourceChecks.entrySet()) {
            if (entry.getValue().isEnabled() && entry.getValue().isChecked()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    private boolean isSearchable(OnlineBookSource source) {
        return source.enabled
                && !source.search.disabled
                && source.search.result != null
                && !source.search.result.trim().isEmpty();
    }

    private List<ResultGroup> mergeResults(String keyword, List<OnlineBookResult> results) {
        Map<String, ResultGroup> grouped = new LinkedHashMap<>();
        for (OnlineBookResult result : results) {
            String key = normalizeTitle(result.bookName);
            ResultGroup group = grouped.get(key);
            if (group == null) {
                group = new ResultGroup();
                group.title = OnlineBookResult.clean(result.bookName);
                grouped.put(key, group);
            }
            group.versions.add(result);
        }
        List<ResultGroup> groups = new ArrayList<>(grouped.values());
        for (ResultGroup group : groups) {
            group.versions.sort(Comparator
                    .comparingInt((OnlineBookResult result) -> scoreResult(keyword, result))
                    .reversed()
                    .thenComparing(result -> OnlineBookResult.clean(result.sourceName)));
            group.relevanceScore = scoreGroup(keyword, group);
        }
        groups.sort(Comparator
                .comparingInt((ResultGroup group) -> group.relevanceScore)
                .reversed()
                .thenComparing(group -> group.title));
        return groups;
    }

    private String normalizeTitle(String title) {
        return OnlineBookResult.clean(title).replaceAll("\\s+", "").toLowerCase(Locale.US);
    }

    private int scoreGroup(String keyword, ResultGroup group) {
        int best = 0;
        for (OnlineBookResult version : group.versions) {
            best = Math.max(best, scoreResult(keyword, version));
        }
        return best + Math.min(50, group.versions.size() * 5);
    }

    private int scoreResult(String keyword, OnlineBookResult result) {
        String cleanKeyword = OnlineBookResult.clean(keyword).toLowerCase(Locale.US);
        String normalizedKeyword = normalizeTitle(keyword);
        String title = OnlineBookResult.clean(result.bookName).toLowerCase(Locale.US);
        String normalizedTitle = normalizeTitle(result.bookName);
        String author = OnlineBookResult.clean(result.author).toLowerCase(Locale.US);
        String latest = OnlineBookResult.clean(result.latestChapter).toLowerCase(Locale.US);
        int score = 0;
        if (!normalizedKeyword.isEmpty()) {
            if (normalizedTitle.equals(normalizedKeyword)) {
                score += 10000;
            } else if (normalizedTitle.startsWith(normalizedKeyword)) {
                score += 8000;
            } else if (normalizedTitle.contains(normalizedKeyword)) {
                score += 6500;
            } else if (normalizedKeyword.contains(normalizedTitle)) {
                score += 5000;
            }
        }
        if (!cleanKeyword.isEmpty()) {
            if (title.contains(cleanKeyword)) {
                score += 1200;
            }
            if (author.contains(cleanKeyword)) {
                score += 700;
            }
            if (latest.contains(cleanKeyword)) {
                score += 200;
            }
            for (String token : cleanKeyword.split("\\s+")) {
                if (token.length() >= 2 && title.contains(token)) {
                    score += 150;
                }
            }
        }
        return score;
    }

    private void renderResults(List<ResultGroup> groups) {
        resultList.removeAllViews();
        if (groups.isEmpty()) {
            resultList.addView(messageText("没有搜索结果"));
            return;
        }
        for (ResultGroup group : groups) {
            TextView row = resultRow(group.title + "\n" + group.versions.size() + " 个书源版本");
            row.setOnClickListener(v -> showVersionDialog(group));
            resultList.addView(row);
            resultList.addView(divider());
        }
    }

    private void scrollToResults() {
        if (pageScrollView == null || resultSectionLabel == null) {
            return;
        }
        pageScrollView.post(() -> pageScrollView.smoothScrollTo(0, Math.max(0, resultSectionLabel.getTop() - dpToPx(8))));
    }

    private void showVersionDialog(ResultGroup group) {
        Dialog dialog = plainDialog();
        LinearLayout content = dialogContent(0.85f);
        content.addView(dialogTitle(group.title));
        ScrollView versionScroll = new ScrollView(this);
        versionScroll.setVerticalScrollBarEnabled(true);
        LinearLayout versions = new LinearLayout(this);
        versions.setOrientation(LinearLayout.VERTICAL);
        for (OnlineBookResult version : group.versions) {
            TextView row = resultRow(version.sourceName + "\n" + version.displayAuthor() + " · " + version.displayLatest());
            row.setOnClickListener(v -> {
                dialog.dismiss();
                startResultDownload(version);
            });
            versions.addView(row);
            versions.addView(divider());
        }
        versionScroll.addView(versions, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // Only the list shrinks when the dialog reaches its height limit.
        content.addView(versionScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(textButton("关闭", v -> dialog.dismiss()));
        dialog.setContentView(content);
        showDialog(dialog);
    }

    private void startResultDownload(OnlineBookResult result) {
        String format = selectedFormat();
        if (format.isEmpty()) {
            Toast.makeText(this, "请选择下载格式", Toast.LENGTH_SHORT).show();
            return;
        }
        if (OnlineDownloadService.isRunning()) {
            Toast.makeText(this, "已有下载任务正在进行", Toast.LENGTH_SHORT).show();
            return;
        }
        downloadDialog("准备下载《" + result.bookName + "》...");
        startDownloadService(OnlineDownloadService.resultDownloadIntent(this, result, format));
    }

    private void startUrlDownload(String url) {
        String format = selectedFormat();
        if (format.isEmpty()) {
            Toast.makeText(this, "请选择下载格式", Toast.LENGTH_SHORT).show();
            return;
        }
        if (OnlineDownloadService.isRunning()) {
            Toast.makeText(this, "已有下载任务正在进行", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> selectedSources = selectedEnabledSourceIds();
        if (selectedSources.isEmpty()) {
            Toast.makeText(this, "请选择至少一个可用书源", Toast.LENGTH_SHORT).show();
            return;
        }
        downloadDialog("正在识别书源...");
        startDownloadService(OnlineDownloadService.urlDownloadIntent(this, url, format, selectedSources));
    }

    private void startDownloadService(Intent intent) {
        requestNotificationPermissionIfNeeded();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3001);
        }
    }

    private String selectedFormat() {
        if (formatGroup.getCheckedRadioButtonId() == View.NO_ID) {
            return "";
        }
        return txtFormatButton.isChecked() ? OnlineBookClient.FORMAT_TXT : OnlineBookClient.FORMAT_EPUB;
    }

    private Dialog downloadDialog(String message) {
        Dialog dialog = plainDialog();
        dialog.setCancelable(false);
        LinearLayout content = dialogContent();
        content.addView(dialogTitle("正在下载"));
        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        content.addView(progressBar);
        TextView progressText = messageText(message);
        progressText.setId(View.generateViewId());
        content.addView(progressText);
        TextView readButton = textButton("立即阅读（后续章节继续下载）", v -> {
            long bookId = OnlineDownloadService.readableBookId();
            if (bookId > 0) {
                dialog.dismiss();
                startActivity(ReaderActivity.createIntent(this, bookId));
            }
        });
        readButton.setVisibility(View.GONE);
        content.addView(readButton);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(textButton("后台下载", v -> {
            dialog.dismiss();
            Toast.makeText(this, "已转入后台下载，可从通知栏取消", Toast.LENGTH_SHORT).show();
        }));
        actions.addView(textButton("取消下载", v -> {
            progressText.setText("正在取消下载...");
            startService(OnlineDownloadService.cancelIntent(this));
        }));
        content.addView(actions);
        dialog.setContentView(content);
        dialog.setOnDismissListener(d -> {
            if (currentDownloadDialog == dialog) {
                currentDownloadDialog = null;
                currentDownloadProgress = null;
                currentDownloadReadButton = null;
            }
        });
        showDialog(dialog);
        currentDownloadDialog = dialog;
        currentDownloadProgress = progressText;
        currentDownloadReadButton = readButton;
        return dialog;
    }

    private String retainedChaptersMessage() {
        return OnlineDownloadService.readableBookId() > 0 ? "，已下载章节保留在书架，可选择原书源继续下载" : "";
    }

    private void showDownloadReadable(long bookId) {
        if (currentDownloadReadButton != null) currentDownloadReadButton.setVisibility(View.VISIBLE);
        if (currentDownloadDialog == null) updateDownloadProgress(OnlineDownloadService.currentMessage());
    }

    private void finishDownload(Dialog dialog, long bookId) {
        dialog.dismiss();
        Dialog done = plainDialog();
        LinearLayout content = dialogContent();
        content.addView(dialogTitle("已加入书架"));
        content.addView(messageText("下载完成，可以离线阅读。"));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(textButton("留在这里", v -> done.dismiss()));
        actions.addView(textButton("打开阅读", v -> {
            done.dismiss();
            startActivity(ReaderActivity.createIntent(this, bookId));
        }));
        content.addView(actions);
        done.setContentView(content);
        showDialog(done);
    }

    private void updateDownloadProgress(String message) {
        if (currentDownloadProgress != null) {
            currentDownloadProgress.setText(message);
        } else if (OnlineDownloadService.isRunning()) {
            progressText.setVisibility(View.VISIBLE);
            long bookId = OnlineDownloadService.readableBookId();
            progressText.setText("后台下载：" + message + (bookId > 0 ? "\n已可阅读，点击这里打开" : ""));
            progressText.setOnClickListener(bookId > 0
                    ? v -> startActivity(ReaderActivity.createIntent(this, bookId)) : null);
        }
    }

    private void showDownloadFinished(long bookId) {
        progressText.setVisibility(View.GONE);
        Dialog dialog = currentDownloadDialog;
        currentDownloadDialog = null;
        currentDownloadProgress = null;
        currentDownloadReadButton = null;
        if (dialog != null) {
            finishDownload(dialog, bookId);
        } else {
            Toast.makeText(this, "后台下载完成，已加入书架", Toast.LENGTH_LONG).show();
        }
    }

    private void showDownloadStopped(String message) {
        progressText.setVisibility(View.GONE);
        Dialog dialog = currentDownloadDialog;
        currentDownloadDialog = null;
        currentDownloadProgress = null;
        currentDownloadReadButton = null;
        if (dialog != null) {
            dialog.dismiss();
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setBusy(boolean busy, String message) {
        progressText.setOnClickListener(null);
        searchButton.setEnabled(!busy);
        searchButton.setAlpha(busy ? 0.55f : 1f);
        searchProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        progressText.setVisibility(busy ? View.VISIBLE : View.GONE);
        progressText.setText(message);
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_TEXT);
        label.setTextSize(17f);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(dpToPx(2), dpToPx(8), 0, dpToPx(7));
        return label;
    }

    private RadioButton radioButton(String text, boolean checked) {
        RadioButton button = new RadioButton(this);
        button.setId(View.generateViewId());
        button.setText(text);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(15f);
        button.setChecked(checked);
        button.setButtonTintList(controlTint());
        button.setPadding(0, 0, dpToPx(16), 0);
        return button;
    }

    private void makeRadioGroupClearable(RadioGroup group, boolean searchMode) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setOnClickListener(v -> {
                int currentCheckedId = searchMode ? checkedSearchModeId : checkedFormatId;
                if (currentCheckedId == v.getId()) {
                    group.clearCheck();
                    if (searchMode) {
                        checkedSearchModeId = View.NO_ID;
                    } else {
                        checkedFormatId = View.NO_ID;
                    }
                } else {
                    group.check(v.getId());
                    if (searchMode) {
                        checkedSearchModeId = v.getId();
                    } else {
                        checkedFormatId = v.getId();
                    }
                }
            });
        }
    }

    private TextView textButton(String text) {
        return textButton(text, null);
    }

    private TextView textButton(String text, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(COLOR_ACCENT);
        button.setTextSize(16f);
        button.setTypeface(null, Typeface.BOLD);
        button.setGravity(android.view.Gravity.CENTER);
        button.setMinHeight(dpToPx(44));
        button.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        if (listener != null) {
            button.setOnClickListener(listener);
        }
        return button;
    }

    private TextView messageText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(15f);
        view.setPadding(0, dpToPx(8), 0, dpToPx(8));
        return view;
    }

    private TextView resultRow(String text) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(COLOR_TEXT);
        row.setTextSize(16f);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinHeight(dpToPx(68));
        row.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));
        row.setBackground(roundedBackground(COLOR_SURFACE, 12, 0x12000000, 1));
        return row;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.TRANSPARENT);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(8)
        ));
        return divider;
    }

    private Dialog plainDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    private LinearLayout dialogContent() {
        return dialogContent(1f);
    }

    private LinearLayout dialogContent(float maxHeightFraction) {
        LinearLayout content = new LinearLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (maxHeightFraction >= 1f) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    return;
                }
                int maxHeight = Math.round(getResources().getDisplayMetrics().heightPixels * maxHeightFraction);
                if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    maxHeight = Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec));
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackground(roundedBackground(COLOR_SURFACE_STRONG, 16, 0, 0));
        int padding = dpToPx(20);
        content.setPadding(padding, padding, padding, padding);
        return content;
    }

    private TextView dialogTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(10));
        return title;
    }

    private LinearLayout surfacePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(14));
        panel.setBackground(roundedBackground(COLOR_SURFACE_STRONG, 18, COLOR_LINE, 1));
        return panel;
    }

    private LinearLayout.LayoutParams sectionParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private LinearLayout.LayoutParams actionButtonParams(boolean hasRightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(44), 1f);
        if (hasRightMargin) {
            params.setMargins(0, 0, dpToPx(8), 0);
        }
        return params;
    }

    private void styleActionButton(Button button, boolean primary) {
        button.setAllCaps(false);
        button.setTextSize(15f);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : COLOR_ACCENT_DARK);
        button.setBackground(roundedBackground(primary ? COLOR_ACCENT : COLOR_ACCENT_SOFT, 12, primary ? 0 : 0x22B95A52, 1));
        button.setMinHeight(dpToPx(44));
        button.setPadding(dpToPx(6), 0, dpToPx(6), 0);
    }

    private ColorStateList controlTint() {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_enabled},
                new int[]{}
        };
        int[] colors = new int[]{COLOR_ACCENT, 0xFFCBC4BC, 0xFFCBC4BC};
        return new ColorStateList(states, colors);
    }

    private GradientDrawable roundedBackground(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dpToPx(radiusDp));
        if (strokeDp > 0) {
            drawable.setStroke(dpToPx(strokeDp), strokeColor);
        }
        return drawable;
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

    private void showDialog(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static class ResultGroup {
        String title;
        int relevanceScore;
        final List<OnlineBookResult> versions = new ArrayList<>();
    }
}
