package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.ItemAdapter;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.service.TrackingReadiness;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;

/**
 * Permissions are asked for when a log job is turned on, with an explanation first, and an
 * answer of "Not now" is remembered: no prompts at start-up, and none repeated on every start.
 */
@RunWith(AndroidJUnit4.class)
public class PermissionPromptsTest {

    private Application app;
    private PowerManager powerManager;
    private ActivityController<LogjobsListViewActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
        powerManager = app.getSystemService(PowerManager.class);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        DbTestSupport.resetDatabase();
    }

    private LogjobsListViewActivity launch() {
        ShadowDialog.reset();
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idle();
        return controller.get();
    }

    private void relaunch() {
        controller.pause().stop().destroy();
        launch();
    }

    /** Turn on the first log job of the list with its switch, as the user would. */
    private void turnOnFirstJob(LogjobsListViewActivity activity) throws InterruptedException {
        RecyclerView list = ListActivityTestSupport.awaitRows(activity, 1);
        activity.onLogjobEnabledClick(0, list);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static AlertDialog shownDialog() {
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        return dialog != null && dialog.isShowing() ? dialog : null;
    }

    private static String message(AlertDialog dialog) {
        return ((TextView) dialog.findViewById(android.R.id.message)).getText().toString();
    }

    private void click(AlertDialog dialog, int button) {
        dialog.getButton(button).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    /**
     * Robolectric has no permission screen: a request that is not already granted is answered
     * with a denial straight away. To "allow" in the system dialog, grant before continuing.
     */
    private void allowInSystemDialog(String... permissions) {
        shadowOf(app).grantPermissions(permissions);
    }

    @Test
    public void nothingIsAskedAtStartUp() {
        LogjobsListViewActivity activity = launch();

        assertNull(shownDialog());
        assertNull(shadowOf(activity).getLastRequestedPermission());
        Intent started;
        while ((started = shadowOf(activity).getNextStartedActivity()) != null) {
            assertFalse(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.equals(started.getAction()));
        }
    }

    @Test
    public void anEnabledJobWithoutBackgroundLocationDoesNotPromptOnAnyStart() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        ListActivityTestSupport.addLogjob(app, "Hike", true);

        LogjobsListViewActivity activity = launch();
        assertNull(shownDialog());
        assertNull(shadowOf(activity).getLastRequestedPermission());

        relaunch();
        assertNull(shownDialog());
        assertNull(shadowOf(controller.get()).getLastRequestedPermission());
    }

    @Test
    public void turningAJobOnExplainsThenAsksForLocation() throws Exception {
        ListActivityTestSupport.addLogjob(app, "Hike", false);
        LogjobsListViewActivity activity = launch();
        turnOnFirstJob(activity);

        AlertDialog dialog = shownDialog();
        assertNotNull("no explanation before the request", dialog);
        assertEquals(app.getString(R.string.location_rationale), message(dialog));
        assertEquals("Continue", dialog.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());
        assertEquals("Not now", dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getText().toString());
        assertNull("asked before the explanation", shadowOf(activity).getLastRequestedPermission());

        click(dialog, DialogInterface.BUTTON_POSITIVE);
        assertArrayEquals(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                shadowOf(activity).getLastRequestedPermission().requestedPermissions);
    }

    @Test
    public void afterLocationComesBackgroundLocationInPlainWords() throws Exception {
        ListActivityTestSupport.addLogjob(app, "Hike", false);
        LogjobsListViewActivity activity = launch();
        turnOnFirstJob(activity);
        allowInSystemDialog(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);
        click(shownDialog(), DialogInterface.BUTTON_POSITIVE);

        // the system's own label in quotes, and buttons that say what they do
        AlertDialog dialog = shownDialog();
        assertNotNull(dialog);
        String text = message(dialog);
        assertTrue(text, text.contains("set location access to \"" + TrackingReadiness.backgroundOptionLabel(app) + "\""));
        assertFalse(text, text.trim().endsWith(":"));
        assertEquals("Continue", dialog.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());

        click(dialog, DialogInterface.BUTTON_POSITIVE);
        assertArrayEquals(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                shadowOf(activity).getLastRequestedPermission().requestedPermissions);
    }

    @Test
    public void notNowOnBackgroundLocationIsRememberedAcrossStarts() throws Exception {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS);
        shadowOf(powerManager).setIgnoringBatteryOptimizations(app.getPackageName(), true);
        ListActivityTestSupport.addLogjob(app, "Hike", false);
        ListActivityTestSupport.addLogjob(app, "Commute", false);
        LogjobsListViewActivity activity = launch();
        ListActivityTestSupport.awaitRows(activity, 2);
        turnOnFirstJob(activity);

        AlertDialog dialog = shownDialog();
        assertNotNull(dialog);
        assertEquals(app.getString(R.string.background_location_permission_title),
                shadowOf(dialog).getTitle().toString());
        click(dialog, DialogInterface.BUTTON_NEGATIVE);
        assertNull(shownDialog());
        assertTrue(PreferenceManager.getDefaultSharedPreferences(app)
                .getBoolean(TrackingPermissionFlow.PREF_DECLINED_PREFIX + "BACKGROUND_LOCATION", false));

        // a new process: turning a job on again does not ask again, the banner says it instead
        relaunch();
        activity = controller.get();
        assertNull(shownDialog());
        RecyclerView list = ListActivityTestSupport.awaitRows(activity, 2);
        ItemAdapter adapter = (ItemAdapter) list.getAdapter();
        int disabled = ((DBLogjob) adapter.getItem(0)).isEnabled() ? 1 : 0;
        assertFalse(((DBLogjob) adapter.getItem(disabled)).isEnabled());
        activity.onLogjobEnabledClick(disabled, list);
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(shownDialog());
        assertNull(shadowOf(activity).getLastRequestedPermission());
        TextView banner = activity.findViewById(R.id.status_banner_text);
        assertTrue(banner.getText().toString(), banner.getText().toString().contains("screen off"));
    }

    @Test
    public void deniedLocationIsNotAskedForAgainWhenTheNextJobIsTurnedOn() throws Exception {
        ListActivityTestSupport.addLogjob(app, "Hike", false);
        LogjobsListViewActivity activity = launch();
        turnOnFirstJob(activity);
        // denied once: Android would still show the request again
        shadowOf(app.getPackageManager()).setShouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION, true);
        click(shownDialog(), DialogInterface.BUTTON_POSITIVE);
        assertNotNull(shadowOf(activity).getLastRequestedPermission());
        // nothing else is useful without location
        assertNull(shownDialog());

        relaunch();
        activity = controller.get();
        // off and on again
        RecyclerView list = ListActivityTestSupport.awaitRows(activity, 1);
        activity.onLogjobEnabledClick(0, list);
        activity.onLogjobEnabledClick(0, list);
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(shownDialog());
        assertNull(shadowOf(activity).getLastRequestedPermission());
        assertEquals("Allow location",
                ((TextView) activity.findViewById(R.id.status_banner_action)).getText().toString());
    }

    @Test
    public void batteryOptimisationIsExplainedBeforeTheSystemDialog() throws Exception {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS);
        ListActivityTestSupport.addLogjob(app, "Hike", false);
        LogjobsListViewActivity activity = launch();
        turnOnFirstJob(activity);

        AlertDialog dialog = shownDialog();
        assertNotNull(dialog);
        assertEquals(app.getString(R.string.battery_rationale), message(dialog));
        assertNull(shadowOf(activity).getNextStartedActivity());

        click(dialog, DialogInterface.BUTTON_POSITIVE);
        Intent started = shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, started.getAction());
    }
}
