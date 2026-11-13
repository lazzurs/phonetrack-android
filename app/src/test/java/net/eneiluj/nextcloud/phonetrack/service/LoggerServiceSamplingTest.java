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

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import net.eneiluj.nextcloud.phonetrack.R;
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
import java.util.concurrent.TimeUnit;

/**
 * How an interval log job searches for a position: when a search ends and when the next starts.
 */
@RunWith(AndroidJUnit4.class)
public class LoggerServiceSamplingTest {

    private static final double LAT = 53.3498;
    private static final double LON = -6.2603;
    /** Roughly one metre of latitude, in degrees. */
    private static final double ONE_METRE = 1.0 / 111_000;

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
        shadowOf(locationManager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
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

    /** An enabled classic (interval) log job whose server refuses connections. */
    private long addLogjob(int interval, int minDistance, int minAccuracy, int timeout) {
        return db.addLogjob(new DBLogjob(0, "test", "http://127.0.0.1:9/log", "", "",
                interval, minDistance, minAccuracy, false, false, false, timeout,
                false, true, 0, null, null, false));
    }

    private void useProviders(String value) {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putString(app.getString(R.string.pref_key_providers), value).commit();
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

    private static Location fix(String provider, double metresNorth, float accuracy) {
        Location l = new Location(provider);
        l.setLatitude(LAT + metresNorth * ONE_METRE);
        l.setLongitude(LON);
        l.setAccuracy(accuracy);
        l.setTime(System.currentTimeMillis());
        l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        return l;
    }

    private void deliver(Location location) {
        shadowOf(locationManager).simulateLocation(location);
        idleAll();
    }

    private boolean searching(String provider) {
        return !shadowOf(locationManager).getLocationUpdateListeners(provider).isEmpty();
    }

    private int logged(long id) {
        return db.getLogjobLocationNotSyncedCount(id);
    }

    /** Milliseconds from now until the next-sample alarm. */
    private long nextSampleIn() {
        ShadowAlarmManager.ScheduledAlarm alarm = shadowOf(alarmManager).peekNextScheduledAlarm();
        assertNotNull("no next sample scheduled", alarm);
        return alarm.getTriggerAtMs() - SystemClock.elapsedRealtime();
    }

    /** Fire the pending next-sample alarm, as the system would. */
    private void fireNextSample() {
        ShadowAlarmManager.ScheduledAlarm alarm = shadowOf(alarmManager).getNextScheduledAlarm();
        assertNotNull("no next sample scheduled", alarm);
        Intent intent = shadowOf(alarm.operation).getSavedIntent();
        controller.withIntent(intent).startCommand(0, 1);
        idleAll();
    }

    @Test
    public void accurateFixTooCloseToTheLastPointEndsTheSearchWithoutATimeout() {
        // timeout 0, as SMS-created log jobs and the bike/walk/drive presets have
        long id = addLogjob(60, 50, 50, 0);
        startService();
        assertTrue(searching(LocationManager.GPS_PROVIDER));

        deliver(fix(LocationManager.GPS_PROVIDER, 0, 5));
        assertEquals(1, logged(id));
        assertFalse(searching(LocationManager.GPS_PROVIDER));

        fireNextSample();
        assertTrue(searching(LocationManager.GPS_PROVIDER));

        // the phone has not moved: used to keep GPS on until the user walked 50 m away
        deliver(fix(LocationManager.GPS_PROVIDER, 2, 5));
        assertEquals(1, logged(id));
        assertFalse(searching(LocationManager.GPS_PROVIDER));
        long next = nextSampleIn();
        assertTrue("next sample in " + next, next > 55_000 && next <= 60_000);

        // distance is still measured from the last logged point, so slow drift is logged
        fireNextSample();
        deliver(fix(LocationManager.GPS_PROVIDER, 60, 5));
        assertEquals(2, logged(id));
        assertFalse(searching(LocationManager.GPS_PROVIDER));
    }

    @Test
    public void accurateFixTooCloseToTheLastPointEndsTheSearchBeforeTheTimeout() {
        long id = addLogjob(60, 50, 50, 59);
        startService();
        deliver(fix(LocationManager.GPS_PROVIDER, 0, 5));
        fireNextSample();

        deliver(fix(LocationManager.GPS_PROVIDER, 2, 5));
        // removed now, not after 59 s
        assertFalse(searching(LocationManager.GPS_PROVIDER));
        long next = nextSampleIn();
        assertTrue("next sample in " + next, next > 55_000 && next <= 60_000);

        // and the timeout no longer fires
        ShadowLooper.idleMainLooper(59, TimeUnit.SECONDS);
        assertEquals(1, logged(id));
        assertFalse(searching(LocationManager.GPS_PROVIDER));
    }

    @Test
    public void inaccurateFixKeepsSearchingUntilTheTimeout() {
        long id = addLogjob(60, 50, 50, 30);
        startService();

        deliver(fix(LocationManager.GPS_PROVIDER, 0, 200));
        assertEquals(0, logged(id));
        assertTrue(searching(LocationManager.GPS_PROVIDER));

        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS);
        assertFalse(searching(LocationManager.GPS_PROVIDER));
        assertEquals(30_000, nextSampleIn());
    }

    @Test
    public void networkOnlyLogjobAlsoStopsWhenThePhoneHasNotMoved() {
        useProviders("2");
        long id = addLogjob(60, 50, 100, 30);
        startService();
        assertTrue(searching(LocationManager.NETWORK_PROVIDER));

        deliver(fix(LocationManager.NETWORK_PROVIDER, 0, 20));
        assertEquals(1, logged(id));
        fireNextSample();

        deliver(fix(LocationManager.NETWORK_PROVIDER, 5, 20));
        assertEquals(1, logged(id));
        assertFalse(searching(LocationManager.NETWORK_PROVIDER));
    }
}
