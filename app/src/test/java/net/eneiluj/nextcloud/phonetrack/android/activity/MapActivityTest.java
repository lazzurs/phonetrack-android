package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowShortcutManager;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

import java.util.Collections;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class MapActivityTest {

    private static final int[] MAP_BUTTONS = {R.id.ic_map_layers, R.id.ic_center_map, R.id.ic_follow_me,
            R.id.ic_zoom_all, R.id.ic_zoom_auto, R.id.ic_allow_rotation};

    private final Application app = ApplicationProvider.getApplicationContext();
    private ActivityController<MapActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        ShadowToast.reset();
    }

    @After
    public void tearDown() {
        if (controller != null) {
            // stops the refresh timer before the database goes away
            controller.pause().stop().destroy();
        }
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit();
        DbTestSupport.resetDatabase();
    }

    private Intent mapIntent(long sessionId) {
        return new Intent(app, MapActivity.class).putExtra(MapActivity.PARAM_SESSIONID, sessionId);
    }

    private MapActivity launchMap() {
        long id = PhoneTrackSQLiteOpenHelper.getInstance(app).addSessionAndSync(
                new DBSession(0, "family", "family", "https://cloud.example.org/", "", false, false));
        controller = Robolectric.buildActivity(MapActivity.class, mapIntent(id)).setup();
        shadowOf(Looper.getMainLooper()).idle();
        return controller.get();
    }

    private void click(MapActivity activity, int id) {
        activity.findViewById(id).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void assertToggle(ImageButton button, boolean on) {
        assertEquals(on, button.isSelected());
        Application app = ApplicationProvider.getApplicationContext();
        assertEquals(app.getString(on ? R.string.map_toggle_on : R.string.map_toggle_off),
                String.valueOf(button.getStateDescription()));
    }

    private static TextView findSnackbarText(View view) {
        if (view.getId() == com.google.android.material.R.id.snackbar_text) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findSnackbarText(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void deletedSessionClosesTheMapAndDisablesItsShortcut() {
        ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(app, "map42")
                .setShortLabel("family")
                .setIntent(mapIntent(42).setAction(Intent.ACTION_VIEW))
                .build();
        shadowOf(app.getSystemService(ShortcutManager.class)).setIsRequestPinShortcutSupported(true);
        assertTrue(ShortcutManagerCompat.requestPinShortcut(app, shortcut, null));
        assertFalse(disabledShortcut("map42"));

        controller = Robolectric.buildActivity(MapActivity.class, mapIntent(42)).setup();

        assertTrue(controller.get().isFinishing());
        assertEquals(app.getString(R.string.session_not_found), ShadowToast.getTextOfLatestToast());
        assertTrue(disabledShortcut("map42"));
    }

    /** Robolectric's ShortcutManager keeps disabled pinned shortcuts apart but marks them enabled. */
    private static boolean disabledShortcut(String id) {
        Map<String, ?> disabled = ReflectionHelpers.getStaticField(ShadowShortcutManager.class, "disabledPinnedShortcuts");
        return disabled.containsKey(id);
    }

    @Test
    public void missingSessionIdClosesTheMap() {
        controller = Robolectric.buildActivity(MapActivity.class, new Intent(app, MapActivity.class)).setup();
        assertTrue(controller.get().isFinishing());
    }

    @Test
    public void loadErrorIsShownWithRetry() {
        MapActivity activity = launchMap();
        activity.syncCallBack.onFinish(null, null,
                "Session retrieval failed: server connection is broken\n\njava.io.IOException: boom");
        shadowOf(Looper.getMainLooper()).idle();

        TextView text = findSnackbarText(activity.getWindow().getDecorView());
        assertNotNull("no snackbar", text);
        assertEquals("Session retrieval failed: server connection is broken", text.getText().toString());
        assertNull(activity.getSupportActionBar().getSubtitle());
    }

    @Test
    public void successfulRefreshShowsTheTimeAndClearsTheError() {
        MapActivity activity = launchMap();
        activity.syncCallBack.onFinish(null, null, "Session retrieval failed: boom");
        shadowOf(Looper.getMainLooper()).idle();

        activity.syncCallBack.onFinish(Collections.emptyMap(), Collections.emptyMap(), "");
        shadowOf(Looper.getMainLooper()).idle();

        CharSequence subtitle = activity.getSupportActionBar().getSubtitle();
        assertNotNull(subtitle);
        assertTrue(subtitle.toString(), subtitle.toString().startsWith("Updated "));
    }

    @Test
    public void togglesExposeTheirState() {
        MapActivity activity = launchMap();
        ImageButton autoZoom = activity.findViewById(R.id.ic_zoom_auto);
        ImageButton rotation = activity.findViewById(R.id.ic_allow_rotation);
        // defaults: auto zoom on, rotation off
        assertToggle(autoZoom, true);
        assertToggle(rotation, false);

        click(activity, R.id.ic_zoom_auto);
        assertToggle(autoZoom, false);
        click(activity, R.id.ic_allow_rotation);
        assertToggle(rotation, true);
        click(activity, R.id.ic_allow_rotation);
        assertToggle(rotation, false);
    }

    @Test
    public void followMeWithPermissionTogglesOnAndOff() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        MapActivity activity = launchMap();
        ImageButton followMe = activity.findViewById(R.id.ic_follow_me);
        assertToggle(followMe, false);

        click(activity, R.id.ic_follow_me);
        assertToggle(followMe, true);
        assertToggle(activity.findViewById(R.id.ic_center_map), true);
        // follow me and auto zoom exclude each other
        assertToggle(activity.findViewById(R.id.ic_zoom_auto), false);

        click(activity, R.id.ic_follow_me);
        assertToggle(followMe, false);
    }

    @Test
    public void myPositionWithoutPermissionAsksForItAndStaysOff() {
        MapActivity activity = launchMap();
        ImageButton myPosition = activity.findViewById(R.id.ic_center_map);
        // on by default, but it can't work without the permission
        assertToggle(myPosition, false);

        click(activity, R.id.ic_center_map);
        assertToggle(myPosition, false);
        String[] requested = shadowOf(activity).getLastRequestedPermission().requestedPermissions;
        assertTrue(java.util.Arrays.asList(requested).contains(Manifest.permission.ACCESS_FINE_LOCATION));
    }

    @Test
    public void lightAppColourGetsDarkToggleIcons() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putInt(app.getString(R.string.pref_key_color), 0xFFCDDC39).commit();
        MapActivity activity = launchMap();
        ImageButton autoZoom = activity.findViewById(R.id.ic_zoom_auto);
        assertEquals(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_ATOP), autoZoom.getColorFilter());
        click(activity, R.id.ic_zoom_auto);
        // off: white on the dark strip over the map, as before
        assertEquals(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP), autoZoom.getColorFilter());
    }

    @Test
    public void mapButtonsAreAtLeast48dp() {
        MapActivity activity = launchMap();
        int min = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        for (int id : MAP_BUTTONS) {
            View button = activity.findViewById(id);
            String name = activity.getResources().getResourceEntryName(id);
            assertTrue(name, button.getMinimumWidth() >= min);
            assertTrue(name, button.getMinimumHeight() >= min);
        }
    }
}
