package net.eneiluj.nextcloud.phonetrack.util;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
//import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatDelegate;

import net.eneiluj.nextcloud.phonetrack.service.SmsAlarmService;

public class PhoneTrack extends Application {
    /** Old theme setting, a boolean (dark or not). Kept as it was after the migration. */
    static final String DARK_THEME = "darkTheme";
    /** Theme setting: a string, one of the THEME_* values below. */
    public static final String THEME_MODE = "themeMode";
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    @Override
    public void onCreate() {
        // first, before any other code writes a preference: tells a new install from an update
        setAppTheme(getThemeMode(getApplicationContext()));
        super.onCreate();
        // before anything is backed up again: passwords leave the default preferences
        CredentialStore.migrate(this);
        // if the process died while an SMS alarm was ringing, put the alarm volume back
        SmsAlarmService.restoreVolumeAfterInterruptedAlarm(this);
        SystemLogger.init(getApplicationContext());
    }

    public static void setAppTheme(String themeMode) {
        if (THEME_DARK.equals(themeMode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (THEME_LIGHT.equals(themeMode)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    /**
     * The theme setting: "system", "light" or "dark". It used to be a Light/Dark switch stored
     * as a boolean under another key; that value is converted once:
     * - a stored switch keeps what the user chose;
     * - an update from a version that had no stored switch keeps the light theme it always had;
     * - a fresh install follows the system.
     * The old key is left alone.
     */
    @NonNull
    public static String getThemeMode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String mode;
        try {
            mode = prefs.getString(THEME_MODE, null);
        } catch (ClassCastException e) {
            mode = null;
        }
        if (THEME_SYSTEM.equals(mode) || THEME_LIGHT.equals(mode) || THEME_DARK.equals(mode)) {
            return mode;
        }
        if (prefs.contains(DARK_THEME)) {
            boolean dark;
            try {
                dark = prefs.getBoolean(DARK_THEME, false);
            } catch (ClassCastException e) {
                dark = false;
            }
            mode = dark ? THEME_DARK : THEME_LIGHT;
        } else if (!prefs.getAll().isEmpty()) {
            mode = THEME_LIGHT;
        } else {
            mode = THEME_SYSTEM;
        }
        prefs.edit().putString(THEME_MODE, mode).apply();
        return mode;
    }

    /** Whether the dark theme is showing now, whether chosen or followed from the system. */
    public static boolean getAppTheme(Context context) {
        return isDark(context, getThemeMode(context));
    }

    /** Whether the dark theme shows with this setting. */
    public static boolean isDark(Context context, String mode) {
        if (THEME_SYSTEM.equals(mode)) {
            return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        }
        return THEME_DARK.equals(mode);
    }
}
