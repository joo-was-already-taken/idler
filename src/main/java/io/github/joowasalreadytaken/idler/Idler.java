package io.github.joowasalreadytaken.idler;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.util.Log;

public class Idler extends TileService {

    private static final String TAG = "Idler";
    private static final String PREFS_NAME = "IdlerPrefs";
    private static final String PREF_PREVIOUS_TIMEOUT = "previous_timeout";
    private static final int NOTIFICATION_ID = 42;
    private static final String NOTIFICATION_CHANNEL_ID = "idler_indicator_channel";
    private static final int MAX_TIMEOUT = Integer.MAX_VALUE;
    private static final int DEFAULT_TIMEOUT = 30000; // 30s

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        if (!hasPermissions()) {
            requestPermissions();
            return;
        }

        try {
            int currentTimeout;
            try {
                currentTimeout = Settings.System.getInt(super.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
            } catch (Settings.SettingNotFoundException e) {
                Log.w(TAG, String.format("Could not read current timeout, defaulting to %ds", DEFAULT_TIMEOUT / 1000), e);
                currentTimeout = DEFAULT_TIMEOUT;
            }

            var prefs = super.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            if (currentTimeout == MAX_TIMEOUT) { // turn OFF Idler
                int previous = prefs.getInt(PREF_PREVIOUS_TIMEOUT, DEFAULT_TIMEOUT);
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, previous);
                removeStatusBarIndicator();
            } else { // turn ON Idler
                prefs.edit().putInt(PREF_PREVIOUS_TIMEOUT, currentTimeout).apply();
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, MAX_TIMEOUT);
                showStatusBarIndicator();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Missing permission to write settings", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error", e);
        } finally {
            updateTileState();
        }
    }

    private boolean hasPermissions() {
        return Settings.System.canWrite(this)
            && super.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        var intent = !Settings.System.canWrite(this)
            ? new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + super.getPackageName()))
            : new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, super.getPackageName());

        var pendingIntent = PendingIntent.getActivity(
            this, 0, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE
        );

        try {
            super.startActivityAndCollapse(pendingIntent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Device does not support direct permission screen", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error launching permission screen", e);
        }
    }

    private void showStatusBarIndicator() {
        var notificationManager = super.getSystemService(NotificationManager.class);
        var channel = new NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Idler Indicator",
            NotificationManager.IMPORTANCE_LOW
        );
        notificationManager.createNotificationChannel(channel);
        var notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Idler")
            .setContentText("Screen will not turn off automatically.")
            .setOngoing(true)
            .build();
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void removeStatusBarIndicator() {
        super.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
    }

    private void updateTileState() {
        Tile tile = super.getQsTile();
        if (tile == null) return;

        try {
            int currentTimeout = Settings.System.getInt(super.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
            tile.setState(currentTimeout == MAX_TIMEOUT ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Could not read timeout state", e);
            tile.setState(Tile.STATE_UNAVAILABLE);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error updating tile", e);
            tile.setState(Tile.STATE_UNAVAILABLE);
        } finally {
            tile.updateTile();
        }
    }
}
