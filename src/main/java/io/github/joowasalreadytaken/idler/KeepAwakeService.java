package io.github.joowasalreadytaken.idler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.service.quicksettings.TileService;

public class KeepAwakeService extends Service {

    private static final String WAKE_LOCK_TAG = "idler:wake_lock";
    private static final int NOTIFICATION_ID = 42;
    private static final String NOTIFICATION_CHANNEL_ID = "idler_indicator_channel";
    private static final String ACTION_STOP = KeepAwakeService.class.getName() + ".ACTION_STOP";

    private static volatile boolean running = false;

    private PowerManager.WakeLock wakeLock;

    /* package */ static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        acquireWakeLock();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
        running = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        TileService.requestListeningState(this, new ComponentName(this, Idler.class));
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void acquireWakeLock() {
        if (wakeLock != null) {
            return;
        }

        var pm = getSystemService(PowerManager.class);

        @SuppressWarnings("deprecation")
        var wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
            WAKE_LOCK_TAG
        );
        wl.setReferenceCounted(false);
        wl.acquire();
        wakeLock = wl;
    }

    private void createNotificationChannel() {
        var channel = new NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        );
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        var stopIntent = new Intent(this, KeepAwakeService.class).setAction(ACTION_STOP);
        var stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        var stopAction = new Notification.Action.Builder(
            null,
            getString(R.string.notification_action_turn_off),
            stopPendingIntent
        ).build();

        return new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(stopAction)
            .build();
    }
}
