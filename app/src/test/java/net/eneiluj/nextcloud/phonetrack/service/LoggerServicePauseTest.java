package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Looper;
import android.os.PowerManager;

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
import java.util.List;

/**
 * A log job that cannot get positions says why (not "permission denied" for everything),
 * and resumes once the cause goes away instead of stalling until the service restarts.
 */
@RunWith(AndroidJUnit4.class)
public class LoggerServicePauseTest {

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

    private long addEnabledLogjob(int interval) {
        return db.addLogjob(new DBLogjob(0, "test", "http://127.0.0.1:9/log", "", "",
                interval, 0, 50, false, false, false, 30,
                false, true, 0, null, null, false));
    }

    private void grant(String... permissions) {
        shadowOf(app).grantPermissions(permissions);
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

    private boolean searching() {
        return !shadowOf(locationManager).getLocationUpdateListeners(LocationManager.GPS_PROVIDER).isEmpty();
    }

    /** The cause of the last "tracking paused" broadcast, or null if there was none. */
    private String lastPausedCause() {
        String cause = null;
        for (Intent intent : shadowOf(app).getBroadcastIntents()) {
            if (LoggerService.BROADCAST_TRACKING_PAUSED.equals(intent.getAction())) {
                cause = intent.getStringExtra(LoggerService.BROADCAST_EXTRA_CAUSE);
            }
        }
        return cause;
    }

    private String notificationText() {
        List<Notification> notifications = shadowOf(app.getSystemService(NotificationManager.class)).getAllNotifications();
        assertFalse("no notification", notifications.isEmpty());
        return String.valueOf(notifications.get(notifications.size() - 1).extras
                .getCharSequence(Notification.EXTRA_TEXT));
    }

    private ShadowAlarmManager.ScheduledAlarm retryAlarm() {
        for (ShadowAlarmManager.ScheduledAlarm alarm : shadowOf(alarmManager).getScheduledAlarms()) {
            Intent intent = shadowOf(alarm.operation).getSavedIntent();
            if (LoggerService.ACTION_RETRY_PAUSED_JOB.equals(intent.getAction())) {
                return alarm;
            }
        }
        return null;
    }

    private void fire(ShadowAlarmManager.ScheduledAlarm alarm) {
        controller.withIntent(shadowOf(alarm.operation).getSavedIntent()).startCommand(0, 1);
        idleAll();
    }

    @Test
    public void missingBackgroundLocationIsReportedAndResumesWhenGranted() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
        addEnabledLogjob(120);
        startService();

        assertFalse(searching());
        assertEquals("NEEDS_BACKGROUND_LOCATION", lastPausedCause());
        String text = notificationText();
        assertTrue(text, text.startsWith("Logging is stopped") && text.contains("screen off"));
        ShadowAlarmManager.ScheduledAlarm retry = retryAlarm();
        assertNotNull("not retried", retry);

        // granted in the app: the list screen asks the service to check again
        grant(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        controller.withIntent(new Intent(app, LoggerService.class)
                .putExtra(LoggerService.RECHECK_PAUSED_JOBS, true)).startCommand(0, 2);
        idleAll();

        assertTrue(searching());
        assertNull("retry not cancelled", retryAlarm());
        assertTrue(notificationText(), notificationText().startsWith("running"));
    }

    @Test
    public void grantedOutsideTheAppResumesAtTheNextRetry() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
        addEnabledLogjob(300);
        startService();
        ShadowAlarmManager.ScheduledAlarm retry = retryAlarm();
        assertNotNull(retry);
        // waits for the job's interval, not the service's next restart
        long delay = retry.getTriggerAtMs() - android.os.SystemClock.elapsedRealtime();
        assertEquals(300_000, delay);

        grant(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        fire(retry);
        assertTrue(searching());
    }

    @Test
    public void approximateLocationAsksForPreciseLocation() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        addEnabledLogjob(60);
        startService();

        assertFalse(searching());
        assertEquals("NEEDS_PRECISE_LOCATION", lastPausedCause());
        assertTrue(notificationText(), notificationText().contains("precise location"));
    }

    @Test
    public void batterySaverPauseIsNotAPermissionProblem() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(app.getString(R.string.pref_key_power_saving_awareness), true).commit();
        PowerManager powerManager = app.getSystemService(PowerManager.class);
        shadowOf(powerManager).setIsPowerSaveMode(true);
        addEnabledLogjob(60);
        startService();

        assertFalse(searching());
        assertEquals("PAUSED_BATTERY_SAVER", lastPausedCause());
        assertTrue(notificationText(), notificationText().contains("battery saver"));

        // battery saver off: resumes at the next check
        shadowOf(powerManager).setIsPowerSaveMode(false);
        fire(retryAlarm());
        assertTrue(searching());
    }

    @Test
    public void locationSwitchedOffIsShownUntilAPositionComes() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        shadowOf(locationManager).setLocationEnabled(false);
        long id = addEnabledLogjob(60);
        startService();
        assertTrue(notificationText(), notificationText().contains("location is switched off"));
        assertEquals("LOCATION_OFF", lastPausedCause());
        // checked again later in case it stays off longer than the search
        assertNotNull(retryAlarm());

        shadowOf(locationManager).setLocationEnabled(true);
        android.location.Location fix = new android.location.Location(LocationManager.GPS_PROVIDER);
        fix.setLatitude(53.35);
        fix.setLongitude(-6.26);
        fix.setAccuracy(5);
        fix.setTime(System.currentTimeMillis());
        fix.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
        shadowOf(locationManager).simulateLocation(fix);
        idleAll();
        assertEquals(1, db.getLogjobLocationNotSyncedCount(id));
        assertTrue(notificationText(), notificationText().startsWith("running"));
    }

    @Test
    public void readyJobIsNotPaused() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        addEnabledLogjob(60);
        startService();

        assertTrue(searching());
        assertNull(lastPausedCause());
        assertNull(retryAlarm());
    }

    @Test
    public void stoppingTheServiceCancelsTheRetry() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
        addEnabledLogjob(60);
        startService();
        assertNotNull(retryAlarm());

        controller.destroy();
        controller = null;
        assertNull(retryAlarm());
    }
}
