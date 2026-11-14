package net.eneiluj.nextcloud.phonetrack.android.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "Keep uploaded positions on this phone" sits with the upload settings, starts at "Forever", and
 * its summary says plainly what a shorter choice deletes and what it never deletes.
 */
@RunWith(AndroidJUnit4.class)
public class KeepUploadedPreferenceTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private ActivityController<PreferencesActivity> controller;
    private ListPreference keep;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
    }

    @After
    public void tearDown() {
        controller.destroy();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        DbTestSupport.resetDatabase();
    }

    private PreferencesFragment openSettings() {
        controller = Robolectric.buildActivity(PreferencesActivity.class).setup();
        PreferencesFragment fragment = (PreferencesFragment) controller.get()
                .getSupportFragmentManager().findFragmentByTag("preftag");
        keep = fragment.findPreference(context.getString(R.string.pref_key_keep_uploaded));
        return fragment;
    }

    /** What the list dialog does when a choice is tapped. */
    private boolean choose(String value) {
        boolean accepted = keep.callChangeListener(value);
        if (accepted) {
            keep.setValue(value);
        }
        return accepted;
    }

    private static List<String> strings(CharSequence[] texts) {
        List<String> result = new ArrayList<>();
        for (CharSequence text : texts) {
            result.add(text.toString());
        }
        return result;
    }

    private String saved() {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.pref_key_keep_uploaded), null);
    }

    @Test
    public void startsAtForeverAndSaysNothingIsDeleted() {
        openSettings();

        assertEquals("0", keep.getValue());
        assertEquals(context.getString(R.string.settings_keep_uploaded_forever_summary), keep.getSummary());
        String summary = keep.getSummary().toString();
        assertTrue(summary, summary.contains("nothing is deleted"));
    }

    @Test
    public void offersForeverAYearNinetyAndThirtyDays() {
        openSettings();

        assertEquals(Arrays.asList("Forever", "1 year", "90 days", "30 days"), strings(keep.getEntries()));
        assertEquals(Arrays.asList("0", "365", "90", "30"), strings(keep.getEntryValues()));
    }

    @Test
    public void shorterChoiceSaysWhatIsDeletedAndWhatIsKept() {
        openSettings();

        assertTrue(choose("30"));
        assertEquals("30", saved());
        String summary = keep.getSummary().toString();
        assertTrue(summary, summary.startsWith("Uploaded positions more than 30 days old are deleted from this phone"));
        assertTrue(summary, summary.contains("not from the server"));
        assertTrue(summary, summary.contains("PhoneTrack and Nextcloud Maps log jobs only"));
        assertTrue(summary, summary.contains("only counts the positions kept"));
        assertTrue(summary, summary.contains("map"));
        assertTrue(summary, summary.contains("Positions not uploaded yet, and all positions of custom log jobs, are always kept"));

        assertTrue(choose("365"));
        assertTrue(keep.getSummary().toString().startsWith("Uploaded positions more than 1 year old"));

        assertTrue(choose("0"));
        assertEquals("0", saved());
        assertEquals(context.getString(R.string.settings_keep_uploaded_forever_summary), keep.getSummary());
    }

    @Test
    public void showsTheSavedChoiceWhenOpened() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.pref_key_keep_uploaded), "90").commit();
        openSettings();

        assertEquals("90 days", keep.getEntry());
        assertTrue(keep.getSummary().toString().startsWith("Uploaded positions more than 90 days old"));
    }

    @Test
    public void sitsWithTheUploadSettings() {
        PreferencesFragment fragment = openSettings();
        EditTextPreference deadline = fragment.findPreference(context.getString(R.string.pref_key_send_deadline));

        assertEquals(deadline.getParent(), keep.getParent());
        assertEquals(deadline.getOrder() + 1, keep.getOrder());
    }
}
