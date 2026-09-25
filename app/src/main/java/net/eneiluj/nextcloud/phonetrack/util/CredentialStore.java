package net.eneiluj.nextcloud.phonetrack.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;

/**
 * Passwords, kept out of the default preferences and the database so they can be left out
 * of cloud backups (see res/xml/data_extraction_rules.xml and backup_rules.xml, which exclude
 * {@link #FILE_NAME}). After a restore from the cloud they have to be entered again.
 * The file is app-private like every SharedPreferences file.
 */
public final class CredentialStore {

    /** SharedPreferences name; the backup rules exclude "credentials.xml" */
    public static final String FILE_NAME = "credentials";

    private static final String KEY_ACCOUNT_PASSWORD = "accountPassword";
    private static final String KEY_LOGJOB_PASSWORD_PREFIX = "logjobPassword_";

    private CredentialStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /** Password of the Nextcloud account used without SSO; "" if none. */
    public static String getAccountPassword(Context context) {
        return prefs(context).getString(KEY_ACCOUNT_PASSWORD, SettingsActivity.DEFAULT_SETTINGS);
    }

    public static void setAccountPassword(Context context, @Nullable String password) {
        put(prefs(context).edit(), KEY_ACCOUNT_PASSWORD, password).apply();
    }

    /** HTTP basic auth password of a custom logjob, or null. */
    @Nullable
    public static String getLogjobPassword(Context context, long logjobId) {
        return prefs(context).getString(KEY_LOGJOB_PASSWORD_PREFIX + logjobId, null);
    }

    @SuppressLint("ApplySharedPref")
    public static void setLogjobPassword(Context context, long logjobId, @Nullable String password) {
        // commit, not apply: the caller has just cleared the database copy
        put(prefs(context).edit(), KEY_LOGJOB_PASSWORD_PREFIX + logjobId, password).commit();
    }

    public static void removeLogjobPassword(Context context, long logjobId) {
        setLogjobPassword(context, logjobId, null);
    }

    /**
     * Moves the account password out of the default preferences, which are included in
     * backups. Safe to call on every start.
     */
    @SuppressLint("ApplySharedPref")
    public static void migrate(Context context) {
        SharedPreferences defaults = PreferenceManager.getDefaultSharedPreferences(context);
        if (defaults.contains(SettingsActivity.SETTINGS_PASSWORD)) {
            String password = defaults.getString(SettingsActivity.SETTINGS_PASSWORD, null);
            if (password != null && !password.isEmpty()) {
                put(prefs(context).edit(), KEY_ACCOUNT_PASSWORD, password).commit();
            }
            defaults.edit().remove(SettingsActivity.SETTINGS_PASSWORD).commit();
        }
    }

    private static SharedPreferences.Editor put(SharedPreferences.Editor editor, String key, @Nullable String value) {
        return value == null || value.isEmpty() ? editor.remove(key) : editor.putString(key, value);
    }
}
