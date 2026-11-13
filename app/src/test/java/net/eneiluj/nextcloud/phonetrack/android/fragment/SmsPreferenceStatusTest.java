package net.eneiluj.nextcloud.phonetrack.android.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.PreferencesActivity;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.util.CredentialStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.HashMap;
import java.util.Map;

/** SMS remote control: explained permissions, and no silently half-enabled state. */
@RunWith(AndroidJUnit4.class)
public class SmsPreferenceStatusTest {

    private final Application context = ApplicationProvider.getApplicationContext();
    private ActivityController<PreferencesActivity> controller;
    private PreferencesFragment fragment;
    private CheckBoxPreference sms;
    private Preference fixBackground;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        CredentialStore.setSmsPin(context, "");
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        CredentialStore.setSmsPin(context, "");
        DbTestSupport.resetDatabase();
    }

    private void open() {
        controller = Robolectric.buildActivity(PreferencesActivity.class).setup();
        fragment = (PreferencesFragment) controller.get().getSupportFragmentManager().findFragmentByTag("preftag");
        sms = fragment.findPreference(context.getString(R.string.pref_key_sms));
        fixBackground = fragment.findPreference(context.getString(R.string.pref_key_sms_fix_background));
    }

    private void saveSms(boolean on) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(context.getString(R.string.pref_key_sms), on).commit();
    }

    private static Map<String, Boolean> result(boolean send, boolean receive) {
        Map<String, Boolean> map = new HashMap<>();
        map.put(Manifest.permission.SEND_SMS, send);
        map.put(Manifest.permission.RECEIVE_SMS, receive);
        return map;
    }

    private String summary() {
        return sms.getSummary().toString();
    }

    @Test
    public void disabledShowsOnlyTheDescription() {
        open();
        assertEquals(context.getString(R.string.settings_sms_subtitle), summary());
    }

    @Test
    public void enabledWithoutSendersOrPinWarnsAboutBoth() {
        saveSms(true);
        shadowOf(context).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS);
        open();

        assertTrue(summary().contains(context.getString(R.string.settings_sms_summary_no_senders)));
        assertTrue(summary().contains(context.getString(R.string.settings_sms_summary_no_pin)));
        assertFalse(summary().contains(context.getString(R.string.settings_sms_summary_no_permission)));
    }

    @Test
    public void warningsGoAwayWhenTheCauseIsFixed() {
        saveSms(true);
        shadowOf(context).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS);
        open();

        EditTextPreference allowed = fragment.findPreference(context.getString(R.string.pref_key_sms_allowed_senders));
        assertFalse(allowed.callChangeListener("+353861234567"));
        assertFalse(summary().contains(context.getString(R.string.settings_sms_summary_no_senders)));
        assertTrue(summary().contains(context.getString(R.string.settings_sms_summary_no_pin)));

        EditTextPreference pin = fragment.findPreference(context.getString(R.string.pref_key_sms_pin));
        assertFalse(pin.callChangeListener("1234"));
        assertFalse(summary().contains(context.getString(R.string.settings_sms_summary_no_pin)));
    }

    @Test
    public void enabledWithoutSmsPermissionSaysNothingCanBeReceived() {
        saveSms(true);
        open();
        assertTrue(summary().contains(context.getString(R.string.settings_sms_summary_no_permission)));
    }

    @Test
    public void disabledShowsNoWarningAndNoFix() {
        open();
        assertFalse(summary().contains(context.getString(R.string.settings_sms_summary_no_senders)));
        assertFalse(fixBackground.isVisible());
    }

    @Test
    public void missingBackgroundLocationOffersTheFix() {
        saveSms(true);
        shadowOf(context).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS);
        open();
        assertTrue(fixBackground.isVisible());

        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        controller.pause().resume();
        assertFalse(fixBackground.isVisible());
    }

    @Test
    public void tickingWithoutPermissionExplainsBeforeAsking() {
        open();

        assertFalse("stays off until allowed", sms.callChangeListener(true));
        shadowOf(Looper.getMainLooper()).idle();

        AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        TextView message = dialog.findViewById(android.R.id.message);
        assertEquals(context.getString(R.string.settings_sms_permission_message), message.getText().toString());
        assertFalse(sms.isChecked());
    }

    @Test
    public void tickingWithPermissionJustEnables() {
        shadowOf(context).grantPermissions(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS);
        open();
        assertTrue(sms.callChangeListener(true));
    }

    @Test
    public void onlyOneOfTheTwoSmsPermissionsKeepsTheFeatureOff() {
        open();
        fragment.onSmsPermissionResult(result(true, false));
        assertFalse(sms.isChecked());
        assertFalse(PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.pref_key_sms), false));
    }

    @Test
    public void grantingBothEnablesAndOffersContactNamesSeparately() {
        open();
        fragment.onSmsPermissionResult(result(true, true));
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(sms.isChecked());
        AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
        assertNotNull(dialog);
        TextView message = dialog.findViewById(android.R.id.message);
        assertEquals(context.getString(R.string.settings_sms_contacts_message), message.getText().toString());
    }

    @Test
    public void permanentDenialOffersTheAppSettings() {
        open();
        // Robolectric reports no rationale to show, which is what a permanent denial looks like
        fragment.onSmsPermissionResult(result(false, false));
        shadowOf(Looper.getMainLooper()).idle();

        assertFalse(sms.isChecked());
        AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
        assertNotNull(dialog);
        TextView message = dialog.findViewById(android.R.id.message);
        assertEquals(context.getString(R.string.settings_sms_permission_blocked_message), message.getText().toString());
    }
}
