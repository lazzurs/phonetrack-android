package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/** Deleting a log job that is running must stop everything the service started for it. */
@RunWith(AndroidJUnit4.class)
public class LoggerServiceDeleteTest {

    private Context context;
    private PhoneTrackSQLiteOpenHelper db;
    private ServiceController<LoggerService> controller;
    private AlarmManager alarmManager;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        context = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        alarmManager = context.getSystemService(AlarmManager.class);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        DbTestSupport.resetDatabase();
    }

    private ServiceController<LoggerService> startService() {
        ServiceController<LoggerService> c = Robolectric.buildService(LoggerService.class).create();
        c.get().onStartCommand(new Intent(context, LoggerService.class), 0, 1);
        return c;
    }

    private long enabledLogjob() {
        return db.addLogjob(new DBLogjob(0, "custom", "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, false, false, 60, false, true, 0, null, null, false));
    }

    @SuppressWarnings("unchecked")
    private Object workerOf(LoggerService service, long id) throws Exception {
        Field field = LoggerService.class.getDeclaredField("mLogjobWorkers");
        field.setAccessible(true);
        return ((Map<Long, Object>) field.get(service)).get(id);
    }

    private void scheduleNextSample(Object worker) throws Exception {
        Method method = worker.getClass().getMethod("scheduleSampleAfterInterval", long.class);
        method.setAccessible(true);
        method.invoke(worker, 60_000L);
    }

    private void notifyUpdated(long id) {
        Intent intent = new Intent(context, LoggerService.class);
        intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, true);
        intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, id);
        controller.get().onStartCommand(intent, 0, 1);
    }

    @Test
    public void deletingARunningLogjobCancelsItsNextSampleAlarm() throws Exception {
        long id = enabledLogjob();
        controller = startService();
        Object worker = workerOf(controller.get(), id);
        assertNotNull(worker);
        scheduleNextSample(worker);
        assertFalse(shadowOf(alarmManager).getScheduledAlarms().isEmpty());

        // the row is gone before the service hears about it, as in the UI
        db.deleteLogjob(id);
        notifyUpdated(id);

        assertTrue(shadowOf(alarmManager).getScheduledAlarms().isEmpty());
        assertEquals(null, workerOf(controller.get(), id));
    }

    @Test
    public void deletingALogjobCancelsItsQueuedUpload() throws Exception {
        long id = enabledLogjob();
        controller = startService();
        WebTrackWorker.enqueue(context, id);

        db.deleteLogjob(id);
        notifyUpdated(id);

        List<WorkInfo> work = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WebTrackWorker.uniqueName(id)).get();
        for (WorkInfo info : work) {
            assertEquals(WorkInfo.State.CANCELLED, info.getState());
        }
    }

    @Test
    public void disablingALogjobKeepsItsQueuedUpload() throws Exception {
        long id = enabledLogjob();
        controller = startService();
        WebTrackWorker.enqueue(context, id);

        db.toggleEnabled(db.getLogjob(id), null, false);
        notifyUpdated(id);

        List<WorkInfo> work = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WebTrackWorker.uniqueName(id)).get();
        assertEquals(1, work.size());
        assertEquals(WorkInfo.State.ENQUEUED, work.get(0).getState());
    }
}
