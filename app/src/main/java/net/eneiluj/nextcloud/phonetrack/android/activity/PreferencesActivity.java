package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.Window;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.fragment.PreferencesFragment;
import net.eneiluj.nextcloud.phonetrack.util.ThemeUtils;

import java.security.Permission;

/**
 * Allows to change application settings.
 */

public class PreferencesActivity extends AppCompatActivity {

    private static final String TAG = PreferencesActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PreferencesFragment(), "preftag")
                .commit();
        setupActionBar();
    }

    @Override
    public void onBackPressed() {
        NavUtils.navigateUpFromSameTask(this);
        //finish();
    }

    private void setupActionBar() {
        ActionBar actionBar = getDelegate().getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            int color = ThemeUtils.primaryColor(this);
            actionBar.setBackgroundDrawable(new ColorDrawable(color));
        }

        Window window = getWindow();
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int colorDark = ThemeUtils.primaryDarkColor(this);
                window.setStatusBarColor(colorDark);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PreferencesFragment.PERMISSION_SMS_SEND_AND_RECEIVE:
                if (grantResults.length > 0) {
                    Log.d(TAG, "[permission SEND'N'RECEIVE SMS result] "+grantResults[0]);
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    }
                    else {
                        disableSms();
                    }
                }
                break;
        }
    }

    private void disableSms() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(getString(R.string.pref_key_sms), false);
        editor.apply();

        PreferencesFragment frag = ((PreferencesFragment) getSupportFragmentManager().findFragmentByTag("preftag"));
        if (frag != null) {
            frag.disableSms();
        }
    }
}
