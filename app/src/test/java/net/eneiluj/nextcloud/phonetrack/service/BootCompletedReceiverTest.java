package net.eneiluj.nextcloud.phonetrack.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Autostart at boot that cannot start says so, instead of only writing to the activity log. */
@RunWith(AndroidJUnit4.class)
public class BootCompletedReceiverTest {

    private Application app;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(app.getString(R.string.pref_key_autostart), true).commit();
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS);
    }

    @After
    public void tearDown() {
        DbTestSupport.resetDatabase();
    }

    private void addLogjob(boolean enabled) {
        PhoneTrackSQLiteOpenHelper.getInstance(app).addLogjob(new DBLogjob(0, "test", "http://127.0.0.1:9/log",
                "", "", 60, 0, 50, false, false, false, 60, false, enabled, 0, null, null, false));
    }

    private List<Notification> notifications() {
        return shadowOf(app.getSystemService(NotificationManager.class)).getAllNotifications();
    }

    private void boot() {
        new BootCompletedReceiver().onReceive(app, new Intent(Intent.ACTION_BOOT_COMPLETED));
    }

    @Test
    public void missingBackgroundLocationIsNotifiedWhenAJobIsOn() {
        addLogjob(true);
        boot();

        assertNull("service started without background location", shadowOf(app).getNextStartedService());
        List<Notification> notifications = notifications();
        assertEquals(1, notifications.size());
        String text = String.valueOf(notifications.get(0).extras.getCharSequence(Notification.EXTRA_TEXT));
        assertTrue(text, text.contains("\"" + TrackingReadiness.backgroundOptionLabel(app) + "\""));
        assertNotNull(notifications.get(0).contentIntent);
    }

    @Test
    public void nothingToSayWithoutEnabledJobs() {
        addLogjob(false);
        boot();
        assertTrue(notifications().isEmpty());
    }

    @Test
    public void startsTheServiceWhenAllowed() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        addLogjob(true);
        boot();
        Intent started = shadowOf(app).getNextStartedService();
        assertNotNull(started);
        assertEquals(LoggerService.class.getName(), started.getComponent().getClassName());
        assertTrue(notifications().isEmpty());
    }
}
