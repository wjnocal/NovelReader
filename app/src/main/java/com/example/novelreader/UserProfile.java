package com.example.novelreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;

import com.example.novelreader.sync.SyncPreferences;
import com.example.novelreader.sync.SyncRepository;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.File;
import java.io.InputStream;

public final class UserProfile {
    public static final String PREFS_NAME = "user_profile";
    public static final String KEY_NAME = "name";
    public static final String KEY_AVATAR_URI = "avatarUri";
    public static final String KEY_FOLLOW_SYSTEM_THEME = "followSystemTheme";
    private static final String DEFAULT_NAME = "默认用户";

    private UserProfile() {
    }

    public static String name(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString(KEY_NAME, DEFAULT_NAME);
        return TextUtils.isEmpty(name) ? DEFAULT_NAME : name;
    }

    public static void saveName(Context context, String name) {
        String normalized = TextUtils.isEmpty(name) ? DEFAULT_NAME : name.trim();
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_NAME, normalized)
                .apply();
        SyncPreferences.markNameChanged(context);
        SyncRepository.requestAutomatic(context);
    }

    public static void saveNameFromSync(Context context, String name, long updatedAt) {
        String normalized = TextUtils.isEmpty(name) ? DEFAULT_NAME : name.trim();
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_NAME, normalized).apply();
        context.getSharedPreferences("webdav_sync", Context.MODE_PRIVATE).edit().putLong("nameUpdatedAt", updatedAt).apply();
    }

    public static String avatarUri(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_AVATAR_URI, "");
    }

    public static void saveAvatarUri(Context context, Uri uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_AVATAR_URI, uri == null ? "" : uri.toString())
                .apply();
        SyncPreferences.markAvatarChanged(context);
        SyncRepository.requestAutomatic(context);
    }

    public static void saveAvatarFromSync(Context context, Uri uri, long updatedAt) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_AVATAR_URI, uri == null ? "" : uri.toString()).apply();
        context.getSharedPreferences("webdav_sync", Context.MODE_PRIVATE).edit().putLong("avatarUpdatedAt", updatedAt).apply();
    }

    public static void clearAvatar(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_AVATAR_URI)
                .apply();
        deleteFiles(new File(context.getFilesDir(), "avatars"));
        SyncPreferences.markAvatarChanged(context);
        SyncRepository.requestAutomatic(context);
    }

    public static void clearAvatarFromSync(Context context, long updatedAt) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_AVATAR_URI).apply();
        deleteFiles(new File(context.getFilesDir(), "avatars"));
        context.getSharedPreferences("webdav_sync", Context.MODE_PRIVATE).edit().putLong("avatarUpdatedAt", updatedAt).apply();
    }

    public static boolean followSystemTheme(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_FOLLOW_SYSTEM_THEME, false);
    }

    public static void setFollowSystemTheme(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FOLLOW_SYSTEM_THEME, enabled)
                .apply();
    }

    public static String avatarInitial(Context context) {
        String name = name(context);
        if (TextUtils.isEmpty(name)) {
            return "默";
        }
        return name.substring(0, Math.min(1, name.length()));
    }

    public static void applyAvatar(TextView avatarView, Context context, int sizePx) {
        avatarView.setGravity(Gravity.CENTER);
        avatarView.setTextColor(Color.WHITE);
        avatarView.setTextSize(sizePx >= 64 ? 24f : 15f);
        avatarView.setTypeface(null, Typeface.BOLD);
        Drawable avatarDrawable = loadAvatarDrawable(context, sizePx);
        if (avatarDrawable != null) {
            avatarView.setText("");
            avatarView.setBackground(avatarDrawable);
        } else {
            avatarView.setText(avatarInitial(context));
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(0xFF8A9A90);
            avatarView.setBackground(background);
        }
    }

    private static Drawable loadAvatarDrawable(Context context, int sizePx) {
        String uriText = avatarUri(context);
        if (TextUtils.isEmpty(uriText)) {
            return null;
        }
        try (InputStream stream = context.getContentResolver().openInputStream(Uri.parse(uriText))) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null) {
                return null;
            }
            RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(context.getResources(), bitmap);
            drawable.setCircular(true);
            drawable.setAntiAlias(true);
            drawable.setBounds(0, 0, sizePx, sizePx);
            return drawable;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void deleteFiles(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFiles(child);
                }
            }
        }
        file.delete();
    }
}
