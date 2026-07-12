package com.wjnocal.novelreader;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wjnocal.novelreader.sync.SyncCancellationToken;
import com.wjnocal.novelreader.sync.SyncPreferences;
import com.wjnocal.novelreader.sync.SyncRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** WebDAV configuration and complete-library sync controls. */
public class SyncActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText urlInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private TextView passwordToggle;
    private TextView statusText;
    private TextView lastSyncText;
    private TextView saveButton;
    private TextView syncButton;
    private TextView cancelButton;
    private ProgressBar progressBar;
    private SyncCancellationToken activeCancellation;
    private boolean passwordVisible;

    @Override protected void onCreate(Bundle state) { super.onCreate(state); hideSystemBars(); buildScreen(); refreshConfiguration(); }
    @Override protected void onResume() { super.onResume(); hideSystemBars(); refreshStatus(); }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars(); }
    @Override protected void onDestroy() { if (activeCancellation != null) activeCancellation.cancel(); executor.shutdownNow(); super.onDestroy(); }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(0xFFF7F3EF);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(22), dp(28), dp(22), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView back = text("‹ 返回", 16, 0xFFB95A52); back.setOnClickListener(v -> finish()); content.addView(back, wrap());
        TextView title = text("WebDAV 同步", 27, 0xFF1D1B1A); title.setPadding(0, dp(12), 0, dp(6)); content.addView(title, wrap());
        TextView hint = text("使用自己的 WebDAV 服务器同步书架、书籍、笔记和阅读记录", 14, 0xFF716B67); hint.setLineSpacing(dp(4), 1f); content.addView(hint, wrap());

        LinearLayout configCard = card();
        configCard.addView(label("服务器地址")); urlInput = input("https://example.com/dav/"); urlInput.setInputType(InputType.TYPE_TEXT_VARIATION_URI); configCard.addView(urlInput, wrap());
        configCard.addView(label("WebDAV 用户名")); usernameInput = input("用户名"); configCard.addView(usernameInput, wrap());
        configCard.addView(label("密码"));
        LinearLayout passwordRow = new LinearLayout(this); passwordRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        passwordInput = input("密码"); passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); passwordRow.addView(passwordInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        passwordToggle = text("显示", 14, 0xFFB95A52); passwordToggle.setPadding(dp(12), dp(8), 0, dp(8)); passwordToggle.setOnClickListener(v -> togglePassword()); passwordRow.addView(passwordToggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)); configCard.addView(passwordRow, wrap());
        TextView test = outlinedButton("测试连接"); test.setOnClickListener(v -> testConnection()); configCard.addView(test, marginTop(dp(14)));
        saveButton = filledButton("保存并登入"); saveButton.setOnClickListener(v -> saveAndSync()); configCard.addView(saveButton, marginTop(dp(10))); content.addView(configCard, marginTop(dp(20)));

        LinearLayout stateCard = card(); stateCard.addView(text("同步状态", 18, 0xFF1D1B1A));
        statusText = text("未同步", 14, 0xFF716B67); statusText.setPadding(0, dp(9), 0, 0); stateCard.addView(statusText, wrap());
        lastSyncText = text("最近同步：从未", 14, 0xFF716B67); lastSyncText.setPadding(0, dp(6), 0, 0); stateCard.addView(lastSyncText, wrap());
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progressBar.setMax(100); progressBar.setVisibility(View.GONE); stateCard.addView(progressBar, marginTop(dp(16)));
        syncButton = filledButton("立即同步"); syncButton.setOnClickListener(v -> startSync()); stateCard.addView(syncButton, marginTop(dp(14)));
        cancelButton = outlinedButton("取消同步"); cancelButton.setVisibility(View.GONE); cancelButton.setOnClickListener(v -> { if (activeCancellation != null) activeCancellation.cancel(); }); stateCard.addView(cancelButton, marginTop(dp(10)));
        TextView disconnect = text("断开账号", 15, 0xFFB95A52); disconnect.setPadding(0, dp(18), 0, dp(4)); disconnect.setOnClickListener(v -> confirmDisconnect()); stateCard.addView(disconnect, wrap()); content.addView(stateCard, marginTop(dp(16)));
        setContentView(scroll);
    }

    private void refreshConfiguration() { SyncPreferences.Config c = SyncPreferences.get(this); urlInput.setText(c.url); usernameInput.setText(c.username); passwordInput.setText(c.password); refreshStatus(); }
    private void refreshStatus() { if (statusText == null) return; statusText.setText(SyncPreferences.status(this)); long last = SyncPreferences.lastSync(this); lastSyncText.setText(last <= 0 ? "最近同步：从未" : "最近同步：" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(last))); boolean ready = SyncPreferences.isEnabled(this) && activeCancellation == null; syncButton.setEnabled(ready); syncButton.setAlpha(ready ? 1f : 0.45f); }
    private SyncPreferences.Config readConfig() { SyncPreferences.Config c = new SyncPreferences.Config(); c.url = urlInput.getText().toString().trim(); c.username = usernameInput.getText().toString().trim(); c.password = passwordInput.getText().toString(); return c; }

    private void testConnection() { SyncPreferences.Config c = readConfig(); if (!c.isComplete()) { Toast.makeText(this, "请填写服务器地址、用户名和密码", Toast.LENGTH_SHORT).show(); return; } saveButton.setEnabled(false); executor.execute(() -> { try { new SyncRepository(this).testConnection(c); runOnUiThread(() -> Toast.makeText(this, "连接成功", Toast.LENGTH_SHORT).show()); } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "连接失败：" + message(e), Toast.LENGTH_LONG).show()); } finally { runOnUiThread(() -> saveButton.setEnabled(true)); } }); }
    private void saveAndSync() { SyncPreferences.Config c = readConfig(); if (!c.isComplete()) { Toast.makeText(this, "请填写服务器地址、用户名和密码", Toast.LENGTH_SHORT).show(); return; } try { SyncPreferences.save(this, c); Toast.makeText(this, "WebDAV 配置已保存", Toast.LENGTH_SHORT).show(); startSync(); } catch (Exception e) { Toast.makeText(this, message(e), Toast.LENGTH_LONG).show(); } }

    private void startSync() {
        if (activeCancellation != null) return;
        SyncCancellationToken token = new SyncCancellationToken();
        activeCancellation = token; progressBar.setProgress(0); progressBar.setVisibility(View.VISIBLE); cancelButton.setVisibility(View.VISIBLE); syncButton.setEnabled(false); saveButton.setEnabled(false);
        executor.execute(() -> {
            SyncRepository repository = new SyncRepository(this);
            SyncRepository.ProgressConflictException conflict = null;
            Exception failure = null;
            boolean completed = false;
            try {
                repository.sync(token, (message, percent) -> runOnUiThread(() -> { statusText.setText(message); progressBar.setProgress(percent); }));
                completed = true;
            } catch (SyncRepository.ProgressConflictException e) {
                conflict = e;
            } catch (Exception e) {
                failure = e;
            }
            final SyncRepository.ProgressConflictException pendingConflict = conflict;
            final Exception pendingFailure = failure;
            final boolean syncCompleted = completed;
            runOnUiThread(() -> {
                activeCancellation = null; progressBar.setVisibility(View.GONE); cancelButton.setVisibility(View.GONE); saveButton.setEnabled(true); refreshStatus();
                if (pendingConflict != null) {
                    showProgressConflict(repository, pendingConflict.getConflicts(), 0);
                } else if (syncCompleted) {
                    Toast.makeText(this, "同步完成", Toast.LENGTH_SHORT).show();
                } else if (pendingFailure != null) {
                    Toast.makeText(this, "同步失败：" + message(pendingFailure), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void showProgressConflict(SyncRepository repository, java.util.List<SyncRepository.ProgressConflict> conflicts, int index) {
        if (index >= conflicts.size()) {
            startSync();
            return;
        }
        SyncRepository.ProgressConflict conflict = conflicts.get(index);
        String message = "《" + conflict.title + "》在本机和云端均存在。书签与笔记会合并，请选择保留的阅读进度。";
        new AlertDialog.Builder(this)
                .setTitle("同名书籍进度冲突")
                .setMessage(message)
                .setNegativeButton("本机：第" + (conflict.localChapterIndex + 1) + "章", (dialog, which) -> resolveProgressConflict(repository, conflict, false, conflicts, index))
                .setPositiveButton("云端：第" + (conflict.remoteChapterIndex + 1) + "章", (dialog, which) -> resolveProgressConflict(repository, conflict, true, conflicts, index))
                .show();
    }

    private void resolveProgressConflict(SyncRepository repository, SyncRepository.ProgressConflict conflict, boolean keepRemote, java.util.List<SyncRepository.ProgressConflict> conflicts, int index) {
        executor.execute(() -> {
            try {
                repository.resolveProgressConflict(conflict, keepRemote);
                runOnUiThread(() -> showProgressConflict(repository, conflicts, index + 1));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "合并同名书籍失败：" + message(e), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmDisconnect() { new AlertDialog.Builder(this).setTitle("断开 WebDAV 账号").setMessage("这只会清除本机的服务器地址和密码，不会删除服务器中的同步数据。").setNegativeButton("取消", null).setPositiveButton("断开", (d, w) -> { SyncPreferences.clear(this); passwordInput.setText(""); refreshStatus(); }).show(); }
    private void togglePassword() { passwordVisible = !passwordVisible; passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | (passwordVisible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD)); passwordInput.setSelection(passwordInput.length()); passwordToggle.setText(passwordVisible ? "隐藏" : "显示"); }
    private void hideSystemBars() { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }
    private LinearLayout card() { LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16), dp(16), dp(16), dp(16)); android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(12)); bg.setStroke(dp(1), 0xFFE7E0DB); card.setBackground(bg); return card; }
    private TextView label(String value) { TextView v = text(value, 14, 0xFF5F5853); v.setPadding(0, dp(12), 0, dp(2)); return v; }
    private EditText input(String hint) { EditText v = new EditText(this); v.setSingleLine(true); v.setHint(hint); v.setTextSize(16f); v.setTextColor(0xFF1D1B1A); v.setHintTextColor(0xFF9B938E); return v; }
    private TextView text(String value, int size, int color) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v; }
    private TextView filledButton(String value) { TextView v = text(value, 16, Color.WHITE); v.setGravity(android.view.Gravity.CENTER); v.setPadding(0, dp(13), 0, dp(13)); android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setColor(0xFFB95A52); bg.setCornerRadius(dp(8)); v.setBackground(bg); return v; }
    private TextView outlinedButton(String value) { TextView v = text(value, 16, 0xFFB95A52); v.setGravity(android.view.Gravity.CENTER); v.setPadding(0, dp(12), 0, dp(12)); android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setColor(0xFFFFF5F1); bg.setCornerRadius(dp(8)); bg.setStroke(dp(1), 0xFFF1CFC9); v.setBackground(bg); return v; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams marginTop(int value) { LinearLayout.LayoutParams p = wrap(); p.topMargin = value; return p; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private String message(Exception e) { return e.getMessage() == null ? "未知错误" : e.getMessage(); }
}
