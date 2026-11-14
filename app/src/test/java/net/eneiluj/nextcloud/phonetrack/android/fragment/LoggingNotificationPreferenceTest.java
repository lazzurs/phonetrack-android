package net.eneiluj.nextcloud.phonetrack.android.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.PreferencesActivity;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.service.NotificationChannels;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

/**
 * "Logging notification" opens the system screen of the channel. The channel must exist by then:
 * the system screen closes at once for a channel it does not know, so that nothing seems to happen.
 */
@RunWith(AndroidJUnit4.class)
public class LoggingNotificationPreferenceTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private ActivityController<PreferencesActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        DbTestSupport.resetDatabase();
    }

    @Test
    public void createsTheChannelBeforeOpeningItsSystemScreen() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        // logging has never started on this install
        assertNull(manager.getNotificationChannel(NotificationChannels.LOGGING));

        controller = Robolectric.buildActivity(PreferencesActivity.class).setup();
        PreferencesFragment fragment = (PreferencesFragment) controller.get()
                .getSupportFragmentManager().findFragmentByTag("preftag");
        Preference pref = fragment.findPreference(context.getString(R.string.pref_key_logging_notification));
        pref.performClick();

        assertNotNull(manager.getNotificationChannel(NotificationChannels.LOGGING));
        Intent started = shadowOf((Application) context).getNextStartedActivity();
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, started.getAction());
        assertEquals(context.getPackageName(), started.getStringExtra(Settings.EXTRA_APP_PACKAGE));
        assertEquals(NotificationChannels.LOGGING, started.getStringExtra(Settings.EXTRA_CHANNEL_ID));
    }
}
