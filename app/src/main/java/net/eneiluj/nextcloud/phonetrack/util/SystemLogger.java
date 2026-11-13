package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import net.eneiluj.nextcloud.phonetrack.BuildConfig;
import net.eneiluj.nextcloud.phonetrack.model.DBSyslog;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Logcat (debug builds) plus the in-app activity log, which the user switches on in the activity
 * log screen.
 *
 * The activity log is written in batches: lines wait in memory and are stored in one transaction
 * a few seconds later (or once {@link #FLUSH_LINES} wait), so a burst of lines is one disk commit
 * instead of one per line. Warnings, errors and lines logged while the activity log screen is open
 * are stored at once. The table keeps the newest {@link #MAX_ROWS} lines.
 */
public class SystemLogger {
    private static final String TAG = SystemLogger.class.getSimpleName();

    /** The activity log keeps at most this many lines; older ones are deleted as new ones come. */
    public static final int MAX_ROWS = 20_000;
    /** Lines waiting in memory are stored after this delay ... */
    private static final long FLUSH_DELAY_MILLIS = 5_000;
    /** ... or as soon as this many wait. */
    @VisibleForTesting
    static final int FLUSH_LINES = 200;

    /** Receives the lines just stored, on a background thread. */
    public interface Listener {
        void onNewLines(@NonNull List<DBSyslog> lines);
    }

    private static volatile boolean enabled = false;
    private static volatile Context appContext = null;
    private static volatile Listener listener = null;
    private static volatile long flushDelayMillis = FLUSH_DELAY_MILLIS;

    /** Guards {@link #pending} and the scheduling flags. */
    private static final Object pendingLock = new Object();
    private static List<DBSyslog> pending = new ArrayList<>();
    private static boolean delayedFlushScheduled = false;
    /** Held while a batch is written, so batches reach the table (and the listener) in order. */
    private static final Object writeLock = new Object();
    private static ScheduledExecutorService writer = null;

    public static void setEnabled(boolean newEnabled) {
        enabled = newEnabled;
    }

    public static boolean getEnabled() {
        return enabled;
    }

    /**
     * Whether a line logged now goes anywhere: logcat in debug builds, or the activity log the user
     * switched on. Code that runs for every position, alarm or upload checks this before building
     * a message, so a release build with the activity log off does no logging work at all.
     */
    public static boolean isActive() {
        return BuildConfig.DEBUG || enabled;
    }

    /** Called once by the application; the database is looked up when lines are stored. */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    /**
     * The activity log screen listens while it is visible: lines are then stored and handed to it
     * at once instead of in batches. Replaces any earlier listener.
     */
    public static void setListener(@Nullable Listener newListener) {
        listener = newListener;
    }

    /** Stop listening, unless another listener replaced this one meanwhile. */
    public static void removeListener(@NonNull Listener oldListener) {
        if (listener == oldListener) {
            listener = null;
        }
    }

    public static void v(String tag, String message) {
        Log.v(tag, message);
        if (enabled) {
            add("v", tag, message);
        }
    }

    public static void d(String tag, String message) {
        Log.d(tag, message);
        if (enabled) {
            add("d", tag, message);
        }
    }

    public static void i(String tag, String message) {
        Log.i(tag, message);
        if (enabled) {
            add("i", tag, message);
        }
    }

    // Messages reach logcat in debug builds only (they can contain phone numbers and the like);
    // the in-app system log below is the user's own, opt-in record. v/d/i are stripped by R8.
    public static void w(String tag, String message) {
        if (BuildConfig.DEBUG) { Log.w(tag, message); }
        if (enabled) {
            add("w", tag, message);
        }
    }

    public static void e(String tag, String message) {
        if (BuildConfig.DEBUG) { Log.e(tag, message); }
        if (enabled) {
            add("e", tag, message);
        }
    }

    public static void wtf(String tag, String message) {
        if (BuildConfig.DEBUG) { Log.wtf(tag, message); }
        if (enabled) {
            add("f", tag, message);
        }
    }

    private static void add(String level, String tag, String message) {
        DBSyslog line = new DBSyslog(message, tag, level, System.currentTimeMillis() / 1000);
        // a warning or an error may come just before the process dies: do not keep it waiting
        boolean urgent = "w".equals(level) || "e".equals(level) || "f".equals(level);
        synchronized (pendingLock) {
            pending.add(line);
            if (urgent || listener != null || pending.size() == FLUSH_LINES) {
                writer().execute(SystemLogger::flushQuietly);
            } else if (!delayedFlushScheduled) {
                delayedFlushScheduled = true;
                writer().schedule(() -> {
                    synchronized (pendingLock) {
                        delayedFlushScheduled = false;
                    }
                    flushQuietly();
                }, flushDelayMillis, TimeUnit.MILLISECONDS);
            }
        }
    }

    private static ScheduledExecutorService writer() {
        // called with pendingLock held
        if (writer == null) {
            writer = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SystemLogger");
                t.setDaemon(true);
                return t;
            });
        }
        return writer;
    }

    private static void flushQuietly() {
        try {
            flush();
        } catch (RuntimeException e) {
            // logging must never take the app down
            Log.w(TAG, "Unable to store activity log lines: " + e);
        }
    }

    /**
     * Store the lines waiting in memory now, on the calling thread (which must not be the main
     * thread when lines may be waiting). Readers of the activity log call this first.
     */
    public static void flush() {
        synchronized (writeLock) {
            List<DBSyslog> lines;
            synchronized (pendingLock) {
                if (pending.isEmpty()) {
                    return;
                }
                lines = pending;
                pending = new ArrayList<>();
            }
            Context context = appContext;
            if (context == null) {
                return;
            }
            PhoneTrackSQLiteOpenHelper.getInstance(context).addSyslogs(lines, MAX_ROWS);
            Listener l = listener;
            if (l != null) {
                l.onNewLines(lines);
            }
        }
    }

    /** Delete every line, stored or still waiting. */
    public static void clear(Context context) {
        synchronized (writeLock) {
            synchronized (pendingLock) {
                pending.clear();
            }
            PhoneTrackSQLiteOpenHelper.getInstance(context).clearSyslog();
        }
    }

    @VisibleForTesting
    static int pendingCount() {
        synchronized (pendingLock) {
            return pending.size();
        }
    }

    @VisibleForTesting
    static void setFlushDelayForTesting(long millis) {
        flushDelayMillis = millis;
    }

    /** Back to the state of a new process: off, nothing waiting or scheduled, no listener. */
    @VisibleForTesting
    public static void resetForTesting() {
        enabled = false;
        listener = null;
        flushDelayMillis = FLUSH_DELAY_MILLIS;
        synchronized (writeLock) {
            synchronized (pendingLock) {
                pending.clear();
                delayedFlushScheduled = false;
                if (writer != null) {
                    // a flush scheduled by an earlier test must not run in the next one
                    writer.shutdownNow();
                    writer = null;
                }
            }
        }
    }
}
