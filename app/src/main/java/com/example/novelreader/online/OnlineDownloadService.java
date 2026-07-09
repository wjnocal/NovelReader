package com.example.novelreader.online;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import com.example.novelreader.OnlineSearchActivity;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.HashSet;
import java.util.Set;

public class OnlineDownloadService extends Service {
    public static final String ACTION_DOWNLOAD_RESULT = "com.example.novelreader.online.DOWNLOAD_RESULT";
    public static final String ACTION_DOWNLOAD_URL = "com.example.novelreader.online.DOWNLOAD_URL";
    public static final String ACTION_CANCEL = "com.example.novelreader.online.CANCEL_DOWNLOAD";

    public static final String EXTRA_SOURCE_ID = "source_id";
    public static final String EXTRA_SOURCE_NAME = "source_name";
    public static final String EXTRA_BOOK_NAME = "book_name";
    public static final String EXTRA_AUTHOR = "author";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_LATEST_CHAPTER = "latest_chapter";
    public static final String EXTRA_LAST_UPDATE_TIME = "last_update_time";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_FORMAT = "format";
    public static final String EXTRA_SOURCE_IDS = "source_ids";

    private static final String CHANNEL_ID = "online_downloads";
    private static final int NOTIFICATION_ID = 3001;

    private static volatile Listener listener;
    private static volatile boolean running;
    private static volatile boolean cancelling;
    private static volatile String currentMessage = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private OnlineDownloadCancelToken cancelToken;
    private Future<?> worker;

    public interface Listener {
        void onDownloadStatus(String message);

        void onDownloadFinished(long bookId);

        void onDownloadCancelled();

        void onDownloadFailed(String message);
    }

    public static void setListener(Listener downloadListener) {
        listener = downloadListener;
        Listener active = listener;
        if (active != null && running) {
            active.onDownloadStatus(currentMessage);
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static String currentMessage() {
        return currentMessage;
    }

    public static Intent resultDownloadIntent(Context context, OnlineBookResult result, String format) {
        Intent intent = new Intent(context, OnlineDownloadService.class);
        intent.setAction(ACTION_DOWNLOAD_RESULT);
        intent.putExtra(EXTRA_SOURCE_ID, result.sourceId);
        intent.putExtra(EXTRA_SOURCE_NAME, result.sourceName);
        intent.putExtra(EXTRA_BOOK_NAME, result.bookName);
        intent.putExtra(EXTRA_AUTHOR, result.author);
        intent.putExtra(EXTRA_CATEGORY, result.category);
        intent.putExtra(EXTRA_LATEST_CHAPTER, result.latestChapter);
        intent.putExtra(EXTRA_LAST_UPDATE_TIME, result.lastUpdateTime);
        intent.putExtra(EXTRA_STATUS, result.status);
        intent.putExtra(EXTRA_URL, result.url);
        intent.putExtra(EXTRA_FORMAT, format);
        return intent;
    }

    public static Intent urlDownloadIntent(Context context, String url, String format, Set<String> sourceIds) {
        Intent intent = new Intent(context, OnlineDownloadService.class);
        intent.setAction(ACTION_DOWNLOAD_URL);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_FORMAT, format);
        intent.putStringArrayListExtra(EXTRA_SOURCE_IDS, new ArrayList<>(sourceIds));
        return intent;
    }

    public static Intent cancelIntent(Context context) {
        Intent intent = new Intent(context, OnlineDownloadService.class);
        intent.setAction(ACTION_CANCEL);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelCurrentDownload();
            return START_NOT_STICKY;
        }
        if (running) {
            notifyListenerStatus("已有下载任务正在进行");
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, notification("准备下载...", true, false));
        startDownload(intent);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void startDownload(Intent intent) {
        running = true;
        cancelling = false;
        cancelToken = new OnlineDownloadCancelToken();
        worker = executor.submit(() -> {
            try {
                long bookId;
                OnlineBookClient client = new OnlineBookClient(this);
                String format = intent.getStringExtra(EXTRA_FORMAT);
                if (ACTION_DOWNLOAD_URL.equals(intent.getAction())) {
                    String url = intent.getStringExtra(EXTRA_URL);
                    bookId = client.downloadUrlToLibrary(url, format, this::notifyListenerStatus, cancelToken, sourceIdsFromIntent(intent));
                } else {
                    OnlineBookResult result = resultFromIntent(intent);
                    bookId = client.downloadToLibrary(result, format, this::notifyListenerStatus, cancelToken);
                }
                markFinished(bookId);
            } catch (CancellationException e) {
                markCancelled();
            } catch (Exception e) {
                if (cancelling || cancelToken != null && cancelToken.isCancelled()) {
                    markCancelled();
                } else {
                    markFailed(e.getMessage());
                }
            }
        });
    }

    private Set<String> sourceIdsFromIntent(Intent intent) {
        ArrayList<String> ids = intent.getStringArrayListExtra(EXTRA_SOURCE_IDS);
        return ids == null ? new HashSet<>() : new HashSet<>(ids);
    }

    private OnlineBookResult resultFromIntent(Intent intent) {
        OnlineBookResult result = new OnlineBookResult();
        result.sourceId = intent.getStringExtra(EXTRA_SOURCE_ID);
        result.sourceName = intent.getStringExtra(EXTRA_SOURCE_NAME);
        result.bookName = intent.getStringExtra(EXTRA_BOOK_NAME);
        result.author = intent.getStringExtra(EXTRA_AUTHOR);
        result.category = intent.getStringExtra(EXTRA_CATEGORY);
        result.latestChapter = intent.getStringExtra(EXTRA_LATEST_CHAPTER);
        result.lastUpdateTime = intent.getStringExtra(EXTRA_LAST_UPDATE_TIME);
        result.status = intent.getStringExtra(EXTRA_STATUS);
        result.url = intent.getStringExtra(EXTRA_URL);
        return result;
    }

    private void cancelCurrentDownload() {
        cancelling = true;
        currentMessage = "正在取消下载...";
        if (cancelToken != null) {
            cancelToken.cancel();
        }
        if (worker != null) {
            worker.cancel(true);
        }
        notifyListenerStatus(currentMessage);
        getNotificationManager().notify(NOTIFICATION_ID, notification(currentMessage, true, false));
    }

    private void markFinished(long bookId) {
        running = false;
        currentMessage = "下载完成";
        stopForeground(false);
        getNotificationManager().notify(NOTIFICATION_ID, notification("下载完成，已加入书架", false, true));
        Listener active = listener;
        if (active != null) {
            active.onDownloadFinished(bookId);
        }
        stopSelf();
    }

    private void markCancelled() {
        running = false;
        cancelling = true;
        currentMessage = "下载已取消";
        stopForeground(true);
        Listener active = listener;
        if (active != null) {
            active.onDownloadCancelled();
        }
        stopSelf();
    }

    private void markFailed(String message) {
        running = false;
        cancelling = false;
        currentMessage = message == null || message.trim().isEmpty() ? "下载失败" : message;
        stopForeground(false);
        getNotificationManager().notify(NOTIFICATION_ID, notification("下载失败：" + currentMessage, false, true));
        Listener active = listener;
        if (active != null) {
            active.onDownloadFailed(currentMessage);
        }
        stopSelf();
    }

    private void notifyListenerStatus(String message) {
        currentMessage = message == null ? "" : message;
        getNotificationManager().notify(NOTIFICATION_ID, notification(currentMessage, true, false));
        Listener active = listener;
        if (active != null) {
            active.onDownloadStatus(currentMessage);
        }
    }

    private Notification notification(String message, boolean ongoing, boolean done) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(done ? android.R.drawable.stat_sys_download_done : android.R.drawable.stat_sys_download)
                .setContentTitle("在线书籍下载")
                .setContentText(message)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent());
        if (ongoing) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelPendingIntent());
        }
        return builder.build();
    }

    private PendingIntent openAppIntent() {
        Intent intent = new Intent(this, OnlineSearchActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    private PendingIntent cancelPendingIntent() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, 1, cancelIntent(this), flags);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "在线下载",
                NotificationManager.IMPORTANCE_LOW
        );
        getNotificationManager().createNotificationChannel(channel);
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
