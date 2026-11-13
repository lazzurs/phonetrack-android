package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** The Light/Dark switch (a boolean) became Follow system / Light / Dark (a string, new key). */
@RunWith(AndroidJUnit4.class)
public class ThemeModeMigrationTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
    }

    @Test
    public void aFreshInstallFollowsTheSystem() {
        assertEquals(PhoneTrack.THEME_SYSTEM, PhoneTrack.getThemeMode(context));
        assertEquals(PhoneTrack.THEME_SYSTEM, prefs.getString(PhoneTrack.THEME_MODE, null));
    }

    @Test
    public void aStoredDarkSwitchBecomesDark() {
        prefs.edit().putBoolean("darkTheme", true).commit();
        assertEquals(PhoneTrack.THEME_DARK, PhoneTrack.getThemeMode(context));
        assertTrue(PhoneTrack.getAppTheme(context));
    }

    @Test
    public void aStoredLightSwitchBecomesLight() {
        prefs.edit().putBoolean("darkTheme", false).commit();
        assertEquals(PhoneTrack.THEME_LIGHT, PhoneTrack.getThemeMode(context));
        assertFalse(PhoneTrack.getAppTheme(context));
    }

    @Test
    public void anUpdateThatNeverTouchedTheSwitchKeepsTheLightThemeItHad() {
        prefs.edit().putString("SMSkeyword", "phonetrack").commit();
        assertEquals(PhoneTrack.THEME_LIGHT, PhoneTrack.getThemeMode(context));
    }

    @Test
    public void theMigrationRunsOnceAndLeavesTheOldKeyAlone() {
        prefs.edit().putBoolean("darkTheme", true).commit();
        PhoneTrack.getThemeMode(context);
        // a later change of the new setting wins, and the old boolean is still readable
        prefs.edit().putString(PhoneTrack.THEME_MODE, PhoneTrack.THEME_SYSTEM).commit();
        assertEquals(PhoneTrack.THEME_SYSTEM, PhoneTrack.getThemeMode(context));
        assertTrue(prefs.getBoolean("darkTheme", false));
    }

    @Test
    public void aCorruptedValueIsRepaired() {
        prefs.edit().putString("darkTheme", "yes").putString(PhoneTrack.THEME_MODE, "purple").commit();
        assertEquals(PhoneTrack.THEME_LIGHT, PhoneTrack.getThemeMode(context));
    }
}
