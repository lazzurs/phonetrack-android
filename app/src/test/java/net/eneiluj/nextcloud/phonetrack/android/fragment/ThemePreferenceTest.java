package net.eneiluj.nextcloud.phonetrack.android.fragment;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.PreferencesActivity;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrack;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

/** The theme setting offers Follow system, Light and Dark, and an old Light/Dark choice survives. */
@RunWith(AndroidJUnit4.class)
public class ThemePreferenceTest {

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
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        DbTestSupport.resetDatabase();
    }

    private ListPreference launch() {
        controller = Robolectric.buildActivity(PreferencesActivity.class).setup();
        PreferencesFragment fragment = (PreferencesFragment) controller.get()
                .getSupportFragmentManager().findFragmentByTag("preftag");
        return fragment.findPreference(context.getString(R.string.pref_key_theme_mode));
    }

    @Test
    public void offersThreeChoices() {
        ListPreference pref = launch();
        assertArrayEquals(new CharSequence[]{"system", "light", "dark"}, pref.getEntryValues());
        assertArrayEquals(new CharSequence[]{context.getString(R.string.pref_value_theme_system),
                context.getString(R.string.pref_value_theme_light),
                context.getString(R.string.pref_value_theme_dark)}, pref.getEntries());
        assertEquals("system", pref.getValue());
        assertEquals(context.getString(R.string.pref_value_theme_system), pref.getSummary().toString());
    }

    @Test
    public void anOldDarkChoiceIsShownAsDark() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("darkTheme", true).commit();
        // the app converts the old switch when it starts, before any screen opens
        PhoneTrack.getThemeMode(context);
        ListPreference pref = launch();
        assertEquals("dark", pref.getValue());
        assertEquals(context.getString(R.string.pref_value_theme_dark), pref.getSummary().toString());
    }

    @Test
    public void choosingAThemeStoresItAndAppliesTheNightMode() {
        ListPreference pref = launch();

        pref.callChangeListener("dark");
        pref.setValue("dark");
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
        assertEquals("dark", PhoneTrack.getThemeMode(context));

        PhoneTrack.setAppTheme("light");
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
        PhoneTrack.setAppTheme("system");
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode());
    }
}
