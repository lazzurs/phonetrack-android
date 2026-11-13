package net.eneiluj.nextcloud.phonetrack.service;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;

import java.util.concurrent.TimeUnit;

/**
 * Uploads unsynced positions when the network is available, and retries with exponential
 * backoff when an upload fails. Replaces the WebTrackService IntentService and its
 * hand-rolled retry alarm, which only retried while tracking was running.
 */
public class WebTrackWorker extends Worker {

    private static final String TAG = WebTrackWorker.class.getSimpleName();

    @VisibleForTesting
    static final String KEY_LOGJOB_ID = "logjobId";
    /** true = send whatever is waiting, ignoring the "send points by bunch" setting */
    @VisibleForTesting
    static final String KEY_FORCE = "force";
    @VisibleForTesting
    static final String KEY_DEADLINE = "deadline";
    static final String DEADLINE_TAG = "websync-deadline";
    @VisibleForTesting
    static final String MANUAL_NAME = "websync-manual";
    /** 0 = every logjob */
    public static final long ALL_LOGJOBS = 0;

    // positions logged during a run are picked up by running again, a few times at most
    private static final int MAX_PASSES = 3;
    // after that, wait for the next position (or a manual sync) to try again
    @VisibleForTesting
    static final int MAX_ATTEMPTS = 10;

    public WebTrackWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /**
     * Upload the positions of a logjob (or {@link #ALL_LOGJOBS}) as soon as there is a network.
     * At most one upload per logjob is queued: a queued one sends everything unsynced when it runs.
     */
    public static void enqueue(Context context, long logjobId) {
        OneTimeWorkRequest request = request(
                new Data.Builder().putLong(KEY_LOGJOB_ID, logjobId).build()).build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(logjobId), ExistingWorkPolicy.KEEP, request);
    }

    /**
     * Upload asked by the user: sends the positions of every logjob, including those held back
     * by the "send points by bunch" setting. Has its own name so that it is not dropped (KEEP)
     * in favour of an already queued upload that would hold them back.
     */
    public static void enqueueManual(Context context) {
        OneTimeWorkRequest request = request(new Data.Builder()
                .putLong(KEY_LOGJOB_ID, ALL_LOGJOBS)
                .putBoolean(KEY_FORCE, true)
                .build()).build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(MANUAL_NAME, ExistingWorkPolicy.KEEP, request);
    }

    /**
     * Send the positions of a logjob that are held back, in delaySeconds at the latest ("send at
     * the latest after" setting). Runs even if no other position is logged until then. The system
     * may run it later than asked to save battery (Doze), which is fine for a delay in hours.
     */
    static void enqueueDeadline(Context context, long logjobId, long delaySeconds, ExistingWorkPolicy policy) {
        OneTimeWorkRequest request = request(new Data.Builder()
                .putLong(KEY_LOGJOB_ID, logjobId)
                .putBoolean(KEY_FORCE, true)
                .putBoolean(KEY_DEADLINE, true)
                .build())
                .setInitialDelay(Math.max(0, delaySeconds), TimeUnit.SECONDS)
                .addTag(DEADLINE_TAG)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(deadlineName(logjobId), policy, request);
    }

    private static OneTimeWorkRequest.Builder request(Data input) {
        return new OneTimeWorkRequest.Builder(WebTrackWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(input)
                .addTag(TAG);
    }

    static String deadlineName(long logjobId) {
        return DEADLINE_TAG + "-" + logjobId;
    }

    /** Drop the queued upload of a logjob and its deadline (used when the logjob is deleted). */
    public static void cancel(Context context, long logjobId) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(logjobId));
        WorkManager.getInstance(context).cancelUniqueWork(deadlineName(logjobId));
    }

    @VisibleForTesting
    static String uniqueName(long logjobId) {
        return logjobId == ALL_LOGJOBS ? "websync-all" : "websync-" + logjobId;
    }

    @NonNull
    @Override
    public Result doWork() {
        long logjobId = getInputData().getLong(KEY_LOGJOB_ID, ALL_LOGJOBS);
        // kept across retries: a forced run that fails is still forced when it is tried again
        boolean force = getInputData().getBoolean(KEY_FORCE, false);
        Context context = getApplicationContext();
        WebTrackSync sync = new WebTrackSync(context);
        PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);

        boolean ok = true;
        boolean sentAny = false;
        for (int pass = 0; pass < MAX_PASSES && ok && !isStopped(); pass++) {
            ok = sync.sync(logjobId, force);
            sentAny |= sync.getSentCount() > 0;
            // KEEP drops requests made while this one runs: go again for positions that arrived
            // meanwhile, but not when nothing could be sent (e.g. held back by "group sync")
            if (sync.getSentCount() == 0 || unsyncedCount(db, logjobId) == 0) {
                break;
            }
        }
        if (sentAny) {
            UploadScheduler.afterUpload(context, logjobId, getInputData().getBoolean(KEY_DEADLINE, false));
        }
        refreshTrackingNotification(context);

        if (ok) {
            return Result.success();
        }
        if (getRunAttemptCount() + 1 >= MAX_ATTEMPTS) {
            SystemLogger.w(TAG, "Upload of logjob " + logjobId + " still failing after "
                    + MAX_ATTEMPTS + " attempts, waiting for the next position");
            return Result.failure();
        }
        return Result.retry();
    }

    private static int unsyncedCount(PhoneTrackSQLiteOpenHelper db, long logjobId) {
        return logjobId == ALL_LOGJOBS
                ? db.getLocationNotSyncedCount()
                : db.getLogjobLocationNotSyncedCount(logjobId);
    }

    private static void refreshTrackingNotification(Context context) {
        if (LoggerService.isRunning()) {
            // the app has a foreground service, so this is not a background start
            Intent intent = new Intent(context, LoggerService.class);
            intent.putExtra(LoggerService.UPDATE_NOTIFICATION, true);
            try {
                context.startService(intent);
            } catch (IllegalStateException e) {
                SystemLogger.w(TAG, "Unable to update the tracking notification: " + e);
            }
        }
    }
}
