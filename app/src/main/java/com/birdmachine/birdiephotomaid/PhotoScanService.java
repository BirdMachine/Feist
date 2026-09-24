package com.birdmachine.birdiephotomaid;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

public final class PhotoScanService extends Service {
    public static final String EXTRA_PARTIAL = "partial_photo_access";
    private static final String CHANNEL = "feist_scan";
    private static final int NOTIFICATION_ID = 1001;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Thread worker;
    private long lastNotificationAt;
    private volatile boolean timedOut;

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Photo scans", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Live progress and completed Feist inspections");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (worker != null && worker.isAlive()) return START_NOT_STICKY;
        final boolean partial = intent != null && intent.getBooleanExtra(EXTRA_PARTIAL, false);
        ScanRepository.started();
        Notification initial = notification("Preparing library", 0, true);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, initial);
        }
        worker = new Thread(() -> {
            try {
                PhotoScanner.Result result = PhotoScanner.scan(this, partial, (done, total, phase) -> {
                    ScanRepository.progress(done, total, phase);
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastNotificationAt >= 500 || done == total) {
                        lastNotificationAt = now;
                        main.post(() -> postNotification(notification(phase + " · " + done + "/" + total,
                                ScanProgress.overall(phase, done, total), true)));
                    }
                });
                main.post(() -> {
                    ScanRepository.finished(result);
                    finishNotification("Scan complete · " + result.exactDuplicateGroups.size() + " exact duplicate groups");
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (timedOut) return;
                    ScanRepository.failed(error.getClass().getSimpleName() + ": " + error.getMessage());
                    finishNotification("Scan stopped · tap for details");
                });
            }
        }, "feist-background-scan");
        worker.start();
        return START_NOT_STICKY;
    }

    private Notification notification(String message, int progress, boolean active) {
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle(active ? "Feist is inspecting photos" : "Feist inspection finished")
                .setContentText(message)
                .setContentIntent(pending)
                .setOnlyAlertOnce(true)
                .setOngoing(active)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        if (active) builder.setProgress(1000, Math.max(0, progress), progress == 0);
        else builder.setAutoCancel(true);
        return builder.build();
    }

    private void postNotification(Notification update) {
        try {
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, update);
        } catch (SecurityException ignored) {
            // The foreground service still runs if notification permission was declined.
        }
    }

    private void finishNotification(String message) {
        stopForeground(STOP_FOREGROUND_DETACH);
        postNotification(notification(message, 1000, false));
        stopSelf();
    }

    @Override public void onTimeout(int startId, int foregroundServiceType) {
        timedOut = true;
        if (worker != null) worker.interrupt();
        main.post(() -> {
            ScanRepository.failed("Android stopped this long scan. Try again later.");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        });
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
