package net.eneiluj.nextcloud.phonetrack.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;

/** The ongoing notification of {@link LoggerService}: what runs, what waits, and a stop button. */
final class LoggingNotification {

    private LoggingNotification() {
    }

    @NonNull
    static NotificationCompat.Builder builder(Context context) {
        NotificationChannels.createLogging(context);
        Intent resultIntent = new Intent(context, LogjobsListViewActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(LogjobsListViewActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent open = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(context, NotificationChannels.LOGGING)
                .setSmallIcon(R.drawable.ic_notify_24dp)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(open)
                .addAction(R.drawable.ic_clear_white_24dp, context.getString(R.string.logging_notification_stop),
                        StopLoggingReceiver.pendingIntent(context));
    }

    /**
     * @param running log jobs being logged (not paused)
     * @param pending positions of every log job waiting to be uploaded
     * @param paused why log jobs are paused, or null
     */
    static void setContent(Context context, NotificationCompat.Builder builder, int running, int pending,
                           @Nullable String paused) {
        String title = running > 0
                ? context.getResources().getQuantityString(R.plurals.logging_notification_title, running, running)
                : context.getString(R.string.notification_channel_logging);
        String pendingText = pending > 0
                ? context.getResources().getQuantityString(R.plurals.a11y_logjob_pending, pending, pending)
                : context.getString(R.string.logging_notification_nothing_pending);
        builder.setContentTitle(title);
        if (paused == null) {
            builder.setContentText(pendingText)
                    .setStyle(null);
        } else {
            builder.setContentText(paused)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(paused + "\n" + pendingText));
        }
    }
}
