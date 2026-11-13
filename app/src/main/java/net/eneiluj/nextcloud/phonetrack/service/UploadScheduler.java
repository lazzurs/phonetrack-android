package net.eneiluj.nextcloud.phonetrack.service;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;
import androidx.work.ExistingWorkPolicy;
import androidx.work.WorkManager;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Applies {@link UploadPolicy} before any upload work is queued, so that a position that is
 * held back ("send points by bunch") does not start a worker or wake the radio. Held back
 * positions get a delayed work per logjob when "send at the latest after" is set, which
 * sends them even if no other position is ever logged (phone not moving).
 */
public final class UploadScheduler {

    private static final long SECONDS_PER_HOUR = 3600;

    private UploadScheduler() {
    }

    /** The two global "Power/data saving" upload settings. */
    public static final class Settings {
        /** 0 and 1 both mean: send every point straight away */
        public final long minBunch;
        /** 0 = no time limit */
        public final long maxDelaySeconds;

        @VisibleForTesting
        Settings(long minBunch, long maxDelaySeconds) {
            this.minBunch = minBunch;
            this.maxDelaySeconds = maxDelaySeconds;
        }

        @NonNull
        public static Settings read(Context context) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            return new Settings(
                    readNumber(prefs, context.getString(R.string.pref_key_group_sync)),
                    readNumber(prefs, context.getString(R.string.pref_key_send_deadline)) * SECONDS_PER_HOUR);
        }

        private static long readNumber(SharedPreferences prefs, String key) {
            try {
                return Math.max(0, Long.parseLong(prefs.getString(key, "0")));
            } catch (NumberFormatException | ClassCastException e) {
                return 0;
            }
        }

        /** Without a bunch size nothing is held back, so there is nothing to put a time limit on. */
        public boolean holdsPointsBack() {
            return minBunch > 1;
        }
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    /** A position of this logjob was just stored: upload now, or make sure its deadline is set. */
    public static void onPointStored(Context context, long logjobId) {
        Settings settings = Settings.read(context);
        if (!settings.holdsPointsBack()) {
            WebTrackWorker.enqueue(context, logjobId);
            return;
        }
        long[] pending = PhoneTrackSQLiteOpenHelper.getInstance(context).getPendingUploadStats(logjobId);
        if (pending == null) {
            return;
        }
        long now = now();
        UploadPolicy.Decision decision = UploadPolicy.decide(
                (int) pending[0], pending[1], now, settings.minBunch, settings.maxDelaySeconds, false);
        if (decision.action == UploadPolicy.Action.UPLOAD_NOW) {
            WebTrackWorker.enqueue(context, logjobId);
        } else if (decision.hasDeadline()) {
            // KEEP: later positions must not push the deadline of the oldest one back
            WebTrackWorker.enqueueDeadline(context, logjobId, decision.deadline - now, ExistingWorkPolicy.KEEP);
        }
    }

    /**
     * For the triggers that carry no new position (tracking service started, network is back):
     * upload if any logjob has enough positions or is past its deadline, otherwise queue nothing.
     */
    public static void uploadWhatIsDue(Context context) {
        Settings settings = Settings.read(context);
        Map<Long, long[]> stats = PhoneTrackSQLiteOpenHelper.getInstance(context).getPendingUploadStats();
        long now = now();
        boolean due = false;
        for (Map.Entry<Long, long[]> entry : stats.entrySet()) {
            long[] pending = entry.getValue();
            UploadPolicy.Decision decision = UploadPolicy.decide(
                    (int) pending[0], pending[1], now, settings.minBunch, settings.maxDelaySeconds, false);
            if (decision.action == UploadPolicy.Action.UPLOAD_NOW) {
                due = true;
            } else if (decision.hasDeadline()) {
                WebTrackWorker.enqueueDeadline(context, entry.getKey(), decision.deadline - now, ExistingWorkPolicy.KEEP);
            }
        }
        if (due) {
            WebTrackWorker.enqueue(context, WebTrackWorker.ALL_LOGJOBS);
        }
    }

    /** The bunch size or the time limit was changed: apply it to the positions already waiting. */
    public static void onSettingsChanged(Context context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WebTrackWorker.DEADLINE_TAG);
        uploadWhatIsDue(context);
    }

    /**
     * Called by the worker after a run that uploaded something: the deadline of a logjob belongs
     * to its oldest waiting position, so drop it when nothing is waiting any more and move it
     * when positions logged during the upload are held back.
     *
     * @param deadlineRun the calling worker is the deadline work of this logjob
     */
    static void afterUpload(Context context, long logjobId, boolean deadlineRun) {
        Settings settings = Settings.read(context);
        if (!settings.holdsPointsBack() || settings.maxDelaySeconds <= 0) {
            return;
        }
        PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        List<Long> logjobIds;
        if (logjobId == WebTrackWorker.ALL_LOGJOBS) {
            logjobIds = new ArrayList<>();
            for (DBLogjob logjob : db.getLogjobs()) {
                logjobIds.add(logjob.getId());
            }
        } else {
            logjobIds = Collections.singletonList(logjobId);
        }
        Map<Long, long[]> stats = db.getPendingUploadStats();
        long now = now();
        for (long id : logjobIds) {
            long[] pending = stats.get(id);
            if (pending == null) {
                if (!deadlineRun) {
                    WorkManager.getInstance(context).cancelUniqueWork(WebTrackWorker.deadlineName(id));
                }
                continue;
            }
            UploadPolicy.Decision decision = UploadPolicy.decide(
                    (int) pending[0], pending[1], now, settings.minBunch, settings.maxDelaySeconds, false);
            if (decision.action == UploadPolicy.Action.WAIT && decision.hasDeadline()) {
                // a running work cannot replace itself: queue the next deadline behind it
                WebTrackWorker.enqueueDeadline(context, id, decision.deadline - now,
                        deadlineRun ? ExistingWorkPolicy.APPEND_OR_REPLACE : ExistingWorkPolicy.REPLACE);
            }
        }
    }
}
