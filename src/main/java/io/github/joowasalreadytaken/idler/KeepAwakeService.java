package io.github.joowasalreadytaken.idler;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.service.quicksettings.TileService;
import android.util.Log;

public class KeepAwakeService extends Service {

    /* package */ enum State {
        STOPPED,
        STARTING,
        RUNNING,
    }

    /* package */ interface StateListener {
        void onStateChanged(State state);
    }

    private static final String WAKE_LOCK_TAG = "idler:wake_lock";
    private static final int NOTIFICATION_ID = 42;
    private static final String NOTIFICATION_CHANNEL_ID = "idler_indicator_channel";
    private static final String ACTION_STOP = KeepAwakeService.class.getName() + ".ACTION_STOP";

    private static State state = State.STOPPED;
    private static StateListener stateListener;

    private PowerManager.WakeLock wakeLock;

    /* package */ static State getState() {
        return state;
    }

    /* package */ static void setStateListener(StateListener listener) {
        stateListener = listener;
    }

    /* package */ static void start(Context context) {
        setState(State.STARTING);
        try {
            context.startForegroundService(new Intent(context, KeepAwakeService.class));
        } catch (ForegroundServiceStartNotAllowedException e) {
            Log.e(Idler.TAG, "Not allowed to start keep-awake service", e);
            setState(State.STOPPED);
        }
    }

    /* package */ static void stop(Context context) {
        context.stopService(new Intent(context, KeepAwakeService.class));
    }

    private static void setState(State newState) {
        state = newState;
        if (stateListener != null) {
            stateListener.onStateChanged(newState);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (ForegroundServiceStartNotAllowedException e) {
            Log.e(Idler.TAG, "Not allowed to run keep-awake service in the foreground", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        acquireWakeLock();
        setState(State.RUNNING);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
        setState(State.STOPPED);
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
