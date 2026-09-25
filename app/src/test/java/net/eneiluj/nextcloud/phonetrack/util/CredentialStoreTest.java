package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CredentialStoreTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private final SharedPreferences defaults = PreferenceManager.getDefaultSharedPreferences(context);

    @Test
    public void migrateMovesTheAccountPasswordOutOfTheBackedUpPreferences() {
        defaults.edit().putString(SettingsActivity.SETTINGS_PASSWORD, "s3cret").commit();

        CredentialStore.migrate(context);

        assertFalse(defaults.contains(SettingsActivity.SETTINGS_PASSWORD));
        assertEquals("s3cret", CredentialStore.getAccountPassword(context));

        // runs on every start: nothing to move any more, the password stays
        CredentialStore.migrate(context);
        assertEquals("s3cret", CredentialStore.getAccountPassword(context));
    }

    @Test
    public void accountPasswordDefaultsToEmpty() {
        assertEquals("", CredentialStore.getAccountPassword(context));
        CredentialStore.setAccountPassword(context, "pw");
        CredentialStore.setAccountPassword(context, "");
        assertEquals("", CredentialStore.getAccountPassword(context));
    }

    @Test
    public void logjobPasswordsAreStoredPerLogjob() {
        CredentialStore.setLogjobPassword(context, 1, "one");
        CredentialStore.setLogjobPassword(context, 2, "two");
        assertEquals("one", CredentialStore.getLogjobPassword(context, 1));
        assertEquals("two", CredentialStore.getLogjobPassword(context, 2));

        CredentialStore.removeLogjobPassword(context, 1);
        assertNull(CredentialStore.getLogjobPassword(context, 1));
        assertEquals("two", CredentialStore.getLogjobPassword(context, 2));
    }
}
