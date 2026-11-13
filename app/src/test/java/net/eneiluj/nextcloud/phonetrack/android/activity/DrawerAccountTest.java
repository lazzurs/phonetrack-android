package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

/** The account line at the top of the navigation drawer. */
@RunWith(AndroidJUnit4.class)
public class DrawerAccountTest {

    private Application app;
    private ActivityController<LogjobsListViewActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        DbTestSupport.resetDatabase();
    }

    private String accountText() {
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        TextView account = controller.get().findViewById(R.id.account);
        return account.getText().toString();
    }

    @Test
    public void missingFilesAppAccountAsksToReconnect() {
        // signed in through the Nextcloud Files app, whose account is gone
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(SettingsActivity.SETTINGS_USE_SSO, true).commit();

        assertEquals(app.getString(R.string.drawer_account_unavailable), accountText());
    }

    @Test
    public void webLoginAccountIsShownAsUserAtServer() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putString(SettingsActivity.SETTINGS_URL, "https://cloud.example.org/")
                .putString(SettingsActivity.SETTINGS_USERNAME, "alice").commit();

        assertEquals("alice@cloud.example.org", accountText());
    }

    @Test
    public void noAccountInvitesToConnect() {
        assertEquals(app.getString(R.string.drawer_connect_hint), accountText());
    }
}
