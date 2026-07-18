package com.wjnocal.novelreader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.wjnocal.novelreader.ui.ReaderActivity;

public class BrowserActivity extends AppCompatActivity {
    private static final String EXTRA_URL = "url";
    private static final String DEFAULT_HOME = "https://www.bing.com/search?q=%E5%B0%8F%E8%AF%B4";

    private WebView webView;
    private EditText addressInput;
    private ProgressBar progressBar;
    private TextView backButton;
    private TextView forwardButton;

    public static Intent createIntent(Context context, String url) {
        Intent intent = new Intent(context, BrowserActivity.class);
        intent.putExtra(EXTRA_URL, url);
        return intent;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_browser);
        configureSystemBars();
        applyTopSafeArea(findViewById(R.id.browserRoot));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) webView.goBack(); else finish();
            }
        });

        webView = findViewById(R.id.browserWebView);
        addressInput = findViewById(R.id.browserAddress);
        progressBar = findViewById(R.id.browserProgress);
        backButton = findViewById(R.id.browserBack);
        forwardButton = findViewById(R.id.browserForward);
        TextView refreshButton = findViewById(R.id.browserRefresh);
        TextView goButton = findViewById(R.id.browserGo);
        TextView onlineReaderButton = findViewById(R.id.onlineReaderBall);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(settings.getUserAgentString() + " NovelReader/OnlineBrowser");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    Toast.makeText(BrowserActivity.this, "无法打开这个链接", Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                addressInput.setText(url);
                updateNavigationButtons();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                addressInput.setText(url);
                updateNavigationButtons();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                setTitle(title);
            }
        });

        backButton.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack(); else finish();
        });
        forwardButton.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });
        refreshButton.setOnClickListener(v -> webView.reload());
        goButton.setOnClickListener(v -> loadAddress());
        addressInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadAddress();
                return true;
            }
            return false;
        });
        onlineReaderButton.setOnClickListener(v -> {
            String url = webView.getUrl();
            if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
                Toast.makeText(this, "当前页面不能进入在线阅读", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(ReaderActivity.createOnlineIntent(this, url));
        });

        if (savedInstanceState == null) {
            String initialUrl = getIntent().getStringExtra(EXTRA_URL);
            webView.loadUrl(normalizeAddress(initialUrl));
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void loadAddress() {
        webView.loadUrl(normalizeAddress(addressInput.getText().toString()));
    }

    private String normalizeAddress(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return DEFAULT_HOME;
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean;
        if (clean.contains(".") && !clean.contains(" ")) return "https://" + clean;
        return "https://www.bing.com/search?q=" + Uri.encode(clean);
    }

    private void updateNavigationButtons() {
        backButton.setEnabled(webView.canGoBack());
        forwardButton.setEnabled(webView.canGoForward());
        backButton.setAlpha(backButton.isEnabled() ? 1f : 0.35f);
        forwardButton.setAlpha(forwardButton.isEnabled() ? 1f : 0.35f);
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

    private void configureSystemBars() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView()
        );
        controller.show(WindowInsetsCompat.Type.statusBars());
        controller.hide(WindowInsetsCompat.Type.navigationBars());
        controller.setAppearanceLightStatusBars(true);
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        configureSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            configureSystemBars();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
