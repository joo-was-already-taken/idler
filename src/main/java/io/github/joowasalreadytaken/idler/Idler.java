package io.github.joowasalreadytaken.idler;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class Idler extends TileService {

    /* package */ static final String TAG = "Idler";

    @Override
    public void onStartListening() {
        super.onStartListening();
        KeepAwakeService.setStateListener(this::showState);
        showState(KeepAwakeService.getState());
    }

    @Override
    public void onStopListening() {
        KeepAwakeService.setStateListener(null);
        super.onStopListening();
    }

    @Override
    public void onClick() {
        if (!hasNotificationPermission()) {
            requestNotificationPermission();
            return;
        }

        switch (KeepAwakeService.getState()) {
            case STOPPED -> KeepAwakeService.start(this);
            case RUNNING -> KeepAwakeService.stop(this);
            case STARTING -> {}
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

    private void showState(KeepAwakeService.State state) {
        var tile = getQsTile();
        if (tile == null) {
            return;
        }

        tile.setState(state == KeepAwakeService.State.STOPPED ? Tile.STATE_INACTIVE : Tile.STATE_ACTIVE);
        tile.updateTile();
    }
}
