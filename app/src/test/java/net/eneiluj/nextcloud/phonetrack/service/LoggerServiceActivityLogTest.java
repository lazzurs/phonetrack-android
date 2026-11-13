package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBSyslog;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * What the logger writes to the in-app activity log for each position: nothing while the log is
 * off, and the usual lines (stored in a batch, no broadcast) while it is on.
 */
@RunWith(AndroidJUnit4.class)
public class LoggerServiceActivityLogTest {

    private Application app;
    private PhoneTrackSQLiteOpenHelper db;
    private LocationManager locationManager;
    private ServiceController<LoggerService> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        SystemLogger.resetForTesting();
        app = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS);
        locationManager = app.getSystemService(LocationManager.class);
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        db = PhoneTrackSQLiteOpenHelper.getInstance(app);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        SystemLogger.resetForTesting();
        ShadowAlarmManager.reset();
        DbTestSupport.resetDatabase();
    }

    private long addLogjob() {
        // 60 s interval, 50 m maximum accuracy, 30 s timeout; the server refuses connections
        return db.addLogjob(new DBLogjob(0, "test", "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, false, false, 30,
                false, true, 0, null, null, false));
    }

    private static void idleAll() {
        for (Looper looper : new ArrayList<>(ShadowLooper.getAllLoopers())) {
            shadowOf(looper).idle();
        }
    }

    private void startServiceAndDeliver(float... accuracies) {
        controller = Robolectric.buildService(LoggerService.class).create();
        idleAll();
        for (float accuracy : accuracies) {
            // updates come at most once a second
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS);
            Location l = new Location(LocationManager.GPS_PROVIDER);
            l.setLatitude(53.35);
            l.setLongitude(-6.26);
            l.setAccuracy(accuracy);
            l.setTime(System.currentTimeMillis());
            l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            shadowOf(locationManager).simulateLocation(l);
            idleAll();
        }
    }

    private List<String> activityLog() {
        SystemLogger.flush();
        List<String> messages = new ArrayList<>();
        for (DBSyslog line : db.getSyslogs(null, null)) {
            messages.add(line.getMessage());
        }
        return messages;
    }

    private boolean anyNewSyslogBroadcast() {
        for (Intent intent : shadowOf(app).getBroadcastIntents()) {
            if (intent.getAction() != null && intent.getAction().contains("syslog")) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void logOffStoresNothingForRejectedAndAcceptedFixes() {
        long id = addLogjob();
        // a fix too inaccurate to log, then a good one
        startServiceAndDeliver(500, 5);

        assertEquals(1, db.getLogjobLocationNotSyncedCount(id));
        assertTrue(activityLog().isEmpty());
        assertFalse(anyNewSyslogBroadcast());
    }

    @Test
    public void logOnRecordsTheFixesWithoutBroadcasts() {
        SystemLogger.setEnabled(true);
        long id = addLogjob();
        startServiceAndDeliver(500, 5);

        assertEquals(1, db.getLogjobLocationNotSyncedCount(id));
        List<String> log = activityLog();
        long fixLines = log.stream().filter(m -> m.startsWith("Fix from gps")).count();
        assertEquals(log.toString(), 2, fixLines);
        assertTrue(log.stream().anyMatch(m -> m.startsWith("location changed [GPS]")));
        assertFalse(anyNewSyslogBroadcast());
    }
}
