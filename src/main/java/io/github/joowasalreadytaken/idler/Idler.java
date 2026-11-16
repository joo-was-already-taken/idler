package io.github.joowasalreadytaken.idler;

import android.Manifest;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class Idler extends TileService {

    /* package */ static final String TAG = "Idler";

    private enum TileState {
        ACTIVE(Tile.STATE_ACTIVE),
        INACTIVE(Tile.STATE_INACTIVE);

        private final int value;

        TileState(int value) {
            this.value = value;
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        setTileState(KeepAwakeService.isRunning() ? TileState.ACTIVE : TileState.INACTIVE);
    }

    @Override
    public void onClick() {
        if (!hasNotificationPermission()) {
            requestNotificationPermission();
            return;
        }

        var intent = new Intent(this, KeepAwakeService.class);
        if (KeepAwakeService.isRunning()) {
            stopService(intent);
            setTileState(TileState.INACTIVE);
        } else {
            try {
                startForegroundService(intent);
                setTileState(TileState.ACTIVE);
            } catch (ForegroundServiceStartNotAllowedException e) {
                Log.e(TAG, "Not allowed to start keep-awake service", e);
            }
        }
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
        startActivityAndCollapse(pendingIntent);
    }

    private void setTileState(TileState state) {
        var tile = getQsTile();
        if (tile == null) {
            return;
        }

        tile.setState(state.value);
        tile.updateTile();
    }
}
