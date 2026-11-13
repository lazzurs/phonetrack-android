package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.model.DBSyslog;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.LocalNetworkAccess;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.fakes.RoboMenu;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.List;

/** Switching off the Nextcloud Files account and re-enabling the activity log ask first. */
@RunWith(AndroidJUnit4.class)
public class DestructiveActionConfirmationTest {

    private Application app;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
        shadowOf(app).grantPermissions(LocalNetworkAccess.PERMISSION);
        SystemLogger.setEnabled(false);
    }

    @After
    public void tearDown() {
        SystemLogger.setEnabled(false);
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit();
        DbTestSupport.resetDatabase();
    }

    private AlertDialog latestDialog() {
        return ShadowAlertDialog.getLatestAlertDialog();
    }

    // account / SSO

    private SettingsActivity launchSettingsWithSso() {
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(SettingsActivity.SETTINGS_USE_SSO, true)
                .putString(SettingsActivity.SETTINGS_URL, "https://cloud.example.org")
                .commit();
        PhoneTrackSQLiteOpenHelper.getInstance(app).addSessionAndSync(
                new DBSession(0, "tok", "tok", "https://cloud.example.org", null, false, false));
        return Robolectric.buildActivity(SettingsActivity.class).setup().get();
    }

    @Test
    public void switchingOffSsoAsksFirstAndKeepsEverythingUntilConfirmed() {
        SettingsActivity activity = launchSettingsWithSso();
        SwitchCompat sso = activity.findViewById(R.id.use_sso_switch);
        assertTrue(sso.isChecked());

        sso.performClick();

        AlertDialog dialog = latestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        assertEquals(app.getString(R.string.sso_disconnect_title),
                shadowOf(dialog).getTitle().toString());
        // nothing has happened yet
        assertTrue(sso.isChecked());
        assertEquals(1, PhoneTrackSQLiteOpenHelper.getInstance(app).getSessions().size());
        assertTrue(PreferenceManager.getDefaultSharedPreferences(app)
                .getBoolean(SettingsActivity.SETTINGS_USE_SSO, false));
    }

    @Test
    public void cancellingKeepsTheAccount() {
        SettingsActivity activity = launchSettingsWithSso();
        SwitchCompat sso = activity.findViewById(R.id.use_sso_switch);
        sso.performClick();

        latestDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(sso.isChecked());
        assertEquals(1, PhoneTrackSQLiteOpenHelper.getInstance(app).getSessions().size());
        assertTrue(PreferenceManager.getDefaultSharedPreferences(app)
                .getBoolean(SettingsActivity.SETTINGS_USE_SSO, false));
    }

    @Test
    public void confirmingDisconnectsAndRemovesTheSyncedSessions() {
        SettingsActivity activity = launchSettingsWithSso();
        SwitchCompat sso = activity.findViewById(R.id.use_sso_switch);
        sso.performClick();

        latestDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(sso.isChecked());
        assertTrue(PhoneTrackSQLiteOpenHelper.getInstance(app).getSessions().isEmpty());
        assertFalse(PreferenceManager.getDefaultSharedPreferences(app)
                .getBoolean(SettingsActivity.SETTINGS_USE_SSO, true));
        assertEquals(SettingsActivity.DEFAULT_SETTINGS, PreferenceManager.getDefaultSharedPreferences(app)
                .getString(SettingsActivity.SETTINGS_URL, "x"));
    }

    // activity log

    private MenuItem toggleItem(SyslogManagerActivity activity) {
        Menu menu = new RoboMenu(app);
        activity.onCreateOptionsMenu(menu);
        return menu.findItem(R.id.menu_toggle_logs);
    }

    @Test
    public void enablingTheActivityLogAsksBeforeWipingEntries() {
        PhoneTrackSQLiteOpenHelper.getInstance(app).addSyslogs(
                List.of(new DBSyslog("an old entry", "TAG", "w", 1_700_000_000L)), SystemLogger.MAX_ROWS);
        SyslogManagerActivity activity = Robolectric.buildActivity(SyslogManagerActivity.class).setup().get();
        MenuItem toggle = toggleItem(activity);

        assertTrue(activity.onOptionsItemSelected(toggle));

        AlertDialog dialog = latestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        assertEquals(1, PhoneTrackSQLiteOpenHelper.getInstance(app).getSyslogs(null, null).size());
        assertFalse(SystemLogger.getEnabled());

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, PhoneTrackSQLiteOpenHelper.getInstance(app).getSyslogs(null, null).size());
        assertFalse(SystemLogger.getEnabled());
    }

    @Test
    public void confirmingClearsTheLogAndEnablesLogging() {
        PhoneTrackSQLiteOpenHelper.getInstance(app).addSyslogs(
                List.of(new DBSyslog("an old entry", "TAG", "w", 1_700_000_000L)), SystemLogger.MAX_ROWS);
        SyslogManagerActivity activity = Robolectric.buildActivity(SyslogManagerActivity.class).setup().get();
        MenuItem toggle = toggleItem(activity);
        activity.onOptionsItemSelected(toggle);

        latestDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(PhoneTrackSQLiteOpenHelper.getInstance(app).getSyslogs(null, null).isEmpty());
        assertTrue(SystemLogger.getEnabled());
        assertTrue(toggle.isChecked());
        assertEquals("", ((TextView) activity.findViewById(R.id.syslog_text)).getText().toString());
    }

    @Test
    public void enablingAnEmptyLogNeedsNoQuestion() {
        SyslogManagerActivity activity = Robolectric.buildActivity(SyslogManagerActivity.class).setup().get();
        ShadowAlertDialog.reset();

        activity.onOptionsItemSelected(toggleItem(activity));

        assertNull(latestDialog());
        assertTrue(SystemLogger.getEnabled());
    }

    @Test
    public void switchingTheLogOffNeedsNoQuestion() {
        SystemLogger.setEnabled(true);
        PhoneTrackSQLiteOpenHelper.getInstance(app).addSyslogs(
                List.of(new DBSyslog("an entry", "TAG", "w", 1_700_000_000L)), SystemLogger.MAX_ROWS);
        SyslogManagerActivity activity = Robolectric.buildActivity(SyslogManagerActivity.class).setup().get();
        ShadowAlertDialog.reset();

        activity.onOptionsItemSelected(toggleItem(activity));

        assertNull(latestDialog());
        assertFalse(SystemLogger.getEnabled());
        assertEquals(1, PhoneTrackSQLiteOpenHelper.getInstance(app).getSyslogs(null, null).size());
    }
}
