package net.eneiluj.nextcloud.phonetrack.service;

import static net.eneiluj.nextcloud.phonetrack.service.TrackingReadiness.Cause;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.location.LocationManager;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

/** Why log jobs are not logging: one cause per situation, not "permission denied" for all of them. */
@RunWith(AndroidJUnit4.class)
public class TrackingReadinessTest {

    /** Everything allowed, nothing paused. */
    private static TrackingReadiness.State ready() {
        TrackingReadiness.State s = new TrackingReadiness.State();
        s.fineLocation = true;
        s.coarseLocation = true;
        s.backgroundLocation = true;
        return s;
    }

    @Test
    public void allowedAndNotPausedIsOk() {
        assertEquals(Cause.OK, TrackingReadiness.serviceGate(ready()));
        assertEquals(Collections.emptyList(), TrackingReadiness.problems(ready()));
    }

    @Test
    public void noLocationAtAll() {
        TrackingReadiness.State s = ready();
        s.fineLocation = false;
        s.coarseLocation = false;
        s.backgroundLocation = false;
        assertEquals(Cause.NEEDS_LOCATION, TrackingReadiness.serviceGate(s));
    }

    @Test
    public void approximateOnlyNeedsPreciseLocation() {
        TrackingReadiness.State s = ready();
        s.fineLocation = false;
        assertEquals(Cause.NEEDS_PRECISE_LOCATION, TrackingReadiness.serviceGate(s));
        // even with background location: approximate fixes were never logged
        assertEquals(Collections.singletonList(Cause.NEEDS_PRECISE_LOCATION), TrackingReadiness.problems(s));
    }

    @Test
    public void whileInUseOnlyNeedsBackgroundLocation() {
        TrackingReadiness.State s = ready();
        s.backgroundLocation = false;
        assertEquals(Cause.NEEDS_BACKGROUND_LOCATION, TrackingReadiness.serviceGate(s));
    }

    @Test
    public void batterySaverPausesOnlyWhenTheUserChoseSo() {
        TrackingReadiness.State s = ready();
        s.powerSaveMode = true;
        assertEquals(Cause.OK, TrackingReadiness.serviceGate(s));
        s.respectPowerSaveMode = true;
        assertEquals(Cause.PAUSED_BATTERY_SAVER, TrackingReadiness.serviceGate(s));
        s.powerSaveMode = false;
        assertEquals(Cause.OK, TrackingReadiness.serviceGate(s));
    }

    @Test
    public void airplaneModePausesOnlyWhenTheUserChoseSo() {
        TrackingReadiness.State s = ready();
        s.airplaneMode = true;
        assertEquals(Cause.OK, TrackingReadiness.serviceGate(s));
        s.respectAirplaneMode = true;
        assertEquals(Cause.PAUSED_AIRPLANE_MODE, TrackingReadiness.serviceGate(s));
    }

    @Test
    public void missingPermissionComesBeforeAChosenPause() {
        TrackingReadiness.State s = ready();
        s.backgroundLocation = false;
        s.powerSaveMode = true;
        s.respectPowerSaveMode = true;
        assertEquals(Cause.NEEDS_BACKGROUND_LOCATION, TrackingReadiness.serviceGate(s));
        assertEquals(Arrays.asList(Cause.NEEDS_BACKGROUND_LOCATION, Cause.PAUSED_BATTERY_SAVER),
                TrackingReadiness.problems(s));
    }

    @Test
    public void locationSwitchedOffIsAProblemButNotAGate() {
        // updates requested while location is off start when it is switched on
        TrackingReadiness.State s = ready();
        s.locationEnabled = false;
        assertEquals(Cause.OK, TrackingReadiness.serviceGate(s));
        assertEquals(Collections.singletonList(Cause.LOCATION_OFF), TrackingReadiness.problems(s));
    }

    @Test
    public void notificationsAndBatteryOptimisationDoNotStopLogging() {
        TrackingReadiness.State s = ready();
        s.notificationsEnabled = false;
        s.ignoringBatteryOptimisations = false;
        assertEquals(Cause.OK, TrackingReadiness.serviceGate(s));
        assertEquals(Arrays.asList(Cause.NOTIFICATIONS_OFF, Cause.BATTERY_OPTIMISED), TrackingReadiness.problems(s));
        assertFalse(Cause.NOTIFICATIONS_OFF.stopsLogging);
        assertFalse(Cause.BATTERY_OPTIMISED.stopsLogging);
    }

    @Test
    public void everythingAtOnceIsListedMostImportantFirst() {
        TrackingReadiness.State s = new TrackingReadiness.State();
        s.locationEnabled = false;
        s.airplaneMode = true;
        s.respectAirplaneMode = true;
        s.powerSaveMode = true;
        s.respectPowerSaveMode = true;
        s.notificationsEnabled = false;
        s.ignoringBatteryOptimisations = false;
        assertEquals(Arrays.asList(Cause.NEEDS_LOCATION, Cause.LOCATION_OFF, Cause.PAUSED_AIRPLANE_MODE,
                        Cause.PAUSED_BATTERY_SAVER, Cause.NOTIFICATIONS_OFF, Cause.BATTERY_OPTIMISED),
                TrackingReadiness.problems(s));
    }

    @Test
    public void retryWaitsForTheIntervalWithinBounds() {
        assertEquals(60_000, TrackingReadiness.retryDelayMillis(0));
        assertEquals(60_000, TrackingReadiness.retryDelayMillis(5));
        assertEquals(300_000, TrackingReadiness.retryDelayMillis(300));
        assertEquals(15 * 60_000, TrackingReadiness.retryDelayMillis(3600));
    }

    @Test
    public void readsTheDevice() {
        Application app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(app.getString(R.string.pref_key_power_saving_awareness), true)
                .putBoolean(app.getString(R.string.pref_key_offline_mode_awareness), true).commit();
        shadowOf(app.getSystemService(PowerManager.class)).setIsPowerSaveMode(true);
        Settings.Global.putInt(app.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);
        shadowOf(app.getSystemService(LocationManager.class)).setLocationEnabled(false);

        TrackingReadiness.State s = TrackingReadiness.read(app);
        assertFalse(s.fineLocation);
        assertTrue(s.coarseLocation);
        assertFalse(s.backgroundLocation);
        assertFalse(s.locationEnabled);
        assertTrue(s.powerSaveMode && s.respectPowerSaveMode);
        assertTrue(s.airplaneMode && s.respectAirplaneMode);
        assertFalse("POST_NOTIFICATIONS is not granted", s.notificationsEnabled);
        assertFalse(s.ignoringBatteryOptimisations);
        assertEquals(Cause.NEEDS_PRECISE_LOCATION, TrackingReadiness.serviceGate(s));
    }

    @Test
    public void backgroundMessageNamesTheSystemOption() {
        Application app = ApplicationProvider.getApplicationContext();
        String text = TrackingReadiness.describe(app, Cause.NEEDS_BACKGROUND_LOCATION);
        assertTrue(text, text.contains("\"" + TrackingReadiness.backgroundOptionLabel(app) + "\""));
        assertFalse(TrackingReadiness.backgroundOptionLabel(app).isEmpty());
    }
}
