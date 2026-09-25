package net.eneiluj.nextcloud.phonetrack.util;

import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.VisibleForTesting;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replacement for the deprecated android.os.AsyncTask, keeping its three steps and its
 * two ways of running so tasks convert without behaviour changes:
 * {@link #execute} runs tasks one after another on a shared thread (like AsyncTask.execute),
 * {@link #executeInParallel} runs them concurrently (like executeOnExecutor(THREAD_POOL_EXECUTOR)).
 *
 * An exception thrown by {@link #doInBackground} is not caught, so it still crashes loudly
 * as it did with AsyncTask instead of silently skipping {@link #onPostExecute}.
 *
 * The steps are deliberately not annotated @MainThread/@WorkerThread: lint's thread inference
 * would then propagate through the (unchanged) call graph and flag long-standing code paths
 * that AsyncTask's own annotations never reached.
 */
public abstract class BackgroundTask<Params, Result> {

    private static final ExecutorService SERIAL = Executors.newSingleThreadExecutor(threads("BackgroundTask-serial"));
    private static final ExecutorService PARALLEL = Executors.newCachedThreadPool(threads("BackgroundTask"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Runs on the calling thread (normally the main thread), before {@link #doInBackground}. */
    protected void onPreExecute() {
    }

    /** Runs on a background thread. */
    protected abstract Result doInBackground(Params[] params);

    /** Runs on the main thread with the result of {@link #doInBackground}. */
    protected void onPostExecute(Result result) {
    }

    /** Run after the other serial tasks. */
    @SafeVarargs
    public final void execute(Params... params) {
        run(SERIAL, params);
    }

    /** Run now, concurrently with other tasks. */
    @SafeVarargs
    public final void executeInParallel(Params... params) {
        run(PARALLEL, params);
    }

    private void run(ExecutorService executor, Params[] params) {
        onPreExecute();
        executor.execute(() -> {
            // as AsyncTask did: don't compete with the UI thread
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            Result result = doInBackground(params);
            MAIN.post(() -> onPostExecute(result));
        });
    }

    /** Blocks until the serial queue has run everything submitted before this call. */
    @VisibleForTesting
    public static void awaitSerialQueue() throws Exception {
        SERIAL.submit(() -> { }).get();
    }

    private static ThreadFactory threads(String name) {
        AtomicInteger count = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
