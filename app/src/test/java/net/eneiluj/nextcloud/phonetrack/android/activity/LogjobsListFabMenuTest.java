package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Looper;
import android.view.View;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.util.ThemeUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

/** The "New ..." speed-dial menu (Material FAB, replaced com.github.clans:fab). */
@RunWith(AndroidJUnit4.class)
public class LogjobsListFabMenuTest {

    private ActivityController<LogjobsListViewActivity> controller;
    private LogjobsListViewActivity activity;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        activity = controller.get();
    }

    @After
    public void tearDown() {
        controller.destroy();
        DbTestSupport.resetDatabase();
    }

    private View view(int id) {
        return activity.findViewById(id);
    }

    private boolean menuOpen() {
        return view(R.id.fabActions).getVisibility() == View.VISIBLE;
    }

    private void tapMain() {
        view(R.id.fabMain).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** The activity also starts other things (permission / battery screens): look for ours. */
    private Intent startedActivity(Class<?> cls) {
        Intent intent;
        while ((intent = shadowOf(activity).getNextStartedActivity()) != null) {
            if (intent.getComponent() != null && cls.getName().equals(intent.getComponent().getClassName())) {
                return intent;
            }
        }
        return null;
    }

    @Test
    public void mainButtonOpensAndClosesTheMenu() {
        assertFalse(menuOpen());
        assertEquals(View.GONE, view(R.id.fabScrim).getVisibility());

        tapMain();
        assertTrue(menuOpen());
        assertEquals(View.VISIBLE, view(R.id.fabScrim).getVisibility());
        assertEquals(activity.getString(R.string.simple_cancel), view(R.id.fabMain).getContentDescription());

        tapMain();
        assertFalse(menuOpen());
        assertEquals(activity.getString(R.string.action_create), view(R.id.fabMain).getContentDescription());
    }

    @Test
    public void backAndTheBackdropCloseTheMenu() {
        tapMain();
        activity.getOnBackPressedDispatcher().onBackPressed();
        assertFalse(menuOpen());
        assertFalse(activity.isFinishing());

        tapMain();
        view(R.id.fabScrim).performClick();
        assertFalse(menuOpen());
    }

    @Test
    public void accountEntriesAreHiddenWithoutANextcloudAccount() {
        tapMain();
        assertEquals(View.GONE, view(R.id.fab_row_session).getVisibility());
        assertEquals(View.GONE, view(R.id.fab_row_maps).getVisibility());
        assertEquals(View.VISIBLE, view(R.id.fab_row_phonetrack).getVisibility());
        assertEquals(View.VISIBLE, view(R.id.fab_row_custom).getVisibility());
    }

    @Test
    public void accountEntriesShowOnceAnAccountIsConfigured() {
        // checked each time the menu opens, so no restart is needed after logging in
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putString(SettingsActivity.SETTINGS_URL, "https://cloud.example.org/").commit();
        tapMain();
        assertEquals(View.VISIBLE, view(R.id.fab_row_session).getVisibility());
        assertEquals(View.VISIBLE, view(R.id.fab_row_maps).getVisibility());
    }

    @Test
    public void buttonsAndTheirLabelsStartTheLogjobEditorsAndCloseTheMenu() {
        tapMain();
        view(R.id.fab_create_phonetrack).performClick();
        assertNotNull(startedActivity(EditPhoneTrackLogjobActivity.class));
        assertFalse(menuOpen());

        tapMain();
        view(R.id.fab_create_custom_label).performClick();
        assertNotNull(startedActivity(EditCustomLogjobActivity.class));
        assertFalse(menuOpen());
    }

    @Test
    public void buttonsUseTheAppColor() {
        int expected = ThemeUtils.primaryColor(activity);
        for (int id : new int[]{R.id.fabMain, R.id.fab_create_session, R.id.fab_create_phonetrack,
                R.id.fab_create_maps, R.id.fab_create_custom}) {
            FloatingActionButton fab = activity.findViewById(id);
            assertEquals(expected, fab.getBackgroundTintList().getDefaultColor());
        }
    }
}
