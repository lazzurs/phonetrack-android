package net.eneiluj.nextcloud.phonetrack.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;

/**
 * Receiver for boot completed broadcast
 *
 */

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = BootCompletedReceiver.class.getSimpleName();

    /**
     * Broadcast received on system boot completed.
     * Starts background logging service
     *
     * @param context Context
     * @param intent Intent
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean autoStart = prefs.getBoolean(context.getString(R.string.pref_key_autostart), false);
        if (autoStart && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // A location foreground service started without visible UI needs background
            // location access; without it startForeground() would throw.
            if (!SupportUtil.hasBackgroundLocationPermission(context)) {
                SystemLogger.w(TAG, "Not starting tracking at boot: background location permission missing");
                return;
            }
            Intent loggerIntent = new Intent(context, LoggerService.class);
            context.startForegroundService(loggerIntent);
        }
    }
}
