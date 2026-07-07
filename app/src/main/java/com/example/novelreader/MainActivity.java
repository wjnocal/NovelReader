package com.example.novelreader;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.novelreader.data.AppDatabase;
import com.example.novelreader.data.BookEntity;
import com.example.novelreader.data.BookmarkEntity;
import com.example.novelreader.data.FolderMetaEntity;
import com.example.novelreader.data.ImportRecordEntity;
import com.example.novelreader.data.NoteEntity;
import com.example.novelreader.parser.BookImporter;
import com.example.novelreader.ui.BooksAdapter;
import com.example.novelreader.ui.ReaderActivity;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "reader_stats";
    private static final String KEY_TOTAL_READING_MILLIS = "totalReadingMillis";
    private static final String KEY_SORT_MODE = "bookshelfSortMode";
    private static final String KEY_DISPLAY_MODE = "bookshelfDisplayMode";
    private static final String KEY_BOOKSHELF_BG_URI = "bookshelfBackgroundUri";
    private static final String KEY_BOOKSHELF_BG_ALPHA = "bookshelfBackgroundAlpha";
    private static final String KEY_DAILY_GOAL_MINUTES = "dailyGoalMinutes";
    private static final int SORT_RECENT = 0;
    private static final int SORT_NAME = 1;
    private static final int TAB_BOOKSHELF = 0;
    private static final int TAB_HOME = 1;
    private static final String CATEGORY_ALL = "全部";
    private static final String CATEGORY_READING = "在读";
    private static final String CATEGORY_FINISHED = "已读";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<BookEntity> currentBooks = new ArrayList<>();
    private final List<FolderMetaEntity> currentFolderMetas = new ArrayList<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private AppDatabase database;
    private BooksAdapter adapter;
    private View bookshelfPage;
    private View homePage;
    private View bookshelfTopBar;
    private TextView groupBackButton;
    private TextView groupTitleText;
    private TextView readingTimeText;
    private TextView readingStreakBadge;
    private TextView homeReadingTimeText;
    private TextView homeBookCountText;
    private TextView homeReadingCountText;
    private TextView homeFinishedCountText;
    private TextView homeTitleText;
    private TextView homeAvatarButton;
    private TextView bookshelfTabButton;
    private TextView homeTabButton;
    private TextView emptyView;
    private View selectionBar;
    private TextView selectedCountText;
    private ImageView bookshelfBackground;
    private ExtendedFloatingActionButton importButton;
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<String[]> backgroundLauncher;
    private ActivityResultLauncher<String[]> avatarLauncher;
    private ActivityResultLauncher<Intent> avatarCropLauncher;
    private ActivityResultLauncher<String[]> rebindLauncher;
    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;
    private RecyclerView recyclerView;
    private View moreMenuPanel;
    private TextView menuListModeButton;
    private SharedPreferences prefs;
    private PopupWindow bookActionPopup;
    private View bookActionMenuView;
    private int sortMode = SORT_RECENT;
    private int displayMode = BooksAdapter.DISPLAY_GRID;
    private int mainTab = TAB_BOOKSHELF;
    private String categoryFilter = CATEGORY_ALL;
    private String pendingBackupJson;
    private long pendingRebindBookId = -1L;
    private boolean moreMenuVisible;

    private interface CategoryChoiceCallback {
        void onCategorySelected(String category);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        hideSystemBars();
        database = AppDatabase.getInstance(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        sortMode = prefs.getInt(KEY_SORT_MODE, SORT_RECENT);
        displayMode = prefs.getInt(KEY_DISPLAY_MODE, BooksAdapter.DISPLAY_GRID);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bookshelfPage = findViewById(R.id.bookshelfPage);
        homePage = findViewById(R.id.homePage);
        bookshelfTopBar = findViewById(R.id.bookshelfTopBar);
        groupBackButton = findViewById(R.id.groupBackButton);
        groupTitleText = findViewById(R.id.groupTitleText);
        readingTimeText = findViewById(R.id.readingTimeText);
        readingStreakBadge = findViewById(R.id.readingStreakBadge);
        homeReadingTimeText = findViewById(R.id.homeReadingTimeText);
        homeBookCountText = findViewById(R.id.homeBookCountText);
        homeReadingCountText = findViewById(R.id.homeReadingCountText);
        homeFinishedCountText = findViewById(R.id.homeFinishedCountText);
        homeTitleText = findViewById(R.id.homeTitleText);
        homeAvatarButton = findViewById(R.id.homeAvatarButton);
        bookshelfTabButton = findViewById(R.id.bookshelfTabButton);
        homeTabButton = findViewById(R.id.homeTabButton);
        emptyView = findViewById(R.id.emptyView);
        selectionBar = findViewById(R.id.selectionBar);
        selectedCountText = findViewById(R.id.selectedCountText);
        TextView cancelSelectionButton = findViewById(R.id.cancelSelectionButton);
        TextView groupSelectedButton = findViewById(R.id.groupSelectedButton);
        TextView markReadSelectedButton = findViewById(R.id.markReadSelectedButton);
        TextView markUnreadSelectedButton = findViewById(R.id.markUnreadSelectedButton);
        TextView deleteSelectedButton = findViewById(R.id.deleteSelectedButton);
        TextView historyButton = findViewById(R.id.historyButton);
        TextView searchButton = findViewById(R.id.searchButton);
        TextView moreButton = findViewById(R.id.moreButton);
        TextView homeStatsButton = findViewById(R.id.homeStatsButton);
        TextView homeGoalButton = findViewById(R.id.homeGoalButton);
        TextView homeHistoryButton = findViewById(R.id.homeHistoryButton);
        TextView homeImportButton = findViewById(R.id.homeImportButton);
        TextView homeBackupButton = findViewById(R.id.homeBackupButton);
        TextView homeRestoreButton = findViewById(R.id.homeRestoreButton);
        moreMenuPanel = findViewById(R.id.moreMenuPanel);
        TextView menuImportButton = findViewById(R.id.menuImportButton);
        TextView menuImportRecordsButton = findViewById(R.id.menuImportRecordsButton);
        TextView menuSortButton = findViewById(R.id.menuSortButton);
        menuListModeButton = findViewById(R.id.menuListModeButton);
        TextView menuCategoryButton = findViewById(R.id.menuCategoryButton);
        TextView menuStatsButton = findViewById(R.id.menuStatsButton);
        TextView menuGoalButton = findViewById(R.id.menuGoalButton);
        TextView menuBackgroundButton = findViewById(R.id.menuBackgroundButton);
        TextView menuBackupButton = findViewById(R.id.menuBackupButton);
        TextView menuRestoreButton = findViewById(R.id.menuRestoreButton);
        bookshelfBackground = findViewById(R.id.bookshelfBackground);
        recyclerView = findViewById(R.id.bookRecyclerView);
        applyLayoutManager();
        adapter = new BooksAdapter(new BooksAdapter.Listener() {
            @Override
            public void onBookClick(BookEntity book) {
                if (adapter.isSelectionMode()) {
                    toggleBookSelection(book);
                } else {
                    startActivity(ReaderActivity.createIntent(MainActivity.this, book.id));
                }
            }

            @Override
            public void onBookLongClick(BookEntity book, View anchor) {
                if (adapter.isSelectionMode()) {
                    toggleBookSelection(book);
                } else {
                    showBookActionMenu(book, anchor);
                }
            }

            @Override
            public void onFolderClick(String folderName) {
                categoryFilter = folderName;
                displayMode = BooksAdapter.DISPLAY_LIST;
                prefs.edit().putInt(KEY_DISPLAY_MODE, displayMode).apply();
                applyLayoutManager();
                adapter.setDisplayMode(displayMode);
                submitSortedBooks();
                updateGroupHeader();
                Toast.makeText(MainActivity.this, "已进入分组：" + folderName, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFolderLongClick(String folderName, View anchor) {
                showFolderActionMenu(folderName, anchor);
            }

            @Override
            public void onAddToFolderClick(String folderName) {
                showAddBookToFolderDialog(folderName);
            }
        });
        adapter.setDisplayMode(displayMode);
        recyclerView.setAdapter(adapter);

        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::handleImportUris);
        backgroundLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleBackgroundUri);
        avatarLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleAvatarUri);
        avatarCropLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                handleCroppedAvatarResult(result.getData());
            }
        });
        rebindLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handleRebindUri);
        backupLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::writeBackupToUri);
        restoreLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::restoreFromUri);
        importButton = findViewById(R.id.importButton);
        View.OnClickListener importClickListener = v -> {
            hideMoreMenu();
            importLauncher.launch(new String[]{"text/plain", "application/epub+zip", "application/octet-stream"});
        };
        importButton.setOnClickListener(importClickListener);
        menuImportButton.setOnClickListener(importClickListener);
        homeImportButton.setOnClickListener(importClickListener);
        menuImportRecordsButton.setOnClickListener(v -> showImportRecordsDialog());
        cancelSelectionButton.setOnClickListener(v -> exitSelectionMode());
        groupSelectedButton.setOnClickListener(v -> showGroupSelectedDialog());
        markReadSelectedButton.setOnClickListener(v -> markSelectedBooksFinished(true));
        markUnreadSelectedButton.setOnClickListener(v -> markSelectedBooksFinished(false));
        deleteSelectedButton.setOnClickListener(v -> confirmDeleteSelectedBooks());
        historyButton.setOnClickListener(v -> showHistoryDialog());
        searchButton.setOnClickListener(v -> showSearchDialog());
        moreButton.setOnClickListener(v -> toggleMoreMenu());
        groupBackButton.setOnClickListener(v -> exitGroupView());
        menuSortButton.setOnClickListener(v -> showSortDialog());
        menuListModeButton.setOnClickListener(v -> toggleDisplayMode());
        menuCategoryButton.setOnClickListener(v -> showCategoryDialog());
        menuStatsButton.setOnClickListener(v -> showStatsDialog());
        menuGoalButton.setOnClickListener(v -> showReadingGoalDialog());
        homeStatsButton.setOnClickListener(v -> showStatsDialog());
        homeGoalButton.setOnClickListener(v -> showReadingGoalDialog());
        homeHistoryButton.setOnClickListener(v -> showHistoryDialog());
        homeAvatarButton.setOnClickListener(v -> showUserProfilePanel());
        bookshelfTabButton.setOnClickListener(v -> switchMainTab(TAB_BOOKSHELF));
        homeTabButton.setOnClickListener(v -> switchMainTab(TAB_HOME));
        menuBackgroundButton.setOnClickListener(v -> showBookshelfBackgroundDialog());
        menuBackupButton.setOnClickListener(v -> startBackup());
        homeBackupButton.setOnClickListener(v -> startBackup());
        View.OnClickListener restoreClickListener = v -> {
            hideMoreMenu();
            restoreLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"});
        };
        menuRestoreButton.setOnClickListener(restoreClickListener);
        homeRestoreButton.setOnClickListener(restoreClickListener);
        updateDisplayModeText();
        updateReadingTimeText();
        applyBookshelfBackground();
        updateHomeUserName();
        updateHomeAvatar();
        updateGroupHeader();
        switchMainTab(TAB_BOOKSHELF);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        updateReadingTimeText();
        updateHomeUserName();
        updateHomeAvatar();
        loadBooks();
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

    @Override
    public void onBackPressed() {
        if (mainTab == TAB_HOME) {
            switchMainTab(TAB_BOOKSHELF);
            return;
        }
        if (bookActionPopup != null && bookActionPopup.isShowing()) {
            hideBookActionMenu();
            return;
        }
        if (adapter != null && adapter.isSelectionMode()) {
            exitSelectionMode();
            return;
        }
        if (moreMenuVisible) {
            hideMoreMenu();
            return;
        }
        if (!CATEGORY_ALL.equals(categoryFilter)) {
            exitGroupView();
            return;
        }
        super.onBackPressed();
    }

    private void handleImportUri(Uri uri) {
        if (uri == null) {
            return;
        }
        persistUriPermission(uri);
        String possibleTitle = stripExtension(getDisplayName(uri));
        executor.execute(() -> {
            BookEntity duplicate = database.bookDao().getByTitle(possibleTitle);
            runOnUiThread(() -> {
                if (duplicate != null) {
                    showDuplicateImportDialog(uri, possibleTitle);
                } else {
                    importBook(uri, false);
                }
            });
        });
    }

    private void handleImportUris(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        if (uris.size() == 1) {
            handleImportUri(uris.get(0));
            return;
        }
        Toast.makeText(this, "开始批量导入 " + uris.size() + " 个文件", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            BookImporter importer = new BookImporter(this);
            int total = uris.size();
            for (int i = 0; i < total; i++) {
                Uri uri = uris.get(i);
                persistUriPermission(uri);
                String displayName = getDisplayName(uri);
                int current = i + 1;
                runOnUiThread(() -> Toast.makeText(this, "正在导入 " + current + "/" + total + "：" + displayName, Toast.LENGTH_SHORT).show());
                importBookWithRecord(importer, uri, displayName, false, false);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "批量导入完成", Toast.LENGTH_SHORT).show();
                loadBooks();
            });
        });
    }

    private void showDuplicateImportDialog(Uri uri, String title) {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("可能重复导入"));
        content.addView(createDialogMessage("书架里已经有《" + title + "》。是否继续导入？"));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(createDialogOption("取消", v -> dialog.dismiss()));
        actions.addView(createDialogOption("继续导入", v -> {
            dialog.dismiss();
            importBook(uri, true);
        }));
        content.addView(actions);
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void importBook(Uri uri) {
        importBook(uri, false);
    }

    private void importBook(Uri uri, boolean allowDuplicate) {
        persistUriPermission(uri);
        Toast.makeText(this, "正在导入...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            boolean success = importBookWithRecord(new BookImporter(this), uri, getDisplayName(uri), true, allowDuplicate);
            if (success) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
                    loadBooks();
                });
            }
        });
    }

    private boolean importBookWithRecord(BookImporter importer, Uri uri, String displayName, boolean notifyFailure, boolean allowDuplicate) {
        ImportRecordEntity record = new ImportRecordEntity();
        long now = System.currentTimeMillis();
        record.uri = uri.toString();
        record.displayName = displayName;
        record.status = "导入中";
        record.errorMessage = "";
        record.bookId = 0L;
        record.createdAt = now;
        record.updatedAt = now;
        record.id = database.importRecordDao().insert(record);
        try {
            String possibleTitle = stripExtension(displayName);
            if (!allowDuplicate && database.bookDao().getByTitle(possibleTitle) != null) {
                throw new IllegalStateException("书架里已存在同名书籍");
            }
            long bookId = importer.importBook(uri);
            record.status = "成功";
            record.errorMessage = "";
            record.bookId = bookId;
            record.updatedAt = System.currentTimeMillis();
            database.importRecordDao().update(record);
            return true;
        } catch (Exception e) {
            record.status = "失败";
            record.errorMessage = e.getMessage() == null ? "未知错误" : e.getMessage();
            record.updatedAt = System.currentTimeMillis();
            database.importRecordDao().update(record);
            if (notifyFailure) {
                runOnUiThread(() -> Toast.makeText(this, "导入失败：" + record.errorMessage, Toast.LENGTH_LONG).show());
            }
            return false;
        }
    }

    private void showImportRecordsDialog() {
        hideMoreMenu();
        executor.execute(() -> {
            List<ImportRecordEntity> records = database.importRecordDao().getAll();
            runOnUiThread(() -> {
                Dialog dialog = createPlainDialog();
                LinearLayout content = createDialogContent();
                content.addView(createDialogTitle("导入记录"));
                if (records.isEmpty()) {
                    content.addView(createDialogMessage("还没有导入记录"));
                }
                int count = Math.min(records.size(), 30);
                for (int i = 0; i < count; i++) {
                    ImportRecordEntity record = records.get(i);
                    StringBuilder rowText = new StringBuilder();
                    rowText.append(record.displayName == null ? "未知文件" : record.displayName)
                            .append("\n")
                            .append(record.status == null ? "未知状态" : record.status)
                            .append(" · ")
                            .append(formatDateTime(record.updatedAt));
                    if ("失败".equals(record.status) && record.errorMessage != null && !record.errorMessage.trim().isEmpty()) {
                        rowText.append("\n").append(record.errorMessage).append(" · 点击重试");
                    }
                    TextView row = createDialogOption(rowText.toString(), v -> {
                        if ("失败".equals(record.status)) {
                            dialog.dismiss();
                            retryImportRecord(record.id);
                        }
                    });
                    row.setMinHeight(dpToPx("失败".equals(record.status) ? 76 : 58));
                    content.addView(row);
                    if (i < count - 1) {
                        content.addView(createDivider());
                    }
                }
                dialog.setContentView(content);
                showPlainDialog(dialog);
            });
        });
    }

    private void retryImportRecord(long recordId) {
        executor.execute(() -> {
            ImportRecordEntity record = database.importRecordDao().getById(recordId);
            if (record == null || record.uri == null || record.uri.trim().isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "导入记录不可重试", Toast.LENGTH_SHORT).show());
                return;
            }
            Uri uri = Uri.parse(record.uri);
            persistUriPermission(uri);
            record.status = "导入中";
            record.errorMessage = "";
            record.updatedAt = System.currentTimeMillis();
            database.importRecordDao().update(record);
            try {
                String displayName = record.displayName == null || record.displayName.trim().isEmpty() ? getDisplayName(uri) : record.displayName;
                String possibleTitle = stripExtension(displayName);
                if (database.bookDao().getByTitle(possibleTitle) != null) {
                    throw new IllegalStateException("书架里已存在同名书籍");
                }
                long bookId = new BookImporter(this).importBook(uri);
                record.status = "成功";
                record.errorMessage = "";
                record.bookId = bookId;
                record.updatedAt = System.currentTimeMillis();
                database.importRecordDao().update(record);
                runOnUiThread(() -> {
                    Toast.makeText(this, "重试导入成功", Toast.LENGTH_SHORT).show();
                    loadBooks();
                });
            } catch (Exception e) {
                record.status = "失败";
                record.errorMessage = e.getMessage() == null ? "未知错误" : e.getMessage();
                record.updatedAt = System.currentTimeMillis();
                database.importRecordDao().update(record);
                runOnUiThread(() -> Toast.makeText(this, "重试失败：" + record.errorMessage, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void startRebindBook(BookEntity book) {
        pendingRebindBookId = book.id;
        rebindLauncher.launch(new String[]{"text/plain", "application/epub+zip", "application/octet-stream"});
    }

    private void handleRebindUri(Uri uri) {
        if (uri == null || pendingRebindBookId <= 0) {
            pendingRebindBookId = -1L;
            return;
        }
        long bookId = pendingRebindBookId;
        pendingRebindBookId = -1L;
        persistUriPermission(uri);
        Toast.makeText(this, "正在重新绑定...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                new BookImporter(this).rebindBook(bookId, uri);
                runOnUiThread(() -> {
                    Toast.makeText(this, "已重新绑定文件", Toast.LENGTH_SHORT).show();
                    loadBooks();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "重新绑定失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void handleAvatarUri(Uri uri) {
        if (uri == null) {
            return;
        }
        persistUriPermission(uri);
        avatarCropLauncher.launch(AvatarCropActivity.createIntent(this, uri));
    }

    private void handleCroppedAvatarResult(Intent data) {
        Uri uri = data.getParcelableExtra(AvatarCropActivity.EXTRA_CROPPED_URI);
        if (uri == null) {
            return;
        }
        UserProfile.saveAvatarUri(this, uri);
        updateHomeAvatar();
        Toast.makeText(this, "头像已更新", Toast.LENGTH_SHORT).show();
    }

    private void loadBooks() {
        executor.execute(() -> {
            List<BookEntity> books = database.bookDao().getAll();
            List<FolderMetaEntity> folderMetas = database.folderMetaDao().getAll();
            runOnUiThread(() -> {
                currentBooks.clear();
                currentBooks.addAll(books);
                currentFolderMetas.clear();
                currentFolderMetas.addAll(folderMetas);
                submitSortedBooks();
                updateGroupHeader();
                updateHomePageStats();
                if (adapter.isSelectionMode()) {
                    updateSelectionUi();
                }
            });
        });
    }

    private void switchMainTab(int tab) {
        mainTab = tab;
        hideMoreMenu();
        hideBookActionMenu();
        if (tab == TAB_HOME && adapter != null && adapter.isSelectionMode()) {
            exitSelectionMode();
        }
        boolean showBookshelf = tab == TAB_BOOKSHELF;
        if (bookshelfPage != null) {
            bookshelfPage.setVisibility(showBookshelf ? View.VISIBLE : View.GONE);
        }
        if (homePage != null) {
            homePage.setVisibility(showBookshelf ? View.GONE : View.VISIBLE);
            if (!showBookshelf) {
                homePage.setAlpha(0f);
                homePage.setTranslationY(dpToPx(12));
                homePage.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(180)
                        .setInterpolator(new DecelerateInterpolator(1.3f))
                        .start();
            }
        }
        updateMainTabStyle();
        updateHomePageStats();
    }

    private void updateMainTabStyle() {
        updateTabButtonStyle(bookshelfTabButton, mainTab == TAB_BOOKSHELF);
        updateTabButtonStyle(homeTabButton, mainTab == TAB_HOME);
    }

    private void updateTabButtonStyle(TextView tabButton, boolean selected) {
        if (tabButton == null) {
            return;
        }
        tabButton.setTextColor(selected ? Color.rgb(185, 90, 82) : Color.rgb(17, 17, 17));
        tabButton.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void updateHomePageStats() {
        if (homeReadingTimeText != null && readingTimeText != null) {
            homeReadingTimeText.setText(readingTimeText.getText());
        }
        if (homeBookCountText == null || homeReadingCountText == null || homeFinishedCountText == null) {
            return;
        }
        int finishedCount = 0;
        for (BookEntity book : currentBooks) {
            if (book.finishedAt > 0L) {
                finishedCount++;
            }
        }
        int totalCount = currentBooks.size();
        int readingCount = Math.max(0, totalCount - finishedCount);
        homeBookCountText.setText(totalCount + "本");
        homeReadingCountText.setText(readingCount + "本");
        homeFinishedCountText.setText(finishedCount + "本");
    }

    private void updateHomeAvatar() {
        if (homeAvatarButton != null) {
            UserProfile.applyAvatar(homeAvatarButton, this, dpToPx(58));
        }
    }

    private void updateHomeUserName() {
        if (homeTitleText != null) {
            homeTitleText.setText(UserProfile.name(this));
        }
    }

    private void showUserProfilePanel() {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        TextView avatar = new TextView(this);
        UserProfile.applyAvatar(avatar, this, dpToPx(76));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dpToPx(76), dpToPx(76));
        avatarParams.setMargins(0, 0, 0, dpToPx(10));
        content.addView(avatar, avatarParams);

        TextView name = createDialogTitle(UserProfile.name(this));
        name.setGravity(android.view.Gravity.CENTER);
        content.addView(name);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setAlpha(0f);
        content.addView(menu, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        menu.addView(createProfileOption("更改头像", v -> {
            dialog.dismiss();
            avatarLauncher.launch(new String[]{"image/*"});
        }));
        menu.addView(createProfileOption("更改名称", v -> {
            dialog.dismiss();
            showChangeUserNameDialog();
        }));
        menu.addView(createProfileOption("通用设置", v -> {
            dialog.dismiss();
            showGeneralSettingsDialog();
        }));
        menu.addView(createProfileOption("关于软件", v -> {
            dialog.dismiss();
            showAboutSoftwareDialog();
        }));
        menu.addView(createProfileOption("切换账号", v -> Toast.makeText(this, "开发中，敬请期待", Toast.LENGTH_SHORT).show()));
        menu.addView(createProfileOption("退出账号", v -> Toast.makeText(this, "开发中，敬请期待", Toast.LENGTH_SHORT).show()));
        menu.addView(createProfileOption("开源说明", v -> {
            dialog.dismiss();
            showOpenSourceNoticeDialog();
        }));

        dialog.setContentView(content);
        showPlainDialog(dialog);
        avatar.setScaleX(0.45f);
        avatar.setScaleY(0.45f);
        avatar.setTranslationY(-dpToPx(26));
        avatar.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
        menu.animate()
                .alpha(1f)
                .setStartDelay(110)
                .setDuration(180)
                .start();
    }

    private TextView createProfileOption(String text, View.OnClickListener listener) {
        TextView option = createDialogOption(text, listener);
        option.setMinHeight(dpToPx(52));
        option.setPadding(dpToPx(18), 0, dpToPx(18), 0);
        return option;
    }

    private void showChangeUserNameDialog() {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("更改名称"));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(UserProfile.name(this));
        input.setSelectAllOnFocus(true);
        input.setTextColor(0xFF111111);
        input.setHintTextColor(0xFF777777);
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(createDialogOption("取消", v -> dialog.dismiss()));
        actions.addView(createDialogOption("保存", v -> {
            String newName = input.getText().toString().trim();
            if (!isValidUserName(newName)) {
                Toast.makeText(this, "名称需为中文0-10字、英文0-20字母，可混搭", Toast.LENGTH_SHORT).show();
                return;
            }
            UserProfile.saveName(this, newName);
            updateHomeUserName();
            updateHomeAvatar();
            dialog.dismiss();
            Toast.makeText(this, "名称已更新", Toast.LENGTH_SHORT).show();
        }));
        content.addView(actions);
        dialog.setContentView(content);
        showPlainDialog(dialog);
        input.requestFocus();
    }

    private boolean isValidUserName(String name) {
        if (name == null) {
            return false;
        }
        int chineseCount = 0;
        int englishCount = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (isChineseChar(c)) {
                chineseCount++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                englishCount++;
            } else {
                return false;
            }
        }
        return chineseCount <= 10 && englishCount <= 20;
    }

    private boolean isChineseChar(char c) {
        return c >= '\u4E00' && c <= '\u9FFF';
    }

    private void showGeneralSettingsDialog() {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("通用设置"));
        content.addView(createDialogOption("阅读主题跟随系统：" + (UserProfile.followSystemTheme(this) ? "开" : "关"), v -> {
            boolean enabled = !UserProfile.followSystemTheme(this);
            UserProfile.setFollowSystemTheme(this, enabled);
            dialog.dismiss();
            Toast.makeText(this, enabled ? "已开启跟随系统" : "已关闭跟随系统", Toast.LENGTH_SHORT).show();
        }));
        content.addView(createDialogOption("清除缓存", v -> {
            dialog.dismiss();
            showClearCacheDialog();
        }));
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void showClearCacheDialog() {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("清除缓存"));
        content.addView(createDialogOption("清除阅读记录", v -> {
            clearReadingRecords();
            dialog.dismiss();
        }));
        content.addView(createDialogOption("清除导入记录", v -> {
            clearImportRecords();
            dialog.dismiss();
        }));
        content.addView(createDialogOption("清除阅读统计", v -> {
            clearReadingStats();
            dialog.dismiss();
        }));
        content.addView(createDialogOption("清除历史头像图片", v -> {
            UserProfile.clearAvatar(this);
            updateHomeAvatar();
            dialog.dismiss();
            Toast.makeText(this, "已清除头像", Toast.LENGTH_SHORT).show();
        }));
        content.addView(createDialogOption("清除背景图片", v -> {
            prefs.edit().remove(KEY_BOOKSHELF_BG_URI).apply();
            applyBookshelfBackground();
            dialog.dismiss();
            Toast.makeText(this, "已清除背景图片", Toast.LENGTH_SHORT).show();
        }));
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void clearReadingRecords() {
        executor.execute(() -> {
            database.bookDao().clearReadingProgress();
            runOnUiThread(() -> {
                loadBooks();
                Toast.makeText(this, "已清除阅读记录", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void clearImportRecords() {
        executor.execute(() -> {
            database.importRecordDao().deleteAll();
            runOnUiThread(() -> Toast.makeText(this, "已清除导入记录", Toast.LENGTH_SHORT).show());
        });
    }

    private void clearReadingStats() {
        prefs.edit().remove(KEY_TOTAL_READING_MILLIS).apply();
        executor.execute(() -> {
            database.dailyReadingDao().deleteAll();
            runOnUiThread(() -> {
                updateReadingTimeText();
                Toast.makeText(this, "已清除阅读统计", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void showAboutSoftwareDialog() {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        content.addView(createDialogTitle("关于软件"));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_app_icon);
        icon.setAdjustViewBounds(true);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(86), dpToPx(86));
        iconParams.setMargins(0, dpToPx(12), 0, dpToPx(20));
        content.addView(icon, iconParams);

        TextView name = createDialogMessage(getString(R.string.app_name) + " " + getVersionName());
        name.setGravity(android.view.Gravity.CENTER);
        name.setTextSize(19f);
        name.setTextColor(0xFF111111);
        content.addView(name);

        TextView words = createDialogMessage("开发者的话：来点彩蛋找一找，找一找。");
        words.setGravity(android.view.Gravity.CENTER);
        words.setPadding(0, dpToPx(24), 0, dpToPx(12));
        final int[] easterTapCount = {0};
        words.setOnClickListener(v -> {
            easterTapCount[0]++;
            if (easterTapCount[0] >= 5) {
                easterTapCount[0] = 0;
                showDeveloperEasterEggDialog();
            }
        });
        content.addView(words);

        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void showOpenSourceNoticeDialog() {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("开源说明"));
        TextView message = createDialogMessage("NovelReader（小说阅读器）是一款本地小说阅读器应用。本项目源代码已公开在 GitHub，主要用于学习、交流和展示。\n\n"
                + "项目地址：");
        message.setLineSpacing(dpToPx(3), 1.05f);
        content.addView(message);

        String projectUrl = "https://github.com/wjnocal/NovelReader";
        TextView urlView = createDialogMessage(projectUrl);
        urlView.setTextColor(0xFFB64B4B);
        urlView.setTypeface(null, android.graphics.Typeface.BOLD);
        urlView.setOnLongClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("NovelReader GitHub", projectUrl));
                Toast.makeText(this, "已复制项目地址", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        content.addView(urlView);

        TextView license = createDialogMessage("许可说明：\n"
                + "当前项目暂未指定正式开源许可证。未经作者明确许可，请勿将本项目代码用于复制、修改、分发或商业用途。\n\n"
                + "版权声明：\n"
                + "Copyright (c) 2026 wjnocal\n\n"
                + "第三方开源组件：\n"
                + "本应用使用了 AndroidX AppCompat、Activity KTX、CoordinatorLayout、ConstraintLayout、DrawerLayout、RecyclerView、Material Components、Room、jsoup、JUnit、AndroidX Test 和 Espresso 等第三方开源组件。相关组件的版权归其原作者所有，并遵循对应的开源许可证。");
        license.setLineSpacing(dpToPx(3), 1.05f);
        content.addView(license);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(createDialogOption("知道了", v -> dialog.dismiss()));
        content.addView(actions);
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void showDeveloperEasterEggDialog() {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("彩蛋"));
        TextView message = createDialogMessage("你居然真的点进来了。\n\n"
                + "好吧其实这里什么都没有，你觉得彩蛋应该写什么在QQ跟我说得了。");
        message.setLineSpacing(dpToPx(3), 1.05f);
        content.addView(message);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(createDialogOption("知道了", v -> dialog.dismiss()));
        content.addView(actions);
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "未知版本";
        }
    }

    private void enterSelectionMode(BookEntity book) {
        hideBookActionMenu();
        hideMoreMenu();
        adapter.setSelectionMode(true);
        adapter.setBookSelected(book, true);
        updateSelectionUi();
    }

    private void toggleBookSelection(BookEntity book) {
        adapter.toggleBookSelected(book);
        if (adapter.getSelectedCount() == 0) {
            exitSelectionMode();
        } else {
            updateSelectionUi();
        }
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        boolean selectionMode = adapter.isSelectionMode();
        bookshelfTopBar.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        selectionBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        importButton.setVisibility(View.GONE);
        if (selectionMode) {
            selectedCountText.setText("已选择 " + adapter.getSelectedCount() + " 本");
        }
    }

    private void submitSortedBooks() {
        List<BookEntity> sortedBooks = new ArrayList<>();
        for (BookEntity book : currentBooks) {
            if (matchesCategoryFilter(book)) {
                sortedBooks.add(book);
            }
        }
        if (sortMode == SORT_NAME) {
            Collections.sort(sortedBooks, (left, right) -> {
                int pinned = Boolean.compare(right.pinnedAt > 0, left.pinnedAt > 0);
                return pinned != 0 ? pinned : safeText(left.title).compareToIgnoreCase(safeText(right.title));
            });
        } else {
            Collections.sort(sortedBooks, (left, right) -> {
                int pinned = Boolean.compare(right.pinnedAt > 0, left.pinnedAt > 0);
                return pinned != 0 ? pinned : Long.compare(right.updatedAt, left.updatedAt);
            });
        }
        if (CATEGORY_ALL.equals(categoryFilter)) {
            adapter.submitShelfItems(buildShelfItems(sortedBooks));
        } else if (isRealFolderFilter()) {
            List<Object> folderItems = new ArrayList<>();
            folderItems.addAll(sortedBooks);
            folderItems.add(new BooksAdapter.AddBookItem(categoryFilter));
            adapter.submitShelfItems(folderItems);
        } else {
            adapter.submitBooks(sortedBooks);
        }
        emptyView.setVisibility(sortedBooks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean isRealFolderFilter() {
        return !CATEGORY_ALL.equals(categoryFilter)
                && !CATEGORY_READING.equals(categoryFilter)
                && !CATEGORY_FINISHED.equals(categoryFilter)
                && !"未分类".equals(categoryFilter);
    }

    private List<Object> buildShelfItems(List<BookEntity> books) {
        List<Object> items = new ArrayList<>();
        List<String> folderNames = new ArrayList<>();
        for (BookEntity book : books) {
            String category = normalizeCategory(book.category);
            if ("未分类".equals(category)) {
                items.add(book);
            } else if (!folderNames.contains(category)) {
                folderNames.add(category);
            }
        }
        for (String folderName : folderNames) {
            List<BookEntity> folderBooks = new ArrayList<>();
            for (BookEntity book : books) {
                if (folderName.equals(normalizeCategory(book.category))) {
                    folderBooks.add(book);
                }
            }
            FolderMetaEntity meta = findFolderMeta(folderName);
            items.add(new BooksAdapter.FolderItem(
                    folderName,
                    folderBooks,
                    meta == null ? 0L : meta.pinnedAt,
                    folderUpdatedAt(meta, folderBooks)
            ));
        }
        Collections.sort(items, this::compareShelfItems);
        return items;
    }

    private int compareShelfItems(Object left, Object right) {
        int pinned = Boolean.compare(itemPinnedAt(right) > 0, itemPinnedAt(left) > 0);
        if (pinned != 0) {
            return pinned;
        }
        if (sortMode == SORT_NAME) {
            return itemName(left).compareToIgnoreCase(itemName(right));
        }
        return Long.compare(itemUpdatedAt(right), itemUpdatedAt(left));
    }

    private long itemPinnedAt(Object item) {
        if (item instanceof BooksAdapter.FolderItem) {
            return ((BooksAdapter.FolderItem) item).pinnedAt;
        }
        if (item instanceof BookEntity) {
            return ((BookEntity) item).pinnedAt;
        }
        return 0L;
    }

    private long itemUpdatedAt(Object item) {
        if (item instanceof BooksAdapter.FolderItem) {
            return ((BooksAdapter.FolderItem) item).updatedAt;
        }
        if (item instanceof BookEntity) {
            return ((BookEntity) item).updatedAt;
        }
        return 0L;
    }

    private String itemName(Object item) {
        if (item instanceof BooksAdapter.FolderItem) {
            return safeText(((BooksAdapter.FolderItem) item).name);
        }
        if (item instanceof BookEntity) {
            return safeText(((BookEntity) item).title);
        }
        return "";
    }

    private long folderUpdatedAt(FolderMetaEntity meta, List<BookEntity> books) {
        long updatedAt = meta == null ? 0L : meta.updatedAt;
        for (BookEntity book : books) {
            updatedAt = Math.max(updatedAt, book.updatedAt);
        }
        return updatedAt;
    }

    private FolderMetaEntity findFolderMeta(String folderName) {
        String normalized = normalizeCategory(folderName);
        for (FolderMetaEntity meta : currentFolderMetas) {
            if (normalized.equals(meta.name)) {
                return meta;
            }
        }
        return null;
    }

    private boolean matchesCategoryFilter(BookEntity book) {
        if (CATEGORY_ALL.equals(categoryFilter)) {
            return true;
        }
        if (CATEGORY_READING.equals(categoryFilter)) {
            return book.finishedAt == 0L;
        }
        if (CATEGORY_FINISHED.equals(categoryFilter)) {
            return book.finishedAt > 0L;
        }
        return categoryFilter.equals(normalizeCategory(book.category));
    }

    private void applyLayoutManager() {
        if (recyclerView == null) {
            return;
        }
        if (displayMode == BooksAdapter.DISPLAY_LIST) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        } else {
            GridLayoutManager manager = new GridLayoutManager(this, 3);
            manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return adapter != null && adapter.isFolderPosition(position) ? 3 : 1;
                }
            });
            recyclerView.setLayoutManager(manager);
        }
    }

    private void toggleDisplayMode() {
        hideMoreMenu();
        displayMode = displayMode == BooksAdapter.DISPLAY_GRID ? BooksAdapter.DISPLAY_LIST : BooksAdapter.DISPLAY_GRID;
        prefs.edit().putInt(KEY_DISPLAY_MODE, displayMode).apply();
        applyLayoutManager();
        adapter.setDisplayMode(displayMode);
        updateDisplayModeText();
    }

    private void updateDisplayModeText() {
        if (menuListModeButton != null) {
            menuListModeButton.setText(displayMode == BooksAdapter.DISPLAY_GRID ? "列表模式" : "网格模式");
        }
    }

    private void exitGroupView() {
        categoryFilter = CATEGORY_ALL;
        submitSortedBooks();
        updateGroupHeader();
    }

    private void updateGroupHeader() {
        if (groupBackButton == null || groupTitleText == null || readingTimeText == null) {
            return;
        }
        boolean inGroup = !CATEGORY_ALL.equals(categoryFilter);
        groupBackButton.setVisibility(inGroup ? View.VISIBLE : View.GONE);
        groupTitleText.setVisibility(inGroup ? View.VISIBLE : View.GONE);
        readingTimeText.setVisibility(inGroup ? View.GONE : View.VISIBLE);
        if (readingStreakBadge != null) {
            readingStreakBadge.setVisibility(inGroup ? View.GONE : View.VISIBLE);
        }
        groupTitleText.setText(inGroup ? categoryFilter : "");
    }

    private void toggleMoreMenu() {
        if (moreMenuVisible) {
            hideMoreMenu();
        } else {
            showMoreMenu();
        }
    }

    private void showMoreMenu() {
        moreMenuVisible = true;
        moreMenuPanel.animate().cancel();
        moreMenuPanel.setVisibility(View.VISIBLE);
        moreMenuPanel.setAlpha(0f);
        moreMenuPanel.setScaleX(0.86f);
        moreMenuPanel.setScaleY(0.86f);
        moreMenuPanel.setPivotX(moreMenuPanel.getWidth());
        moreMenuPanel.setPivotY(0f);
        moreMenuPanel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(170)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    private void hideMoreMenu() {
        if (!moreMenuVisible || moreMenuPanel == null) {
            return;
        }
        moreMenuVisible = false;
        moreMenuPanel.animate().cancel();
        moreMenuPanel.animate()
                .alpha(0f)
                .scaleX(0.86f)
                .scaleY(0.86f)
                .setDuration(130)
                .withEndAction(() -> {
                    moreMenuPanel.setVisibility(View.GONE);
                    moreMenuPanel.setScaleX(1f);
                    moreMenuPanel.setScaleY(1f);
                })
                .start();
    }

    private void showBookActionMenu(BookEntity book, View anchor) {
        hideMoreMenu();
        hideBookActionMenu();

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundColor(0xF8FFFFFF);
        menu.setPadding(0, dpToPx(6), 0, dpToPx(6));
        menu.addView(createBookActionOption("详情", v -> {
            hideBookActionMenu();
            showBookDetailDialog(book);
        }));
        if (isBookFileMissing(book)) {
            menu.addView(createBookActionOption("重新绑定", v -> {
                hideBookActionMenu();
                startRebindBook(book);
            }));
        }
        menu.addView(createBookActionOption("修改分类", v -> {
            hideBookActionMenu();
            showChangeCategoryDialog(book);
        }));
        menu.addView(createBookActionOption(book.finishedAt > 0 ? "标为在读" : "标为已读", v -> {
            hideBookActionMenu();
            toggleFinished(book);
        }));
        menu.addView(createBookActionOption("多选", v -> {
            hideBookActionMenu();
            enterSelectionMode(book);
        }));
        menu.addView(createBookActionOption("改名", v -> {
            hideBookActionMenu();
            showRenameBookDialog(book);
        }));
        menu.addView(createBookActionOption(book.pinnedAt > 0 ? "取消置顶" : "置顶", v -> {
            hideBookActionMenu();
            togglePinBook(book);
        }));

        int popupWidth = dpToPx(128);
        bookActionMenuView = menu;
        bookActionPopup = new PopupWindow(menu, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        bookActionPopup.setOutsideTouchable(true);
        bookActionPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        bookActionPopup.setElevation(dpToPx(8));
        bookActionPopup.setOnDismissListener(() -> {
            if (bookActionMenuView == menu) {
                bookActionMenuView = null;
            }
        });

        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        menu.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int popupHeight = menu.getMeasuredHeight();
        int gap = dpToPx(6);
        int x = anchorLocation[0] + anchor.getWidth() + gap;
        if (x + popupWidth > screenWidth - gap) {
            x = Math.max(gap, anchorLocation[0] - popupWidth - gap);
        }
        int y = anchorLocation[1] + anchor.getHeight() / 2 - popupHeight / 2;
        y = Math.max(gap, Math.min(y, screenHeight - popupHeight - gap));

        View rootView = findViewById(R.id.main);
        bookActionPopup.showAtLocation(rootView, android.view.Gravity.NO_GRAVITY, x, y);
        menu.setAlpha(0f);
        menu.setScaleX(0.88f);
        menu.setScaleY(0.88f);
        menu.setPivotX(x > anchorLocation[0] ? 0f : popupWidth);
        menu.setPivotY(popupHeight / 2f);
        menu.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    private void showFolderActionMenu(String folderName, View anchor) {
        hideMoreMenu();
        hideBookActionMenu();

        String normalized = normalizeCategory(folderName);
        FolderMetaEntity meta = findFolderMeta(normalized);
        boolean pinned = meta != null && meta.pinnedAt > 0;

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackgroundColor(0xF8FFFFFF);
        menu.setPadding(0, dpToPx(6), 0, dpToPx(6));
        menu.addView(createBookActionOption("改名", v -> {
            hideBookActionMenu();
            showRenameFolderDialog(normalized);
        }));
        menu.addView(createBookActionOption(pinned ? "取消置顶" : "置顶", v -> {
            hideBookActionMenu();
            togglePinFolder(normalized);
        }));
        menu.addView(createBookActionOption("解散分类", v -> {
            hideBookActionMenu();
            dissolveFolder(normalized);
        }));

        int popupWidth = dpToPx(132);
        bookActionMenuView = menu;
        bookActionPopup = new PopupWindow(menu, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        bookActionPopup.setOutsideTouchable(true);
        bookActionPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        bookActionPopup.setElevation(dpToPx(8));
        bookActionPopup.setOnDismissListener(() -> {
            if (bookActionMenuView == menu) {
                bookActionMenuView = null;
            }
        });

        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        menu.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int popupHeight = menu.getMeasuredHeight();
        int gap = dpToPx(6);
        int x = anchorLocation[0] + anchor.getWidth() + gap;
        if (x + popupWidth > screenWidth - gap) {
            x = Math.max(gap, anchorLocation[0] - popupWidth - gap);
        }
        int y = anchorLocation[1] + anchor.getHeight() / 2 - popupHeight / 2;
        y = Math.max(gap, Math.min(y, screenHeight - popupHeight - gap));

        View rootView = findViewById(R.id.main);
        bookActionPopup.showAtLocation(rootView, android.view.Gravity.NO_GRAVITY, x, y);
        menu.setAlpha(0f);
        menu.setScaleX(0.88f);
        menu.setScaleY(0.88f);
        menu.setPivotX(x > anchorLocation[0] ? 0f : popupWidth);
        menu.setPivotY(popupHeight / 2f);
        menu.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    private TextView createBookActionOption(String text, View.OnClickListener listener) {
        TextView option = new TextView(this);
        option.setText(text);
        option.setGravity(android.view.Gravity.CENTER);
        option.setTextColor(0xFF111111);
        option.setTextSize(16f);
        option.setTypeface(null, android.graphics.Typeface.BOLD);
        option.setMinHeight(dpToPx(46));
        option.setOnClickListener(listener);
        return option;
    }

    private void hideBookActionMenu() {
        if (bookActionPopup == null) {
            return;
        }
        PopupWindow popup = bookActionPopup;
        View menu = bookActionMenuView;
        bookActionPopup = null;
        bookActionMenuView = null;
        if (menu == null || !popup.isShowing()) {
            popup.dismiss();
            return;
        }
        menu.animate().cancel();
        menu.animate()
                .alpha(0f)
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(120)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .withEndAction(popup::dismiss)
                .start();
    }

    private void showRenameBookDialog(BookEntity book) {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("改名"));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(book.title);
        input.setSelectAllOnFocus(true);
        input.setTextColor(0xFF111111);
        input.setHintTextColor(0xFF777777);
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(createDialogOption("取消", v -> dialog.dismiss()));
        actions.addView(createDialogOption("保存", v -> {
            String newTitle = input.getText().toString().trim();
            if (newTitle.isEmpty()) {
                Toast.makeText(this, "书名不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            renameBook(book, newTitle);
        }));
        content.addView(actions);
        dialog.setContentView(content);
        showPlainDialog(dialog);
        input.requestFocus();
    }

    private void renameBook(BookEntity book, String newTitle) {
        if (newTitle.equals(book.title)) {
            return;
        }
        book.title = newTitle;
        executor.execute(() -> {
            database.bookDao().update(book);
            runOnUiThread(() -> {
                Toast.makeText(this, "已改名", Toast.LENGTH_SHORT).show();
                loadBooks();
            });
        });
    }

    private void showRenameFolderDialog(String folderName) {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("分类改名"));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(folderName);
        input.setSelectAllOnFocus(true);
        input.setTextColor(0xFF111111);
        input.setHintTextColor(0xFF777777);
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(createDialogOption("取消", v -> dialog.dismiss()));
        actions.addView(createDialogOption("保存", v -> {
            String newName = normalizeCategory(input.getText().toString());
            if ("未分类".equals(newName)) {
                Toast.makeText(this, "分类名不能为未分类", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            renameFolder(folderName, newName);
        }));
        content.addView(actions);
        dialog.setContentView(content);
        showPlainDialog(dialog);
        input.requestFocus();
    }

    private void renameFolder(String oldName, String newName) {
        String normalizedOld = normalizeCategory(oldName);
        String normalizedNew = normalizeCategory(newName);
        if (normalizedOld.equals(normalizedNew)) {
            return;
        }
        long now = System.currentTimeMillis();
        executor.execute(() -> {
            FolderMetaEntity oldMeta = database.folderMetaDao().getByName(normalizedOld);
            FolderMetaEntity newMeta = database.folderMetaDao().getByName(normalizedNew);
            FolderMetaEntity savedMeta = newMeta == null ? new FolderMetaEntity() : newMeta;
            savedMeta.name = normalizedNew;
            savedMeta.pinnedAt = Math.max(newMeta == null ? 0L : newMeta.pinnedAt, oldMeta == null ? 0L : oldMeta.pinnedAt);
            savedMeta.updatedAt = now;
            database.bookDao().renameCategory(normalizedOld, normalizedNew, now);
            database.folderMetaDao().save(savedMeta);
            database.folderMetaDao().deleteByName(normalizedOld);
            runOnUiThread(() -> {
                Toast.makeText(this, "已改名", Toast.LENGTH_SHORT).show();
                if (normalizedOld.equals(categoryFilter)) {
                    categoryFilter = normalizedNew;
                }
                loadBooks();
                updateGroupHeader();
            });
        });
    }

    private void togglePinBook(BookEntity book) {
        boolean pinned = book.pinnedAt > 0;
        book.pinnedAt = pinned ? 0L : System.currentTimeMillis();
        executor.execute(() -> {
            database.bookDao().update(book);
            runOnUiThread(() -> {
                Toast.makeText(this, pinned ? "已取消置顶" : "已置顶", Toast.LENGTH_SHORT).show();
                loadBooks();
            });
        });
    }

    private void togglePinFolder(String folderName) {
        String normalized = normalizeCategory(folderName);
        executor.execute(() -> {
            FolderMetaEntity meta = database.folderMetaDao().getByName(normalized);
            if (meta == null) {
                meta = new FolderMetaEntity();
                meta.name = normalized;
            }
            boolean pinned = meta.pinnedAt > 0;
            meta.pinnedAt = pinned ? 0L : System.currentTimeMillis();
            meta.updatedAt = System.currentTimeMillis();
            database.folderMetaDao().save(meta);
            boolean nowPinned = !pinned;
            runOnUiThread(() -> {
                Toast.makeText(this, nowPinned ? "已置顶" : "已取消置顶", Toast.LENGTH_SHORT).show();
                loadBooks();
            });
        });
    }

    private void dissolveFolder(String folderName) {
        String normalized = normalizeCategory(folderName);
        long now = System.currentTimeMillis();
        executor.execute(() -> {
            database.bookDao().clearCategory(normalized, now);
            database.folderMetaDao().deleteByName(normalized);
            runOnUiThread(() -> {
                Toast.makeText(this, "已解散分类", Toast.LENGTH_SHORT).show();
                if (normalized.equals(categoryFilter)) {
                    categoryFilter = CATEGORY_ALL;
                }
                loadBooks();
                updateGroupHeader();
            });
        });
    }

    private void toggleFinished(BookEntity book) {
        boolean finished = book.finishedAt > 0;
        book.finishedAt = finished ? 0L : System.currentTimeMillis();
        executor.execute(() -> {
            database.bookDao().update(book);
            runOnUiThread(() -> {
                Toast.makeText(this, finished ? "已标为在读" : "已标为已读", Toast.LENGTH_SHORT).show();
                loadBooks();
            });
        });
    }

    private void showChangeCategoryDialog(BookEntity book) {
        showCategoryPickerDialog("修改分类", normalizeCategory(book.category), selectedCategory -> {
            book.category = selectedCategory;
            long now = System.currentTimeMillis();
            book.updatedAt = now;
            executor.execute(() -> {
                database.bookDao().update(book);
                saveFolderMetaIfNeeded(selectedCategory, now);
                runOnUiThread(() -> {
                    Toast.makeText(this, "已修改分类", Toast.LENGTH_SHORT).show();
                    if (!CATEGORY_ALL.equals(categoryFilter)) {
                        categoryFilter = selectedCategory;
                    }
                    loadBooks();
                    updateGroupHeader();
                });
            });
        });
    }

    private void showCategoryPickerDialog(String title, String selectedCategory, CategoryChoiceCallback callback) {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle(title));
        for (String category : buildAssignableCategoryOptions()) {
            boolean selected = category.equals(normalizeCategory(selectedCategory));
            content.addView(createDialogOption((selected ? "✓ " : "") + category, v -> {
                dialog.dismiss();
                callback.onCategorySelected(category);
            }));
        }
        content.addView(createDialogOption("+ 新建分类", v -> {
            dialog.dismiss();
            showCreateCategoryDialog(callback);
        }));
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void showCreateCategoryDialog(CategoryChoiceCallback callback) {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("新建分类"));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("输入分类名称");
        input.setTextColor(0xFF111111);
        input.setHintTextColor(0xFF777777);
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(createDialogOption("取消", v -> dialog.dismiss()));
        actions.addView(createDialogOption("保存", v -> {
            String category = normalizeCategory(input.getText().toString());
            dialog.dismiss();
            callback.onCategorySelected(category);
        }));
        content.addView(actions);
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void showBookDetailDialog(BookEntity book) {
        executor.execute(() -> {
            int bookmarkCount = database.bookmarkDao().countForBook(book.id);
            int noteCount = database.noteDao().countForBook(book.id);
            runOnUiThread(() -> {
                Dialog dialog = createPlainDialog();
                LinearLayout content = createDialogContent();
                content.addView(createDialogTitle(book.title));
                int chapterNumber = book.totalChapters == 0 ? 0 : Math.min(book.currentChapterIndex + 1, book.totalChapters);
                int progressPercent = book.totalChapters == 0 ? 0 : Math.round(chapterNumber * 100f / book.totalChapters);
                StringBuilder details = new StringBuilder();
                details.append("作者：").append(safeText(book.author).isEmpty() ? "未知" : book.author).append("\n");
                details.append("类型：").append(safeText(book.fileType).toUpperCase(Locale.US)).append("\n");
                details.append("分类：").append(normalizeCategory(book.category)).append("\n");
                details.append("章节：").append(chapterNumber).append("/").append(book.totalChapters).append("\n");
                details.append("进度：").append(progressPercent).append("%").append("\n");
                details.append("状态：").append(book.finishedAt > 0 ? "已读" : "在读").append("\n");
                details.append("预计剩余阅读时间：").append(formatEstimatedRemaining(book)).append("\n");
                details.append("书签：").append(bookmarkCount).append(" 个").append("\n");
                details.append("笔记：").append(noteCount).append(" 条").append("\n");
                details.append("最近阅读：").append(formatDateTime(book.updatedAt)).append("\n");
                details.append("原文件：").append(isBookFileMissing(book) ? "文件缺失" : "存在");
                if (!safeText(book.description).isEmpty()) {
                    details.append("\n简介：").append(book.description);
                }
                content.addView(createDialogMessage(details.toString()));
                content.addView(createDialogOption("继续阅读", v -> {
                    dialog.dismiss();
                    startActivity(ReaderActivity.createIntent(MainActivity.this, book.id));
                }));
                content.addView(createDialogOption("修改分类", v -> {
                    dialog.dismiss();
                    showChangeCategoryDialog(book);
                }));
                if (isBookFileMissing(book)) {
                    content.addView(createDialogOption("重新绑定本地文件", v -> {
                        dialog.dismiss();
                        startRebindBook(book);
                    }));
                }
                content.addView(createDialogOption(book.finishedAt > 0 ? "标为在读" : "标为已读", v -> {
                    dialog.dismiss();
                    toggleFinished(book);
                }));
                dialog.setContentView(content);
                showPlainDialog(dialog);
            });
        });
    }

    private void showAddBookToFolderDialog(String folderName) {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("加入《" + folderName + "》"));
        List<BookEntity> candidates = new ArrayList<>();
        for (BookEntity book : currentBooks) {
            if (!folderName.equals(normalizeCategory(book.category))) {
                candidates.add(book);
            }
        }
        Collections.sort(candidates, (left, right) -> safeText(left.title).compareToIgnoreCase(safeText(right.title)));
        if (candidates.isEmpty()) {
            content.addView(createDialogMessage("没有可加入的小说"));
        } else {
            for (BookEntity book : candidates) {
                TextView option = createDialogOption(book.title + "\n" + normalizeCategory(book.category), v -> {
                    dialog.dismiss();
                    moveBookToFolder(book, folderName, true);
                });
                option.setMinHeight(dpToPx(58));
                content.addView(option);
            }
        }
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void moveBookToFolder(BookEntity book, String folderName, boolean stayInFolder) {
        book.category = normalizeCategory(folderName);
        long now = System.currentTimeMillis();
        book.updatedAt = now;
        executor.execute(() -> {
            database.bookDao().update(book);
            saveFolderMetaIfNeeded(folderName, now);
            runOnUiThread(() -> {
                Toast.makeText(this, "已加入分组：" + folderName, Toast.LENGTH_SHORT).show();
                if (stayInFolder) {
                    categoryFilter = folderName;
                }
                loadBooks();
                updateGroupHeader();
            });
        });
    }

    private void showSortDialog() {
        hideMoreMenu();
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("书架排序"));
        content.addView(createDialogOption("按照最近阅读时间排序", v -> {
            sortMode = SORT_RECENT;
            prefs.edit().putInt(KEY_SORT_MODE, sortMode).apply();
            submitSortedBooks();
            dialog.dismiss();
        }));
        content.addView(createDialogOption("按照名称排序", v -> {
            sortMode = SORT_NAME;
            prefs.edit().putInt(KEY_SORT_MODE, sortMode).apply();
            submitSortedBooks();
            dialog.dismiss();
        }));
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void showCategoryDialog() {
        hideMoreMenu();
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("分类筛选"));
        List<String> categories = buildCategoryOptions();
        for (String category : categories) {
            content.addView(createDialogOption((category.equals(categoryFilter) ? "✓ " : "") + category, v -> {
                categoryFilter = category;
                submitSortedBooks();
                updateGroupHeader();
                dialog.dismiss();
            }));
        }
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private List<String> buildCategoryOptions() {
        List<String> categories = new ArrayList<>();
        categories.add(CATEGORY_ALL);
        categories.add(CATEGORY_READING);
        categories.add(CATEGORY_FINISHED);
        Set<String> custom = new HashSet<>();
        for (BookEntity book : currentBooks) {
            custom.add(normalizeCategory(book.category));
        }
        List<String> sorted = new ArrayList<>(custom);
        Collections.sort(sorted);
        categories.addAll(sorted);
        return categories;
    }

    private List<String> buildAssignableCategoryOptions() {
        List<String> categories = new ArrayList<>();
        categories.add("未分类");
        Set<String> custom = new HashSet<>();
        for (BookEntity book : currentBooks) {
            String category = normalizeCategory(book.category);
            if (!"未分类".equals(category)) {
                custom.add(category);
            }
        }
        List<String> sorted = new ArrayList<>(custom);
        Collections.sort(sorted);
        categories.addAll(sorted);
        return categories;
    }

    private void showStatsDialog() {
        hideMoreMenu();
        startActivity(new Intent(this, ReadingStatsActivity.class));
    }

    private void showBookshelfBackgroundDialog() {
        hideMoreMenu();
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("书架背景"));
        content.addView(createDialogOption("选择背景图片", v -> {
            dialog.dismiss();
            backgroundLauncher.launch(new String[]{"image/*"});
        }));
        content.addView(createDialogOption("透明度 30%", v -> {
            setBookshelfBackgroundAlpha(0.3f);
            dialog.dismiss();
        }));
        content.addView(createDialogOption("透明度 50%", v -> {
            setBookshelfBackgroundAlpha(0.5f);
            dialog.dismiss();
        }));
        content.addView(createDialogOption("透明度 70%", v -> {
            setBookshelfBackgroundAlpha(0.7f);
            dialog.dismiss();
        }));
        content.addView(createDialogOption("透明度 90%", v -> {
            setBookshelfBackgroundAlpha(0.9f);
            dialog.dismiss();
        }));
        content.addView(createDialogOption("恢复默认背景", v -> {
            prefs.edit().remove(KEY_BOOKSHELF_BG_URI).putFloat(KEY_BOOKSHELF_BG_ALPHA, 1f).apply();
            applyBookshelfBackground();
            dialog.dismiss();
        }));
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void handleBackgroundUri(Uri uri) {
        if (uri == null) {
            return;
        }
        persistUriPermission(uri);
        prefs.edit().putString(KEY_BOOKSHELF_BG_URI, uri.toString()).apply();
        applyBookshelfBackground();
    }

    private void persistUriPermission(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private void setBookshelfBackgroundAlpha(float alpha) {
        prefs.edit().putFloat(KEY_BOOKSHELF_BG_ALPHA, alpha).apply();
        applyBookshelfBackground();
    }

    private void applyBookshelfBackground() {
        if (bookshelfBackground == null || prefs == null) {
            return;
        }
        String uriText = prefs.getString(KEY_BOOKSHELF_BG_URI, "");
        if (uriText == null || uriText.trim().isEmpty()) {
            bookshelfBackground.setImageResource(R.drawable.bookshelf_bg);
        } else {
            try {
                bookshelfBackground.setImageURI(Uri.parse(uriText));
            } catch (Exception e) {
                bookshelfBackground.setImageResource(R.drawable.bookshelf_bg);
            }
        }
        bookshelfBackground.setAlpha(prefs.getFloat(KEY_BOOKSHELF_BG_ALPHA, 1f));
    }

    private void startBackup() {
        hideMoreMenu();
        executor.execute(() -> {
            try {
                pendingBackupJson = buildBackupJson();
                runOnUiThread(() -> backupLauncher.launch("novel-reader-backup.json"));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "备份失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private String buildBackupJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 2);
        JSONArray books = new JSONArray();
        for (BookEntity book : database.bookDao().getAll()) {
            JSONObject item = new JSONObject();
            item.put("title", book.title);
            item.put("author", book.author);
            item.put("fileType", book.fileType);
            item.put("category", normalizeCategory(book.category));
            item.put("description", book.description);
            item.put("originalFilePath", book.originalFilePath);
            item.put("storageDirPath", book.storageDirPath);
            item.put("totalChapters", book.totalChapters);
            item.put("currentChapterIndex", book.currentChapterIndex);
            item.put("scrollY", book.scrollY);
            item.put("currentPageIndex", book.currentPageIndex);
            item.put("currentPageStartOffset", book.currentPageStartOffset);
            item.put("chapterSplitMode", book.chapterSplitMode);
            item.put("pinnedAt", book.pinnedAt);
            item.put("finishedAt", book.finishedAt);
            item.put("createdAt", book.createdAt);
            item.put("updatedAt", book.updatedAt);
            JSONArray bookmarks = new JSONArray();
            for (BookmarkEntity bookmark : database.bookmarkDao().getForBook(book.id)) {
                JSONObject saved = new JSONObject();
                saved.put("chapterIndex", bookmark.chapterIndex);
                saved.put("pageIndex", bookmark.pageIndex);
                saved.put("pageStartOffset", bookmark.pageStartOffset);
                saved.put("chapterTitle", bookmark.chapterTitle);
                saved.put("summary", bookmark.summary);
                saved.put("createdAt", bookmark.createdAt);
                bookmarks.put(saved);
            }
            item.put("bookmarks", bookmarks);
            JSONArray notes = new JSONArray();
            for (NoteEntity note : database.noteDao().getForBook(book.id)) {
                JSONObject saved = new JSONObject();
                saved.put("chapterIndex", note.chapterIndex);
                saved.put("pageIndex", note.pageIndex);
                saved.put("pageStartOffset", note.pageStartOffset);
                saved.put("chapterTitle", note.chapterTitle);
                saved.put("selectedText", note.selectedText);
                saved.put("noteText", note.noteText);
                saved.put("color", note.color);
                saved.put("createdAt", note.createdAt);
                notes.put(saved);
            }
            item.put("notes", notes);
            books.put(item);
        }
        root.put("books", books);
        JSONArray folderMeta = new JSONArray();
        for (FolderMetaEntity folder : database.folderMetaDao().getAll()) {
            JSONObject item = new JSONObject();
            item.put("name", folder.name);
            item.put("pinnedAt", folder.pinnedAt);
            item.put("updatedAt", folder.updatedAt);
            folderMeta.put(item);
        }
        root.put("folderMeta", folderMeta);
        JSONArray daily = new JSONArray();
        for (com.example.novelreader.data.DailyReadingEntity day : database.dailyReadingDao().getRecent(365)) {
            JSONObject item = new JSONObject();
            item.put("date", day.date);
            item.put("readingMillis", day.readingMillis);
            daily.put(item);
        }
        root.put("dailyReading", daily);
        return root.toString(2);
    }

    private void writeBackupToUri(Uri uri) {
        if (uri == null || pendingBackupJson == null) {
            return;
        }
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                throw new IllegalArgumentException("无法打开备份文件");
            }
            output.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "备份完成", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "备份失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingBackupJson = null;
        }
    }

    private void restoreFromUri(Uri uri) {
        if (uri == null) {
            return;
        }
        executor.execute(() -> {
            try {
                String json = readTextFromUri(uri);
                JSONObject root = new JSONObject(json);
                JSONArray books = root.optJSONArray("books");
                if (books != null) {
                    for (int i = 0; i < books.length(); i++) {
                        JSONObject item = books.getJSONObject(i);
                        String title = item.optString("title", "");
                        if (title.trim().isEmpty() || database.bookDao().getByTitle(title) != null) {
                            continue;
                        }
                        BookEntity book = new BookEntity();
                        book.title = title;
                        book.author = item.optString("author", "");
                        book.fileType = item.optString("fileType", "txt");
                        book.category = normalizeCategory(item.optString("category", ""));
                        book.description = item.optString("description", "");
                        book.originalFilePath = item.optString("originalFilePath", "");
                        book.storageDirPath = item.optString("storageDirPath", "");
                        book.totalChapters = item.optInt("totalChapters", 0);
                        book.currentChapterIndex = item.optInt("currentChapterIndex", 0);
                        book.scrollY = item.optInt("scrollY", 0);
                        book.currentPageIndex = item.optInt("currentPageIndex", 0);
                        book.currentPageStartOffset = item.optInt("currentPageStartOffset", 0);
                        book.chapterSplitMode = item.optInt("chapterSplitMode", 0);
                        book.pinnedAt = item.optLong("pinnedAt", 0L);
                        book.finishedAt = item.optLong("finishedAt", 0L);
                        book.createdAt = item.optLong("createdAt", System.currentTimeMillis());
                        book.updatedAt = item.optLong("updatedAt", book.createdAt);
                        long restoredBookId = database.bookDao().insert(book);
                        JSONArray bookmarks = item.optJSONArray("bookmarks");
                        if (bookmarks != null) {
                            for (int j = 0; j < bookmarks.length(); j++) {
                                JSONObject saved = bookmarks.getJSONObject(j);
                                BookmarkEntity bookmark = new BookmarkEntity();
                                bookmark.bookId = restoredBookId;
                                bookmark.chapterIndex = saved.optInt("chapterIndex", 0);
                                bookmark.pageIndex = saved.optInt("pageIndex", 0);
                                bookmark.pageStartOffset = saved.optInt("pageStartOffset", 0);
                                bookmark.chapterTitle = saved.optString("chapterTitle", "");
                                bookmark.summary = saved.optString("summary", "");
                                bookmark.createdAt = saved.optLong("createdAt", System.currentTimeMillis());
                                database.bookmarkDao().insert(bookmark);
                            }
                        }
                        JSONArray notes = item.optJSONArray("notes");
                        if (notes != null) {
                            for (int j = 0; j < notes.length(); j++) {
                                JSONObject saved = notes.getJSONObject(j);
                                NoteEntity note = new NoteEntity();
                                note.bookId = restoredBookId;
                                note.chapterIndex = saved.optInt("chapterIndex", 0);
                                note.pageIndex = saved.optInt("pageIndex", 0);
                                note.pageStartOffset = saved.optInt("pageStartOffset", 0);
                                note.chapterTitle = saved.optString("chapterTitle", "");
                                note.selectedText = saved.optString("selectedText", "");
                                note.noteText = saved.optString("noteText", "");
                                note.color = saved.optInt("color", 0x66FFE08A);
                                note.createdAt = saved.optLong("createdAt", System.currentTimeMillis());
                                database.noteDao().insert(note);
                            }
                        }
                    }
                }
                JSONArray folderMeta = root.optJSONArray("folderMeta");
                if (folderMeta != null) {
                    for (int i = 0; i < folderMeta.length(); i++) {
                        JSONObject item = folderMeta.getJSONObject(i);
                        String name = normalizeCategory(item.optString("name", ""));
                        if ("未分类".equals(name)) {
                            continue;
                        }
                        FolderMetaEntity meta = new FolderMetaEntity();
                        meta.name = name;
                        meta.pinnedAt = item.optLong("pinnedAt", 0L);
                        meta.updatedAt = item.optLong("updatedAt", System.currentTimeMillis());
                        database.folderMetaDao().save(meta);
                    }
                }
                JSONArray daily = root.optJSONArray("dailyReading");
                if (daily != null) {
                    for (int i = 0; i < daily.length(); i++) {
                        JSONObject item = daily.getJSONObject(i);
                        com.example.novelreader.data.DailyReadingEntity entity = new com.example.novelreader.data.DailyReadingEntity();
                        entity.date = item.optString("date", "");
                        entity.readingMillis = item.optLong("readingMillis", 0L);
                        if (!entity.date.isEmpty()) {
                            database.dailyReadingDao().save(entity);
                        }
                    }
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, "恢复完成", Toast.LENGTH_SHORT).show();
                    loadBooks();
                    updateReadingTimeText();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "恢复失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showSearchDialog() {
        hideMoreMenu();
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("搜索书架"));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("输入书名");
        input.setTextColor(0xFF111111);
        input.setHintTextColor(0xFF777777);
        content.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(createDialogOption("取消", v -> dialog.dismiss()));
        actions.addView(createDialogOption("搜索", v -> {
            List<BookEntity> results = findBooks(input.getText().toString().trim());
            if (results.isEmpty()) {
                Toast.makeText(this, "书架里没有找到这本书", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            if (results.size() == 1) {
                locateSearchResult(results.get(0));
            } else {
                showSearchResultsDialog(results);
            }
        }));
        content.addView(actions);
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void showHistoryDialog() {
        hideMoreMenu();
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("最近阅读"));
        List<BookEntity> recentBooks = new ArrayList<>(currentBooks);
        Collections.sort(recentBooks, (left, right) -> Long.compare(right.updatedAt, left.updatedAt));
        int count = Math.min(10, recentBooks.size());
        if (count == 0) {
            content.addView(createDialogMessage("还没有阅读记录"));
        }
        for (int i = 0; i < count; i++) {
            BookEntity book = recentBooks.get(i);
            int chapterNumber = book.totalChapters == 0 ? 0 : Math.min(book.currentChapterIndex + 1, book.totalChapters);
            TextView row = createDialogOption(book.title + "\n进度 " + chapterNumber + "/" + book.totalChapters, v -> {
                dialog.dismiss();
                startActivity(ReaderActivity.createIntent(MainActivity.this, book.id));
            });
            row.setMinHeight(dpToPx(58));
            content.addView(row);
            if (i < count - 1) {
                content.addView(createDivider());
            }
        }
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private void showSearchResultsDialog(List<BookEntity> results) {
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("搜索结果"));
        for (int i = 0; i < results.size(); i++) {
            content.addView(createSearchResultRow(results.get(i), dialog));
            if (i < results.size() - 1) {
                content.addView(createDivider());
            }
        }
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private TextView createSearchResultRow(BookEntity book, Dialog dialog) {
        int chapterNumber = book.totalChapters == 0 ? 0 : Math.min(book.currentChapterIndex + 1, book.totalChapters);
        int progressPercent = book.totalChapters == 0 ? 0 : Math.round(chapterNumber * 100f / book.totalChapters);
        TextView row = createDialogOption(book.title + "\n进度 " + chapterNumber + "/" + book.totalChapters + " · 已读" + progressPercent + "%", v -> {
            dialog.dismiss();
            startActivity(ReaderActivity.createIntent(MainActivity.this, book.id));
        });
        row.setMinHeight(dpToPx(58));
        return row;
    }

    private void locateSearchResult(BookEntity book) {
        int position = adapter.findPositionById(book.id);
        if (position == RecyclerView.NO_POSITION && !CATEGORY_ALL.equals(normalizeCategory(book.category))) {
            categoryFilter = normalizeCategory(book.category);
            displayMode = BooksAdapter.DISPLAY_LIST;
            prefs.edit().putInt(KEY_DISPLAY_MODE, displayMode).apply();
            applyLayoutManager();
            adapter.setDisplayMode(displayMode);
            submitSortedBooks();
            updateGroupHeader();
            recyclerView.post(() -> locateSearchResult(book));
            return;
        }
        if (position != RecyclerView.NO_POSITION) {
            recyclerView.smoothScrollToPosition(position);
            highlightSearchResult(book.id);
        }
    }

    private void highlightSearchResult(long bookId) {
        uiHandler.removeCallbacksAndMessages(null);
        adapter.clearHighlight();
        uiHandler.postDelayed(() -> adapter.setHighlight(bookId, true), 260L);
        uiHandler.postDelayed(() -> adapter.clearHighlight(), 1260L);
    }

    private List<BookEntity> findBooks(String keyword) {
        List<BookEntity> results = new ArrayList<>();
        if (keyword.isEmpty()) {
            return results;
        }
        for (BookEntity book : currentBooks) {
            if (safeText(book.title).contains(keyword)) {
                results.add(book);
            }
        }
        return results;
    }

    private void updateReadingTimeText() {
        if (readingTimeText == null || prefs == null) {
            return;
        }
        int goalMinutes = prefs.getInt(KEY_DAILY_GOAL_MINUTES, 30);
        executor.execute(() -> {
            List<com.example.novelreader.data.DailyReadingEntity> recent = database.dailyReadingDao().getRecent(7);
            long todayMillis = getTodayMillis(recent);
            long todayMinutes = Math.max(0L, todayMillis / 60000L);
            int progress = goalMinutes <= 0 ? 0 : Math.min(100, Math.round(todayMinutes * 100f / goalMinutes));
            int streak = countReadingStreak(recent);
            runOnUiThread(() -> {
                if (readingTimeText != null) {
                    readingTimeText.setText("今日目标 " + todayMinutes + "/" + goalMinutes + "分钟 · " + progress + "%");
                }
                if (readingStreakBadge != null) {
                    readingStreakBadge.setText("连续" + streak + "天");
                }
                updateHomePageStats();
            });
        });
    }

    private void showReadingGoalDialog() {
        hideMoreMenu();
        Dialog dialog = createPlainDialog();
        LinearLayout content = createDialogContent();
        content.addView(createDialogTitle("每日阅读目标"));
        int current = Math.max(0, Math.min(23 * 60 + 59, prefs.getInt(KEY_DAILY_GOAL_MINUTES, 30)));

        LinearLayout pickerRow = new LinearLayout(this);
        pickerRow.setOrientation(LinearLayout.HORIZONTAL);
        pickerRow.setGravity(android.view.Gravity.CENTER);
        pickerRow.setPadding(0, dpToPx(8), 0, dpToPx(16));

        NumberPicker hourPicker = createGoalNumberPicker(0, 23, current / 60, "%02d h");
        NumberPicker minutePicker = createGoalNumberPicker(0, 59, current % 60, "%02d min");
        pickerRow.addView(hourPicker);
        pickerRow.addView(minutePicker);
        content.addView(pickerRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.addView(createDialogOption("取消", v -> dialog.dismiss()));
        actions.addView(createDialogOption("保存", v -> {
            int goalMinutes = hourPicker.getValue() * 60 + minutePicker.getValue();
            prefs.edit().putInt(KEY_DAILY_GOAL_MINUTES, goalMinutes).apply();
            updateReadingTimeText();
            dialog.dismiss();
        }));
        content.addView(actions);
        dialog.setContentView(content);
        showPlainDialog(dialog);
    }

    private NumberPicker createGoalNumberPicker(int min, int max, int value, String format) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        picker.setFormatter(number -> String.format(Locale.getDefault(), format, number));
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dpToPx(150), 1f);
        params.setMargins(dpToPx(6), 0, dpToPx(6), 0);
        picker.setLayoutParams(params);
        styleGoalNumberPicker(picker);
        return picker;
    }

    private void styleGoalNumberPicker(NumberPicker picker) {
        picker.setBackgroundColor(Color.TRANSPARENT);
        picker.setAlpha(1f);
        picker.setFadingEdgeLength(0);
        picker.setVerticalFadingEdgeEnabled(false);
        applyNumberPickerInternals(picker);
        picker.setOnValueChangedListener((numberPicker, oldValue, newValue) ->
                numberPicker.post(() -> applyNumberPickerInternals(numberPicker)));
        picker.post(() -> applyNumberPickerInternals(picker));
    }

    private void applyNumberPickerInternals(NumberPicker picker) {
        styleNumberPickerText(picker);
        invokeNumberPickerColorSetter(picker, "setTextColor", 0xFF111111);
        invokeNumberPickerColorSetter(picker, "setSelectedTextColor", 0xFF111111);
        setNumberPickerIntField(picker, "mTextColor", 0xFF111111);
        setNumberPickerIntField(picker, "mSelectedTextColor", 0xFF111111);
        setNumberPickerIntField(picker, "mSelectorElementHeight", dpToPx(42));
        try {
            Field paintField = NumberPicker.class.getDeclaredField("mSelectorWheelPaint");
            paintField.setAccessible(true);
            Object paint = paintField.get(picker);
            if (paint instanceof Paint) {
                Paint selectorPaint = (Paint) paint;
                selectorPaint.setColor(0xFF111111);
                selectorPaint.setAlpha(255);
                selectorPaint.setTextSize(dpToPx(18));
                selectorPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            }
        } catch (Exception ignored) {
            // Some Android builds hide this field; child TextView styling still keeps the value readable.
        }
        try {
            Field dividerField = NumberPicker.class.getDeclaredField("mSelectionDivider");
            dividerField.setAccessible(true);
            dividerField.set(picker, new ColorDrawable(0x99111111));
        } catch (Exception ignored) {
            // Divider access is platform-dependent, so failures are harmless.
        }
        picker.invalidate();
    }

    private void invokeNumberPickerColorSetter(NumberPicker picker, String methodName, int color) {
        try {
            Method method = NumberPicker.class.getMethod(methodName, int.class);
            method.invoke(picker, color);
        } catch (Exception ignored) {
            // Older Android versions do not expose these setters.
        }
    }

    private void setNumberPickerIntField(NumberPicker picker, String fieldName, int value) {
        try {
            Field field = NumberPicker.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(picker, value);
        } catch (Exception ignored) {
            // Field names vary across Android versions.
        }
    }

    private void styleNumberPickerText(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextColor(0xFF111111);
            textView.setTextSize(18f);
            textView.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleNumberPickerText(group.getChildAt(i));
            }
        }
    }

    private Dialog createPlainDialog() {
        Dialog dialog = new AnimatedDialog();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    private LinearLayout createDialogContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.WHITE);
        int padding = dpToPx(20);
        content.setPadding(padding, padding, padding, padding);
        return content;
    }

    private TextView createDialogTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(0xFF111111);
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(12));
        return title;
    }

    private TextView createDialogMessage(String text) {
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextColor(0xFF222222);
        message.setTextSize(16f);
        message.setPadding(0, dpToPx(8), 0, dpToPx(8));
        return message;
    }

    private TextView createDialogOption(String text, View.OnClickListener listener) {
        TextView option = new TextView(this);
        option.setText(text);
        option.setTextColor(0xFF111111);
        option.setTextSize(17f);
        option.setGravity(android.view.Gravity.CENTER_VERTICAL);
        option.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        option.setMinHeight(dpToPx(48));
        option.setOnClickListener(listener);
        return option;
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(0x22000000);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, dpToPx(1))
        ));
        return divider;
    }

    private void showPlainDialog(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.82f);
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
            View decorView = window.getDecorView();
            decorView.setAlpha(0f);
            decorView.setScaleX(0.9f);
            decorView.setScaleY(0.9f);
            decorView.setTranslationY(dpToPx(10));
            decorView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(160)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
        }
    }

    private void confirmDeleteSelectedBooks() {
        List<BookEntity> selectedBooks = adapter.getSelectedBooks();
        if (selectedBooks.isEmpty()) {
            exitSelectionMode();
            return;
        }
        Dialog dialog = new AnimatedDialog();
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int dialogWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.86f);
        int imageSize = dialogWidth / 2;
        int imageGap = dpToPx(10);
        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dialogContent.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setAlpha(0f);
        dialogContent.setScaleX(0.82f);
        dialogContent.setScaleY(0.82f);

        ImageView deleteImage = new ImageView(this);
        deleteImage.setImageResource(R.drawable.delete_dialog_image);
        deleteImage.setAdjustViewBounds(true);
        deleteImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(imageSize, imageSize);
        imageParams.setMargins(0, 0, 0, imageGap);
        dialogContent.addView(deleteImage, imageParams);

        View questionView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_books, dialogContent, false);
        dialogContent.addView(questionView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dialog.setContentView(dialogContent);
        TextView message = questionView.findViewById(R.id.deleteDialogMessage);
        TextView cancelButton = questionView.findViewById(R.id.deleteDialogCancel);
        TextView confirmButton = questionView.findViewById(R.id.deleteDialogConfirm);
        message.setText("确定删除选中的 " + selectedBooks.size() + " 本书和本地缓存吗？");
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        confirmButton.setOnClickListener(v -> {
            dialog.dismiss();
            deleteBooks(selectedBooks);
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = dialogWidth;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
        dialogContent.post(() -> dialogContent.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140)
                .setInterpolator(new DecelerateInterpolator())
                .start());
    }

    private class AnimatedDialog extends Dialog {
        private boolean dismissing;

        AnimatedDialog() {
            super(MainActivity.this);
        }

        @Override
        public void dismiss() {
            if (dismissing) {
                return;
            }
            Window window = getWindow();
            View decorView = window == null ? null : window.getDecorView();
            if (!isShowing() || decorView == null) {
                dismissImmediately();
                return;
            }
            dismissing = true;
            decorView.setOnTouchListener((view, event) -> true);
            decorView.animate().cancel();
            decorView.animate()
                    .alpha(0f)
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .translationY(dpToPx(10))
                    .setDuration(130)
                    .setInterpolator(new DecelerateInterpolator(1.3f))
                    .withEndAction(this::dismissImmediately)
                    .start();
        }

        private void dismissImmediately() {
            super.dismiss();
        }
    }

    private void showGroupSelectedDialog() {
        List<BookEntity> selectedBooks = adapter.getSelectedBooks();
        if (selectedBooks.isEmpty()) {
            exitSelectionMode();
            return;
        }
        showCategoryPickerDialog("移动到分组", "", category -> groupBooks(selectedBooks, category));
    }

    private void groupBooks(List<BookEntity> books, String folderName) {
        long now = System.currentTimeMillis();
        String normalizedFolderName = normalizeCategory(folderName);
        executor.execute(() -> {
            for (BookEntity book : books) {
                book.category = normalizedFolderName;
                book.updatedAt = now;
                database.bookDao().update(book);
            }
            saveFolderMetaIfNeeded(normalizedFolderName, now);
            runOnUiThread(() -> {
                Toast.makeText(this, "已移动到分组：" + normalizedFolderName, Toast.LENGTH_SHORT).show();
                categoryFilter = CATEGORY_ALL;
                exitSelectionMode();
                loadBooks();
                updateGroupHeader();
            });
        });
    }

    private void markSelectedBooksFinished(boolean finished) {
        List<BookEntity> selectedBooks = adapter.getSelectedBooks();
        if (selectedBooks.isEmpty()) {
            exitSelectionMode();
            return;
        }
        long now = System.currentTimeMillis();
        executor.execute(() -> {
            for (BookEntity book : selectedBooks) {
                book.finishedAt = finished ? now : 0L;
                book.updatedAt = now;
                database.bookDao().update(book);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, finished ? "已批量标为已读" : "已批量标为在读", Toast.LENGTH_SHORT).show();
                exitSelectionMode();
                loadBooks();
            });
        });
    }

    private void saveFolderMetaIfNeeded(String folderName, long updatedAt) {
        String normalized = normalizeCategory(folderName);
        if ("未分类".equals(normalized)) {
            return;
        }
        FolderMetaEntity meta = database.folderMetaDao().getByName(normalized);
        if (meta == null) {
            meta = new FolderMetaEntity();
            meta.name = normalized;
        }
        meta.updatedAt = updatedAt;
        database.folderMetaDao().save(meta);
    }

    private void deleteBooks(List<BookEntity> books) {
        executor.execute(() -> {
            for (BookEntity book : books) {
                database.bookDao().delete(book);
                deleteRecursively(new File(book.storageDirPath));
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                exitSelectionMode();
                loadBooks();
            });
        });
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private String getDisplayName(Uri uri) {
        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }
        }
        String lastPath = uri.getLastPathSegment();
        return lastPath == null || lastPath.trim().isEmpty() ? "imported_book" : lastPath;
    }

    private String readTextFromUri(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IllegalArgumentException("无法读取文件");
            }
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private long getTodayMillis(List<com.example.novelreader.data.DailyReadingEntity> recent) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
        for (com.example.novelreader.data.DailyReadingEntity day : recent) {
            if (today.equals(day.date)) {
                return day.readingMillis;
            }
        }
        return 0L;
    }

    private int countReadingStreak(List<com.example.novelreader.data.DailyReadingEntity> recent) {
        Set<String> activeDays = new HashSet<>();
        for (com.example.novelreader.data.DailyReadingEntity day : recent) {
            if (day.readingMillis > 0) {
                activeDays.add(day.date);
            }
        }
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        int streak = 0;
        for (int i = 0; i < 7; i++) {
            if (!activeDays.contains(format.format(calendar.getTime()))) {
                break;
            }
            streak++;
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    private String formatMinutes(long millis) {
        long minutes = Math.max(0L, millis / 60000L);
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }

    private String formatEstimatedRemaining(BookEntity book) {
        if (book == null || book.finishedAt > 0) {
            return "已读完";
        }
        int current = book.totalChapters == 0 ? 0 : Math.min(book.currentChapterIndex + 1, book.totalChapters);
        int remainingChapters = Math.max(0, book.totalChapters - current);
        long estimatedMinutes = Math.max(1, remainingChapters * 5L);
        if (remainingChapters == 0) {
            return "不足 5 分钟";
        }
        if (estimatedMinutes < 60) {
            return "约 " + estimatedMinutes + " 分钟";
        }
        return "约 " + (estimatedMinutes / 60) + " 小时 " + (estimatedMinutes % 60) + " 分钟";
    }

    private String formatDateTime(long millis) {
        if (millis <= 0L) {
            return "无";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new java.util.Date(millis));
    }

    private static String stripExtension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : safeText(name);
    }

    private static String normalizeCategory(String category) {
        String clean = category == null ? "" : category.trim();
        return clean.isEmpty() ? "未分类" : clean;
    }

    private static boolean isBookFileMissing(BookEntity book) {
        if (book == null) {
            return false;
        }
        if (book.storageDirPath != null && !book.storageDirPath.trim().isEmpty() && !new File(book.storageDirPath).exists()) {
            return true;
        }
        return book.originalFilePath != null && !book.originalFilePath.trim().isEmpty() && !new File(book.originalFilePath).exists();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
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
}
