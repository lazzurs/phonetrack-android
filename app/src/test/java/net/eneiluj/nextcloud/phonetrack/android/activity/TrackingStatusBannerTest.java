package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

/** The banner above the list names what stops logging and offers the fix for that cause. */
@RunWith(AndroidJUnit4.class)
public class TrackingStatusBannerTest {

    private Application app;
    private PowerManager powerManager;
    private ActivityController<LogjobsListViewActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
        powerManager = app.getSystemService(PowerManager.class);
        shadowOf(powerManager).setIgnoringBatteryOptimizations(app.getPackageName(), true);
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        DbTestSupport.resetDatabase();
    }

    private LogjobsListViewActivity launch() {
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idle();
        return controller.get();
    }

    private void grantAllLocation() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    private static View banner(LogjobsListViewActivity activity) {
        return activity.findViewById(R.id.status_banner);
    }

    private static String bannerText(LogjobsListViewActivity activity) {
        return ((TextView) activity.findViewById(R.id.status_banner_text)).getText().toString();
    }

    private static String bannerAction(LogjobsListViewActivity activity) {
        return ((TextView) activity.findViewById(R.id.status_banner_action)).getText().toString();
    }

    @Test
    public void approximateOnlyShowsTheBannerAboutPreciseLocation() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION);
        ListActivityTestSupport.addLogjob(app, "Hike", true);
        LogjobsListViewActivity activity = launch();

        assertEquals(View.VISIBLE, banner(activity).getVisibility());
        assertTrue(bannerText(activity), bannerText(activity).contains("precise location"));
        assertEquals("Allow precise location", bannerAction(activity));
        // and no dialog or request at start-up
        assertNull(ShadowDialog.getLatestDialog());
        assertNull(shadowOf(activity).getLastRequestedPermission());

        activity.findViewById(R.id.status_banner_action).performClick();
        assertEquals(Manifest.permission.ACCESS_FINE_LOCATION,
                shadowOf(activity).getLastRequestedPermission().requestedPermissions[0]);
    }

    @Test
    public void batterySaverPauseIsExplainedWithoutPermissionToastOrDialog() {
        grantAllLocation();
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(app.getString(R.string.pref_key_power_saving_awareness), true).commit();
        shadowOf(powerManager).setIsPowerSaveMode(true);
        ListActivityTestSupport.addLogjob(app, "Hike", true);
        LogjobsListViewActivity activity = launch();

        // what the logger service sends when it pauses the job
        app.sendBroadcast(new Intent(LoggerService.BROADCAST_TRACKING_PAUSED)
                .setPackage(app.getPackageName())
                .putExtra(LoggerService.BROADCAST_EXTRA_CAUSE, "PAUSED_BATTERY_SAVER"));
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(ShadowToast.getTextOfLatestToast());
        assertNull(ShadowDialog.getLatestDialog());
        assertNull(shadowOf(activity).getLastRequestedPermission());
        assertTrue(bannerText(activity), bannerText(activity).contains("battery saver"));

        activity.findViewById(R.id.status_banner_action).performClick();
        Intent opened = shadowOf(activity).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(Settings.ACTION_BATTERY_SAVER_SETTINGS, opened.getAction());
    }

    @Test
    public void hiddenWhileNoJobIsOn() {
        // nothing allowed at all, but nothing to log either
        ListActivityTestSupport.addLogjob(app, "Hike", false);
        LogjobsListViewActivity activity = launch();
        assertEquals(View.GONE, banner(activity).getVisibility());
    }

    @Test
    public void hiddenWhenEverythingIsFine() {
        grantAllLocation();
        ListActivityTestSupport.addLogjob(app, "Hike", true);
        LogjobsListViewActivity activity = launch();
        assertEquals(View.GONE, banner(activity).getVisibility());
    }

    @Test
    public void advisoryCausesAreShownAfterTheBlockingOnesAndCanBeDismissed() {
        grantAllLocation();
        shadowOf(powerManager).setIgnoringBatteryOptimizations(app.getPackageName(), false);
        ListActivityTestSupport.addLogjob(app, "Hike", true);
        LogjobsListViewActivity activity = launch();
        assertTrue(bannerText(activity), bannerText(activity).contains("Battery optimisation"));
        assertEquals("Stop optimising", bannerAction(activity));

        activity.findViewById(R.id.status_banner_dismiss).performClick();
        assertEquals(View.GONE, banner(activity).getVisibility());

        // stays dismissed on the next start
        controller.destroy();
        activity = launch();
        assertEquals(View.GONE, banner(activity).getVisibility());

        // fixed, then back again: shown again
        shadowOf(powerManager).setIgnoringBatteryOptimizations(app.getPackageName(), true);
        controller.destroy();
        launch();
        shadowOf(powerManager).setIgnoringBatteryOptimizations(app.getPackageName(), false);
        controller.destroy();
        activity = launch();
        assertEquals(View.VISIBLE, banner(activity).getVisibility());
    }

    @Test
    public void dismissingOneCauseRevealsTheNext() {
        grantAllLocation();
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        shadowOf(powerManager).setIgnoringBatteryOptimizations(app.getPackageName(), false);
        ListActivityTestSupport.addLogjob(app, "Hike", true);
        LogjobsListViewActivity activity = launch();
        assertTrue(bannerText(activity), bannerText(activity).contains("screen off"));

        activity.findViewById(R.id.status_banner_dismiss).performClick();
        assertTrue(bannerText(activity), bannerText(activity).contains("Battery optimisation"));
    }

    @Test
    public void permanentlyDeniedLeadsToTheAppSettings() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        ListActivityTestSupport.addLogjob(app, "Hike", true);
        // asked before, and Android no longer shows the request ("Don't ask again")
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(TrackingPermissionFlow.PREF_ASKED_PREFIX + Manifest.permission.ACCESS_BACKGROUND_LOCATION, true)
                .commit();
        shadowOf(app.getPackageManager()).setShouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION, false);
        LogjobsListViewActivity activity = launch();

        assertEquals("Open settings", bannerAction(activity));
        activity.findViewById(R.id.status_banner_action).performClick();
        Intent opened = shadowOf(activity).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opened.getAction());
        assertEquals("package:" + app.getPackageName(), opened.getData().toString());
    }
}
