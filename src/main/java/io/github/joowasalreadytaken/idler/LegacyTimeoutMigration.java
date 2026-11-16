package io.github.joowasalreadytaken.idler;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * Up to v0.2.0, Idler kept the screen on by setting SCREEN_OFF_TIMEOUT to Integer.MAX_VALUE
 * and saving the user's timeout in shared preferences. If the app was updated while that was
 * active, restore the saved timeout and remove the old indicator notification.
 */
public class LegacyTimeoutMigration extends BroadcastReceiver {

    private static final String LEGACY_PREFS_NAME = "IdlerPrefs";
    private static final String LEGACY_PREF_PREVIOUS_TIMEOUT = "previous_timeout";
    private static final int LEGACY_MAX_TIMEOUT = Integer.MAX_VALUE;
    private static final int LEGACY_DEFAULT_TIMEOUT = 30000; // 30s
    private static final int LEGACY_NOTIFICATION_ID = 42;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }

        var prefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(LEGACY_PREF_PREVIOUS_TIMEOUT)) {
            return;
        }

        if (!Settings.System.canWrite(context)) {
            Log.w(Idler.TAG, "Cannot restore legacy screen timeout without WRITE_SETTINGS");
            return;
        }

        var resolver = context.getContentResolver();
        if (Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 0) == LEGACY_MAX_TIMEOUT) {
            var previous = prefs.getInt(LEGACY_PREF_PREVIOUS_TIMEOUT, LEGACY_DEFAULT_TIMEOUT);
            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, previous);
            context.getSystemService(NotificationManager.class).cancel(LEGACY_NOTIFICATION_ID);
        }

        context.deleteSharedPreferences(LEGACY_PREFS_NAME);
    }
}
