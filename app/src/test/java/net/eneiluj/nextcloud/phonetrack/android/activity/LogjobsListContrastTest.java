package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.graphics.Color;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

/** The "+" buttons and the drawer header follow the app colour's brightness. */
@RunWith(AndroidJUnit4.class)
public class LogjobsListContrastTest {

    private static final int[] FABS = {R.id.fabMain, R.id.fab_create_session, R.id.fab_create_phonetrack,
            R.id.fab_create_maps, R.id.fab_create_custom};

    private final Context context = ApplicationProvider.getApplicationContext();
    private ActivityController<LogjobsListViewActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        DbTestSupport.resetDatabase();
    }

    private LogjobsListViewActivity launchWithColor(int color) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt(context.getString(R.string.pref_key_color), color).commit();
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        return controller.get();
    }

    @Test
    public void lightAppColourGetsDarkIcons() {
        LogjobsListViewActivity activity = launchWithColor(0xFFCDDC39);
        for (int id : FABS) {
            FloatingActionButton fab = activity.findViewById(id);
            assertEquals(Color.BLACK, fab.getImageTintList().getDefaultColor());
        }
        assertEquals(Color.BLACK, ((TextView) activity.findViewById(R.id.account)).getCurrentTextColor());
    }

    @Test
    public void darkAppColourKeepsWhiteIcons() {
        LogjobsListViewActivity activity = launchWithColor(0xFF3F51B5);
        for (int id : FABS) {
            FloatingActionButton fab = activity.findViewById(id);
            assertEquals(Color.WHITE, fab.getImageTintList().getDefaultColor());
        }
        assertEquals(Color.WHITE, ((TextView) activity.findViewById(R.id.account)).getCurrentTextColor());
    }
}
