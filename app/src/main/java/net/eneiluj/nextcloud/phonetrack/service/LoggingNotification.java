package net.eneiluj.nextcloud.phonetrack.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The ongoing notification of {@link LoggerService}: what runs, what waits, uploads that keep
 * failing, and a stop button.
 */
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
     * @param paused why log jobs are paused, or null
     */
    static void setContent(Context context, NotificationCompat.Builder builder, int running,
                           @Nullable String paused, PhoneTrackSQLiteOpenHelper db) {
        int pending = db.getLocationNotSyncedCount();
        String title = running > 0
                ? context.getResources().getQuantityString(R.plurals.logging_notification_title, running, running)
                : context.getString(R.string.notification_channel_logging);
        String pendingText = pending > 0
                ? context.getResources().getQuantityString(R.plurals.a11y_logjob_pending, pending, pending)
                : context.getString(R.string.logging_notification_nothing_pending);
        // said in the ongoing notification rather than in one more notification per failure
        String failure = uploadFailure(context, db.getLogjobsWithFailingUploads(UploadErrors.NOTIFY_AFTER_FAILURES), db);
        List<String> problems = new ArrayList<>();
        if (paused != null) {
            problems.add(paused);
        }
        if (failure != null) {
            problems.add(failure);
        }
        builder.setContentTitle(title);
        if (problems.isEmpty()) {
            builder.setContentText(pendingText)
                    .setStyle(null);
        } else {
            problems.add(pendingText);
            builder.setContentText(problems.get(0))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(TextUtils.join("\n", problems)));
        }
    }

    @Nullable
    private static String uploadFailure(Context context, List<DBLogjob> failing, PhoneTrackSQLiteOpenHelper db) {
        if (failing.isEmpty()) {
            return null;
        }
        if (failing.size() > 1) {
            return context.getResources().getQuantityString(R.plurals.logging_notification_upload_failing_several,
                    failing.size(), failing.size());
        }
        DBLogjob logjob = failing.get(0);
        return context.getString(R.string.logging_notification_upload_failing,
                logjob.getTitle(), db.getLastSyncError(logjob.getId()).getMessage());
    }
}
