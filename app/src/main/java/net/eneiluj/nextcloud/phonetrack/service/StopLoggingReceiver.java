package net.eneiluj.nextcloud.phonetrack.service;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;

/**
 * "Stop" action of the logging notification: turns off every enabled log job, as the switch of
 * each row in the log job list does, and lets {@link LoggerService} stop.
 */
public class StopLoggingReceiver extends BroadcastReceiver {

    private static final String TAG = StopLoggingReceiver.class.getSimpleName();
    static final String ACTION_STOP_LOGGING = "net.eneiluj.nextcloud.phonetrack.action.STOP_LOGGING";

    static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, StopLoggingReceiver.class).setAction(ACTION_STOP_LOGGING);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_STOP_LOGGING.equals(intent.getAction())) {
            return;
        }
        int stopped = stopAll(context);
        SystemLogger.d(TAG, "Stopped " + stopped + " log jobs from the notification");
    }

    /** @return the number of log jobs that were turned off */
    static int stopAll(Context context) {
        PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        int stopped = 0;
        for (DBLogjob logjob : db.getLogjobs()) {
            if (!logjob.isEnabled()) {
                continue;
            }
            // the "reset statistics" setting only applies when a log job is turned on
            db.toggleEnabled(logjob, null, false);
            stopped++;

            Intent update = new Intent(context, LoggerService.class);
            update.putExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, true);
            update.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, logjob.getId());
            try {
                // the app has a foreground service while the notification is shown
                context.startService(update);
            } catch (IllegalStateException e) {
                SystemLogger.w(TAG, "Unable to notify LoggerService: " + e);
            }
        }
        if (stopped > 0) {
            // refresh the log job list if it is open (also its "enabled" filter)
            Intent changed = new Intent(SmsListener.BROADCAST_LOGJOB_LIST_UPDATED);
            changed.setPackage(context.getPackageName());
            context.sendBroadcast(changed);
        }
        return stopped;
    }
}
