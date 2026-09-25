package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestWorkerBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.CorrectingLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class WebTrackWorkerTest {

    private Context context;
    private PhoneTrackSQLiteOpenHelper db;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        context = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
    }

    @After
    public void tearDown() {
        DbTestSupport.resetDatabase();
    }

    /** A custom logjob whose server refuses connections, with one position to upload. */
    private long logjobWithUnreachableServer() {
        // token and device name are "" for custom logjobs (see EditCustomLogjobFragment)
        long id = db.addLogjob(new DBLogjob(0, "custom", "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, false, false, 60, false, false, 0, null, null, false));
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(53.35);
        location.setLongitude(-6.26);
        location.setTime(System.currentTimeMillis());
        db.addLocation(id, new CorrectingLocation(location), 80.0);
        return id;
    }

    private ListenableWorker.Result run(long logjobId, int runAttemptCount) {
        WebTrackWorker worker = TestWorkerBuilder.from(context, WebTrackWorker.class, Executors.newSingleThreadExecutor())
                .setInputData(new Data.Builder().putLong(WebTrackWorker.KEY_LOGJOB_ID, logjobId).build())
                .setRunAttemptCount(runAttemptCount)
                .build();
        return worker.doWork();
    }

    @Test
    public void succeedsWhenThereIsNothingToUpload() {
        assertEquals(ListenableWorker.Result.success(), run(WebTrackWorker.ALL_LOGJOBS, 0));
    }

    @Test
    public void retriesWhenTheUploadFails() {
        long id = logjobWithUnreachableServer();

        assertEquals(ListenableWorker.Result.retry(), run(id, 0));
        // the position is kept for the next attempt, and the failure is a network one
        assertEquals(1, db.getLogjobLocationNotSyncedCount(id));
        String error = db.getLastSyncError(id).getMessage();
        assertTrue(error, error.toLowerCase().contains("connect"));
    }

    @Test
    public void givesUpAfterTheMaximumNumberOfAttempts() {
        long id = logjobWithUnreachableServer();

        assertEquals(ListenableWorker.Result.failure(), run(id, WebTrackWorker.MAX_ATTEMPTS - 1));
        assertEquals(1, db.getLogjobLocationNotSyncedCount(id));
    }

    @Test
    public void enqueueKeepsOneUploadPerLogjobAndWaitsForTheNetwork() throws Exception {
        WebTrackWorker.enqueue(context, 42);
        WebTrackWorker.enqueue(context, 42);
        WebTrackWorker.enqueue(context, WebTrackWorker.ALL_LOGJOBS);

        List<WorkInfo> forLogjob = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WebTrackWorker.uniqueName(42)).get();
        assertEquals(1, forLogjob.size());
        assertEquals(NetworkType.CONNECTED, forLogjob.get(0).getConstraints().getRequiredNetworkType());
        assertEquals(WorkInfo.State.ENQUEUED, forLogjob.get(0).getState());

        assertTrue(WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WebTrackWorker.uniqueName(WebTrackWorker.ALL_LOGJOBS)).get().size() == 1);
    }
}
