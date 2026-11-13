package net.eneiluj.nextcloud.phonetrack.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;

import java.util.Map;

/**
 * Receiver for boot completed broadcast
 *
 */

public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = BootCompletedReceiver.class.getSimpleName();

    static final String PROBLEMS_CHANNEL_ID = "tracking_problems";
    static final int AUTOSTART_SKIPPED_NOTIFICATION_ID = 1526756641;

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
                SystemLogger.w(TAG, "Not starting tracking at boot: "
                        + TrackingReadiness.serviceGate(TrackingReadiness.read(context)));
                if (hasEnabledLogjobs(context)) {
                    notifyAutostartSkipped(context);
                }
                return;
            }
            Intent loggerIntent = new Intent(context, LoggerService.class);
            context.startForegroundService(loggerIntent);
        }
    }

    private static boolean hasEnabledLogjobs(Context context) {
        Map<String, Integer> enabled = PhoneTrackSQLiteOpenHelper.getInstance(context).getEnabledCount();
        Integer count = enabled.get("1");
        return count != null && count > 0;
    }

    /** Say that logging did not start, and why; tapping it opens the log job list and its status banner. */
    private static void notifyAutostartSkipped(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(PROBLEMS_CHANNEL_ID,
                context.getString(R.string.tracking_problems_channel), NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, LogjobsListViewActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = context.getString(R.string.autostart_skipped_text,
                TrackingReadiness.backgroundOptionLabel(context));
        manager.notify(AUTOSTART_SKIPPED_NOTIFICATION_ID, new NotificationCompat.Builder(context, PROBLEMS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify_24dp)
                .setContentTitle(context.getString(R.string.autostart_skipped_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build());
    }
}
