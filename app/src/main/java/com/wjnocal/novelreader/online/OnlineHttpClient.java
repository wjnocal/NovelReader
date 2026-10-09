package com.wjnocal.novelreader.online;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Shared connections, start-to-start pacing and cancellable requests. */
final class OnlineHttpClient {
    private static final OkHttpClient SHARED = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(12, 5, TimeUnit.MINUTES))
            .build();
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";
    private final OkHttpClient client;
    private final Map<String, RequestGate> gates = new ConcurrentHashMap<>();

    OnlineHttpClient() { this(SHARED); }
    OnlineHttpClient(OkHttpClient client) { this.client = client; }

    Document get(String url, OnlineBookSource source, OnlineDownloadCancelToken token) throws Exception {
        return execute(url, source, null, token);
    }

    Document execute(String url, OnlineBookSource source, FormBody form,
                     OnlineDownloadCancelToken token) throws Exception {
        return execute(url, source, form, token, false);
    }

    Document search(String url, OnlineBookSource source, FormBody form, OnlineDownloadCancelToken token) throws Exception {
        return execute(url, source, form, token, true);
    }

    private Document execute(String url, OnlineBookSource source, FormBody form,
                             OnlineDownloadCancelToken token, boolean search) throws Exception {
        RequestGate gate = gates.computeIfAbsent(source.id, ignored -> new RequestGate());
        OkHttpClient configured = client.newBuilder()
                .callTimeout(source.crawl.timeoutMillis, TimeUnit.MILLISECONDS)
                .connectTimeout(source.crawl.timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(source.crawl.timeoutMillis, TimeUnit.MILLISECONDS).build();
        Request.Builder builder = new Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                // Some sources reject the root Referer when it has a trailing slash.
                .header("Referer", source.baseUrl.replaceFirst("/+$", ""));
        if (form != null) {
            builder.post(form);
            if (source.search.cookies != null && !source.search.cookies.isEmpty()) {
                builder.header("Cookie", source.search.cookies);
            }
        }
        int attempts = Math.max(1, source.crawl.maxRetries + 1);
        for (int attempt = 0; ; attempt++) {
            gate.await(source, token, search);
            checkCancelled(token);
            Call call = configured.newCall(builder.build());
            Runnable cancelCall = call::cancel;
            if (token != null) token.register(cancelCall);
            try (Response response = call.execute()) {
                checkCancelled(token);
                if (!response.isSuccessful()) {
                    int code = response.code();
                    boolean retryable = code == 408 || code == 429 || code >= 500;
                    long delay = retryDelay(response.header("Retry-After"), attempt);
                    if (retryable) gate.defer(delay);
                    if (retryable && attempt + 1 < attempts) continue;
                    throw new HttpFailure(code, retryable, delay, url);
                }
                ResponseBody body = response.body();
                if (body == null) throw new IOException("网页响应为空：" + url);
                if (search && "quanben5".equals(source.id)) {
                    // JSONP contains quoted HTML; parsing it as HTML first would
                    // discard markup and corrupt the JSON string.
                    String raw = body.string();
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("^\\s*search\\((\\{.*\\})\\)\\s*;?\\s*$", java.util.regex.Pattern.DOTALL).matcher(raw);
                    if (!matcher.find()) throw new IOException("搜索 JSONP 响应格式异常");
                    String html = new JSONObject(matcher.group(1)).optString("content");
                    return Jsoup.parse(html, source.baseUrl);
                }
                MediaType type = body.contentType();
                Charset charset = type == null ? null : type.charset();
                try (InputStream input = body.byteStream()) {
                    // Jsoup still inspects meta charset / BOM when HTTP has no charset.
                    return Jsoup.parse(input, charset == null ? null : charset.name(), response.request().url().toString());
                }
            } catch (IOException error) {
                checkCancelled(token);
                if (error instanceof HttpFailure || attempt + 1 >= attempts) throw error;
                gate.defer(retryDelay(null, attempt));
            } finally {
                if (token != null) token.unregister(cancelCall);
            }
        }
    }

    static long retryDelay(String retryAfter, int attempt) {
        if (retryAfter != null) {
            try { return Math.min(120_000, Math.max(0, Long.parseLong(retryAfter.trim()) * 1000)); }
            catch (NumberFormatException ignored) {
                try {
                    java.text.SimpleDateFormat date = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US);
                    return Math.min(120_000, Math.max(0, date.parse(retryAfter).getTime() - System.currentTimeMillis()));
                } catch (java.text.ParseException invalid) { }
            }
        }
        return Math.min(30_000, 1000L << Math.min(attempt, 5));
    }

    static void checkCancelled(OnlineDownloadCancelToken token) {
        if (token != null) token.throwIfCancelled();
        else if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("下载已取消");
    }

    static final class HttpFailure extends IOException {
        final int status;
        final boolean retryable;
        final long delayMillis;
        HttpFailure(int status, boolean retryable, long delayMillis, String url) {
            super("HTTP " + status + "：" + url);
            this.status = status;
            this.retryable = retryable;
            this.delayMillis = delayMillis;
        }
    }

    private static final class RequestGate {
        private long nextStart;
        synchronized void defer(long millis) {
            nextStart = Math.max(nextStart, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis));
        }
        void await(OnlineBookSource source, OnlineDownloadCancelToken token, boolean search) throws InterruptedException {
            for (;;) {
                checkCancelled(token);
                long wait;
                synchronized (this) {
                    wait = nextStart - System.nanoTime();
                    if (wait <= 0) {
                        int min = Math.max(0, source.crawl.minIntervalMillis);
                        int max = Math.max(min, source.crawl.maxIntervalMillis);
                        long interval = min == max ? min : ThreadLocalRandom.current().nextLong(min, (long) max + 1);
                        int concurrency = Math.max(1, Math.min(8, source.crawl.concurrency));
                        long delay = Math.max(interval / concurrency, search ? source.search.minIntervalMillis : 0);
                        nextStart = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay);
                        return;
                    }
                }
                TimeUnit.NANOSECONDS.sleep(Math.min(wait, TimeUnit.MILLISECONDS.toNanos(100)));
            }
        }
    }
}
