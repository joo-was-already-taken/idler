package io.github.joowasalreadytaken.idler;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import java.util.concurrent.atomic.AtomicReference;

public class Idler extends TileService {

    private static final String TAG = "Idler";
    private static final String WAKE_LOCK_TAG = "idler:wake_lock";
    private static final int NOTIFICATION_ID = 42;
    private static final String NOTIFICATION_CHANNEL_ID = "idler_indicator_channel";
    private static final String ACTION_STOP = Idler.class.getName() + ".ACTION_STOP";

    private static final AtomicReference<PowerManager.WakeLock> wakeLockRef = new AtomicReference<>();

    public static boolean isHeld() {
        var wl = wakeLockRef.get();
        return wl != null && wl.isHeld();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            releaseWakeLock();
            requestListeningState(this, new ComponentName(this, Idler.class));
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        if (!hasNotificationPermission()) {
            requestNotificationPermission();
            return;
        }

        if (isHeld()) {
            releaseWakeLock();
        } else {
            acquireWakeLock();
        }
        updateTileState();
    }

    private void acquireWakeLock() {
        if (wakeLockRef.get() != null) {
            return;
        }

        var pm = getSystemService(PowerManager.class);
        if (pm == null) {
            Log.e(TAG, "Failed to get PowerManager service");
            return;
        }

        @SuppressWarnings("deprecation")
        var wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
            WAKE_LOCK_TAG
        );
        wl.setReferenceCounted(false);

        wl.acquire();
        if (wakeLockRef.compareAndSet(null, wl)) {
            showStatusBarIndicator();
        } else {
            wl.release();
        }
    }

    private void releaseWakeLock() {
        var wl = wakeLockRef.getAndSet(null);
        if (wl != null) {
            if (wl.isHeld()) {
                wl.release();
            }
        }
        removeStatusBarIndicator();
    }

    private boolean hasNotificationPermission() {
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        var intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        var pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        try {
            startActivityAndCollapse(pendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error launching notification settings", e);
        }
    }

    private void showStatusBarIndicator() {
        var notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            Log.e(TAG, "Failed to get NotificationManager service");
            return;
        }

        var channel = new NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Idler Indicator",
            NotificationManager.IMPORTANCE_LOW
        );
        notificationManager.createNotificationChannel(channel);

        var stopIntent = new Intent(this, Idler.class).setAction(ACTION_STOP);
        var stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        var stopAction = new Notification.Action.Builder(
            null,
            "Turn off",
            stopPendingIntent
        ).build();

        var notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Idler")
            .setContentText("Screen will not turn off automatically.")
            .setOngoing(true)
            .addAction(stopAction)
            .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void removeStatusBarIndicator() {
        var notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            Log.e(TAG, "Failed to get NotificationManager service");
            return;
        }
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setState(isHeld() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
