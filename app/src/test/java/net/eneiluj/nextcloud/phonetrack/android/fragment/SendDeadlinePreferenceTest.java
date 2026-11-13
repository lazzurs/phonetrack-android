package net.eneiluj.nextcloud.phonetrack.android.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.PreferencesActivity;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

/** "Send at the latest after" sits under "Send points by bunch of at least" and depends on it. */
@RunWith(AndroidJUnit4.class)
public class SendDeadlinePreferenceTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private ActivityController<PreferencesActivity> controller;
    private EditTextPreference bunch;
    private EditTextPreference deadline;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
    }

    @After
    public void tearDown() {
        controller.destroy();
        DbTestSupport.resetDatabase();
    }

    private void openSettings() {
        controller = Robolectric.buildActivity(PreferencesActivity.class).setup();
        PreferencesFragment fragment = (PreferencesFragment) controller.get()
                .getSupportFragmentManager().findFragmentByTag("preftag");
        bunch = fragment.findPreference(context.getString(R.string.pref_key_group_sync));
        deadline = fragment.findPreference(context.getString(R.string.pref_key_send_deadline));
    }

    /** What the edit dialog does when OK is pressed. */
    private static boolean type(EditTextPreference pref, String value) {
        boolean accepted = pref.callChangeListener(value);
        if (accepted) {
            pref.setText(value);
        }
        shadowOf(Looper.getMainLooper()).idle();
        return accepted;
    }

    private String saved(int keyId) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(keyId), null);
    }

    @Test
    public void isDirectlyBelowTheBunchSizeAndDisabledWhileEveryPointIsSent() {
        openSettings();

        PreferenceGroup category = bunch.getParent();
        assertEquals(category, deadline.getParent());
        assertEquals(bunch.getOrder() + 1, deadline.getOrder());

        assertFalse(deadline.isEnabled());
        assertEquals(context.getString(R.string.settings_send_deadline_not_needed), deadline.getSummary());
    }

    @Test
    public void isEnabledByABunchOfTwoOrMoreAndShowsHours() {
        openSettings();

        assertTrue(type(bunch, "1"));
        assertFalse(deadline.isEnabled());

        assertTrue(type(bunch, "10"));
        assertTrue(deadline.isEnabled());
        assertEquals(context.getString(R.string.settings_send_deadline_off), deadline.getSummary());

        assertTrue(type(deadline, "1"));
        assertEquals("1 hour", deadline.getSummary());
        assertTrue(type(deadline, "3"));
        assertEquals("3 hours", deadline.getSummary());
        assertEquals("3", saved(R.string.pref_key_send_deadline));

        // the value is kept, but has no effect, when the bunch is turned off
        assertTrue(type(bunch, "0"));
        assertFalse(deadline.isEnabled());
        assertEquals("3", saved(R.string.pref_key_send_deadline));
    }

    @Test
    public void rejectsWhatIsNotANumberOfHours() {
        openSettings();
        assertTrue(type(bunch, "10"));

        assertFalse(type(deadline, ""));
        assertFalse(type(deadline, "12345"));
        assertFalse(type(deadline, "1.5"));
        assertEquals(context.getString(R.string.settings_send_deadline_off), deadline.getSummary());
    }

    @Test
    public void showsTheSavedValuesWhenOpened() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.pref_key_group_sync), "100")
                .putString(context.getString(R.string.pref_key_send_deadline), "3")
                .commit();
        openSettings();

        assertTrue(deadline.isEnabled());
        assertEquals("3 hours", deadline.getSummary());
    }
}
