package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
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
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

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
 * When a significant motion log job searches for a position. Robolectric cannot deliver trigger
 * sensor events, so motion is delivered through {@link LoggerService#onSignificantMotion}.
 */
@RunWith(AndroidJUnit4.class)
public class LoggerServiceSignificantMotionTest {

    private Application app;
    private PhoneTrackSQLiteOpenHelper db;
    private LocationManager locationManager;
    private AlarmManager alarmManager;
    private ServiceController<LoggerService> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS);
        locationManager = app.getSystemService(LocationManager.class);
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        alarmManager = app.getSystemService(AlarmManager.class);
        db = PhoneTrackSQLiteOpenHelper.getInstance(app);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        ShadowAlarmManager.reset();
        DbTestSupport.resetDatabase();
    }

    /** An enabled significant motion log job with "apply minimum time" (interval 60 s). */
    private long addLogjob(boolean hybrid) {
        return db.addLogjob(new DBLogjob(0, "test", "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, true, hybrid, 30,
                false, true, 0, null, null, false));
    }

    private void startService() {
        controller = Robolectric.buildService(LoggerService.class).create();
        idleAll();
    }

    private static void idleAll() {
        for (Looper looper : new ArrayList<>(ShadowLooper.getAllLoopers())) {
            shadowOf(looper).idle();
        }
    }

    private void deliverFix() {
        Location l = new Location(LocationManager.GPS_PROVIDER);
        l.setLatitude(53.3498);
        l.setLongitude(-6.2603);
        l.setAccuracy(5);
        l.setTime(System.currentTimeMillis());
        l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        shadowOf(locationManager).simulateLocation(l);
        idleAll();
    }

    private boolean searching() {
        return !shadowOf(locationManager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty();
    }

    private List<ShadowAlarmManager.ScheduledAlarm> alarms() {
        return shadowOf(alarmManager).getScheduledAlarms();
    }

    /** Let time pass until the only pending alarm, which must be the next point one, and deliver it. */
    private void reachNextPointAlarm() {
        List<ShadowAlarmManager.ScheduledAlarm> alarms = alarms();
        assertEquals(1, alarms.size());
        ShadowAlarmManager.ScheduledAlarm alarm = alarms.get(0);
        Intent intent = shadowOf(alarm.operation).getSavedIntent();
        assertTrue(intent.getBooleanExtra(LoggerService.GET_NEXT_POINT, false));
        ShadowLooper.idleMainLooper(alarm.getTriggerAtMs() - SystemClock.elapsedRealtime(), TimeUnit.MILLISECONDS);
        alarmManager.cancel(alarm.operation);
        controller.withIntent(intent).startCommand(0, 1);
        idleAll();
    }

    private void motion(long jobId) {
        controller.get().onSignificantMotion(jobId);
        idleAll();
    }

    @Test
    public void noWakeUpAlarmWhileThePhoneLiesStill() {
        long id = addLogjob(false);
        startService();
        deliverFix();
        assertEquals(1, db.getLogjobLocationNotSyncedCount(id));
        assertFalse(searching());

        // the interval is over and nothing moved: used to set the same alarm again, every interval
        reachNextPointAlarm();
        assertFalse(searching());
        assertTrue(alarms().isEmpty());
    }

    @Test
    public void motionLongAfterTheLastPointSearchesAtOnce() {
        long id = addLogjob(false);
        startService();
        deliverFix();
        reachNextPointAlarm();

        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS);
        motion(id);
        assertTrue(searching());
        deliverFix();
        assertEquals(2, db.getLogjobLocationNotSyncedCount(id));
    }

    @Test
    public void motionSoonAfterTheLastPointSearchesWhenTheIntervalIsOver() {
        long id = addLogjob(false);
        startService();
        deliverFix();
        // as if the alarm had been delivered on time while the phone lay still
        ShadowAlarmManager.ScheduledAlarm alarm = alarms().get(0);
        alarmManager.cancel(alarm.operation);
        controller.withIntent(shadowOf(alarm.operation).getSavedIntent()).startCommand(0, 1);
        idleAll();
        assertTrue(alarms().isEmpty());

        ShadowLooper.idleMainLooper(20, TimeUnit.SECONDS);
        motion(id);
        assertFalse(searching());
        List<ShadowAlarmManager.ScheduledAlarm> alarms = alarms();
        assertEquals(1, alarms.size());
        assertEquals(40_000, alarms.get(0).getTriggerAtMs() - SystemClock.elapsedRealtime());

        reachNextPointAlarm();
        assertTrue(searching());
    }

    @Test
    public void motionDuringASearchStartedByTheAlarmDoesNotRestartIt() {
        long id = addLogjob(false);
        startService();
        deliverFix();
        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS);
        motion(id);
        // a point is due when the interval is over
        reachNextPointAlarm();
        assertTrue(searching());
        long timeoutAt = alarms().get(0).getTriggerAtMs();

        ShadowLooper.idleMainLooper(10, TimeUnit.SECONDS);
        motion(id);
        // same search, same timeout
        assertEquals(1, alarms().size());
        assertEquals(timeoutAt, alarms().get(0).getTriggerAtMs());
    }

    /** Let time pass until the location timeout of the running search and deliver it. */
    private void reachTimeout() {
        ShadowAlarmManager.ScheduledAlarm alarm = null;
        for (ShadowAlarmManager.ScheduledAlarm a : alarms()) {
            if (LoggerService.ACTION_LOCATION_TIMEOUT.equals(shadowOf(a.operation).getSavedIntent().getAction())) {
                alarm = a;
            }
        }
        assertNotNull(alarm);
        ShadowLooper.idleMainLooper(alarm.getTriggerAtMs() - SystemClock.elapsedRealtime(), TimeUnit.MILLISECONDS);
        alarmManager.cancel(alarm.operation);
        controller.withIntent(shadowOf(alarm.operation).getSavedIntent()).startCommand(0, 1);
        idleAll();
    }

    @Test
    public void motionEndsSearchingLessOften() {
        long id = addLogjob(true);
        startService();
        reachTimeout();
        reachNextPointAlarm();
        reachTimeout();
        reachNextPointAlarm();
        reachTimeout();
        // 3 searches without a position: the next one is 2 intervals after the last
        assertEquals(90_000, alarms().get(0).getTriggerAtMs() - SystemClock.elapsedRealtime());

        motion(id);
        assertTrue(searching());
        reachTimeout();
        assertEquals(30_000, alarms().get(0).getTriggerAtMs() - SystemClock.elapsedRealtime());
    }

    @Test
    public void hybridModeStillSearchesEveryInterval() {
        long id = addLogjob(true);
        startService();
        deliverFix();

        reachNextPointAlarm();
        assertTrue(searching());
        deliverFix();
        assertEquals(2, db.getLogjobLocationNotSyncedCount(id));
        assertNotNull(shadowOf(alarmManager).peekNextScheduledAlarm());
    }
}
