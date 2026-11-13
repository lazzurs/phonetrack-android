package net.eneiluj.nextcloud.phonetrack.android.activity;

//import android.support.v4.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import android.content.Intent;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.Toast;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.fragment.EditLogjobFragment;
import net.eneiluj.nextcloud.phonetrack.android.fragment.EditPhoneTrackLogjobFragment;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.service.LocationSamplePolicy;

public class EditPhoneTrackLogjobActivity extends EditLogjobActivity {


    /**
     * Starts a {@link EditLogjobFragment} for an existing logjob.
     *
     * @param logjobId ID of the existing logjob.
     */
    protected void launchExistingLogjob(long logjobId) {
        // save state of the fragment in order to resume with the same logjob and originalLogjob
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = EditPhoneTrackLogjobFragment.newInstance(logjobId);
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    /**
     * Starts the {@link EditLogjobFragment} with a new logjob.
     *
     */
    protected void launchNewLogjob() {
        Intent intent = getIntent();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int minTime = prefs.getInt(EditLogjobFragment.SETTINGS_LAST_MINTIME, 60);
        int minDistance = prefs.getInt(EditLogjobFragment.SETTINGS_LAST_MINDISTANCE, 5);
        int minAccuracy = prefs.getInt(EditLogjobFragment.SETTINGS_LAST_MINACCURACY, 50);
        boolean keepGpsOn = prefs.getBoolean(EditLogjobFragment.SETTINGS_LAST_KEEPGPSON, false);
        boolean sigMotion = prefs.getBoolean(EditLogjobFragment.SETTINGS_LAST_SIGMOTION, false);
        boolean sigMotionMixed = prefs.getBoolean(EditLogjobFragment.SETTINGS_LAST_SIGMOTION_MIXED, false);
        int timeout = prefs.getInt(EditLogjobFragment.SETTINGS_LAST_TIMEOUT,
                LocationSamplePolicy.defaultTimeoutSeconds(minTime));

        DBLogjob newLogjob = new DBLogjob(
                // address and token start empty (the form shows an example address): a
                // placeholder saved as a real value sends positions nowhere
                0, "", "", "",
                android.os.Build.MODEL.replaceAll(" ", "").replaceAll("/", ""),
                minTime, minDistance, minAccuracy,
                keepGpsOn, sigMotion, sigMotionMixed,
                timeout, false,false, 0, null, null, false
        );

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String url = getSharedText();
            if (url == null || !newLogjob.setAttrFromLoggingUrl(url)) {
                showToast(getString(R.string.error_invalid_pt_url), Toast.LENGTH_LONG);
            } else {
                newLogjob.setTitle(getString(R.string.logjob_title_from_logging_link));
                showPrefilledFromOtherApp();
            }
        }

        fragment = EditPhoneTrackLogjobFragment.newInstanceWithNewLogjob(newLogjob);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }
}
