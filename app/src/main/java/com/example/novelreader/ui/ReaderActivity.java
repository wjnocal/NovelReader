package com.example.novelreader.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextPaint;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.UpdateAppearance;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.novelreader.R;
import com.example.novelreader.data.AppDatabase;
import com.example.novelreader.data.BookEntity;
import com.example.novelreader.data.BookmarkEntity;
import com.example.novelreader.data.ChapterEntity;
import com.example.novelreader.data.DailyReadingEntity;
import com.example.novelreader.data.NoteEntity;
import com.example.novelreader.data.ReaderSettingsEntity;
import com.example.novelreader.parser.ParsedBook;
import com.example.novelreader.parser.ParsedChapter;
import com.example.novelreader.parser.TxtParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReaderActivity extends AppCompatActivity {
    private static final String EXTRA_BOOK_ID = "book_id";
    private static final int MODE_PAGED = 0;
    private static final int MODE_SCROLL = 1;
    private static final int DRAWER_CATALOG = 0;
    private static final int DRAWER_BOOKMARKS = 1;
    private static final int DRAWER_NOTES = 2;
    private static final int OPEN_LAST_PAGE = Integer.MAX_VALUE;
    private static final String PREFS_NAME = "reader_stats";
    private static final String KEY_TOTAL_READING_MILLIS = "totalReadingMillis";
    private static final int NOTE_YELLOW = 0xD6FFE08A;
    private static final int NOTE_GREEN = 0xD67ED68A;
    private static final int NOTE_BLUE = 0xD67AB7FF;
    private static final int NOTE_RED = 0xD6FF8A8A;
    private static final int NOTE_PINK = 0xD6FF9BC8;
    private static final int SELECTION_MENU_DELAY_MS = 700;
    private static final String REPLY_PREFIX = "\u0001reply:";
    private static final String REPLY_SEPARATOR = "\u0001";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private SharedPreferences readingStatsPrefs;
    private ActivityResultLauncher<String> notesExportLauncher;
    private String pendingNotesExportText;
    private long bookId;
    private BookEntity book;
    private List<ChapterEntity> chapters = new ArrayList<>();
    private List<PageInfo> currentPages = new ArrayList<>();
    private List<BookmarkEntity> bookmarks = new ArrayList<>();
    private List<NoteEntity> notes = new ArrayList<>();
    private ReaderSettingsEntity settings;

    private DrawerLayout readerDrawer;
    private FrameLayout root;
    private LinearLayout toolbar;
    private LinearLayout chapterNav;
    private LinearLayout settingsPanel;
    private LinearLayout drawerPanel;
    private ScrollView scrollView;
    private TextView titleView;
    private TextView contentView;
    private TextView pageTransitionView;
    private TextView pageIndicator;
    private TextView drawerBookTitle;
    private TextView bookmarksTab;
    private TextView catalogTab;
    private TextView notesTab;
    private View bookmarksIndicator;
    private View catalogIndicator;
    private View notesIndicator;
    private TextView emptyBookmarksView;
    private TextView bookmarkButton;
    private TextView previousButton;
    private TextView nextButton;
    private TextView timeView;
    private TextView batteryView;
    private ProgressBar novelProgressBar;
    private SeekBar brightnessSeekBar;
    private SeekBar textSizeSeekBar;
    private SeekBar lineSpacingSeekBar;
    private SeekBar paragraphSpacingSeekBar;
    private SeekBar firstLineIndentSeekBar;
    private SeekBar pageMarginSeekBar;
    private TextView textSizeLabel;
    private TextView lineSpacingLabel;
    private TextView paragraphSpacingLabel;
    private TextView firstLineIndentLabel;
    private TextView pageMarginLabel;
    private TextView pagedModeButton;
    private TextView scrollModeButton;
    private TextView systemFontButton;
    private TextView serifFontButton;
    private TextView monoFontButton;
    private TextView slideTurnButton;
    private TextView simulatedTurnButton;
    private TextView chapterSplitLabel;
    private TextView chapterSplitButton;
    private LinearLayout chapterSplitOptions;
    private TextView chapterSplitDefaultOption;
    private TextView chapterSplitChineseOption;
    private TextView chapterSplitArabicOption;
    private TextView chapterSplitMarkOption;
    private TextView chapterSplitChapterOption;
    private TextView chapterSplitSectionOption;
    private TextView chapterSplitVolumeOption;
    private TextView chapterSplitPrologueOption;
    private TextView lightThemeButton;
    private TextView darkThemeButton;
    private TextView eyeThemeButton;
    private TextView paperThemeButton;
    private TextView grayThemeButton;
    private TextView greenThemeButton;
    private TextView settingsDoneButton;
    private RecyclerView catalogRecyclerView;
    private RecyclerView bookmarksRecyclerView;
    private RecyclerView notesRecyclerView;
    private ChaptersAdapter chaptersAdapter;
    private BookmarksAdapter bookmarksAdapter;
    private NotesAdapter notesAdapter;
    private GestureDetector gestureDetector;

    private String currentChapterTitle = "";
    private String currentDisplayText = "";
    private int currentTitleLength = 0;
    private int renderedTextStartOffset = 0;
    private boolean readerControlsVisible = false;
    private boolean settingsPanelVisible = false;
    private boolean chapterSplitOptionsVisible = false;
    private boolean pageAnimating = false;
    private boolean pageDragActive = false;
    private float pageDragStartX = 0f;
    private float pageDragStartY = 0f;
    private float pageDragLastDx = 0f;
    private int pageDragDirection = 0;
    private int pageDragSourceIndex = -1;
    private int pageDragTargetIndex = -1;
    private CharSequence pageDragSourceText = "";
    private boolean textSelectionActive = false;
    private int textSelectionStart = -1;
    private int textSelectionEnd = -1;
    private View selectionStartHandle;
    private View selectionEndHandle;
    private PopupWindow selectionActionsPopup;
    private PopupWindow selectionColorPopup;
    private final List<View> commentBubbleViews = new ArrayList<>();
    private int currentDrawerPage = DRAWER_CATALOG;
    private long readingSessionStartMillis = 0L;
    private int drawerNormalTextColor = 0xFF222222;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRunnable = new Runnable() {
        @Override
        public void run() {
            updateReaderStatus();
            statusHandler.postDelayed(this, 60000);
        }
    };
    private final Runnable selectionMenuRunnable = this::showSelectionActionsPopup;

    public static Intent createIntent(Context context, long bookId) {
        Intent intent = new Intent(context, ReaderActivity.class);
        intent.putExtra(EXTRA_BOOK_ID, bookId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        database = AppDatabase.getInstance(this);
        readingStatsPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        notesExportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/markdown"), this::writeNotesExportToUri);
        bookId = getIntent().getLongExtra(EXTRA_BOOK_ID, -1);

        bindViews();
        setupSystemBars();
        setupDrawer();
        setupGestures();
        setupButtons();
        setupBrightness();
        hideSystemBars();
        loadReader();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clearTextSelection();
        saveReadingSessionTime();
        saveProgress();
        statusHandler.removeCallbacks(statusRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        readingSessionStartMillis = System.currentTimeMillis();
        hideSystemBars();
        updateReaderStatus();
        statusHandler.postDelayed(statusRunnable, 60000);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onBackPressed() {
        if (readerDrawer != null && readerDrawer.isDrawerOpen(GravityCompat.START)) {
            readerDrawer.closeDrawer(GravityCompat.START);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        statusHandler.removeCallbacks(statusRunnable);
        executor.shutdown();
    }

    private void bindViews() {
        readerDrawer = findViewById(R.id.readerDrawer);
        root = findViewById(R.id.readerRoot);
        toolbar = findViewById(R.id.readerToolbar);
        chapterNav = findViewById(R.id.chapterNav);
        settingsPanel = findViewById(R.id.settingsPanel);
        drawerPanel = findViewById(R.id.drawerPanel);
        scrollView = findViewById(R.id.readerScrollView);
        titleView = findViewById(R.id.chapterTitle);
        contentView = findViewById(R.id.chapterContent);
        pageTransitionView = findViewById(R.id.pageTransitionView);
        pageIndicator = findViewById(R.id.pageIndicator);
        timeView = findViewById(R.id.readerTime);
        batteryView = findViewById(R.id.readerBattery);
        novelProgressBar = findViewById(R.id.novelProgressBar);
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar);
        textSizeSeekBar = findViewById(R.id.textSizeSeekBar);
        lineSpacingSeekBar = findViewById(R.id.lineSpacingSeekBar);
        paragraphSpacingSeekBar = findViewById(R.id.paragraphSpacingSeekBar);
        firstLineIndentSeekBar = findViewById(R.id.firstLineIndentSeekBar);
        pageMarginSeekBar = findViewById(R.id.pageMarginSeekBar);
        textSizeLabel = findViewById(R.id.textSizeLabel);
        lineSpacingLabel = findViewById(R.id.lineSpacingLabel);
        paragraphSpacingLabel = findViewById(R.id.paragraphSpacingLabel);
        firstLineIndentLabel = findViewById(R.id.firstLineIndentLabel);
        pageMarginLabel = findViewById(R.id.pageMarginLabel);
        pagedModeButton = findViewById(R.id.pagedModeButton);
        scrollModeButton = findViewById(R.id.scrollModeButton);
        systemFontButton = findViewById(R.id.systemFontButton);
        serifFontButton = findViewById(R.id.serifFontButton);
        monoFontButton = findViewById(R.id.monoFontButton);
        slideTurnButton = findViewById(R.id.slideTurnButton);
        simulatedTurnButton = findViewById(R.id.simulatedTurnButton);
        chapterSplitLabel = findViewById(R.id.chapterSplitLabel);
        chapterSplitButton = findViewById(R.id.chapterSplitButton);
        chapterSplitOptions = findViewById(R.id.chapterSplitOptions);
        chapterSplitDefaultOption = findViewById(R.id.chapterSplitDefaultOption);
        chapterSplitChineseOption = findViewById(R.id.chapterSplitChineseOption);
        chapterSplitArabicOption = findViewById(R.id.chapterSplitArabicOption);
        chapterSplitMarkOption = findViewById(R.id.chapterSplitMarkOption);
        chapterSplitChapterOption = findViewById(R.id.chapterSplitChapterOption);
        chapterSplitSectionOption = findViewById(R.id.chapterSplitSectionOption);
        chapterSplitVolumeOption = findViewById(R.id.chapterSplitVolumeOption);
        chapterSplitPrologueOption = findViewById(R.id.chapterSplitPrologueOption);
        lightThemeButton = findViewById(R.id.lightThemeButton);
        darkThemeButton = findViewById(R.id.darkThemeButton);
        eyeThemeButton = findViewById(R.id.eyeThemeButton);
        paperThemeButton = findViewById(R.id.paperThemeButton);
        grayThemeButton = findViewById(R.id.grayThemeButton);
        greenThemeButton = findViewById(R.id.greenThemeButton);
        settingsDoneButton = findViewById(R.id.settingsDoneButton);
        drawerBookTitle = findViewById(R.id.drawerBookTitle);
        bookmarksTab = findViewById(R.id.bookmarksTab);
        catalogTab = findViewById(R.id.catalogTab);
        notesTab = findViewById(R.id.notesTab);
        bookmarksIndicator = findViewById(R.id.bookmarksIndicator);
        catalogIndicator = findViewById(R.id.catalogIndicator);
        notesIndicator = findViewById(R.id.notesIndicator);
        emptyBookmarksView = findViewById(R.id.emptyBookmarksView);
        bookmarkButton = findViewById(R.id.bookmarkButton);
        previousButton = findViewById(R.id.previousChapterButton);
        nextButton = findViewById(R.id.nextChapterButton);
        catalogRecyclerView = findViewById(R.id.catalogRecyclerView);
        bookmarksRecyclerView = findViewById(R.id.bookmarksRecyclerView);
        notesRecyclerView = findViewById(R.id.notesRecyclerView);
    }

    private void setupSystemBars() {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            v.setPadding(0, 0, 0, 0);
            return insets;
        });
    }

    private void hideSystemBars() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void setupDrawer() {
        readerDrawer.setScrimColor(0x99000000);
        drawerPanel.post(() -> {
            ViewGroup.LayoutParams params = drawerPanel.getLayoutParams();
            params.width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f);
            drawerPanel.setLayoutParams(params);
        });

        catalogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        bookmarksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        chaptersAdapter = new ChaptersAdapter(chapter -> {
            readerDrawer.closeDrawer(GravityCompat.START);
            openChapter(chapter.chapterIndex, 0, 0);
        });
        catalogRecyclerView.setAdapter(chaptersAdapter);

        bookmarksAdapter = new BookmarksAdapter(bookmark -> {
            readerDrawer.closeDrawer(GravityCompat.START);
            openChapter(bookmark.chapterIndex, 0, bookmark.pageStartOffset);
        });
        bookmarksRecyclerView.setAdapter(bookmarksAdapter);

        notesAdapter = new NotesAdapter(new NotesAdapter.Listener() {
            @Override
            public void onNoteClick(NoteEntity note) {
                readerDrawer.closeDrawer(GravityCompat.START);
                openChapter(note.chapterIndex, 0, note.pageStartOffset);
            }

            @Override
            public void onNoteLongClick(NoteEntity note) {
                showNoteActionDialog(note);
            }
        });
        notesRecyclerView.setAdapter(notesAdapter);

        bookmarksTab.setOnClickListener(v -> showDrawerPage(DRAWER_BOOKMARKS));
        catalogTab.setOnClickListener(v -> showDrawerPage(DRAWER_CATALOG));
        notesTab.setOnClickListener(v -> showDrawerPage(DRAWER_NOTES));
        notesTab.setOnLongClickListener(v -> {
            exportNotes();
            return true;
        });
        showDrawerPage(DRAWER_CATALOG);
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                float third = scrollView.getWidth() / 3f;
                if (!isPagedMode()) {
                    if (e.getX() >= third && e.getX() <= third * 2f) {
                        toggleReaderControls();
                        return true;
                    }
                    return false;
                }
                if (pageAnimating) {
                    return true;
                }
                if (e.getX() < third) {
                    previousPage();
                } else if (e.getX() > third * 2f) {
                    nextPage();
                } else {
                    toggleReaderControls();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (!isPagedMode() || e1 == null || e2 == null) {
                    return false;
                }
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) < dp(48) || Math.abs(dx) < Math.abs(e2.getY() - e1.getY())) {
                    return false;
                }
                if (dx < 0) {
                    nextPage();
                } else {
                    previousPage();
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (pageAnimating || pageDragActive) {
                    return;
                }
                beginTextSelection(e);
            }
        });
        View.OnTouchListener listener = (v, event) -> {
            if (textSelectionActive) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    clearTextSelection();
                }
                return true;
            }
            if (handlePagedDragTouch(event)) {
                return true;
            }
            gestureDetector.onTouchEvent(event);
            return isPagedMode();
        };
        scrollView.setOnTouchListener(listener);
        contentView.setOnTouchListener(listener);
        pageTransitionView.setOnTouchListener(listener);
    }

    private void setupButtons() {
        TextView backButton = findViewById(R.id.backButton);
        TextView catalogButton = findViewById(R.id.catalogButton);
        TextView settingsButton = findViewById(R.id.settingsButton);

        backButton.setOnClickListener(v -> finish());
        catalogButton.setOnClickListener(v -> showCatalog());
        bookmarkButton.setOnClickListener(v -> toggleBookmark());
        settingsButton.setOnClickListener(v -> showInlineSettingsPanel());
        previousButton.setOnClickListener(v -> {
            if (book != null) {
                openChapter(book.currentChapterIndex - 1, 0, OPEN_LAST_PAGE);
            }
        });
        nextButton.setOnClickListener(v -> {
            if (book != null) {
                openChapter(book.currentChapterIndex + 1, 0, 0);
            }
        });
        pagedModeButton.setOnClickListener(v -> switchReadMode(MODE_PAGED));
        scrollModeButton.setOnClickListener(v -> switchReadMode(MODE_SCROLL));
        chapterSplitButton.setOnClickListener(v -> toggleChapterSplitOptions());
        chapterSplitDefaultOption.setOnClickListener(v -> selectChapterSplitMode(TxtParser.SPLIT_DEFAULT));
        chapterSplitChineseOption.setOnClickListener(v -> selectChapterSplitMode(TxtParser.SPLIT_CHINESE_CHAPTER));
        chapterSplitArabicOption.setOnClickListener(v -> selectChapterSplitMode(TxtParser.SPLIT_ARABIC_CHAPTER));
        chapterSplitMarkOption.setOnClickListener(v -> selectChapterSplitMode(TxtParser.SPLIT_ARABIC_MARK));
        chapterSplitChapterOption.setOnClickListener(v -> selectChapterSplitMode(TxtParser.SPLIT_CHAPTER_ARABIC));
        chapterSplitSectionOption.setOnClickListener(v -> selectChapterSplitMode(TxtParser.SPLIT_CHINESE_SECTION));
        chapterSplitVolumeOption.setOnClickListener(v -> selectChapterSplitMode(TxtParser.SPLIT_CHINESE_VOLUME));
        chapterSplitPrologueOption.setOnClickListener(v -> selectChapterSplitMode(TxtParser.SPLIT_PROLOGUE));
        lightThemeButton.setOnClickListener(v -> switchTheme(0));
        darkThemeButton.setOnClickListener(v -> switchTheme(1));
        eyeThemeButton.setOnClickListener(v -> switchTheme(2));
        paperThemeButton.setOnClickListener(v -> switchTheme(3));
        grayThemeButton.setOnClickListener(v -> switchTheme(4));
        greenThemeButton.setOnClickListener(v -> switchTheme(5));
        systemFontButton.setOnClickListener(v -> switchFontMode(0));
        serifFontButton.setOnClickListener(v -> switchFontMode(1));
        monoFontButton.setOnClickListener(v -> switchFontMode(2));
        slideTurnButton.setOnClickListener(v -> switchPageTurnMode(0));
        simulatedTurnButton.setOnClickListener(v -> switchPageTurnMode(1));
        settingsDoneButton.setOnClickListener(v -> hideInlineSettingsPanel());
        textSizeSeekBar.setOnSeekBarChangeListener(new SimpleSeekListener(progress -> {
            settings.textSizeSp = 14f + progress;
            updateSettingsPanelValues();
            applyReaderSettings();
            rerenderCurrentChapterKeepingOffset();
        }));
        lineSpacingSeekBar.setOnSeekBarChangeListener(new SimpleSeekListener(progress -> {
            settings.lineSpacingMultiplier = 1.2f + progress / 10f;
            updateSettingsPanelValues();
            applyReaderSettings();
            rerenderCurrentChapterKeepingOffset();
        }));
        paragraphSpacingSeekBar.setOnSeekBarChangeListener(new SimpleSeekListener(progress -> {
            settings.paragraphSpacingDp = progress;
            updateSettingsPanelValues();
            applyReaderSettings();
            rerenderCurrentChapterKeepingOffset();
        }));
        firstLineIndentSeekBar.setOnSeekBarChangeListener(new SimpleSeekListener(progress -> {
            settings.firstLineIndentEm = progress;
            updateSettingsPanelValues();
            rerenderCurrentChapterKeepingOffset();
        }));
        pageMarginSeekBar.setOnSeekBarChangeListener(new SimpleSeekListener(progress -> {
            settings.pageMarginDp = 12 + progress;
            updateSettingsPanelValues();
            applyReaderSettings();
            rerenderCurrentChapterKeepingOffset();
        }));
        setReaderControlsVisible(false, false);
    }

    private void setupBrightness() {
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        float currentBrightness = attributes.screenBrightness;
        int progress = currentBrightness >= 0f ? Math.round(currentBrightness * 100f) : 50;
        brightnessSeekBar.setProgress(Math.max(1, Math.min(100, progress)));
        brightnessSeekBar.setOnSeekBarChangeListener(new SimpleSeekListener(progressValue -> {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.screenBrightness = Math.max(0.02f, progressValue / 100f);
            getWindow().setAttributes(params);
        }));
    }

    private void loadReader() {
        executor.execute(() -> {
            book = database.bookDao().getById(bookId);
            chapters = database.chapterDao().getForBook(bookId);
            bookmarks = database.bookmarkDao().getForBook(bookId);
            settings = database.readerSettingsDao().get();
            if (settings == null) {
                settings = new ReaderSettingsEntity();
                database.readerSettingsDao().save(settings);
            }
            runOnUiThread(() -> {
                if (book == null || chapters.isEmpty()) {
                    Toast.makeText(this, "书籍不存在或章节为空", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                drawerBookTitle.setText(book.title);
                applyReaderSettings();
                refreshDrawerLists();
                int chapterIndex = Math.max(0, Math.min(book.currentChapterIndex, chapters.size() - 1));
                openChapter(chapterIndex, book.scrollY, book.currentPageStartOffset);
            });
        });
    }

    private void openChapter(int index, int targetScrollY, int targetPageStartOffset) {
        openChapter(index, targetScrollY, targetPageStartOffset, 0);
    }

    private void openChapter(int index, int targetScrollY, int targetPageStartOffset, int direction) {
        if (index < 0 || index >= chapters.size()) {
            pageAnimating = false;
            return;
        }
        clearTextSelection();
        clearCommentBubbles();
        ChapterEntity chapter = chapters.get(index);
        executor.execute(() -> {
            try {
                String content = readText(new File(chapter.contentPath));
                runOnUiThread(() -> {
                    setReaderControlsVisible(false, false);
                    book.currentChapterIndex = index;
                    book.scrollY = targetScrollY;
                    currentChapterTitle = chapter.title;
                    titleView.setText(chapter.title);
                    buildCurrentDisplayText(chapter.title, content);
                    previousButton.setEnabled(index > 0);
                    nextButton.setEnabled(index < chapters.size() - 1);
                    if (isPagedMode()) {
                        renderPagedChapter(targetPageStartOffset, direction);
                    } else {
                        renderScrollChapter(targetScrollY);
                    }
                    refreshDrawerLists();
                    updateBookmarkButton();
                    updateNovelProgress();
                    saveProgress();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pageAnimating = false;
                    Toast.makeText(this, "章节读取失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void buildCurrentDisplayText(String chapterTitle, String content) {
        String title = chapterTitle == null ? "" : chapterTitle.trim();
        String body = content == null ? "" : content.trim();
        if (!title.isEmpty() && body.startsWith(title)) {
            body = body.substring(title.length()).trim();
        }
        currentChapterTitle = title;
        currentTitleLength = title.length();
        currentDisplayText = title.isEmpty() ? body : title + "\n\n" + body;
    }

    private void renderScrollChapter(int targetScrollY) {
        resetPageTransition();
        renderedTextStartOffset = 0;
        contentView.setText(formatTextSlice(0, currentDisplayText.length()));
        contentView.post(this::renderCommentBubbles);
        pageIndicator.setVisibility(View.GONE);
        scrollView.setFillViewport(true);
        scrollView.post(() -> {
            scrollView.scrollTo(0, targetScrollY);
            renderCommentBubbles();
        });
        updateNovelProgress();
    }

    private void renderPagedChapter(int targetPageStartOffset) {
        renderPagedChapter(targetPageStartOffset, 0);
    }

    private void renderPagedChapter(int targetPageStartOffset, int direction) {
        pageIndicator.setVisibility(readerControlsVisible ? View.VISIBLE : View.GONE);
        scrollView.scrollTo(0, 0);
        contentView.post(() -> {
            int availableWidth = contentView.getWidth() - contentView.getPaddingStart() - contentView.getPaddingEnd();
            int availableHeight = scrollView.getHeight() - contentView.getPaddingTop() - contentView.getPaddingBottom();
            currentPages = TextPaginator.paginate(
                    currentDisplayText,
                    contentView.getPaint(),
                    availableWidth,
                    availableHeight,
                    dp(Math.round(settings.paragraphSpacingDp)),
                    settings.lineSpacingMultiplier
            );
            int pageIndex = targetPageStartOffset == OPEN_LAST_PAGE
                    ? currentPages.size() - 1
                    : TextPaginator.findPageByOffset(currentPages, targetPageStartOffset);
            renderPage(pageIndex, direction);
        });
    }

    private void renderPage(int pageIndex, int direction) {
        if (currentPages.isEmpty()) {
            pageAnimating = false;
            return;
        }
        int safeIndex = Math.max(0, Math.min(pageIndex, currentPages.size() - 1));
        PageInfo page = currentPages.get(safeIndex);
        CharSequence oldPageText = direction == 0 ? null : contentView.getText();
        book.currentPageIndex = safeIndex;
        book.currentPageStartOffset = page.start;
        renderedTextStartOffset = page.start;
        contentView.setText(formatTextSlice(page.start, page.end));
        contentView.post(this::renderCommentBubbles);
        pageIndicator.setText((safeIndex + 1) + "/" + currentPages.size());
        pageIndicator.setVisibility(readerControlsVisible ? View.VISIBLE : View.GONE);
        updateBookmarkButton();
        updateNovelProgress();
        if (direction != 0) {
            animatePageTransition(oldPageText, direction);
        } else {
            resetPageTransition();
        }
        saveProgress();
    }

    private CharSequence formatTextSlice(int start, int end) {
        if (TextUtils.isEmpty(currentDisplayText)) {
            return "";
        }
        int safeStart = Math.max(0, Math.min(start, currentDisplayText.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, currentDisplayText.length()));
        String pageText = currentDisplayText.substring(safeStart, safeEnd);
        SpannableString styled = new SpannableString(pageText);
        if (safeStart == 0 && currentTitleLength > 0) {
            int titleEnd = Math.min(currentTitleLength, pageText.length());
            styled.setSpan(
                    new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    0,
                    titleEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            styled.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new RelativeSizeSpan(1.15f), 0, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        applyFirstLineIndent(styled, pageText, safeStart);
        applyNoteUnderlines(styled, safeStart, safeEnd);
        applyActiveSelectionHighlight(styled, safeStart, safeEnd);
        return styled;
    }

    private void applyActiveSelectionHighlight(SpannableString styled, int sliceStart, int sliceEnd) {
        if (!textSelectionActive || textSelectionStart < 0 || textSelectionEnd <= textSelectionStart) {
            return;
        }
        int spanStart = Math.max(textSelectionStart, sliceStart) - sliceStart;
        int spanEnd = Math.min(textSelectionEnd, sliceEnd) - sliceStart;
        if (spanEnd > spanStart) {
            styled.setSpan(new BackgroundColorSpan(0x663BAA78), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void applyFirstLineIndent(SpannableString styled, String pageText, int sliceStart) {
        if (settings == null || settings.firstLineIndentEm <= 0f || TextUtils.isEmpty(pageText)) {
            return;
        }
        int indent = Math.round(contentView.getTextSize() * settings.firstLineIndentEm);
        int paragraphStart = 0;
        while (paragraphStart < pageText.length()) {
            int paragraphEnd = pageText.indexOf('\n', paragraphStart);
            if (paragraphEnd < 0) {
                paragraphEnd = pageText.length();
            }
            int absoluteStart = sliceStart + paragraphStart;
            boolean titleParagraph = absoluteStart == 0 && currentTitleLength > 0 && paragraphStart < currentTitleLength;
            if (!titleParagraph && paragraphEnd > paragraphStart) {
                styled.setSpan(
                        new LeadingMarginSpan.Standard(indent, 0),
                        paragraphStart,
                        paragraphEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            paragraphStart = paragraphEnd + 1;
        }
    }

    private void applyNoteUnderlines(SpannableString styled, int sliceStart, int sliceEnd) {
        if (notes == null || notes.isEmpty() || book == null) {
            return;
        }
        for (NoteEntity note : notes) {
            if (note.chapterIndex != book.currentChapterIndex
                    || TextUtils.isEmpty(note.selectedText)
                    || !TextUtils.isEmpty(note.noteText)) {
                continue;
            }
            int noteStart = Math.max(0, note.pageStartOffset);
            int noteEnd = Math.min(currentDisplayText.length(), noteStart + note.selectedText.length());
            if (noteEnd <= sliceStart || noteStart >= sliceEnd) {
                continue;
            }
            int spanStart = Math.max(noteStart, sliceStart) - sliceStart;
            int spanEnd = Math.min(noteEnd, sliceEnd) - sliceStart;
            if (spanEnd > spanStart) {
                styled.setSpan(
                        new ColoredUnderlineSpan(normalizeUnderlineColor(note.color)),
                        spanStart,
                        spanEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
    }

    private NoteEntity findNoteAtTouch(TextView textView, MotionEvent event) {
        if (notes == null || notes.isEmpty() || textView.getLayout() == null || book == null) {
            return null;
        }
        int x = Math.round(event.getX()) - textView.getTotalPaddingLeft() + textView.getScrollX();
        int y = Math.round(event.getY()) - textView.getTotalPaddingTop() + textView.getScrollY();
        if (x < 0 || y < 0) {
            return null;
        }
        Layout layout = textView.getLayout();
        int line = layout.getLineForVertical(y);
        int localOffset = layout.getOffsetForHorizontal(line, x);
        int absoluteOffset = renderedTextStartOffset + localOffset;
        for (NoteEntity note : notes) {
            if (note.chapterIndex != book.currentChapterIndex || TextUtils.isEmpty(note.selectedText)) {
                continue;
            }
            int noteStart = Math.max(0, note.pageStartOffset);
            int noteEnd = Math.min(currentDisplayText.length(), noteStart + note.selectedText.length());
            if (absoluteOffset >= noteStart && absoluteOffset <= noteEnd) {
                return note;
            }
        }
        return null;
    }

    private void beginTextSelection(MotionEvent event) {
        if (TextUtils.isEmpty(currentDisplayText) || contentView.getLayout() == null || book == null) {
            return;
        }
        setReaderControlsVisible(false, true);
        int absoluteOffset = getTextOffsetForTouch(event);
        if (absoluteOffset < 0) {
            return;
        }
        int[] range = expandSelectionAroundOffset(absoluteOffset);
        textSelectionActive = true;
        textSelectionStart = range[0];
        textSelectionEnd = range[1];
        ensureSelectionHandles();
        refreshVisibleText();
        placeSelectionHandles();
        scheduleSelectionMenu();
    }

    private int getTextOffsetForTouch(MotionEvent event) {
        int[] contentLocation = new int[2];
        contentView.getLocationOnScreen(contentLocation);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return getTextOffsetForScreenPoint(rawX, rawY);
    }

    private int getTextOffsetForScreenPoint(float rawX, float rawY) {
        Layout layout = contentView.getLayout();
        if (layout == null) {
            return -1;
        }
        int[] contentLocation = new int[2];
        contentView.getLocationOnScreen(contentLocation);
        int x = Math.round(rawX - contentLocation[0]) - contentView.getTotalPaddingLeft() + contentView.getScrollX();
        int y = Math.round(rawY - contentLocation[1]) - contentView.getTotalPaddingTop() + contentView.getScrollY();
        if (x < 0 || y < 0) {
            return -1;
        }
        int line = Math.max(0, Math.min(layout.getLineCount() - 1, layout.getLineForVertical(y)));
        int localOffset = layout.getOffsetForHorizontal(line, x);
        int absoluteOffset = renderedTextStartOffset + localOffset;
        int sliceEnd = getRenderedSliceEnd();
        return Math.max(renderedTextStartOffset, Math.min(sliceEnd, absoluteOffset));
    }

    private int[] expandSelectionAroundOffset(int absoluteOffset) {
        int safeOffset = Math.max(0, Math.min(currentDisplayText.length(), absoluteOffset));
        int start = safeOffset;
        int end = safeOffset;
        while (start > 0 && isSelectableChar(currentDisplayText.charAt(start - 1))) {
            start--;
        }
        while (end < currentDisplayText.length() && isSelectableChar(currentDisplayText.charAt(end))) {
            end++;
        }
        if (end <= start) {
            int pageStart = renderedTextStartOffset;
            int pageEnd = getRenderedSliceEnd();
            start = Math.max(pageStart, safeOffset - 4);
            end = Math.min(pageEnd, safeOffset + 8);
        }
        if (end <= start) {
            end = Math.min(currentDisplayText.length(), start + 1);
        }
        return new int[]{start, end};
    }

    private boolean isSelectableChar(char c) {
        int type = Character.getType(c);
        return !Character.isWhitespace(c)
                && type != Character.START_PUNCTUATION
                && type != Character.END_PUNCTUATION
                && type != Character.OTHER_PUNCTUATION
                && type != Character.DASH_PUNCTUATION
                && type != Character.INITIAL_QUOTE_PUNCTUATION
                && type != Character.FINAL_QUOTE_PUNCTUATION;
    }

    private void ensureSelectionHandles() {
        if (selectionStartHandle == null) {
            selectionStartHandle = createSelectionHandle(true);
            root.addView(selectionStartHandle);
        }
        if (selectionEndHandle == null) {
            selectionEndHandle = createSelectionHandle(false);
            root.addView(selectionEndHandle);
        }
        selectionStartHandle.setVisibility(View.VISIBLE);
        selectionEndHandle.setVisibility(View.VISIBLE);
    }

    private View createSelectionHandle(boolean startHandle) {
        View handle = new View(this);
        handle.setBackground(roundedBackground(0xFF3BAA78, 18, 0xFFFFFFFF, 1));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(22), dp(22));
        handle.setLayoutParams(params);
        handle.setElevation(dp(8));
        handle.setOnTouchListener((v, event) -> {
            if (!textSelectionActive) {
                return false;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                statusHandler.removeCallbacks(selectionMenuRunnable);
                dismissSelectionPopups();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE || event.getActionMasked() == MotionEvent.ACTION_UP) {
                statusHandler.removeCallbacks(selectionMenuRunnable);
                int offset = getTextOffsetForScreenPoint(event.getRawX(), event.getRawY());
                if (offset >= 0) {
                    if (startHandle) {
                        textSelectionStart = Math.min(offset, textSelectionEnd - 1);
                    } else {
                        textSelectionEnd = Math.max(offset, textSelectionStart + 1);
                    }
                    clampSelectionToRenderedSlice();
                    refreshVisibleText();
                    placeSelectionHandles();
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        scheduleSelectionMenu();
                    }
                }
                return true;
            }
            return true;
        });
        return handle;
    }

    private void clampSelectionToRenderedSlice() {
        int sliceStart = renderedTextStartOffset;
        int sliceEnd = getRenderedSliceEnd();
        textSelectionStart = Math.max(sliceStart, Math.min(sliceEnd - 1, textSelectionStart));
        textSelectionEnd = Math.max(textSelectionStart + 1, Math.min(sliceEnd, textSelectionEnd));
    }

    private void placeSelectionHandles() {
        if (!textSelectionActive || selectionStartHandle == null || selectionEndHandle == null) {
            return;
        }
        contentView.post(() -> {
            positionHandle(selectionStartHandle, textSelectionStart, true);
            positionHandle(selectionEndHandle, textSelectionEnd, false);
        });
    }

    private void positionHandle(View handle, int absoluteOffset, boolean startHandle) {
        Layout layout = contentView.getLayout();
        if (layout == null) {
            return;
        }
        int localOffset = Math.max(0, Math.min(contentView.getText().length(), absoluteOffset - renderedTextStartOffset));
        int line = layout.getLineForOffset(localOffset);
        float x = layout.getPrimaryHorizontal(localOffset);
        int y = startHandle ? layout.getLineTop(line) : layout.getLineBottom(line);
        int[] contentLocation = new int[2];
        int[] rootLocation = new int[2];
        contentView.getLocationOnScreen(contentLocation);
        root.getLocationOnScreen(rootLocation);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) handle.getLayoutParams();
        params.leftMargin = Math.round(contentLocation[0] - rootLocation[0] + contentView.getTotalPaddingLeft() + x - dp(11));
        params.topMargin = Math.round(contentLocation[1] - rootLocation[1] + contentView.getTotalPaddingTop() + y - dp(11));
        handle.setLayoutParams(params);
    }

    private void scheduleSelectionMenu() {
        statusHandler.removeCallbacks(selectionMenuRunnable);
        statusHandler.postDelayed(selectionMenuRunnable, SELECTION_MENU_DELAY_MS);
    }

    private void showSelectionActionsPopup() {
        if (!textSelectionActive || root == null || textSelectionStart < 0 || textSelectionEnd <= textSelectionStart) {
            return;
        }
        dismissSelectionPopups();
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackground(roundedBackground(0xF02E2E2E, 12, 0x00000000, 0));

        boolean hasMark = hasMarkInSelection();
        TextView mark = createSelectionActionButton(hasMark ? "\u5220\u9664\u6807\u8bb0" : "\u6807\u8bb0");
        TextView copy = createSelectionActionButton("\u590d\u5236");
        TextView comment = createSelectionActionButton("\u8bc4\u8bba");
        mark.setOnClickListener(v -> {
            if (hasMark) {
                deleteMarksInSelection();
            } else {
                showSelectionColorPopup(mark);
            }
        });
        copy.setOnClickListener(v -> copySelectedText());
        comment.setOnClickListener(v -> showCommentSheetForSelection());
        bar.addView(mark);
        bar.addView(copy);
        bar.addView(comment);

        selectionActionsPopup = new PopupWindow(bar, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        selectionActionsPopup.setOutsideTouchable(false);
        selectionActionsPopup.setClippingEnabled(false);
        bar.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] position = selectionMenuPosition(bar.getMeasuredWidth(), bar.getMeasuredHeight());
        selectionActionsPopup.showAtLocation(root, android.view.Gravity.NO_GRAVITY, position[0], position[1]);
        bar.setAlpha(0f);
        bar.setTranslationY(dp(8));
        bar.animate().alpha(1f).translationY(0f).setDuration(150).start();
    }

    private TextView createSelectionActionButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(15f);
        button.setGravity(android.view.Gravity.CENTER);
        button.setMinWidth(dp(text.length() > 2 ? 88 : 58));
        button.setMinHeight(dp(42));
        button.setTypeface(null, Typeface.BOLD);
        return button;
    }

    private int[] selectionMenuPosition(int menuWidth, int menuHeight) {
        Layout layout = contentView.getLayout();
        int x = Math.max(dp(8), (root.getWidth() - menuWidth) / 2);
        int y = dp(80);
        if (layout != null) {
            int local = Math.max(0, Math.min(contentView.getText().length(), textSelectionStart - renderedTextStartOffset));
            int line = layout.getLineForOffset(local);
            int[] contentLocation = new int[2];
            int[] rootLocation = new int[2];
            contentView.getLocationOnScreen(contentLocation);
            root.getLocationOnScreen(rootLocation);
            float lineX = layout.getPrimaryHorizontal(local);
            x = Math.round(contentLocation[0] - rootLocation[0] + contentView.getTotalPaddingLeft() + lineX - menuWidth / 2f);
            y = Math.round(contentLocation[1] - rootLocation[1] + contentView.getTotalPaddingTop() + layout.getLineTop(line) - menuHeight - dp(14));
            if (y < dp(12)) {
                y = Math.round(contentLocation[1] - rootLocation[1] + contentView.getTotalPaddingTop() + layout.getLineBottom(line) + dp(14));
            }
        }
        x = Math.max(dp(8), Math.min(root.getWidth() - menuWidth - dp(8), x));
        y = Math.max(dp(8), Math.min(root.getHeight() - menuHeight - dp(8), y));
        return new int[]{x, y};
    }

    private void showSelectionColorPopup(View markButton) {
        if (!textSelectionActive) {
            return;
        }
        if (selectionColorPopup != null) {
            selectionColorPopup.dismiss();
        }
        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.VERTICAL);
        colors.setPadding(dp(6), dp(6), dp(6), dp(6));
        colors.setBackground(roundedBackground(0xF02E2E2E, 8, 0x00000000, 0));
        addUnderlineColorChoice(colors, NOTE_RED);
        addUnderlineColorChoice(colors, NOTE_YELLOW);
        addUnderlineColorChoice(colors, NOTE_BLUE);
        addUnderlineColorChoice(colors, NOTE_GREEN);
        addUnderlineColorChoice(colors, NOTE_PINK);
        int width = Math.max(dp(52), markButton.getWidth());
        selectionColorPopup = new PopupWindow(colors, width, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        colors.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int[] markLocation = new int[2];
        int[] rootLocation = new int[2];
        markButton.getLocationOnScreen(markLocation);
        root.getLocationOnScreen(rootLocation);
        int x = markLocation[0] - rootLocation[0];
        int y = markLocation[1] - rootLocation[1] - colors.getMeasuredHeight() - dp(6);
        if (y < dp(8)) {
            y = markLocation[1] - rootLocation[1] + markButton.getHeight() + dp(6);
        }
        selectionColorPopup.showAtLocation(root, android.view.Gravity.NO_GRAVITY, x, y);
    }

    private void addUnderlineColorChoice(LinearLayout parent, int color) {
        TextView choice = new TextView(this);
        choice.setText("\u2501\u2501\u2501\u2501");
        choice.setTextColor(normalizeUnderlineColor(color));
        choice.setTextSize(18f);
        choice.setGravity(android.view.Gravity.CENTER);
        choice.setOnClickListener(v -> saveSelectionAsMark(color));
        parent.addView(choice, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
    }

    private void saveSelectionAsMark(int color) {
        saveNoteAtRange(textSelectionStart, textSelectionEnd, "", color);
        clearTextSelection();
    }

    private boolean hasMarkInSelection() {
        return !getMarksInSelection().isEmpty();
    }

    private List<NoteEntity> getMarksInSelection() {
        List<NoteEntity> result = new ArrayList<>();
        if (notes == null || book == null || textSelectionStart < 0 || textSelectionEnd <= textSelectionStart) {
            return result;
        }
        for (NoteEntity note : notes) {
            if (note.chapterIndex != book.currentChapterIndex
                    || TextUtils.isEmpty(note.selectedText)
                    || !TextUtils.isEmpty(note.noteText)) {
                continue;
            }
            int markStart = Math.max(0, note.pageStartOffset);
            int markEnd = Math.min(currentDisplayText.length(), markStart + note.selectedText.length());
            if (markEnd > textSelectionStart && markStart < textSelectionEnd) {
                result.add(note);
            }
        }
        return result;
    }

    private void deleteMarksInSelection() {
        List<NoteEntity> marks = getMarksInSelection();
        if (marks.isEmpty()) {
            clearTextSelection();
            return;
        }
        executor.execute(() -> {
            for (NoteEntity mark : marks) {
                database.noteDao().delete(mark);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "\u5df2\u5220\u9664\u6807\u8bb0", Toast.LENGTH_SHORT).show();
                clearTextSelection();
                loadNotes();
            });
        });
    }

    private void copySelectedText() {
        String selected = getSelectedText();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("\u9009\u4e2d\u6587\u672c", selected));
            Toast.makeText(this, "\u5df2\u590d\u5236\u9009\u4e2d\u6587\u672c", Toast.LENGTH_SHORT).show();
        }
        clearTextSelection();
    }

    private void showCommentSheetForSelection() {
        int[] paragraph = paragraphRangeForRange(textSelectionStart, textSelectionEnd);
        String selected = textForRange(paragraph[0], paragraph[1]);
        int start = paragraph[0];
        int end = paragraph[1];
        dismissSelectionPopups();
        showCommentSheetForRange(selected, start, end, true);
    }

    private void showCommentSheetForRange(String selected, int start, int end, boolean keepSelection) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = createCommentsSheet(selected, start, end, dialog, keepSelection);
        dialog.setContentView(content);
        showBottomSheetDialog(dialog);
    }

    private LinearLayout createCommentsSheet(String selected, int start, int end, Dialog dialog, boolean keepSelection) {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(roundedBackground(0xFFFFFFFF, 28, 0x00000000, 0));
        sheet.setPadding(dp(22), dp(18), dp(22), dp(12));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView all = new TextView(this);
        int count = getCommentsForRange(start, selected).size();
        all.setText("\u5168\u90e8 " + count);
        all.setTextColor(0xFF222222);
        all.setTextSize(22f);
        all.setTypeface(null, Typeface.BOLD);
        tabs.addView(all, new LinearLayout.LayoutParams(0, dp(52), 1f));
        TextView close = new TextView(this);
        close.setText("\u00d7");
        close.setTextSize(30f);
        close.setGravity(android.view.Gravity.CENTER);
        close.setOnClickListener(v -> {
            dialog.dismiss();
            if (keepSelection) {
                clearTextSelection();
            }
        });
        tabs.addView(close, new LinearLayout.LayoutParams(dp(48), dp(52)));
        sheet.addView(tabs);

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        CommentsAdapter adapter = new CommentsAdapter(getCommentsForRange(start, selected));
        list.setAdapter(adapter);
        sheet.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(8), 0, 0);
        EditText input = new EditText(this);
        input.setHint("\u8d77\u70b9\u4e0d\u9650\uff0c\u5999\u8bc4\u4f60\u6765\u5199\uff01");
        input.setSingleLine(false);
        input.setMaxLines(3);
        input.setTextSize(15f);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(roundedBackground(0xFFF5F5F5, 22, 0x00000000, 0));
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView send = new TextView(this);
        send.setText("\u53d1\u5e03");
        send.setTextColor(0xFFB95A52);
        send.setTextSize(16f);
        send.setTypeface(null, Typeface.BOLD);
        send.setGravity(android.view.Gravity.CENTER);
        send.setOnClickListener(v -> {
            String comment = input.getText().toString().trim();
            if (TextUtils.isEmpty(comment)) {
                return;
            }
            saveTopCommentAtRange(start, end, comment, adapter);
            input.setText("");
            if (keepSelection) {
                clearTextSelection();
            }
        });
        inputRow.addView(send, new LinearLayout.LayoutParams(dp(64), dp(46)));
        sheet.addView(inputRow);
        return sheet;
    }

    private List<NoteEntity> getCommentsForRange(int start, String selected) {
        List<NoteEntity> comments = new ArrayList<>();
        if (notes == null) {
            return comments;
        }
        int[] paragraph = paragraphRangeForOffset(start);
        int paragraphStart = paragraph[0];
        int paragraphEnd = paragraph[1];
        for (NoteEntity note : notes) {
            int noteStart = Math.max(0, Math.min(currentDisplayText.length(), note.pageStartOffset));
            if (note.chapterIndex == book.currentChapterIndex
                    && noteStart >= paragraphStart
                    && noteStart < paragraphEnd
                    && !TextUtils.isEmpty(note.noteText)) {
                comments.add(note);
            }
        }
        comments.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
        return comments;
    }

    private void saveTopCommentAtRange(int startOffset, int endOffset, String comment, CommentsAdapter adapter) {
        int[] paragraph = paragraphRangeForRange(startOffset, endOffset);
        insertCommentAtRange(paragraph[0], paragraph[1], comment, inserted -> {
            adapter.addComment(inserted);
            loadNotes();
        });
    }

    private void saveReplyToComment(NoteEntity parent, String reply, CommentsAdapter adapter) {
        if (parent == null || TextUtils.isEmpty(reply)) {
            return;
        }
        String encodedReply = REPLY_PREFIX + parent.id + REPLY_SEPARATOR + reply;
        int[] paragraph = paragraphRangeForOffset(parent.pageStartOffset);
        insertCommentAtRange(paragraph[0], paragraph[1], encodedReply, inserted -> {
            adapter.addComment(inserted);
            adapter.expand(parent.id);
            loadNotes();
        });
    }

    private void insertCommentAtRange(int startOffset, int endOffset, String noteText, CommentInsertCallback callback) {
        if (book == null) {
            return;
        }
        int safeStart = Math.max(0, Math.min(currentDisplayText.length(), startOffset));
        int safeEnd = Math.max(safeStart, Math.min(currentDisplayText.length(), endOffset));
        if (safeEnd <= safeStart) {
            return;
        }
        NoteEntity note = new NoteEntity();
        note.bookId = bookId;
        note.chapterIndex = book.currentChapterIndex;
        note.pageIndex = isPagedMode() && currentPages != null && !currentPages.isEmpty()
                ? TextPaginator.findPageByOffset(currentPages, safeStart)
                : 0;
        note.pageStartOffset = safeStart;
        note.chapterTitle = currentChapterTitle;
        note.selectedText = currentDisplayText.substring(safeStart, safeEnd);
        note.noteText = noteText;
        note.color = 0;
        note.createdAt = System.currentTimeMillis();
        executor.execute(() -> {
            long id = database.noteDao().insert(note);
            note.id = id;
            runOnUiThread(() -> {
                Toast.makeText(this, "\u5df2\u53d1\u5e03\u8bc4\u8bba", Toast.LENGTH_SHORT).show();
                if (callback != null) {
                    callback.onInserted(note);
                }
            });
        });
    }

    private interface CommentInsertCallback {
        void onInserted(NoteEntity note);
    }

    private String getSelectedText() {
        if (TextUtils.isEmpty(currentDisplayText) || textSelectionStart < 0 || textSelectionEnd <= textSelectionStart) {
            return "";
        }
        return textForRange(textSelectionStart, textSelectionEnd);
    }

    private String textForRange(int startOffset, int endOffset) {
        if (TextUtils.isEmpty(currentDisplayText)) {
            return "";
        }
        int start = Math.max(0, Math.min(currentDisplayText.length(), startOffset));
        int end = Math.max(start, Math.min(currentDisplayText.length(), endOffset));
        return currentDisplayText.substring(start, end);
    }

    private int[] paragraphRangeForRange(int startOffset, int endOffset) {
        int anchor = Math.max(0, Math.min(currentDisplayText.length(), startOffset));
        return paragraphRangeForOffset(anchor);
    }

    private int[] paragraphRangeForOffset(int offset) {
        if (TextUtils.isEmpty(currentDisplayText)) {
            return new int[]{0, 0};
        }
        int safeOffset = Math.max(0, Math.min(currentDisplayText.length(), offset));
        if (safeOffset == currentDisplayText.length() && safeOffset > 0) {
            safeOffset--;
        }
        int start = currentDisplayText.lastIndexOf('\n', safeOffset);
        start = start < 0 ? 0 : start + 1;
        while (start < currentDisplayText.length() && currentDisplayText.charAt(start) == '\n') {
            start++;
        }
        int end = currentDisplayText.indexOf('\n', safeOffset);
        end = end < 0 ? currentDisplayText.length() : end;
        while (end > start && Character.isWhitespace(currentDisplayText.charAt(end - 1))) {
            end--;
        }
        if (end <= start) {
            end = Math.min(currentDisplayText.length(), start + 1);
        }
        return new int[]{start, end};
    }

    private void refreshVisibleText() {
        if (TextUtils.isEmpty(currentDisplayText)) {
            return;
        }
        int sliceStart = isPagedMode() ? renderedTextStartOffset : 0;
        int sliceEnd = getRenderedSliceEnd();
        contentView.setText(formatTextSlice(sliceStart, sliceEnd));
        contentView.post(this::renderCommentBubbles);
    }

    private int getRenderedSliceEnd() {
        if (!isPagedMode() || currentPages == null || currentPages.isEmpty() || book == null) {
            return currentDisplayText.length();
        }
        int index = Math.max(0, Math.min(book.currentPageIndex, currentPages.size() - 1));
        return currentPages.get(index).end;
    }

    private void clearTextSelection() {
        boolean hadSelectionUi = textSelectionActive || selectionActionsPopup != null || selectionColorPopup != null;
        statusHandler.removeCallbacks(selectionMenuRunnable);
        dismissSelectionPopups();
        textSelectionActive = false;
        textSelectionStart = -1;
        textSelectionEnd = -1;
        if (selectionStartHandle != null) {
            selectionStartHandle.setVisibility(View.GONE);
        }
        if (selectionEndHandle != null) {
            selectionEndHandle.setVisibility(View.GONE);
        }
        if (hadSelectionUi) {
            refreshVisibleText();
        }
    }

    private void dismissSelectionPopups() {
        if (selectionColorPopup != null) {
            selectionColorPopup.dismiss();
            selectionColorPopup = null;
        }
        if (selectionActionsPopup != null) {
            selectionActionsPopup.dismiss();
            selectionActionsPopup = null;
        }
    }

    private void renderCommentBubbles() {
        clearCommentBubbles();
        if (notes == null || notes.isEmpty() || book == null || contentView.getLayout() == null || root == null) {
            return;
        }
        int sliceStart = renderedTextStartOffset;
        int sliceEnd = getRenderedSliceEnd();
        List<CommentBubbleTarget> targets = collectCommentBubbleTargets(sliceStart, sliceEnd);
        for (CommentBubbleTarget target : targets) {
            addCommentBubble(target);
        }
    }

    private List<CommentBubbleTarget> collectCommentBubbleTargets(int sliceStart, int sliceEnd) {
        List<CommentBubbleTarget> targets = new ArrayList<>();
        for (NoteEntity note : notes) {
            if (note.chapterIndex != book.currentChapterIndex || TextUtils.isEmpty(note.selectedText) || TextUtils.isEmpty(note.noteText)) {
                continue;
            }
            int[] paragraph = paragraphRangeForOffset(note.pageStartOffset);
            int start = paragraph[0];
            int end = paragraph[1];
            if (end <= sliceStart || start >= sliceEnd) {
                continue;
            }
            CommentBubbleTarget target = null;
            for (CommentBubbleTarget existing : targets) {
                if (existing.start == start) {
                    target = existing;
                    break;
                }
            }
            if (target == null) {
                target = new CommentBubbleTarget(start, end, textForRange(start, end));
                targets.add(target);
            }
            target.count++;
        }
        return targets;
    }

    private void addCommentBubble(CommentBubbleTarget target) {
        Layout layout = contentView.getLayout();
        if (layout == null) {
            return;
        }
        int localEnd = Math.max(0, Math.min(contentView.getText().length(), target.end - renderedTextStartOffset));
        int line = layout.getLineForOffset(localEnd);
        float x = layout.getPrimaryHorizontal(localEnd);
        int y = layout.getLineBaseline(line);
        int[] contentLocation = new int[2];
        int[] rootLocation = new int[2];
        contentView.getLocationOnScreen(contentLocation);
        root.getLocationOnScreen(rootLocation);

        TextView bubble = new TextView(this);
        bubble.setText(target.count > 100 ? "100+" : String.valueOf(target.count));
        bubble.setTextColor(target.count > 100 ? 0xFFFFFFFF : 0xFF6A746E);
        bubble.setTextSize(11f);
        bubble.setGravity(android.view.Gravity.CENTER);
        bubble.setPadding(dp(7), 0, dp(7), 0);
        int bg = target.count > 100 ? 0xFFE45F5F : 0xFFEAF1EC;
        bubble.setBackground(roundedBackground(bg, 10, 0x666A746E, 1));
        bubble.setOnClickListener(v -> showCommentSheetForRange(target.selectedText, target.start, target.end, false));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(24));
        params.leftMargin = Math.round(contentLocation[0] - rootLocation[0] + contentView.getTotalPaddingLeft() + x + dp(6));
        params.topMargin = Math.round(contentLocation[1] - rootLocation[1] + contentView.getTotalPaddingTop() + y - dp(20));
        root.addView(bubble, params);
        commentBubbleViews.add(bubble);
    }

    private void clearCommentBubbles() {
        if (root == null) {
            commentBubbleViews.clear();
            return;
        }
        for (View view : commentBubbleViews) {
            root.removeView(view);
        }
        commentBubbleViews.clear();
    }

    private void nextPage() {
        if (!isPagedMode() || pageAnimating) {
            return;
        }
        setReaderControlsVisible(false, true);
        if (book.currentPageIndex < currentPages.size() - 1) {
            renderPage(book.currentPageIndex + 1, -1);
        } else if (book.currentChapterIndex < chapters.size() - 1) {
            pageAnimating = true;
            openChapter(book.currentChapterIndex + 1, 0, 0, -1);
        } else {
            markBookFinished();
        }
    }

    private void markBookFinished() {
        if (book == null || book.finishedAt > 0) {
            return;
        }
        book.finishedAt = System.currentTimeMillis();
        BookEntity snapshot = book;
        executor.execute(() -> {
            database.bookDao().update(snapshot);
            int noteCount = database.noteDao().countForBook(bookId);
            int bookmarkCount = database.bookmarkDao().countForBook(bookId);
            runOnUiThread(() -> showFinishedSummaryDialog(noteCount, bookmarkCount));
        });
    }

    private void showFinishedSummaryDialog(int noteCount, int bookmarkCount) {
        Dialog dialog = new AnimatedDialog();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFFFFFBF4);
        content.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        int padding = dp(22);
        content.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("完读总结");
        title.setTextColor(0xFF2A211C);
        title.setTextSize(24f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(6));
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("《" + (book == null ? "" : book.title) + "》已读完");
        subtitle.setTextColor(0xFF6D625B);
        subtitle.setTextSize(15f);
        subtitle.setGravity(android.view.Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(18));
        content.addView(subtitle);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(android.view.Gravity.CENTER);
        stats.setBackgroundColor(0xFFFFF1DF);
        stats.setPadding(dp(10), dp(12), dp(10), dp(12));
        stats.addView(createFinishedStat("章节", chapters.size() + "章"));
        stats.addView(createFinishedDivider());
        stats.addView(createFinishedStat("书签", bookmarkCount + "个"));
        stats.addView(createFinishedDivider());
        stats.addView(createFinishedStat("笔记", noteCount + "条"));
        content.addView(stats, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView tip = new TextView(this);
        tip.setText("这本书已经标记为已读，进度会保存在书架中。");
        tip.setTextColor(0xFF7A6F67);
        tip.setTextSize(14f);
        tip.setGravity(android.view.Gravity.CENTER);
        tip.setPadding(0, dp(16), 0, dp(8));
        content.addView(tip);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        TextView close = createNoteDialogButton("知道了");
        close.setTextSize(17f);
        close.setTextColor(0xFFD75A52);
        close.setOnClickListener(v -> dialog.dismiss());
        actions.addView(close);
        content.addView(actions);
        dialog.setContentView(content);
        showCelebrationDialog(dialog);
    }

    private TextView createFinishedStat(String label, String value) {
        TextView stat = new TextView(this);
        stat.setText(value + "\n" + label);
        stat.setGravity(android.view.Gravity.CENTER);
        stat.setTextColor(0xFF30251F);
        stat.setTextSize(16f);
        stat.setTypeface(null, android.graphics.Typeface.BOLD);
        stat.setLineSpacing(dp(2), 1f);
        stat.setMinHeight(dp(58));
        stat.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return stat;
    }

    private View createFinishedDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(0x33B95A52);
        divider.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(44)));
        return divider;
    }

    private void previousPage() {
        if (!isPagedMode() || pageAnimating) {
            return;
        }
        setReaderControlsVisible(false, true);
        if (book.currentPageIndex > 0) {
            renderPage(book.currentPageIndex - 1, 1);
        } else if (book.currentChapterIndex > 0) {
            pageAnimating = true;
            openChapter(book.currentChapterIndex - 1, 0, OPEN_LAST_PAGE, 1);
        }
    }

    private boolean handlePagedDragTouch(MotionEvent event) {
        if (!isPagedMode() || settings == null || settings.pageTurnMode != 0 || currentPages == null || currentPages.isEmpty()) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pageDragStartX = event.getX();
            pageDragStartY = event.getY();
            pageDragLastDx = 0f;
            if (!pageDragActive && !pageAnimating) {
                contentView.animate().cancel();
                pageTransitionView.animate().cancel();
            }
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (pageAnimating && !pageDragActive) {
                return true;
            }
            float dx = event.getX() - pageDragStartX;
            float dy = event.getY() - pageDragStartY;
            if (!pageDragActive) {
                if (Math.abs(dx) < dp(10) || Math.abs(dx) < Math.abs(dy) * 1.15f) {
                    return false;
                }
                if (!preparePageDrag(dx, event)) {
                    return false;
                }
            }
            updatePageDrag(dx);
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pageDragActive) {
                finishPageDrag(action == MotionEvent.ACTION_UP);
                return true;
            }
            return false;
        }
        return pageDragActive;
    }

    private boolean preparePageDrag(float dx, MotionEvent event) {
        if (book == null || currentPages == null || currentPages.isEmpty()) {
            return false;
        }
        int direction = dx < 0 ? -1 : 1;
        int targetIndex = book.currentPageIndex + (direction < 0 ? 1 : -1);
        if (targetIndex < 0 || targetIndex >= currentPages.size()) {
            return false;
        }
        pageDragActive = true;
        pageAnimating = true;
        pageDragDirection = direction;
        pageDragSourceIndex = book.currentPageIndex;
        pageDragTargetIndex = targetIndex;
        pageDragSourceText = contentView.getText();
        cancelPendingGestureLongPress(event);
        setReaderControlsVisible(false, true);

        PageInfo targetPage = currentPages.get(targetIndex);
        pageTransitionView.setText(pageDragSourceText == null ? "" : pageDragSourceText);
        pageTransitionView.setVisibility(View.VISIBLE);
        pageTransitionView.setTranslationX(0f);
        pageTransitionView.setAlpha(1f);
        pageTransitionView.setScaleX(1f);
        pageTransitionView.setRotationY(0f);

        contentView.setText(formatTextSlice(targetPage.start, targetPage.end));
        contentView.setAlpha(1f);
        contentView.setScaleX(1f);
        contentView.setRotationY(0f);
        contentView.setTranslationX(direction < 0 ? dragWidth() : -dragWidth());
        return true;
    }

    private void cancelPendingGestureLongPress(MotionEvent event) {
        if (gestureDetector == null) {
            return;
        }
        gestureDetector.setIsLongpressEnabled(false);
        MotionEvent cancelEvent = MotionEvent.obtain(event);
        cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
        gestureDetector.onTouchEvent(cancelEvent);
        cancelEvent.recycle();
    }

    private void updatePageDrag(float dx) {
        int width = dragWidth();
        if (width <= 0) {
            return;
        }
        float clampedDx;
        if (pageDragDirection < 0) {
            clampedDx = Math.max(-width, Math.min(0f, dx));
        } else {
            clampedDx = Math.min(width, Math.max(0f, dx));
        }
        pageDragLastDx = clampedDx;
        float progress = Math.min(1f, Math.abs(clampedDx) / width);
        float oldX = pageDragDirection < 0 ? -progress * width : progress * width;
        float targetX = pageDragDirection < 0 ? width - progress * width : -width + progress * width;
        pageTransitionView.setTranslationX(oldX);
        pageTransitionView.setAlpha(1f - progress * 0.22f);
        contentView.setTranslationX(targetX);
        contentView.setAlpha(0.72f + progress * 0.28f);
    }

    private void finishPageDrag(boolean canComplete) {
        int width = dragWidth();
        boolean complete = canComplete && width > 0 && Math.abs(pageDragLastDx) >= width / 3f;
        contentView.animate().cancel();
        pageTransitionView.animate().cancel();
        if (complete) {
            float oldEnd = pageDragDirection < 0 ? -width : width;
            PathInterpolator interpolator = new PathInterpolator(0.22f, 0f, 0f, 1f);
            pageTransitionView.animate()
                    .translationX(oldEnd)
                    .alpha(0.35f)
                    .setDuration(180)
                    .setInterpolator(interpolator)
                    .start();
            contentView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(180)
                    .setInterpolator(interpolator)
                    .withEndAction(this::commitPageDrag)
                    .start();
        } else {
            float targetOffscreen = pageDragDirection < 0 ? width : -width;
            PathInterpolator interpolator = new PathInterpolator(0.22f, 0f, 0f, 1f);
            pageTransitionView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(160)
                    .setInterpolator(interpolator)
                    .start();
            contentView.animate()
                    .translationX(targetOffscreen)
                    .alpha(0.72f)
                    .setDuration(160)
                    .setInterpolator(interpolator)
                    .withEndAction(this::cancelPageDrag)
                    .start();
        }
    }

    private void commitPageDrag() {
        int safeIndex = Math.max(0, Math.min(pageDragTargetIndex, currentPages.size() - 1));
        PageInfo page = currentPages.get(safeIndex);
        book.currentPageIndex = safeIndex;
        book.currentPageStartOffset = page.start;
        renderedTextStartOffset = page.start;
        contentView.setText(formatTextSlice(page.start, page.end));
        contentView.post(this::renderCommentBubbles);
        pageIndicator.setText((safeIndex + 1) + "/" + currentPages.size());
        pageIndicator.setVisibility(readerControlsVisible ? View.VISIBLE : View.GONE);
        updateBookmarkButton();
        updateNovelProgress();
        saveProgress();
        clearPageDragState();
    }

    private void cancelPageDrag() {
        if (pageDragSourceIndex >= 0 && pageDragSourceIndex < currentPages.size()) {
            PageInfo page = currentPages.get(pageDragSourceIndex);
            contentView.setText(formatTextSlice(page.start, page.end));
            renderedTextStartOffset = page.start;
            contentView.post(this::renderCommentBubbles);
        } else {
            contentView.setText(pageDragSourceText == null ? "" : pageDragSourceText);
            contentView.post(this::renderCommentBubbles);
        }
        clearPageDragState();
    }

    private void clearPageDragState() {
        contentView.setTranslationX(0f);
        contentView.setAlpha(1f);
        contentView.setScaleX(1f);
        contentView.setRotationY(0f);
        pageTransitionView.setVisibility(View.GONE);
        pageTransitionView.setTranslationX(0f);
        pageTransitionView.setAlpha(1f);
        pageTransitionView.setScaleX(1f);
        pageTransitionView.setRotationY(0f);
        pageDragActive = false;
        pageAnimating = false;
        pageDragDirection = 0;
        pageDragSourceIndex = -1;
        pageDragTargetIndex = -1;
        pageDragSourceText = "";
        pageDragLastDx = 0f;
        if (gestureDetector != null) {
            gestureDetector.setIsLongpressEnabled(true);
        }
    }

    private int dragWidth() {
        return Math.max(1, Math.max(contentView.getWidth(), root.getWidth()));
    }

    private void animatePageTransition(CharSequence oldPageText, int direction) {
        int width = Math.max(contentView.getWidth(), root.getWidth());
        if (width <= 0) {
            pageAnimating = false;
            return;
        }
        pageAnimating = true;
        contentView.animate().cancel();
        pageTransitionView.animate().cancel();

        if (settings != null && settings.pageTurnMode == 1) {
            pageTransitionView.setText(oldPageText == null ? "" : oldPageText);
            pageTransitionView.setVisibility(View.VISIBLE);
            pageTransitionView.setTranslationX(0f);
            pageTransitionView.setAlpha(1f);
            pageTransitionView.setPivotX(direction < 0 ? 0f : width);
            pageTransitionView.setPivotY(pageTransitionView.getHeight() / 2f);
            contentView.setTranslationX(0f);
            contentView.setAlpha(0f);
            contentView.setScaleX(0.96f);
            contentView.setRotationY(direction < 0 ? 8f : -8f);

            PathInterpolator interpolator = new PathInterpolator(0.25f, 0f, 0.12f, 1f);
            pageTransitionView.animate()
                    .alpha(0.18f)
                    .scaleX(0.92f)
                    .rotationY(direction < 0 ? -10f : 10f)
                    .setDuration(260)
                    .setInterpolator(interpolator)
                    .start();
            contentView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .rotationY(0f)
                    .setDuration(260)
                    .setInterpolator(interpolator)
                    .withEndAction(() -> {
                        pageAnimating = false;
                        resetPageTransition();
                    })
                    .start();
            return;
        }

        float newStart = direction < 0 ? width : -width;
        float oldEnd = direction < 0 ? -width : width;
        pageTransitionView.setText(oldPageText == null ? "" : oldPageText);
        pageTransitionView.setVisibility(View.VISIBLE);
        pageTransitionView.setTranslationX(0f);
        pageTransitionView.setAlpha(1f);
        contentView.setTranslationX(newStart);
        contentView.setAlpha(0.58f);

        PathInterpolator interpolator = new PathInterpolator(0.22f, 0f, 0f, 1f);
        pageTransitionView.animate()
                .translationX(oldEnd)
                .alpha(0.35f)
                .setDuration(280)
                .setInterpolator(interpolator)
                .start();
        contentView.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(280)
                .setInterpolator(interpolator)
                .withEndAction(() -> {
                    pageAnimating = false;
                    resetPageTransition();
                })
                .start();
    }

    private void resetPageTransition() {
        pageAnimating = false;
        contentView.animate().cancel();
        pageTransitionView.animate().cancel();
        contentView.setTranslationX(0f);
        contentView.setAlpha(1f);
        contentView.setScaleX(1f);
        contentView.setRotationY(0f);
        pageTransitionView.setVisibility(View.GONE);
        pageTransitionView.setTranslationX(0f);
        pageTransitionView.setAlpha(1f);
        pageTransitionView.setScaleX(1f);
        pageTransitionView.setRotationY(0f);
        clearCommentBubbles();
        pageDragActive = false;
        pageDragDirection = 0;
        pageDragSourceIndex = -1;
        pageDragTargetIndex = -1;
        pageDragSourceText = "";
        pageDragLastDx = 0f;
        if (gestureDetector != null) {
            gestureDetector.setIsLongpressEnabled(true);
        }
    }

    private void toggleReaderControls() {
        if (settingsPanelVisible) {
            setReaderControlsVisible(false, true);
        } else {
            setReaderControlsVisible(!readerControlsVisible, true);
        }
    }

    private void setReaderControlsVisible(boolean visible, boolean animate) {
        boolean wasSettingsPanelVisible = settingsPanelVisible;
        readerControlsVisible = visible;
        if (!visible) {
            settingsPanelVisible = false;
        }
        toolbar.animate().cancel();
        chapterNav.animate().cancel();
        settingsPanel.animate().cancel();

        View bottomPanel = wasSettingsPanelVisible ? settingsPanel : chapterNav;
        int bottomOffset = bottomPanel == settingsPanel ? dp(260) : dp(132);
        if (!animate) {
            int visibility = visible ? View.VISIBLE : View.GONE;
            toolbar.setVisibility(visibility);
            chapterNav.setVisibility(visible && !settingsPanelVisible ? View.VISIBLE : View.GONE);
            settingsPanel.setVisibility(visible && settingsPanelVisible ? View.VISIBLE : View.GONE);
            pageIndicator.setVisibility(visible && isPagedMode() ? View.VISIBLE : View.GONE);
            toolbar.setAlpha(1f);
            chapterNav.setAlpha(1f);
            settingsPanel.setAlpha(1f);
            toolbar.setTranslationY(0f);
            chapterNav.setTranslationY(0f);
            settingsPanel.setTranslationY(0f);
            return;
        }
        DecelerateInterpolator interpolator = new DecelerateInterpolator(1.7f);
        if (visible) {
            toolbar.setVisibility(View.VISIBLE);
            chapterNav.setVisibility(!settingsPanelVisible ? View.VISIBLE : View.GONE);
            settingsPanel.setVisibility(settingsPanelVisible ? View.VISIBLE : View.GONE);
            pageIndicator.setVisibility(isPagedMode() ? View.VISIBLE : View.GONE);
            bottomPanel = settingsPanelVisible ? settingsPanel : chapterNav;
            bottomPanel.setAlpha(0f);
            bottomPanel.setTranslationY(bottomOffset);
            toolbar.setAlpha(0f);
            toolbar.setTranslationY(-dp(48));
            toolbar.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240)
                    .setInterpolator(interpolator)
                    .start();
            bottomPanel.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(260)
                    .setInterpolator(interpolator)
                    .start();
        } else {
            pageIndicator.setVisibility(View.GONE);
            final View panelToHide = bottomPanel;
            toolbar.animate()
                    .alpha(0f)
                    .translationY(-dp(48))
                    .setDuration(220)
                    .setInterpolator(interpolator)
                    .withEndAction(() -> {
                        toolbar.setVisibility(View.GONE);
                        toolbar.setAlpha(1f);
                        toolbar.setTranslationY(0f);
                    })
                    .start();
            panelToHide.animate()
                    .alpha(0f)
                    .translationY(bottomOffset)
                    .setDuration(220)
                    .setInterpolator(interpolator)
                    .withEndAction(() -> {
                        chapterNav.setVisibility(View.GONE);
                        settingsPanel.setVisibility(View.GONE);
                        chapterNav.setAlpha(1f);
                        settingsPanel.setAlpha(1f);
                        chapterNav.setTranslationY(0f);
                        settingsPanel.setTranslationY(0f);
                    })
                    .start();
        }
    }

    private void saveProgress() {
        if (book == null) {
            return;
        }
        if (isPagedMode()) {
            book.scrollY = 0;
        } else {
            book.scrollY = scrollView == null ? book.scrollY : scrollView.getScrollY();
        }
        book.updatedAt = System.currentTimeMillis();
        BookEntity snapshot = book;
        executor.execute(() -> database.bookDao().update(snapshot));
    }

    private void saveReadingSessionTime() {
        if (readingSessionStartMillis <= 0L || readingStatsPrefs == null) {
            return;
        }
        long elapsed = Math.max(0L, System.currentTimeMillis() - readingSessionStartMillis);
        if (elapsed > 0L) {
            long total = readingStatsPrefs.getLong(KEY_TOTAL_READING_MILLIS, 0L) + elapsed;
            readingStatsPrefs.edit().putLong(KEY_TOTAL_READING_MILLIS, total).apply();
            saveDailyReading(elapsed);
        }
        readingSessionStartMillis = 0L;
    }

    private void saveDailyReading(long elapsed) {
        executor.execute(() -> {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            DailyReadingEntity entity = database.dailyReadingDao().getByDate(today);
            if (entity == null) {
                entity = new DailyReadingEntity();
                entity.date = today;
            }
            entity.readingMillis += elapsed;
            database.dailyReadingDao().save(entity);
        });
    }

    private void showCatalog() {
        setReaderControlsVisible(false, true);
        refreshDrawerLists();
        showDrawerPage(DRAWER_CATALOG);
        readerDrawer.openDrawer(GravityCompat.START);
    }

    private void showDrawerPage(int page) {
        currentDrawerPage = page;
        boolean bookmarksPage = page == DRAWER_BOOKMARKS;
        boolean notesPage = page == DRAWER_NOTES;
        boolean catalogPage = page == DRAWER_CATALOG;
        bookmarksRecyclerView.setVisibility(bookmarksPage && !bookmarks.isEmpty() ? View.VISIBLE : View.GONE);
        notesRecyclerView.setVisibility(notesPage && !notes.isEmpty() ? View.VISIBLE : View.GONE);
        emptyBookmarksView.setVisibility((bookmarksPage && bookmarks.isEmpty()) || (notesPage && notes.isEmpty()) ? View.VISIBLE : View.GONE);
        catalogRecyclerView.setVisibility(catalogPage ? View.VISIBLE : View.GONE);
        bookmarksTab.setTextColor(bookmarksPage ? 0xFFB95A52 : drawerNormalTextColor);
        catalogTab.setTextColor(catalogPage ? 0xFFB95A52 : drawerNormalTextColor);
        notesTab.setTextColor(notesPage ? 0xFFB95A52 : drawerNormalTextColor);
        bookmarksTab.setTypeface(null, bookmarksPage ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        catalogTab.setTypeface(null, catalogPage ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        notesTab.setTypeface(null, notesPage ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        bookmarksIndicator.setVisibility(bookmarksPage ? View.VISIBLE : View.GONE);
        catalogIndicator.setVisibility(catalogPage ? View.VISIBLE : View.GONE);
        notesIndicator.setVisibility(notesPage ? View.VISIBLE : View.GONE);
    }

    private void refreshDrawerLists() {
        if (chaptersAdapter != null) {
            chaptersAdapter.submitList(chapters, book == null ? 0 : book.currentChapterIndex);
            catalogRecyclerView.scrollToPosition(book == null ? 0 : book.currentChapterIndex);
        }
        loadBookmarks();
        loadNotes();
    }

    private void loadBookmarks() {
        executor.execute(() -> {
            List<BookmarkEntity> loaded = database.bookmarkDao().getForBook(bookId);
            runOnUiThread(() -> {
                bookmarks = loaded;
                bookmarksAdapter.submitList(bookmarks);
                updateBookmarkButton();
                if (currentDrawerPage == DRAWER_BOOKMARKS) {
                    showDrawerPage(DRAWER_BOOKMARKS);
                }
            });
        });
    }

    private void loadNotes() {
        executor.execute(() -> {
            List<NoteEntity> loaded = database.noteDao().getForBook(bookId);
            runOnUiThread(() -> {
                notes = loaded;
                notesAdapter.submitList(notes);
                if (!TextUtils.isEmpty(currentDisplayText)) {
                    rerenderCurrentChapterKeepingOffset();
                }
                if (currentDrawerPage == DRAWER_NOTES) {
                    showDrawerPage(DRAWER_NOTES);
                }
            });
        });
    }

    private void showAddNoteDialog() {
        if (book == null || TextUtils.isEmpty(currentDisplayText)) {
            return;
        }
        int pageStart = isPagedMode() ? book.currentPageStartOffset : 0;
        String selectedText = buildBookmarkSummary(pageStart);
        showNoteEditorDialog(null, selectedText);
    }

    private void showNoteEditorDialog(NoteEntity editingNote, String selectedText) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = createNoteSheetContainer();
        renderNoteEditorSheet(dialog, content, editingNote, selectedText, true, false);
        dialog.setContentView(content);
        showBottomSheetDialog(dialog);
    }

    private LinearLayout createNoteSheetContainer() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFFFFFBF4);
        content.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        int padding = dp(22);
        content.setPadding(padding, padding, padding, padding);

        View handle = new View(this);
        handle.setBackgroundColor(0x338A7770);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(48), dp(4));
        handleParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(18));
        content.addView(handle, handleParams);
        return content;
    }

    private void renderNoteEditorSheet(Dialog dialog, LinearLayout content, NoteEntity editingNote, String selectedText, boolean dismissAfterSave, boolean animate) {
        content.removeAllViews();
        View handle = new View(this);
        handle.setBackgroundColor(0x338A7770);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(48), dp(4));
        handleParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(18));
        content.addView(handle, handleParams);

        TextView title = new TextView(this);
        title.setText(editingNote == null ? "添加笔记" : "编辑笔记");
        title.setTextColor(0xFF241F1B);
        title.setTextSize(22f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        TextView meta = new TextView(this);
        meta.setText("选择颜色只会影响原文下划线");
        meta.setTextColor(0xFF7A6F67);
        meta.setTextSize(14f);
        meta.setPadding(0, dp(4), 0, dp(16));
        content.addView(meta);

        TextView quote = new TextView(this);
        quote.setText(selectedText);
        quote.setTextColor(0xFF4E4540);
        quote.setTextSize(15f);
        quote.setLineSpacing(0f, 1.25f);
        quote.setPadding(dp(14), dp(12), dp(14), dp(12));
        quote.setBackground(roundedBackground(0xFFFFF5EA, 10, 0x1AB95A52, 1));
        LinearLayout.LayoutParams quoteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        quoteParams.setMargins(0, 0, 0, dp(14));
        content.addView(quote, quoteParams);

        EditText input = new EditText(this);
        input.setHint("写下想法，也可以留空只保存划线");
        input.setMinLines(4);
        input.setGravity(android.view.Gravity.TOP);
        input.setTextColor(0xFF222222);
        input.setHintTextColor(0xFF777777);
        input.setTextSize(16f);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(roundedBackground(0xFFFFFFFF, 10, 0x268A7770, 1));
        if (editingNote != null) {
            input.setText(editingNote.noteText);
            input.setSelection(input.getText().length());
        }
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(132)
        );
        content.addView(input, inputParams);

        int[] selectedColor = new int[]{editingNote == null ? NOTE_YELLOW : editingNote.color};
        LinearLayout colors = new LinearLayout(this);
        colors.setGravity(android.view.Gravity.CENTER_VERTICAL);
        colors.setPadding(0, dp(10), 0, dp(6));
        colors.addView(createColorChoice("黄", NOTE_YELLOW, selectedColor));
        colors.addView(createColorChoice("绿", NOTE_GREEN, selectedColor));
        colors.addView(createColorChoice("蓝", NOTE_BLUE, selectedColor));
        colors.addView(createColorChoice("红", NOTE_RED, selectedColor));
        content.addView(colors);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        TextView cancel = createNoteDialogButton("取消");
        TextView save = createNoteDialogButton("保存");
        cancel.setOnClickListener(v -> {
            if (editingNote != null && !dismissAfterSave) {
                renderNoteDetailSheet(dialog, content, editingNote, true);
            } else {
                dialog.dismiss();
            }
        });
        save.setOnClickListener(v -> {
            if (editingNote == null) {
                dialog.dismiss();
                saveNote(selectedText, input.getText().toString().trim(), selectedColor[0]);
            } else {
                updateNote(editingNote, input.getText().toString().trim(), selectedColor[0], () -> {
                    if (dismissAfterSave) {
                        dialog.dismiss();
                    } else {
                        renderNoteDetailSheet(dialog, content, editingNote, true);
                    }
                });
            }
        });
        actions.addView(cancel);
        actions.addView(save);
        content.addView(actions);
        if (animate) {
            animateNoteSheetContent(content);
        }
    }

    private TextView createColorChoice(String text, int color, int[] selectedColor) {
        TextView choice = createNoteDialogButton(text);
        choice.setTextColor(0xFF111111);
        choice.setBackground(roundedBackground(color, 8, 0x22000000, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(36));
        params.setMargins(0, 0, dp(8), 0);
        choice.setLayoutParams(params);
        choice.setOnClickListener(v -> {
            selectedColor[0] = color;
            Toast.makeText(this, "已选择" + text + "色", Toast.LENGTH_SHORT).show();
        });
        return choice;
    }

    private void showNoteActionDialog(NoteEntity note) {
        Dialog dialog = new AnimatedDialog();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        content.setPadding(padding, padding, padding, padding);
        TextView title = new TextView(this);
        title.setText("笔记操作");
        title.setTextColor(0xFF111111);
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8));
        content.addView(title);
        content.addView(createNoteActionButton("编辑", v -> {
            dialog.dismiss();
            showNoteEditorDialog(note, note.selectedText);
        }));
        content.addView(createNoteActionButton("复制", v -> {
            dialog.dismiss();
            copyNote(note);
        }));
        content.addView(createNoteActionButton("删除", v -> {
            dialog.dismiss();
            deleteNote(note);
        }));
        content.addView(createNoteActionButton("导出本书笔记", v -> {
            dialog.dismiss();
            exportNotes();
        }));
        dialog.setContentView(content);
        showAnimatedDialog(dialog);
    }

    private void showNoteBottomSheet(NoteEntity note) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = createNoteSheetContainer();
        renderNoteDetailSheet(dialog, content, note, false);
        dialog.setContentView(content);
        showBottomSheetDialog(dialog);
    }

    private void renderNoteDetailSheet(Dialog dialog, LinearLayout content, NoteEntity note, boolean animate) {
        content.removeAllViews();

        View handle = new View(this);
        handle.setBackgroundColor(0x338A7770);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(48), dp(4));
        handleParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(18));
        content.addView(handle, handleParams);

        TextView title = new TextView(this);
        title.setText("阅读笔记");
        title.setTextColor(0xFF241F1B);
        title.setTextSize(22f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(title);

        TextView meta = new TextView(this);
        meta.setText((note.chapterTitle == null ? "当前章节" : note.chapterTitle) + " · 第 " + (note.pageIndex + 1) + " 页");
        meta.setTextColor(0xFF7A6F67);
        meta.setTextSize(14f);
        meta.setPadding(0, dp(4), 0, dp(16));
        content.addView(meta);

        TextView quote = new TextView(this);
        quote.setText(note.selectedText == null ? "" : note.selectedText);
        quote.setTextColor(0xFF4E4540);
        quote.setTextSize(15f);
        quote.setLineSpacing(0f, 1.25f);
        quote.setPadding(dp(14), dp(12), dp(14), dp(12));
        quote.setBackground(roundedBackground(0xFFFFF5EA, 10, 0x1AB95A52, 1));
        LinearLayout.LayoutParams quoteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        quoteParams.setMargins(0, 0, 0, dp(18));
        content.addView(quote, quoteParams);

        TextView noteTitle = new TextView(this);
        noteTitle.setText("我的笔记");
        noteTitle.setTextColor(0xFFB95A52);
        noteTitle.setTextSize(15f);
        noteTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        noteTitle.setPadding(0, 0, 0, dp(8));
        content.addView(noteTitle);

        TextView noteContent = new TextView(this);
        noteContent.setText(TextUtils.isEmpty(note.noteText) ? "没有写笔记内容" : note.noteText);
        noteContent.setTextColor(0xFF222222);
        noteContent.setTextSize(18f);
        noteContent.setLineSpacing(0f, 1.35f);
        noteContent.setPadding(dp(14), dp(12), dp(14), dp(12));
        noteContent.setBackground(roundedBackground(0xFFFFFFFF, 10, 0x168A7770, 1));
        content.addView(noteContent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        TextView edit = createNoteDialogButton("编辑");
        edit.setOnClickListener(v -> renderNoteEditorSheet(dialog, content, note, note.selectedText, false, true));
        TextView close = createNoteDialogButton("关闭");
        close.setOnClickListener(v -> dialog.dismiss());
        actions.addView(edit);
        actions.addView(close);
        content.addView(actions);
        if (animate) {
            animateNoteSheetContent(content);
        }
    }

    private GradientDrawable roundedBackground(int color, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private void animateNoteSheetContent(View content) {
        content.setAlpha(0f);
        content.setTranslationY(dp(18));
        content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private TextView createNoteActionButton(String text, View.OnClickListener listener) {
        TextView option = new TextView(this);
        option.setText(text);
        option.setTextColor(0xFF111111);
        option.setTextSize(17f);
        option.setGravity(android.view.Gravity.CENTER_VERTICAL);
        option.setPadding(dp(8), 0, dp(8), 0);
        option.setMinHeight(dp(48));
        option.setOnClickListener(listener);
        return option;
    }

    private void showAnimatedDialog(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        View decorView = window.getDecorView();
        decorView.setAlpha(0f);
        decorView.setScaleX(0.9f);
        decorView.setScaleY(0.9f);
        decorView.setTranslationY(dp(10));
        decorView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(160)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    private void showBottomSheetDialog(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        window.setGravity(android.view.Gravity.BOTTOM);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.75f);
        window.setAttributes(params);
        View decorView = window.getDecorView();
        decorView.setAlpha(0f);
        decorView.setTranslationY(params.height);
        decorView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    private void showCelebrationDialog(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.copyFrom(window.getAttributes());
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.82f);
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        View decorView = window.getDecorView();
        decorView.setAlpha(0f);
        decorView.setScaleX(0.78f);
        decorView.setScaleY(0.78f);
        decorView.setTranslationY(dp(22));
        decorView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator(2.2f))
                .start();
    }

    private TextView createNoteDialogButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(0xFFB95A52);
        button.setTextSize(16f);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(android.view.Gravity.CENTER);
        button.setMinWidth(dp(72));
        button.setMinHeight(dp(44));
        return button;
    }

    private void saveNote(String selectedText, String noteText, int color) {
        if (book == null) {
            return;
        }
        int start = isPagedMode() ? book.currentPageStartOffset : 0;
        int end = Math.min(currentDisplayText.length(), start + (selectedText == null ? 0 : selectedText.length()));
        saveNoteAtRange(start, end, noteText, color);
    }

    private void saveNoteAtRange(int startOffset, int endOffset, String noteText, int color) {
        if (book == null) {
            return;
        }
        int safeStart = Math.max(0, Math.min(currentDisplayText.length(), startOffset));
        int safeEnd = Math.max(safeStart, Math.min(currentDisplayText.length(), endOffset));
        if (safeEnd <= safeStart) {
            return;
        }
        String selectedText = currentDisplayText.substring(safeStart, safeEnd);
        NoteEntity note = new NoteEntity();
        note.bookId = bookId;
        note.chapterIndex = book.currentChapterIndex;
        note.pageIndex = isPagedMode() && currentPages != null && !currentPages.isEmpty()
                ? TextPaginator.findPageByOffset(currentPages, safeStart)
                : 0;
        note.pageStartOffset = safeStart;
        note.chapterTitle = currentChapterTitle;
        note.selectedText = selectedText;
        note.noteText = noteText;
        note.color = color;
        note.createdAt = System.currentTimeMillis();
        executor.execute(() -> {
            database.noteDao().insert(note);
            runOnUiThread(() -> {
                Toast.makeText(this, TextUtils.isEmpty(noteText) ? "\u5df2\u6dfb\u52a0\u6807\u8bb0" : "\u5df2\u53d1\u5e03\u8bc4\u8bba", Toast.LENGTH_SHORT).show();
                loadNotes();
            });
        });
    }

    private void updateNote(NoteEntity note, String noteText, int color) {
        updateNote(note, noteText, color, null);
    }

    private void updateNote(NoteEntity note, String noteText, int color, Runnable onDone) {
        note.noteText = noteText;
        note.color = color;
        executor.execute(() -> {
            database.noteDao().update(note);
            runOnUiThread(() -> {
                Toast.makeText(this, "已更新笔记", Toast.LENGTH_SHORT).show();
                loadNotes();
                if (onDone != null) {
                    onDone.run();
                }
            });
        });
    }

    private void deleteNote(NoteEntity note) {
        executor.execute(() -> {
            database.noteDao().delete(note);
            runOnUiThread(() -> {
                Toast.makeText(this, "已删除笔记", Toast.LENGTH_SHORT).show();
                loadNotes();
            });
        });
    }

    private void copyNote(NoteEntity note) {
        String text = (note.selectedText == null ? "" : note.selectedText)
                + (TextUtils.isEmpty(note.noteText) ? "" : "\n" + note.noteText);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("阅读笔记", text.trim()));
            Toast.makeText(this, "已复制笔记", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportNotes() {
        executor.execute(() -> {
            List<NoteEntity> loaded = database.noteDao().getForBook(bookId);
            String markdown = buildNotesMarkdown(loaded);
            pendingNotesExportText = markdown;
            runOnUiThread(() -> notesExportLauncher.launch((book == null ? "阅读笔记" : book.title) + "-笔记.md"));
        });
    }

    private String buildNotesMarkdown(List<NoteEntity> source) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(book == null ? "阅读笔记" : book.title).append(" 笔记\n\n");
        int lastChapter = Integer.MIN_VALUE;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        for (NoteEntity note : source) {
            if (note.chapterIndex != lastChapter) {
                builder.append("## ").append(TextUtils.isEmpty(note.chapterTitle) ? "第 " + (note.chapterIndex + 1) + " 章" : note.chapterTitle).append("\n\n");
                lastChapter = note.chapterIndex;
            }
            builder.append("- 时间：").append(format.format(new Date(note.createdAt))).append("\n");
            builder.append("  摘录：").append(note.selectedText == null ? "" : note.selectedText.replace("\n", " ")).append("\n");
            if (!TextUtils.isEmpty(note.noteText)) {
                builder.append("  笔记：").append(note.noteText.replace("\n", " ")).append("\n");
            }
            builder.append("\n");
        }
        if (source.isEmpty()) {
            builder.append("暂无笔记\n");
        }
        return builder.toString();
    }

    private void writeNotesExportToUri(Uri uri) {
        if (uri == null || pendingNotesExportText == null) {
            return;
        }
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                throw new IllegalArgumentException("无法创建导出文件");
            }
            output.write(pendingNotesExportText.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "笔记已导出", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingNotesExportText = null;
        }
    }

    private void toggleBookmark() {
        if (book == null) {
            return;
        }
        int chapterIndex = book.currentChapterIndex;
        int pageStart = isPagedMode() ? book.currentPageStartOffset : 0;
        int pageIndex = isPagedMode() ? book.currentPageIndex : 0;
        executor.execute(() -> {
            BookmarkEntity existing = database.bookmarkDao().getAtPage(bookId, chapterIndex, pageStart);
            if (existing == null) {
                BookmarkEntity bookmark = new BookmarkEntity();
                bookmark.bookId = bookId;
                bookmark.chapterIndex = chapterIndex;
                bookmark.pageIndex = pageIndex;
                bookmark.pageStartOffset = pageStart;
                bookmark.chapterTitle = currentChapterTitle;
                bookmark.summary = buildBookmarkSummary(pageStart);
                bookmark.createdAt = System.currentTimeMillis();
                database.bookmarkDao().insert(bookmark);
                runOnUiThread(() -> Toast.makeText(this, "已添加书签", Toast.LENGTH_SHORT).show());
            } else {
                database.bookmarkDao().delete(existing);
                runOnUiThread(() -> Toast.makeText(this, "已取消书签", Toast.LENGTH_SHORT).show());
            }
            loadBookmarks();
        });
    }

    private String buildBookmarkSummary(int startOffset) {
        if (TextUtils.isEmpty(currentDisplayText)) {
            return "";
        }
        int start = Math.max(0, Math.min(startOffset, currentDisplayText.length()));
        int end = Math.min(currentDisplayText.length(), start + 80);
        return currentDisplayText.substring(start, end).replace('\n', ' ').trim();
    }

    private void updateBookmarkButton() {
        if (bookmarkButton == null || book == null) {
            return;
        }
        int pageStart = isPagedMode() ? book.currentPageStartOffset : 0;
        boolean bookmarked = false;
        for (BookmarkEntity bookmark : bookmarks) {
            if (bookmark.chapterIndex == book.currentChapterIndex && bookmark.pageStartOffset == pageStart) {
                bookmarked = true;
                break;
            }
        }
        bookmarkButton.setText(bookmarked ? "已收藏" : "收藏");
        bookmarkButton.setTextColor(bookmarked ? 0xFFF0ECE6 : 0xFFD8D0C8);
    }

    private void updateNovelProgress() {
        if (novelProgressBar == null || book == null || chapters.isEmpty()) {
            return;
        }
        float progress = (book.currentChapterIndex + 1f) / (float) chapters.size();
        novelProgressBar.setProgress(Math.max(0, Math.min(10000, Math.round(progress * 10000f))));
    }

    private void updateReaderStatus() {
        if (timeView != null) {
            timeView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        }
        if (batteryView != null) {
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                batteryView.setText(Math.round(level * 100f / scale) + "%");
            } else {
                batteryView.setText("--%");
            }
        }
    }

    private void showInlineSettingsPanel() {
        if (settings == null) {
            return;
        }
        updateSettingsPanelValues();
        settingsPanelVisible = true;
        readerControlsVisible = true;
        toolbar.setVisibility(View.VISIBLE);
        chapterNav.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.VISIBLE);
        pageIndicator.setVisibility(isPagedMode() ? View.VISIBLE : View.GONE);
        toolbar.animate().cancel();
        settingsPanel.animate().cancel();
        settingsPanel.setAlpha(0f);
        settingsPanel.setTranslationY(settingsPanel.getHeight() == 0 ? dp(360) : settingsPanel.getHeight());
        settingsPanel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator(1.7f))
                .start();
    }

    private void hideInlineSettingsPanel() {
        saveSettings();
        setChapterSplitOptionsVisible(false, false);
        settingsPanelVisible = false;
        settingsPanel.animate().cancel();
        settingsPanel.animate()
                .translationY(settingsPanel.getHeight())
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.7f))
                .withEndAction(() -> {
                    settingsPanel.setVisibility(View.GONE);
                    settingsPanel.setAlpha(1f);
                    settingsPanel.setTranslationY(0f);
                    if (readerControlsVisible) {
                        chapterNav.setVisibility(View.VISIBLE);
                    }
                })
                .start();
    }

    private void updateSettingsPanelValues() {
        if (settings == null) {
            return;
        }
        textSizeLabel.setText("字号：" + Math.round(settings.textSizeSp));
        textSizeSeekBar.setProgress(Math.max(0, Math.min(16, Math.round(settings.textSizeSp) - 14)));
        lineSpacingLabel.setText("行距：" + String.format(Locale.US, "%.1f", settings.lineSpacingMultiplier));
        lineSpacingSeekBar.setProgress(Math.max(0, Math.min(10, Math.round((settings.lineSpacingMultiplier - 1.2f) * 10f))));
        paragraphSpacingLabel.setText("段距：" + Math.round(settings.paragraphSpacingDp));
        paragraphSpacingSeekBar.setProgress(Math.max(0, Math.min(12, Math.round(settings.paragraphSpacingDp))));
        firstLineIndentLabel.setText("首行缩进：" + Math.round(settings.firstLineIndentEm) + "字");
        firstLineIndentSeekBar.setProgress(Math.max(0, Math.min(4, Math.round(settings.firstLineIndentEm))));
        pageMarginLabel.setText("页边距：" + settings.pageMarginDp);
        pageMarginSeekBar.setProgress(Math.max(0, Math.min(28, settings.pageMarginDp - 12)));
        boolean txtBook = book != null && "txt".equalsIgnoreCase(book.fileType);
        chapterSplitLabel.setText(txtBook ? "选择分章关键词" : "选择分章关键词（仅 TXT）");
        chapterSplitButton.setText(txtBook ? TxtParser.labelForMode(book.chapterSplitMode) : "EPUB 使用内置目录");
        chapterSplitButton.setEnabled(txtBook);
        chapterSplitButton.setAlpha(txtBook ? 1f : 0.45f);
        updateChapterSplitOptionStyles();
        if (!txtBook) {
            setChapterSplitOptionsVisible(false, false);
        }
        int selectedColor = 0xFF8A7770;
        int normalColor = 0xFF5F5A5A;
        pagedModeButton.setBackgroundColor(settings.readMode == MODE_PAGED ? selectedColor : normalColor);
        scrollModeButton.setBackgroundColor(settings.readMode == MODE_SCROLL ? selectedColor : normalColor);
        lightThemeButton.setBackgroundColor(settings.themeMode == 0 ? selectedColor : normalColor);
        darkThemeButton.setBackgroundColor(settings.themeMode == 1 ? selectedColor : normalColor);
        eyeThemeButton.setBackgroundColor(settings.themeMode == 2 ? selectedColor : normalColor);
        paperThemeButton.setBackgroundColor(settings.themeMode == 3 ? selectedColor : normalColor);
        grayThemeButton.setBackgroundColor(settings.themeMode == 4 ? selectedColor : normalColor);
        greenThemeButton.setBackgroundColor(settings.themeMode == 5 ? selectedColor : normalColor);
        systemFontButton.setBackgroundColor(settings.fontMode == 0 ? selectedColor : normalColor);
        serifFontButton.setBackgroundColor(settings.fontMode == 1 ? selectedColor : normalColor);
        monoFontButton.setBackgroundColor(settings.fontMode == 2 ? selectedColor : normalColor);
        slideTurnButton.setBackgroundColor(settings.pageTurnMode == 0 ? selectedColor : normalColor);
        simulatedTurnButton.setBackgroundColor(settings.pageTurnMode == 1 ? selectedColor : normalColor);
    }

    private void toggleChapterSplitOptions() {
        if (book == null || !"txt".equalsIgnoreCase(book.fileType)) {
            Toast.makeText(this, "只有 TXT 书籍可以选择分章关键词", Toast.LENGTH_SHORT).show();
            return;
        }
        setChapterSplitOptionsVisible(!chapterSplitOptionsVisible, true);
    }

    private void selectChapterSplitMode(int mode) {
        setChapterSplitOptionsVisible(false, true);
        changeChapterSplitMode(mode);
    }

    private void setChapterSplitOptionsVisible(boolean visible, boolean animate) {
        if (chapterSplitOptions == null || chapterSplitOptionsVisible == visible) {
            return;
        }
        chapterSplitOptionsVisible = visible;
        chapterSplitOptions.animate().cancel();
        if (visible) {
            updateChapterSplitOptionStyles();
            positionChapterSplitOptions();
            chapterSplitOptions.setVisibility(View.VISIBLE);
            if (animate) {
                chapterSplitOptions.setAlpha(0f);
                chapterSplitOptions.setTranslationY(dp(14));
                chapterSplitOptions.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(180)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .start();
            } else {
                chapterSplitOptions.setAlpha(1f);
                chapterSplitOptions.setTranslationY(0f);
            }
        } else if (animate) {
            chapterSplitOptions.animate()
                    .alpha(0f)
                    .translationY(dp(14))
                    .setDuration(140)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .withEndAction(() -> {
                        chapterSplitOptions.setVisibility(View.GONE);
                        chapterSplitOptions.setAlpha(1f);
                        chapterSplitOptions.setTranslationY(0f);
                    })
                    .start();
        } else {
            chapterSplitOptions.setVisibility(View.GONE);
            chapterSplitOptions.setAlpha(1f);
            chapterSplitOptions.setTranslationY(0f);
        }
    }

    private void positionChapterSplitOptions() {
        if (root == null || chapterSplitButton == null || chapterSplitOptions == null) {
            return;
        }
        int rootWidth = root.getWidth();
        int rootHeight = root.getHeight();
        int buttonWidth = chapterSplitButton.getWidth();
        if (rootWidth == 0 || rootHeight == 0 || buttonWidth == 0) {
            return;
        }

        int[] rootLocation = new int[2];
        int[] buttonLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        chapterSplitButton.getLocationInWindow(buttonLocation);

        int buttonLeft = Math.max(0, buttonLocation[0] - rootLocation[0]);
        int buttonTop = Math.max(0, buttonLocation[1] - rootLocation[1]);
        int rightMargin = Math.max(0, rootWidth - buttonLeft - buttonWidth);
        int bottomMargin = Math.max(0, rootHeight - buttonTop);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) chapterSplitOptions.getLayoutParams();
        params.gravity = android.view.Gravity.BOTTOM;
        params.width = buttonWidth;
        params.leftMargin = buttonLeft;
        params.rightMargin = rightMargin;
        params.bottomMargin = bottomMargin;
        chapterSplitOptions.setLayoutParams(params);
    }

    private void updateChapterSplitOptionStyles() {
        if (book == null) {
            return;
        }
        updateChapterSplitOption(chapterSplitDefaultOption, TxtParser.SPLIT_DEFAULT);
        updateChapterSplitOption(chapterSplitChineseOption, TxtParser.SPLIT_CHINESE_CHAPTER);
        updateChapterSplitOption(chapterSplitArabicOption, TxtParser.SPLIT_ARABIC_CHAPTER);
        updateChapterSplitOption(chapterSplitMarkOption, TxtParser.SPLIT_ARABIC_MARK);
        updateChapterSplitOption(chapterSplitChapterOption, TxtParser.SPLIT_CHAPTER_ARABIC);
        updateChapterSplitOption(chapterSplitSectionOption, TxtParser.SPLIT_CHINESE_SECTION);
        updateChapterSplitOption(chapterSplitVolumeOption, TxtParser.SPLIT_CHINESE_VOLUME);
        updateChapterSplitOption(chapterSplitPrologueOption, TxtParser.SPLIT_PROLOGUE);
    }

    private void updateChapterSplitOption(TextView option, int mode) {
        if (option == null) {
            return;
        }
        boolean selected = book.chapterSplitMode == mode;
        option.setText((selected ? "✓ " : "") + TxtParser.labelForMode(mode));
        option.setBackgroundColor(selected ? 0xFF8A7770 : 0xFF3E3936);
        option.setTextColor(selected ? 0xFFFFFFFF : 0xFFF0ECE6);
    }

    private void changeChapterSplitMode(int mode) {
        if (book == null || mode == book.chapterSplitMode) {
            return;
        }
        Toast.makeText(this, "正在重新分章...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                ParsedBook parsedBook = new TxtParser().parse(new File(book.originalFilePath), book.title, mode);
                List<File> oldChapterDirs = new ArrayList<>();
                for (ChapterEntity chapter : chapters) {
                    if (chapter.contentPath != null) {
                        File parent = new File(chapter.contentPath).getParentFile();
                        if (parent != null && !oldChapterDirs.contains(parent)) {
                            oldChapterDirs.add(parent);
                        }
                    }
                }
                File chaptersDir = new File(book.storageDirPath, "chapters_" + System.currentTimeMillis());
                ensureDir(chaptersDir);

                List<ChapterEntity> newChapters = new ArrayList<>();
                for (int i = 0; i < parsedBook.chapters.size(); i++) {
                    ParsedChapter parsedChapter = parsedBook.chapters.get(i);
                    File chapterFile = new File(chaptersDir, String.format(Locale.US, "%04d.txt", i));
                    writeText(chapterFile, parsedChapter.content);

                    ChapterEntity chapter = new ChapterEntity();
                    chapter.bookId = bookId;
                    chapter.chapterIndex = i;
                    chapter.title = parsedChapter.title;
                    chapter.contentPath = chapterFile.getAbsolutePath();
                    newChapters.add(chapter);
                }

                database.bookmarkDao().deleteForBook(bookId);
                database.chapterDao().deleteForBook(bookId);
                database.chapterDao().insertAll(newChapters);
                for (File oldChapterDir : oldChapterDirs) {
                    deleteRecursively(oldChapterDir);
                }

                book.chapterSplitMode = mode;
                book.totalChapters = newChapters.size();
                book.currentChapterIndex = 0;
                book.scrollY = 0;
                book.currentPageIndex = 0;
                book.currentPageStartOffset = 0;
                book.updatedAt = System.currentTimeMillis();
                database.bookDao().update(book);

                chapters = database.chapterDao().getForBook(bookId);
                bookmarks = new ArrayList<>();
                runOnUiThread(() -> {
                    updateSettingsPanelValues();
                    refreshDrawerLists();
                    openChapter(0, 0, 0);
                    Toast.makeText(this, "已按“" + TxtParser.labelForMode(mode) + "”重新分章", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "重新分章失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void switchTheme(int themeMode) {
        settings.themeMode = themeMode;
        updateSettingsPanelValues();
        applyReaderSettings();
        saveSettings();
    }

    private void switchReadMode(int mode) {
        settings.readMode = mode;
        updateSettingsPanelValues();
        saveSettings();
        if (mode == MODE_PAGED) {
            renderPagedChapter(book.currentPageStartOffset);
        } else {
            renderScrollChapter(book.scrollY);
        }
    }

    private void switchFontMode(int mode) {
        settings.fontMode = mode;
        updateSettingsPanelValues();
        applyReaderSettings();
        rerenderCurrentChapterKeepingOffset();
        saveSettings();
    }

    private void switchPageTurnMode(int mode) {
        settings.pageTurnMode = mode;
        updateSettingsPanelValues();
        saveSettings();
    }

    private void rerenderCurrentChapterKeepingOffset() {
        if (TextUtils.isEmpty(currentDisplayText)) {
            return;
        }
        if (isPagedMode()) {
            renderPagedChapter(book.currentPageStartOffset);
        } else {
            renderScrollChapter(scrollView.getScrollY());
        }
    }

    private void applyReaderSettings() {
        if (settings == null) {
            return;
        }
        int background;
        int text;
        switch (settings.themeMode) {
            case 1:
                background = ContextCompat.getColor(this, R.color.reader_bg_dark);
                text = ContextCompat.getColor(this, R.color.reader_text_dark);
                break;
            case 2:
                background = ContextCompat.getColor(this, R.color.reader_bg_eye);
                text = ContextCompat.getColor(this, R.color.reader_text_eye);
                break;
            case 3:
                background = ContextCompat.getColor(this, R.color.reader_bg_paper);
                text = ContextCompat.getColor(this, R.color.reader_text_paper);
                break;
            case 4:
                background = ContextCompat.getColor(this, R.color.reader_bg_gray);
                text = ContextCompat.getColor(this, R.color.reader_text_gray);
                break;
            case 5:
                background = ContextCompat.getColor(this, R.color.reader_bg_green);
                text = ContextCompat.getColor(this, R.color.reader_text_green);
                break;
            default:
                background = ContextCompat.getColor(this, R.color.reader_bg_light);
                text = ContextCompat.getColor(this, R.color.reader_text_light);
                break;
        }
        root.setBackgroundColor(background);
        drawerPanel.setBackgroundColor(background);
        drawerNormalTextColor = text;
        titleView.setTextColor(0xFFF0ECE6);
        contentView.setTextColor(text);
        pageTransitionView.setTextColor(text);
        pageIndicator.setTextColor(0xFFD8D0C8);
        timeView.setTextColor(text);
        batteryView.setTextColor(text);
        drawerBookTitle.setTextColor(text);
        if (chaptersAdapter != null) {
            chaptersAdapter.setNormalTextColor(text);
        }
        Typeface typeface;
        if (settings.fontMode == 1) {
            typeface = Typeface.SERIF;
        } else if (settings.fontMode == 2) {
            typeface = Typeface.MONOSPACE;
        } else {
            typeface = Typeface.DEFAULT;
        }
        int horizontalPadding = dp(settings.pageMarginDp);
        int topPadding = dp(Math.max(12, settings.pageMarginDp));
        int bottomPadding = dp(Math.max(48, settings.pageMarginDp + 26));
        contentView.setTypeface(typeface);
        contentView.setTextSize(settings.textSizeSp);
        contentView.setLineSpacing(dp(Math.round(settings.paragraphSpacingDp)), settings.lineSpacingMultiplier);
        contentView.setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding);
        pageTransitionView.setTypeface(typeface);
        pageTransitionView.setTextSize(settings.textSizeSp);
        pageTransitionView.setLineSpacing(dp(Math.round(settings.paragraphSpacingDp)), settings.lineSpacingMultiplier);
        pageTransitionView.setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding);
        previousButton.setTextColor(0xFFD8D0C8);
        nextButton.setTextColor(0xFFD8D0C8);
    }

    private void saveSettings() {
        if (settings == null) {
            return;
        }
        ReaderSettingsEntity snapshot = settings;
        executor.execute(() -> database.readerSettingsDao().save(snapshot));
    }

    private boolean isPagedMode() {
        return settings == null || settings.readMode == MODE_PAGED;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int normalizeUnderlineColor(int color) {
        if (color == 0x66FFE08A || color == NOTE_YELLOW) {
            return 0xFFFFB300;
        }
        if (color == 0x667ED68A || color == NOTE_GREEN) {
            return 0xFF20A35B;
        }
        if (color == 0x667AB7FF || color == NOTE_BLUE) {
            return 0xFF1976D2;
        }
        if (color == 0x66FF8A8A || color == NOTE_RED) {
            return 0xFFE53935;
        }
        if (color == NOTE_PINK) {
            return 0xFFE85DA0;
        }
        return color == 0 ? 0xFFFFB300 : color;
    }

    private static class ColoredUnderlineSpan extends CharacterStyle implements UpdateAppearance {
        private final int color;

        ColoredUnderlineSpan(int color) {
            this.color = color;
        }

        @Override
        public void updateDrawState(TextPaint textPaint) {
            textPaint.setUnderlineText(true);
            textPaint.underlineColor = color;
            textPaint.underlineThickness = Math.max(2.5f, textPaint.density * 2.5f);
        }
    }

    private static class CommentBubbleTarget {
        final int start;
        final int end;
        final String selectedText;
        int count;

        CommentBubbleTarget(int start, int end, String selectedText) {
            this.start = start;
            this.end = end;
            this.selectedText = selectedText;
        }
    }

    private boolean isReplyComment(NoteEntity note) {
        return note != null && note.noteText != null && note.noteText.startsWith(REPLY_PREFIX);
    }

    private long replyParentId(NoteEntity note) {
        if (!isReplyComment(note)) {
            return -1L;
        }
        int end = note.noteText.indexOf(REPLY_SEPARATOR, REPLY_PREFIX.length());
        if (end <= REPLY_PREFIX.length()) {
            return -1L;
        }
        try {
            return Long.parseLong(note.noteText.substring(REPLY_PREFIX.length(), end));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String displayCommentText(NoteEntity note) {
        if (note == null || note.noteText == null) {
            return "";
        }
        if (!isReplyComment(note)) {
            return note.noteText;
        }
        int end = note.noteText.indexOf(REPLY_SEPARATOR, REPLY_PREFIX.length());
        String content = end >= 0 && end + 1 < note.noteText.length()
                ? note.noteText.substring(end + 1)
                : "";
        return "\u56de\u590d \u9ed8\u8ba4\u7528\u6237\uff1a" + content;
    }

    private void showCommentActionDialog(NoteEntity comment, CommentsAdapter adapter) {
        Dialog dialog = new AnimatedDialog();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(14));
        content.setBackgroundColor(0xFFFFFFFF);

        TextView copy = createNoteActionButton("\u590d\u5236", v -> {
            dialog.dismiss();
            copyCommentText(comment);
        });
        TextView reply = createNoteActionButton("\u56de\u590d", v -> {
            dialog.dismiss();
            showReplyDialog(comment, adapter);
        });
        TextView delete = createNoteActionButton("\u5220\u9664", v -> {
            dialog.dismiss();
            deleteCommentTree(comment, adapter);
        });
        content.addView(copy);
        content.addView(reply);
        content.addView(delete);
        dialog.setContentView(content);
        showAnimatedDialog(dialog);
    }

    private void copyCommentText(NoteEntity comment) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("\u8bc4\u8bba", displayCommentText(comment)));
            Toast.makeText(this, "\u5df2\u590d\u5236\u8bc4\u8bba", Toast.LENGTH_SHORT).show();
        }
    }

    private void showReplyDialog(NoteEntity parent, CommentsAdapter adapter) {
        Dialog dialog = new AnimatedDialog();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(14));
        content.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText("\u56de\u590d \u9ed8\u8ba4\u7528\u6237");
        title.setTextColor(0xFF222222);
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title);

        EditText input = new EditText(this);
        input.setHint("\u5199\u4e0b\u4f60\u7684\u56de\u590d");
        input.setMinLines(3);
        input.setGravity(android.view.Gravity.TOP);
        input.setTextSize(16f);
        input.setBackground(roundedBackground(0xFFF7F7F7, 10, 0xFFE0E0E0, 1));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120));
        inputParams.setMargins(0, dp(14), 0, dp(10));
        content.addView(input, inputParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        TextView cancel = createNoteDialogButton("\u53d6\u6d88");
        TextView send = createNoteDialogButton("\u53d1\u5e03");
        cancel.setOnClickListener(v -> dialog.dismiss());
        send.setOnClickListener(v -> {
            String reply = input.getText().toString().trim();
            if (TextUtils.isEmpty(reply)) {
                return;
            }
            dialog.dismiss();
            saveReplyToComment(parent, reply, adapter);
        });
        actions.addView(cancel);
        actions.addView(send);
        content.addView(actions);
        dialog.setContentView(content);
        showAnimatedDialog(dialog);
    }

    private void deleteCommentTree(NoteEntity comment, CommentsAdapter adapter) {
        List<NoteEntity> deleting = new ArrayList<>();
        deleting.add(comment);
        for (NoteEntity item : new ArrayList<>(adapter.comments)) {
            if (replyParentId(item) == comment.id) {
                deleting.add(item);
            }
        }
        executor.execute(() -> {
            for (NoteEntity item : deleting) {
                database.noteDao().delete(item);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "\u5df2\u5220\u9664\u8bc4\u8bba", Toast.LENGTH_SHORT).show();
                adapter.removeCommentTree(comment);
                loadNotes();
            });
        });
    }

    private class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {
        private final List<NoteEntity> comments;
        private final List<CommentRow> rows = new ArrayList<>();
        private final Set<Long> expandedParents = new HashSet<>();
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        CommentsAdapter(List<NoteEntity> comments) {
            this.comments = comments;
            rebuildRows();
        }

        void addComment(NoteEntity comment) {
            comments.add(comment);
            comments.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
            rebuildRows();
            notifyDataSetChanged();
        }

        void expand(long parentId) {
            expandedParents.add(parentId);
            rebuildRows();
            notifyDataSetChanged();
        }

        private void rebuildRows() {
            rows.clear();
            for (NoteEntity comment : comments) {
                if (isReplyComment(comment)) {
                    continue;
                }
                rows.add(new CommentRow(comment, false, 0));
                List<NoteEntity> replies = repliesFor(comment.id);
                if (!replies.isEmpty()) {
                    if (expandedParents.contains(comment.id)) {
                        for (NoteEntity reply : replies) {
                            rows.add(new CommentRow(reply, false, 1));
                        }
                    } else {
                        rows.add(new CommentRow(comment, true, replies.size()));
                    }
                }
            }
        }

        @Override
        public CommentViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(16), 0, dp(12));
            row.setGravity(android.view.Gravity.TOP);

            TextView avatar = new TextView(parent.getContext());
            avatar.setText("\u9ed8");
            avatar.setTextColor(0xFFFFFFFF);
            avatar.setTextSize(15f);
            avatar.setGravity(android.view.Gravity.CENTER);
            avatar.setTypeface(null, Typeface.BOLD);
            avatar.setBackground(roundedBackground(0xFF8A9A90, 22, 0x00000000, 0));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout body = new LinearLayout(parent.getContext());
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(14), 0, 0, 0);
            row.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView name = new TextView(parent.getContext());
            name.setTextColor(0xFF222222);
            name.setTextSize(16f);
            name.setTypeface(null, Typeface.BOLD);
            body.addView(name);

            TextView content = new TextView(parent.getContext());
            content.setTextColor(0xFF333333);
            content.setTextSize(20f);
            content.setLineSpacing(0f, 1.25f);
            content.setPadding(0, dp(8), 0, dp(6));
            body.addView(content);

            TextView meta = new TextView(parent.getContext());
            meta.setTextColor(0xFF888888);
            meta.setTextSize(13f);
            body.addView(meta);

            TextView replies = new TextView(parent.getContext());
            replies.setTextColor(0xFF555555);
            replies.setTextSize(16f);
            replies.setTypeface(null, Typeface.BOLD);
            replies.setPadding(0, dp(10), 0, 0);
            body.addView(replies);
            return new CommentViewHolder(row, avatar, name, content, meta, replies);
        }

        @Override
        public void onBindViewHolder(CommentViewHolder holder, int position) {
            CommentRow row = rows.get(position);
            NoteEntity comment = row.comment;
            if (row.expandButton) {
                holder.avatar.setVisibility(View.INVISIBLE);
                holder.name.setVisibility(View.GONE);
                holder.content.setVisibility(View.GONE);
                holder.meta.setVisibility(View.GONE);
                holder.replies.setVisibility(View.VISIBLE);
                holder.replies.setText("\u67e5\u770b\u66f4\u591a" + row.replyCount + "\u6761\u56de\u590d \u2304");
                holder.itemView.setPadding(0, 0, 0, dp(8));
                holder.itemView.setOnClickListener(v -> expand(comment.id));
                holder.itemView.setOnLongClickListener(null);
                return;
            }
            holder.avatar.setVisibility(View.VISIBLE);
            holder.name.setVisibility(View.VISIBLE);
            holder.content.setVisibility(View.VISIBLE);
            holder.meta.setVisibility(View.VISIBLE);
            holder.replies.setVisibility(View.GONE);
            holder.itemView.setPadding(row.level == 0 ? 0 : dp(58), dp(16), 0, dp(12));
            holder.name.setText("\u9ed8\u8ba4\u7528\u6237");
            holder.content.setText(displayCommentText(comment));
            int floor = comments.indexOf(comment) + 1;
            holder.meta.setText(floor + "\u697c \u00b7 " + dateFormat.format(new Date(comment.createdAt)) + " \u00b7 \u56de\u590d");
            holder.itemView.setOnClickListener(null);
            holder.itemView.setOnLongClickListener(v -> {
                showCommentActionDialog(comment, this);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        private List<NoteEntity> repliesFor(long parentId) {
            List<NoteEntity> replies = new ArrayList<>();
            for (NoteEntity comment : comments) {
                if (replyParentId(comment) == parentId) {
                    replies.add(comment);
                }
            }
            replies.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
            return replies;
        }

        private void removeCommentTree(NoteEntity comment) {
            List<NoteEntity> removing = new ArrayList<>();
            removing.add(comment);
            for (NoteEntity item : comments) {
                if (replyParentId(item) == comment.id) {
                    removing.add(item);
                }
            }
            comments.removeAll(removing);
            rebuildRows();
            notifyDataSetChanged();
        }

        class CommentViewHolder extends RecyclerView.ViewHolder {
            final TextView avatar;
            final TextView name;
            final TextView content;
            final TextView meta;
            final TextView replies;

            CommentViewHolder(View itemView, TextView avatar, TextView name, TextView content, TextView meta, TextView replies) {
                super(itemView);
                this.avatar = avatar;
                this.name = name;
                this.content = content;
                this.meta = meta;
                this.replies = replies;
            }
        }
    }

    private static class CommentRow {
        final NoteEntity comment;
        final boolean expandButton;
        final int replyCount;
        final int level;

        CommentRow(NoteEntity comment, boolean expandButton, int replyCount) {
            this(comment, expandButton, replyCount, 0);
        }

        CommentRow(NoteEntity comment, boolean expandButton, int replyCount, int level) {
            this.comment = comment;
            this.expandButton = expandButton;
            this.replyCount = replyCount;
            this.level = level;
        }
    }

    private class AnimatedDialog extends Dialog {
        private boolean dismissing;

        AnimatedDialog() {
            super(ReaderActivity.this);
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
                    .translationY(dp(10))
                    .setDuration(130)
                    .setInterpolator(new DecelerateInterpolator(1.3f))
                    .withEndAction(this::dismissImmediately)
                    .start();
        }

        private void dismissImmediately() {
            super.dismiss();
        }
    }

    private static String readText(File file) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void writeText(File file, String text) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void ensureDir(File dir) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建目录：" + dir.getAbsolutePath());
        }
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

    private interface ProgressCallback {
        void onChanged(int progress);
    }

    private static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        private final ProgressCallback callback;

        SimpleSeekListener(ProgressCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                callback.onChanged(progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
