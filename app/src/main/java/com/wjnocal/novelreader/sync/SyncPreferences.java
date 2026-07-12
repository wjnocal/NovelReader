package com.wjnocal.novelreader.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local WebDAV configuration. Only the password is encrypted. */
public final class SyncPreferences {
    private static final String PREFS = "webdav_sync";
    private static final String KEY_URL = "url";
    private static final String KEY_USER = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DEVICE_ID = "deviceId";
    private static final String KEY_STATUS = "status";
    private static final String KEY_LAST_SYNC = "lastSync";
    private static final String KEY_SETTINGS_UPDATED_AT = "settingsUpdatedAt";
    private static final String KEY_AVATAR_HASH = "avatarHash";
    private static final String KEY_AVATAR_UPDATED_AT = "avatarUpdatedAt";
    private static final String KEY_NAME_UPDATED_AT = "nameUpdatedAt";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "NovelReaderWebDavPassword";

    private SyncPreferences() {
    }

    public static final class Config {
        public String url = "";
        public String username = "";
        public String password = "";

        public boolean isComplete() {
            return !url.trim().isEmpty() && !username.trim().isEmpty() && !password.isEmpty();
        }
    }

    public static Config get(Context context) {
        SharedPreferences prefs = prefs(context);
        Config config = new Config();
        config.url = prefs.getString(KEY_URL, "");
        config.username = prefs.getString(KEY_USER, "");
        config.password = decrypt(prefs.getString(KEY_PASSWORD, ""));
        return config;
    }

    public static void save(Context context, Config config) {
        prefs(context).edit()
                .putString(KEY_URL, normalizeUrl(config.url))
                .putString(KEY_USER, config.username == null ? "" : config.username.trim())
                .putString(KEY_PASSWORD, encrypt(config.password == null ? "" : config.password))
                .putBoolean(KEY_ENABLED, true)
                .apply();
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false) && get(context).isComplete();
    }

    public static void clear(Context context) {
        prefs(context).edit()
                .remove(KEY_URL).remove(KEY_USER).remove(KEY_PASSWORD).remove(KEY_ENABLED)
                .remove(KEY_STATUS).remove(KEY_LAST_SYNC).apply();
    }

    public static String deviceId(Context context) {
        SharedPreferences prefs = prefs(context);
        String id = prefs.getString(KEY_DEVICE_ID, "");
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public static void setStatus(Context context, String status) {
        prefs(context).edit().putString(KEY_STATUS, status == null ? "" : status).apply();
    }

    public static String status(Context context) {
        return prefs(context).getString(KEY_STATUS, "未同步");
    }

    public static void setLastSync(Context context, long time) {
        prefs(context).edit().putLong(KEY_LAST_SYNC, time).apply();
    }

    public static long lastSync(Context context) {
        return prefs(context).getLong(KEY_LAST_SYNC, 0L);
    }

    public static long settingsUpdatedAt(Context context) {
        return prefs(context).getLong(KEY_SETTINGS_UPDATED_AT, 0L);
    }

    public static void markSettingsChanged(Context context) {
        prefs(context).edit().putLong(KEY_SETTINGS_UPDATED_AT, System.currentTimeMillis()).apply();
    }

    public static void setSettingsUpdatedAt(Context context, long updatedAt) {
        prefs(context).edit().putLong(KEY_SETTINGS_UPDATED_AT, updatedAt).apply();
    }

    public static long avatarUpdatedAt(Context context) {
        return prefs(context).getLong(KEY_AVATAR_UPDATED_AT, 0L);
    }

    public static void markAvatarChanged(Context context) {
        prefs(context).edit().putLong(KEY_AVATAR_UPDATED_AT, System.currentTimeMillis()).apply();
    }

    public static long nameUpdatedAt(Context context) {
        return prefs(context).getLong(KEY_NAME_UPDATED_AT, 0L);
    }

    public static void markNameChanged(Context context) {
        prefs(context).edit().putLong(KEY_NAME_UPDATED_AT, System.currentTimeMillis()).apply();
    }

    public static String avatarHash(Context context) {
        return prefs(context).getString(KEY_AVATAR_HASH, "");
    }

    public static void setAvatarHash(Context context, String hash) {
        prefs(context).edit().putString(KEY_AVATAR_HASH, hash == null ? "" : hash).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalizeUrl(String url) {
        String value = url == null ? "" : url.trim();
        return value.endsWith("/") ? value : value + "/";
    }

    private static String encrypt(String text) {
        if (text.isEmpty()) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("无法安全保存 WebDAV 密码", e);
        }
    }

    private static String decrypt(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            String[] parts = value.split(":", 2);
            if (parts.length != 2) {
                return "";
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128,
                    Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
