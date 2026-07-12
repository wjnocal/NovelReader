package com.wjnocal.novelreader.sync;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** WebDAV transport backed by OkHttp so custom DAV verbs work on Android. */
public class WebDavClient {
    private static final MediaType OCTET_STREAM = MediaType.get("application/octet-stream");
    private final HttpUrl baseUrl;
    private final String authorization;
    private final OkHttpClient http;
    private final SyncCancellationToken cancellation;

    public WebDavClient(SyncPreferences.Config config, SyncCancellationToken cancellation) {
        String url = config.url.endsWith("/") ? config.url : config.url + "/";
        this.baseUrl = HttpUrl.parse(url);
        if (baseUrl == null) {
            throw new IllegalArgumentException("WebDAV 地址无效");
        }
        this.authorization = "Basic " + Base64.encodeToString((config.username + ":" + config.password)
                .getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        this.http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.cancellation = cancellation == null ? new SyncCancellationToken() : cancellation;
    }

    public void testConnection() throws Exception {
        try (Response response = execute("PROPFIND", "", emptyBody(), "Depth", "0")) {
            int code = response.code();
            if (code != 207 && !isSuccess(code)) {
                throw error("WebDAV 连接失败", code);
            }
        }
    }

    public void ensureDirectory(String path) throws Exception {
        String current = "";
        for (String segment : path.split("/")) {
            if (segment.trim().isEmpty()) {
                continue;
            }
            current += segment + "/";
            int code;
            try (Response response = execute("MKCOL", current, emptyBody(), null, null)) {
                code = response.code();
            }
            if (isSuccess(code)) {
                continue;
            }
            if (code == 405 && exists(current)) {
                continue;
            }
            throw error("无法创建同步目录", code);
        }
    }

    public boolean exists(String path) throws Exception {
        try (Response response = execute("HEAD", path, null, null, null)) {
            if (isSuccess(response.code())) {
                return true;
            }
            if (response.code() != 405) {
                return false;
            }
        }
        try (Response response = execute("PROPFIND", path, emptyBody(), "Depth", "0")) {
            return response.code() == 207 || isSuccess(response.code());
        }
    }

    public String etag(String path) throws Exception {
        try (Response response = execute("HEAD", path, null, null, null)) {
            if (response.code() == 404) {
                return null;
            }
            if (isSuccess(response.code())) {
                return response.header("ETag");
            }
            if (response.code() != 405) {
                throw error("无法读取服务器文件", response.code());
            }
        }
        try (Response response = execute("PROPFIND", path, emptyBody(), "Depth", "0")) {
            if (response.code() == 404) {
                return null;
            }
            if (response.code() != 207 && !isSuccess(response.code())) {
                throw error("无法读取服务器文件", response.code());
            }
            return response.header("ETag");
        }
    }

    public byte[] getBytes(String path) throws Exception {
        try (Response response = execute("GET", path, null, null, null)) {
            if (response.code() == 404) {
                return null;
            }
            if (!isSuccess(response.code()) || response.body() == null) {
                throw error("下载同步数据失败", response.code());
            }
            try (InputStream input = response.body().byteStream()) {
                return readAll(input);
            }
        }
    }

    public void download(String path, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建本地同步目录");
        }
        try (Response response = execute("GET", path, null, null, null)) {
            if (!isSuccess(response.code()) || response.body() == null) {
                throw new IllegalStateException(response.code() == 404 ? "服务器上找不到同步文件" : "下载同步数据失败：HTTP " + response.code());
            }
            try (InputStream input = response.body().byteStream(); OutputStream output = new FileOutputStream(target)) {
                copy(input, output);
            }
        }
    }

    public void putAtomic(String path, byte[] bytes, String ifMatch) throws Exception {
        File temp = File.createTempFile("webdav-sync", ".tmp");
        try (OutputStream output = new FileOutputStream(temp)) {
            output.write(bytes);
        }
        try {
            putAtomic(path, temp, ifMatch);
        } finally {
            temp.delete();
        }
    }

    public void putAtomic(String path, File file, String ifMatch) throws Exception {
        // Keep the staging file beside its destination. Nutstore accepts this rename pattern reliably.
        String temporary = path + ".cache-" + UUID.randomUUID();
        try {
            putFile(temporary, file, null);
            if (!move(temporary, path, ifMatch)) {
                // Some WebDAV servers reject MOVE despite accepting PUT in the same directory.
                putFile(path, file, ifMatch);
            }
        } catch (Exception e) {
            removeQuietly(temporary);
            throw e;
        }
    }

    private void putFile(String path, File file, String ifMatch) throws Exception {
        cancellation.throwIfCancelled();
        RequestBody body = RequestBody.create(file, OCTET_STREAM);
        Request.Builder request = requestBuilder("PUT", path, body)
                .header("Content-Type", "application/octet-stream");
        if (ifMatch != null && !ifMatch.trim().isEmpty()) {
            request.header("If-Match", ifMatch);
        }
        try (Response response = http.newCall(request.build()).execute()) {
            if (!isSuccess(response.code())) {
                throw error("上传同步数据失败", response.code());
            }
        }
        cancellation.throwIfCancelled();
    }

    private boolean move(String from, String to, String ifMatch) throws Exception {
        return move(from, to, ifMatch, true);
    }

    private boolean move(String from, String to, String ifMatch, boolean allowParentRetry) throws Exception {
        Request.Builder request = requestBuilder("MOVE", from, emptyBody())
                .header("Destination", urlFor(to).toString())
                .header("Overwrite", "T");
        if (ifMatch != null && !ifMatch.trim().isEmpty()) {
            request.header("If-Match", ifMatch);
        }
        int code;
        try (Response response = http.newCall(request.build()).execute()) {
            code = response.code();
        }
        if (code == 412) {
            throw new ConcurrentSyncException();
        }
        if (code == 409 && allowParentRetry) {
            String parent = parentDirectory(to);
            if (!parent.isEmpty()) {
                ensureDirectory(parent);
                return move(from, to, ifMatch, false);
            }
        }
        if (code == 409) {
            return false;
        }
        if (!isSuccess(code)) {
            throw error("发布同步数据失败", code);
        }
        return true;
    }

    private static String parentDirectory(String path) {
        int separator = path == null ? -1 : path.lastIndexOf('/');
        return separator < 0 ? "" : path.substring(0, separator + 1);
    }

    private Response execute(String method, String path, RequestBody body, String header, String value) throws Exception {
        cancellation.throwIfCancelled();
        Request.Builder request = requestBuilder(method, path, body);
        if (header != null) {
            request.header(header, value);
        }
        return http.newCall(request.build()).execute();
    }

    private Request.Builder requestBuilder(String method, String path, RequestBody body) {
        return new Request.Builder()
                .url(urlFor(path))
                .header("Authorization", authorization)
                .header("User-Agent", "NovelReader-WebDAV/3.0")
                .header("Accept-Charset", "utf-8")
                .method(method, body);
    }

    private HttpUrl urlFor(String path) {
        HttpUrl target = baseUrl.resolve(path == null ? "" : path);
        if (target == null) {
            throw new IllegalArgumentException("WebDAV 路径无效");
        }
        return target;
    }

    private void removeQuietly(String path) {
        try (Response ignored = execute("DELETE", path, emptyBody(), null, null)) {
            // A failed cleanup does not affect the original sync failure.
        } catch (Exception ignored) {
        }
    }

    private static RequestBody emptyBody() {
        return RequestBody.create(new byte[0], OCTET_STREAM);
    }

    private static boolean isSuccess(int code) {
        return code >= 200 && code < 300;
    }

    private static IllegalStateException error(String prefix, int code) {
        String suffix = code == 401 || code == 403 ? "：用户名或应用密码错误" : "：HTTP " + code;
        return new IllegalStateException(prefix + suffix);
    }

    private void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            cancellation.throwIfCancelled();
            output.write(buffer, 0, count);
        }
    }

    private byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    public static class ConcurrentSyncException extends Exception {
        ConcurrentSyncException() {
            super("服务器上的同步数据已更新");
        }
    }
}
