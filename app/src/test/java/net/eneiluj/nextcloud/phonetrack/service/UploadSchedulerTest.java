package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestDriver;
import androidx.work.testing.WorkManagerTestInitHelper;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.CorrectingLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * "Send points by bunch of at least" and "send at the latest after": what is queued in
 * WorkManager when a position is stored, and what the queued work uploads to a local server.
 */
@RunWith(AndroidJUnit4.class)
public class UploadSchedulerTest {

    private static final long HOUR_MS = TimeUnit.HOURS.toMillis(1);

    private Context context;
    private PhoneTrackSQLiteOpenHelper db;
    private WorkManager workManager;
    private TestDriver driver;
    private ServerSocket server;
    private final AtomicInteger requests = new AtomicInteger();
    /** positions stored by the server while it answers, as if logged during the upload */
    private volatile long logDuringUploadFor;
    private final AtomicInteger logDuringUpload = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        DbTestSupport.resetDatabase();
        context = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
        workManager = WorkManager.getInstance(context);
        driver = WorkManagerTestInitHelper.getTestDriver(context);
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);

        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread thread = new Thread(this::serve, "upload-test-server");
        thread.setDaemon(true);
        thread.start();
    }

    /** Minimal HTTP server: answers 200 to every GET, one request per connection. */
    private void serve() {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                String line = in.readLine();
                if (line == null) {
                    continue;
                }
                while (line != null && !line.isEmpty()) {
                    line = in.readLine();
                }
                requests.incrementAndGet();
                if (logDuringUpload.getAndDecrement() > 0) {
                    addPoint(logDuringUploadFor, 0);
                }
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .getBytes(StandardCharsets.ISO_8859_1));
                socket.getOutputStream().flush();
            } catch (IOException e) {
                // closed by tearDown, or the client went away
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        server.close();
        DbTestSupport.resetDatabase();
    }

    private void settings(int bunch, int hours) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.pref_key_group_sync), String.valueOf(bunch))
                .putString(context.getString(R.string.pref_key_send_deadline), String.valueOf(hours))
                .commit();
    }

    /** A custom logjob that sends its positions to the local server. */
    private long logjob() {
        String url = "http://127.0.0.1:" + server.getLocalPort() + "/log?lat=%LAT&lon=%LON";
        return db.addLogjob(new DBLogjob(0, "custom", url, "", "",
                60, 0, 50, false, false, false, 60, false, false, 0, null, null, false));
    }

    /** What LoggerService does with an accepted position, minus the notification. */
    private void storePoint(long logjobId, long ageMs) {
        addPoint(logjobId, ageMs);
        UploadScheduler.onPointStored(context, logjobId);
    }

    private void addPoint(long logjobId, long ageMs) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(53.35);
        location.setLongitude(-6.26);
        location.setTime(System.currentTimeMillis() - ageMs);
        db.addLocation(logjobId, new CorrectingLocation(location), 80.0);
    }

    private List<WorkInfo> work(String uniqueName) throws Exception {
        return workManager.getWorkInfosForUniqueWork(uniqueName).get();
    }

    private WorkInfo only(String uniqueName) throws Exception {
        List<WorkInfo> infos = work(uniqueName);
        assertEquals(uniqueName, 1, infos.size());
        return infos.get(0);
    }

    private WorkInfo onlyPending(String uniqueName) throws Exception {
        WorkInfo found = null;
        for (WorkInfo info : work(uniqueName)) {
            if (!info.getState().isFinished()) {
                assertEquals("more than one pending " + uniqueName, null, found);
                found = info;
            }
        }
        return found;
    }

    private boolean pending(String uniqueName) throws Exception {
        return onlyPending(uniqueName) != null;
    }

    private void runNow(WorkInfo info) {
        driver.setInitialDelayMet(info.getId());
        driver.setAllConstraintsMet(info.getId());
    }

    private static void assertAbout(long expectedMs, long actualMs) {
        assertTrue("expected about " + expectedMs + " ms, was " + actualMs,
                actualMs > expectedMs - 60_000 && actualMs <= expectedMs);
    }

    @Test
    public void byDefaultEveryPointIsUploadedStraightAway() throws Exception {
        long id = logjob();
        storePoint(id, 0);

        WorkInfo upload = only(WebTrackWorker.uniqueName(id));
        assertEquals(0, upload.getInitialDelayMillis());
        assertEquals(NetworkType.CONNECTED, upload.getConstraints().getRequiredNetworkType());
        assertTrue(work(WebTrackWorker.deadlineName(id)).isEmpty());

        runNow(upload);
        assertEquals(1, requests.get());
        assertEquals(0, db.getLogjobLocationNotSyncedCount(id));
    }

    @Test
    public void aTimeLimitWithoutBunchChangesNothing() throws Exception {
        settings(0, 2);
        long id = logjob();
        storePoint(id, 0);

        assertEquals(0, only(WebTrackWorker.uniqueName(id)).getInitialDelayMillis());
        assertTrue(work(WebTrackWorker.deadlineName(id)).isEmpty());
    }

    @Test
    public void aPointThatDoesNotCompleteTheBunchQueuesNothing() throws Exception {
        settings(3, 0);
        long id = logjob();
        storePoint(id, 0);
        storePoint(id, 0);

        assertTrue(work(WebTrackWorker.uniqueName(id)).isEmpty());
        assertTrue(work(WebTrackWorker.deadlineName(id)).isEmpty());
        assertTrue(work(WebTrackWorker.uniqueName(WebTrackWorker.ALL_LOGJOBS)).isEmpty());

        // the third one completes the bunch
        storePoint(id, 0);
        runNow(only(WebTrackWorker.uniqueName(id)));
        assertEquals(3, requests.get());
        assertEquals(0, db.getLogjobLocationNotSyncedCount(id));
    }

    @Test
    public void heldBackPointsGetOneDeadlineThatLaterPointsDoNotPushBack() throws Exception {
        settings(10, 2);
        long id = logjob();
        storePoint(id, 0);

        assertTrue(work(WebTrackWorker.uniqueName(id)).isEmpty());
        WorkInfo deadline = only(WebTrackWorker.deadlineName(id));
        assertEquals(WorkInfo.State.ENQUEUED, deadline.getState());
        assertEquals(NetworkType.CONNECTED, deadline.getConstraints().getRequiredNetworkType());
        assertAbout(2 * HOUR_MS, deadline.getInitialDelayMillis());

        storePoint(id, 0);
        assertEquals(deadline.getId(), only(WebTrackWorker.deadlineName(id)).getId());
        assertTrue(work(WebTrackWorker.uniqueName(id)).isEmpty());
        assertEquals(0, requests.get());

        // the deadline is reached without any other position: both are sent
        runNow(deadline);
        assertEquals(2, requests.get());
        assertEquals(0, db.getLogjobLocationNotSyncedCount(id));
        assertEquals(WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(deadline.getId()).get().getState());

        // and the next held back position gets a deadline of its own
        storePoint(id, 0);
        assertTrue(pending(WebTrackWorker.deadlineName(id)));
    }

    @Test
    public void theDeadlineCountsFromTheOldestWaitingPoint() throws Exception {
        settings(10, 2);
        long id = logjob();
        addPoint(id, HOUR_MS);
        storePoint(id, 0);

        assertAbout(HOUR_MS, only(WebTrackWorker.deadlineName(id)).getInitialDelayMillis());
    }

    @Test
    public void aPointAlreadyPastTheTimeLimitIsUploadedStraightAway() throws Exception {
        settings(10, 2);
        long id = logjob();
        storePoint(id, 3 * HOUR_MS);

        runNow(only(WebTrackWorker.uniqueName(id)));
        assertEquals(1, requests.get());
    }

    @Test
    public void completingTheBunchCancelsTheDeadline() throws Exception {
        settings(2, 2);
        long id = logjob();
        storePoint(id, 0);
        WorkInfo deadline = only(WebTrackWorker.deadlineName(id));
        storePoint(id, 0);

        runNow(only(WebTrackWorker.uniqueName(id)));
        assertEquals(2, requests.get());
        assertFalse(pending(WebTrackWorker.deadlineName(id)));
        WorkInfo after = workManager.getWorkInfoById(deadline.getId()).get();
        assertTrue(after == null || after.getState() == WorkInfo.State.CANCELLED);
    }

    @Test
    public void pointsLeftAfterABunchUploadGetADeadlineOfTheirOwn() throws Exception {
        settings(2, 2);
        long id = logjob();
        addPoint(id, HOUR_MS);
        storePoint(id, HOUR_MS);
        WorkInfo upload = only(WebTrackWorker.uniqueName(id));

        // 3 positions arrive during the upload: sent by the 2 extra passes, bar the last one
        logDuringUploadFor = id;
        logDuringUpload.set(3);
        runNow(upload);

        assertEquals(1, db.getLogjobLocationNotSyncedCount(id));
        assertAbout(2 * HOUR_MS, onlyPending(WebTrackWorker.deadlineName(id)).getInitialDelayMillis());
    }

    @Test
    public void pointsLoggedDuringADeadlineRunAreSentOrGetTheNextDeadline() throws Exception {
        settings(10, 2);
        long id = logjob();
        storePoint(id, 0);
        WorkInfo deadline = only(WebTrackWorker.deadlineName(id));

        // a position arrives during each of the 3 passes of the run: the last one is left
        logDuringUploadFor = id;
        logDuringUpload.set(3);
        runNow(deadline);

        assertEquals(3, requests.get());
        assertEquals(1, db.getLogjobLocationNotSyncedCount(id));
        assertEquals(WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(deadline.getId()).get().getState());
        // it was queued behind the running deadline work, and is now waiting for its own delay
        WorkInfo next = onlyPending(WebTrackWorker.deadlineName(id));
        assertNotEquals(deadline.getId(), next.getId());
        assertEquals(WorkInfo.State.ENQUEUED, next.getState());
        assertAbout(2 * HOUR_MS, next.getInitialDelayMillis());

        runNow(next);
        assertEquals(4, requests.get());
        assertEquals(0, db.getLogjobLocationNotSyncedCount(id));
    }

    @Test
    public void cancellingTheUploadsOfADeletedLogjobAlsoDropsItsDeadline() throws Exception {
        settings(2, 2);
        long id = logjob();
        long other = logjob();
        storePoint(id, 0);
        storePoint(other, 0);
        assertTrue(pending(WebTrackWorker.deadlineName(id)));
        storePoint(id, 0);
        assertTrue(pending(WebTrackWorker.uniqueName(id)));

        // what LoggerService does when it is told that the logjob was deleted
        WebTrackWorker.cancel(context, id);

        assertFalse(pending(WebTrackWorker.uniqueName(id)));
        assertFalse(pending(WebTrackWorker.deadlineName(id)));
        assertTrue(pending(WebTrackWorker.deadlineName(other)));
        assertEquals(0, requests.get());
    }

    @Test
    public void aDeadlineOutlivingItsLogjobDoesNothing() throws Exception {
        settings(10, 2);
        long id = logjob();
        storePoint(id, 0);
        WorkInfo deadline = only(WebTrackWorker.deadlineName(id));

        db.deleteLogjob(id);
        runNow(deadline);

        assertEquals(WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(deadline.getId()).get().getState());
        assertEquals(0, requests.get());
    }

    @Test
    public void triggersWithoutNewPointQueueNothingWhenNothingIsDue() throws Exception {
        String all = WebTrackWorker.uniqueName(WebTrackWorker.ALL_LOGJOBS);

        // nothing to upload at all
        UploadScheduler.uploadWhatIsDue(context);
        assertTrue(work(all).isEmpty());

        // held back: no upload, but the deadline is (re)created, e.g. after a restart of the app
        settings(10, 2);
        long id = logjob();
        addPoint(id, 0);
        UploadScheduler.uploadWhatIsDue(context);
        assertTrue(work(all).isEmpty());
        assertTrue(pending(WebTrackWorker.deadlineName(id)));

        // due
        settings(1, 0);
        UploadScheduler.uploadWhatIsDue(context);
        runNow(only(all));
        assertEquals(1, requests.get());
    }

    @Test
    public void changingTheSettingsAppliesToThePointsAlreadyWaiting() throws Exception {
        settings(10, 0);
        long id = logjob();
        storePoint(id, HOUR_MS);
        assertTrue(work(WebTrackWorker.deadlineName(id)).isEmpty());

        // a time limit is set
        settings(10, 3);
        UploadScheduler.onSettingsChanged(context);
        assertAbout(2 * HOUR_MS, onlyPending(WebTrackWorker.deadlineName(id)).getInitialDelayMillis());

        // then removed
        settings(10, 0);
        UploadScheduler.onSettingsChanged(context);
        assertFalse(pending(WebTrackWorker.deadlineName(id)));
        assertEquals(0, requests.get());

        // then the bunch is turned off: what was waiting is sent
        settings(0, 0);
        UploadScheduler.onSettingsChanged(context);
        runNow(only(WebTrackWorker.uniqueName(WebTrackWorker.ALL_LOGJOBS)));
        assertEquals(1, requests.get());
    }

    @Test
    public void manualUploadSendsWhatTheBunchHoldsBack() throws Exception {
        settings(10, 0);
        long id = logjob();
        storePoint(id, 0);
        storePoint(id, 0);
        storePoint(id, 0);

        WebTrackWorker.enqueueManual(context);
        runNow(only(WebTrackWorker.MANUAL_NAME));
        assertEquals(3, requests.get());
        assertEquals(0, db.getLogjobLocationNotSyncedCount(id));
    }

    @Test
    public void syncHoldsBackUnlessForcedOrPastTheTimeLimit() {
        settings(10, 2);
        long id = logjob();
        addPoint(id, 0);
        addPoint(id, 0);
        addPoint(id, 0);
        WebTrackSync sync = new WebTrackSync(context);

        assertTrue(sync.sync(id, false));
        assertEquals(0, sync.getSentCount());
        assertEquals(0, requests.get());

        assertTrue(sync.sync(id, true));
        assertEquals(3, sync.getSentCount());
        assertEquals(3, requests.get());

        // not forced, but the oldest point has waited longer than the limit
        addPoint(id, 3 * HOUR_MS);
        addPoint(id, 0);
        assertTrue(sync.sync(WebTrackWorker.ALL_LOGJOBS, false));
        assertEquals(2, sync.getSentCount());
        assertEquals(0, db.getLogjobLocationNotSyncedCount(id));
    }
}
