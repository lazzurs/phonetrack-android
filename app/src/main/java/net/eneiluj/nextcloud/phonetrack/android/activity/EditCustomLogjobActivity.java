package net.eneiluj.nextcloud.phonetrack.android.activity;

//import android.support.v4.app.Fragment;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import android.content.Intent;

import android.content.SharedPreferences;
import android.widget.Toast;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.fragment.EditCustomLogjobFragment;
import net.eneiluj.nextcloud.phonetrack.android.fragment.EditLogjobFragment;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;

public class EditCustomLogjobActivity extends EditLogjobActivity {

    /**
     * Starts a {@link EditLogjobFragment} for an existing logjob.
     *
     * @param logjobId ID of the existing logjob.
     */
    @Override
    protected void launchExistingLogjob(long logjobId) {
        // save state of the fragment in order to resume with the same logjob and originalLogjob
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = EditCustomLogjobFragment.newInstance(logjobId);
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    /**
     * Starts the {@link EditLogjobFragment} with a new logjob.
     *
     */
    @Override
    protected void launchNewLogjob() {
        Intent intent = getIntent();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int minTime = prefs.getInt(EditLogjobFragment.SETTINGS_LAST_MINTIME, 60);
        int minDistance = prefs.getInt(EditLogjobFragment.SETTINGS_LAST_MINDISTANCE, 5);
        int minAccuracy = prefs.getInt(EditLogjobFragment.SETTINGS_LAST_MINACCURACY, 50);
        boolean keepGpsOn = prefs.getBoolean(EditLogjobFragment.SETTINGS_LAST_KEEPGPSON, false);
        boolean sigMotion = prefs.getBoolean(EditLogjobFragment.SETTINGS_LAST_SIGMOTION, false);
        boolean sigMotionMixed = prefs.getBoolean(EditLogjobFragment.SETTINGS_LAST_SIGMOTION_MIXED, false);
        int timeout = prefs.getInt(EditLogjobFragment.SETTINGS_LAST_TIMEOUT, 59);

        String exampleHost = getString(R.string.example_hostname);

        DBLogjob newLogjob = new DBLogjob(
                0, "",  "https://"+exampleHost+"/page?lat=%LAT&lon=%LON&timestamp=%TIMESTAMP",
                "", "", minTime, minDistance, minAccuracy,
                keepGpsOn, sigMotion, sigMotionMixed,
                timeout, false, false, 0, null, null, false
        );

        String url;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            url = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!url.startsWith("http")) {
                showToast(getString(R.string.error_invalid_url), Toast.LENGTH_LONG);
            }
            else {
                newLogjob.setUrl(url);
            }
        }

        fragment = EditCustomLogjobFragment.newInstanceWithNewLogjob(newLogjob);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }
}
