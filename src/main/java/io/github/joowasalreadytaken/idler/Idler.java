package io.github.joowasalreadytaken.idler;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.app.PendingIntent;

public class Idler extends TileService {

    private static final String PREFS_NAME = "IdlerPrefs";
    private static final String PREF_PREVIOUS_TIMEOUT = "previous_timeout";
    private static final int MAX_TIMEOUT = Integer.MAX_VALUE;
    private static final int DEFAULT_TIMEOUT = 30000; // 30s

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        if (!Settings.System.canWrite(this)) {
            requestPermissions();
            return;
        }

        try {
            int currentTimeout = Settings.System.getInt(super.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
            var prefs = super.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            if (currentTimeout == MAX_TIMEOUT) { // turn OFF Idler
                int previous = prefs.getInt(PREF_PREVIOUS_TIMEOUT, DEFAULT_TIMEOUT);
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, previous);
            } else { // turn ON Idler
                prefs.edit().putInt(PREF_PREVIOUS_TIMEOUT, currentTimeout).apply();
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, MAX_TIMEOUT);
            }
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        } finally {
            updateTileState();
        }
    }

    private void requestPermissions() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
        intent.setData(Uri.parse("package:" + super.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        var pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );
        super.startActivityAndCollapse(pendingIntent);
    }

    private void updateTileState() {
        Tile tile = super.getQsTile();
        if (tile == null) return;

        try {
            int currentTimeout = Settings.System.getInt(super.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
            tile.setState(currentTimeout == MAX_TIMEOUT ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        } catch (Settings.SettingNotFoundException e) {
            tile.setState(Tile.STATE_UNAVAILABLE);
        } finally {
            tile.updateTile();
        }
    }
}
