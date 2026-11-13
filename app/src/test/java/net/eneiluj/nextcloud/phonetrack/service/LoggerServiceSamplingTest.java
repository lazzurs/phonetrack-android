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
import net.eneiluj.nextcloud.phonetrack.android.fragment.PreferencesFragment;
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
        return addLogjob(interval, minDistance, minAccuracy, timeout, false);
    }

    private long addLogjob(int interval, int minDistance, int minAccuracy, int timeout, boolean keepGpsOn) {
        return db.addLogjob(new DBLogjob(0, "test", "http://127.0.0.1:9/log", "", "",
                interval, minDistance, minAccuracy, keepGpsOn, false, false, timeout,
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

    /** The pending location timeout alarm, or null. */
    private ShadowAlarmManager.ScheduledAlarm timeoutAlarm() {
        for (ShadowAlarmManager.ScheduledAlarm alarm : shadowOf(alarmManager).getScheduledAlarms()) {
            if (LoggerService.ACTION_LOCATION_TIMEOUT.equals(shadowOf(alarm.operation).getSavedIntent().getAction())) {
                return alarm;
            }
        }
        return null;
    }

    /** Let time pass until the location timeout alarm, plus lateMillis, and deliver it. */
    private void reachTimeout(long lateMillis) {
        ShadowAlarmManager.ScheduledAlarm alarm = timeoutAlarm();
        assertNotNull("no location timeout scheduled", alarm);
        ShadowLooper.idleMainLooper(alarm.getTriggerAtMs() - SystemClock.elapsedRealtime() + lateMillis,
                TimeUnit.MILLISECONDS);
        alarmManager.cancel(alarm.operation);
        controller.withIntent(shadowOf(alarm.operation).getSavedIntent()).startCommand(0, 1);
        idleAll();
    }

    private void reachTimeout() {
        reachTimeout(0);
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
        assertNull(timeoutAlarm());
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

        reachTimeout();
        assertFalse(searching(LocationManager.GPS_PROVIDER));
        assertEquals(30_000, nextSampleIn());
    }

    @Test
    public void thePlatformEndsTheSearchAtTheTimeoutEvenIfTheCpuSleeps() {
        useProviders("3");
        addLogjob(60, 50, 50, 30);
        startService();

        assertEquals(30_000, shadowOf(locationManager).getLocationRequests(LocationManager.GPS_PROVIDER)
                .get(0).getDurationMillis());
        assertEquals(30_000, shadowOf(locationManager).getLocationRequests(LocationManager.NETWORK_PROVIDER)
                .get(0).getDurationMillis());
        // a wake-up alarm, not a Handler delay that stops while the CPU sleeps
        ShadowAlarmManager.ScheduledAlarm timeout = timeoutAlarm();
        assertNotNull(timeout);
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeout.getType());
        assertTrue(timeout.isAllowWhileIdle());
        assertEquals(SystemClock.elapsedRealtime() + 30_000, timeout.getTriggerAtMs());

        // an accepted fix cancels it
        deliver(fix(LocationManager.GPS_PROVIDER, 0, 5));
        assertNull(timeoutAlarm());
    }

    @Test
    public void aLateTimeoutAlarmKeepsSearchesOneIntervalApart() {
        addLogjob(60, 50, 50, 30);
        startService();

        reachTimeout(20_000);
        assertFalse(searching(LocationManager.GPS_PROVIDER));
        assertEquals(10_000, nextSampleIn());
    }

    @Test
    public void aTimeoutAlarmForAnEarlierSearchIsIgnored() {
        addLogjob(60, 50, 50, 30);
        startService();
        ShadowAlarmManager.ScheduledAlarm timeout = timeoutAlarm();
        assertNotNull(timeout);

        // as if left over from before the service was restarted
        ShadowLooper.idleMainLooper(10, TimeUnit.SECONDS);
        controller.withIntent(shadowOf(timeout.operation).getSavedIntent()).startCommand(0, 1);
        idleAll();
        assertTrue(searching(LocationManager.GPS_PROVIDER));
        assertNotNull(timeoutAlarm());
    }

    /** Let n searches in a row end at the timeout without a position. */
    private void missSearches(int n) {
        for (int i = 0; i < n; i++) {
            if (i > 0 || timeoutAlarm() == null) {
                fireNextSample();
            }
            reachTimeout();
        }
    }

    @Test
    public void searchesLessOftenAfterThreeSearchesInARowWithoutAPosition() {
        long id = addLogjob(60, 0, 50, 30);
        startService();

        missSearches(2);
        assertEquals(30_000, nextSampleIn());
        missSearches(1);
        // the next search starts 2 intervals after the last one
        assertEquals(90_000, nextSampleIn());
        missSearches(1);
        assertEquals(210_000, nextSampleIn());
        // at most 4 intervals
        missSearches(1);
        assertEquals(210_000, nextSampleIn());

        // the first position ends it
        fireNextSample();
        deliver(fix(LocationManager.GPS_PROVIDER, 0, 5));
        assertEquals(1, logged(id));
        long next = nextSampleIn();
        assertTrue("next sample in " + next, next > 55_000 && next <= 60_000);
        missSearches(1);
        assertEquals(30_000, nextSampleIn());
    }

    @Test
    public void aNetworkPositionLoggedAtTheTimeoutAlsoEndsIt() {
        useProviders("3");
        long id = addLogjob(60, 0, 50, 30);
        startService();
        missSearches(3);
        assertEquals(90_000, nextSampleIn());

        fireNextSample();
        deliver(fix(LocationManager.NETWORK_PROVIDER, 0, 20));
        reachTimeout();
        assertEquals(1, logged(id));
        assertEquals(30_000, nextSampleIn());
    }

    @Test
    public void restartingTheLogjobSearchesAtTheNormalIntervalAgain() {
        addLogjob(60, 0, 50, 30);
        startService();
        missSearches(3);
        assertEquals(90_000, nextSampleIn());

        // as when the location provider setting changes
        controller.withIntent(new Intent(app, LoggerService.class)
                .putExtra(PreferencesFragment.UPDATED_PROVIDERS, true)
                .putExtra(PreferencesFragment.UPDATED_PROVIDERS_VALUE, "1")).startCommand(0, 1);
        idleAll();
        assertTrue(searching(LocationManager.GPS_PROVIDER));
        reachTimeout();
        assertEquals(30_000, nextSampleIn());
    }

    @Test
    public void searchingLessOftenCanBeTurnedOff() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(app.getString(R.string.pref_key_search_backoff), false).commit();
        addLogjob(60, 0, 50, 30);
        startService();

        missSearches(5);
        assertEquals(30_000, nextSampleIn());
    }

    @Test
    public void keepGpsOnLogjobHasNoSearchDuration() {
        addLogjob(10, 0, 50, 5, true);
        startService();

        assertEquals(Long.MAX_VALUE, shadowOf(locationManager).getLocationRequests(LocationManager.GPS_PROVIDER)
                .get(0).getDurationMillis());
        assertNull(timeoutAlarm());
    }

    @Test
    public void keepGpsOnLogjobDoesNotAskForNetworkFixesItWouldNotUse() {
        useProviders("3");
        addLogjob(10, 0, 50, 0, true);
        startService();

        assertTrue(searching(LocationManager.GPS_PROVIDER));
        assertFalse(searching(LocationManager.NETWORK_PROVIDER));
    }

    @Test
    public void keepGpsOnLogjobStillUsesTheNetworkWithoutGps() {
        useProviders("2");
        long id = addLogjob(10, 0, 50, 0, true);
        startService();

        assertTrue(searching(LocationManager.NETWORK_PROVIDER));
        ShadowLooper.idleMainLooper(10, TimeUnit.SECONDS);
        deliver(fix(LocationManager.NETWORK_PROVIDER, 0, 20));
        assertEquals(1, logged(id));
    }

    @Test
    public void keepGpsOnLogjobGetsUpdatesAtAFifthOfItsIntervalUpToFiveSeconds() {
        addLogjob(10, 0, 50, 0, true);
        startService();
        assertEquals(2000, shadowOf(locationManager).getLocationRequests(LocationManager.GPS_PROVIDER)
                .get(0).getIntervalMillis());
    }

    @Test
    public void keepGpsOnLogjobLogsOncePerInterval() {
        long id = addLogjob(60, 0, 50, 0, true);
        startService();
        assertEquals(5000, shadowOf(locationManager).getLocationRequests(LocationManager.GPS_PROVIDER)
                .get(0).getIntervalMillis());

        // the test device has only just booted, and the first point waits for an interval after boot
        ShadowLooper.idleMainLooper(60, TimeUnit.SECONDS);
        deliver(fix(LocationManager.GPS_PROVIDER, 0, 5));
        assertEquals(1, logged(id));

        ShadowLooper.idleMainLooper(10, TimeUnit.SECONDS);
        deliver(fix(LocationManager.GPS_PROVIDER, 100, 5));
        assertEquals(1, logged(id));

        ShadowLooper.idleMainLooper(50, TimeUnit.SECONDS);
        deliver(fix(LocationManager.GPS_PROVIDER, 200, 5));
        assertEquals(2, logged(id));
        assertTrue(searching(LocationManager.GPS_PROVIDER));
    }

    @Test
    public void networkFallbackAtTheTimeoutMustMeetTheMinimumAccuracy() {
        useProviders("3");
        long id = addLogjob(60, 5, 50, 30);
        startService();

        // a cell tower position, while GPS gets nothing
        deliver(fix(LocationManager.NETWORK_PROVIDER, 0, 500));
        reachTimeout();
        assertEquals(0, logged(id));
        assertFalse(searching(LocationManager.GPS_PROVIDER));
        assertFalse(searching(LocationManager.NETWORK_PROVIDER));

        // a Wi-Fi position is good enough
        fireNextSample();
        deliver(fix(LocationManager.NETWORK_PROVIDER, 0, 20));
        reachTimeout();
        assertEquals(1, logged(id));
    }

    @Test
    public void networkFallbackAtTheTimeoutMustMeetTheMinimumDistance() {
        useProviders("3");
        long id = addLogjob(60, 50, 50, 30);
        startService();
        deliver(fix(LocationManager.GPS_PROVIDER, 0, 5));
        assertEquals(1, logged(id));

        // indoors, not moved: the network position used to be logged every interval
        fireNextSample();
        deliver(fix(LocationManager.NETWORK_PROVIDER, 10, 20));
        reachTimeout();
        assertEquals(1, logged(id));

        fireNextSample();
        deliver(fix(LocationManager.NETWORK_PROVIDER, 200, 20));
        reachTimeout();
        assertEquals(2, logged(id));
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
