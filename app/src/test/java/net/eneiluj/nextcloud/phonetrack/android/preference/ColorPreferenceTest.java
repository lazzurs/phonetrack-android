package net.eneiluj.nextcloud.phonetrack.android.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Looper;
import android.widget.EditText;
import android.widget.GridLayout;

import androidx.appcompat.app.AlertDialog;
import android.content.SharedPreferences;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.PreferencesActivity;
import net.eneiluj.nextcloud.phonetrack.android.fragment.PreferencesFragment;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;

@RunWith(AndroidJUnit4.class)
public class ColorPreferenceTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @After
    public void tearDown() {
        DbTestSupport.resetDatabase();
    }

    @Test
    public void parsesAndFormatsHexColors() {
        assertEquals(Integer.valueOf(0xFF0082C9), ColorPreference.parseHex("#0082C9"));
        assertEquals(Integer.valueOf(0xFF0082C9), ColorPreference.parseHex(" 0082c9 "));
        assertNull(ColorPreference.parseHex("#0082C"));
        assertNull(ColorPreference.parseHex("#0082CG"));
        assertNull(ColorPreference.parseHex("#800082C9"));
        assertNull(ColorPreference.parseHex(null));
        assertEquals("#0082C9", ColorPreference.toHex(0xFF0082C9));
        assertEquals("#0082C9", ColorPreference.toHex(0x800082C9));
    }

    private ColorPreference attach() {
        PreferenceManager manager = new PreferenceManager(context);
        PreferenceScreen screen = manager.createPreferenceScreen(context);
        ColorPreference pref = new ColorPreference(context, null);
        pref.setKey(context.getString(R.string.pref_key_color));
        screen.addPreference(pref);
        return pref;
    }

    @Test
    public void keepsTheColorStoredByTheOldLibrary() {
        // colorpreference persisted an int under the same key
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putInt(context.getString(R.string.pref_key_color), 0xFF4CAF50).commit();

        assertEquals(0xFF4CAF50, attach().getValue());
    }

    @Test
    public void persistsTheChosenColorAsAnInt() {
        ColorPreference pref = attach();
        pref.setValue(0xFFFF5722);

        assertEquals(0xFFFF5722, PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(context.getString(R.string.pref_key_color), 0));
    }

    @Test
    public void settingsScreenInflatesTheColorPreference() {
        DbTestSupport.resetDatabase();
        ActivityController<PreferencesActivity> controller =
                Robolectric.buildActivity(PreferencesActivity.class).setup();
        PreferencesFragment fragment = (PreferencesFragment) controller.get()
                .getSupportFragmentManager().findFragmentByTag("preftag");
        assertNotNull(fragment);
        Preference pref = fragment.findPreference(context.getString(R.string.pref_key_color));
        assertEquals(ColorPreference.class, pref.getClass());
        controller.destroy();
    }

    @Test
    public void dialogStoresTheTappedPresetAndTheTypedHexColor() {
        DbTestSupport.resetDatabase();
        ActivityController<PreferencesActivity> controller =
                Robolectric.buildActivity(PreferencesActivity.class).setup();
        PreferencesFragment fragment = (PreferencesFragment) controller.get()
                .getSupportFragmentManager().findFragmentByTag("preftag");
        ColorPreference pref = fragment.findPreference(context.getString(R.string.pref_key_color));

        // tap a preset: the second swatch is Material red
        pref.performClick();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        GridLayout grid = dialog.findViewById(R.id.colorGrid);
        assertEquals(20, grid.getChildCount());
        grid.getChildAt(1).performClick();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0xFFF44336, storedColor());

        // OK recreates the activity to apply the color: continue on a fresh settings screen
        controller.destroy();
        controller = Robolectric.buildActivity(PreferencesActivity.class).setup();
        fragment = (PreferencesFragment) controller.get()
                .getSupportFragmentManager().findFragmentByTag("preftag");
        pref = fragment.findPreference(context.getString(R.string.pref_key_color));

        // type a hex value
        pref.performClick();
        dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        EditText hex = dialog.findViewById(R.id.colorHex);
        assertEquals("#F44336", hex.getText().toString());
        hex.setText("#123456");
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0xFF123456, storedColor());
        controller.destroy();
    }

    private int storedColor() {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(context.getString(R.string.pref_key_color), 0);
    }
}
