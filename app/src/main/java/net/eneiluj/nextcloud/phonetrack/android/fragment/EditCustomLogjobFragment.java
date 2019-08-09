package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;

//import android.preference.EditTextPreference;
//import android.preference.ListPreference;
//import android.preference.Preference;
//import android.preference.PreferenceFragment;

public class EditCustomLogjobFragment extends EditLogjobFragment {
    private static final String TAG = EditCustomLogjobFragment.class.getSimpleName();

    private CheckBoxPreference editPost;

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootkey) {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.activity_custom_edit);

        endOnCreate();

        Log.i(TAG, "CUSTOM on create : "+logjob);

        Preference postPref = findPreference("post");
        postPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                CheckBoxPreference pref = (CheckBoxPreference) findPreference("post");
                pref.setChecked((Boolean) newValue);
                //saveLogjob(null);
                return true;
            }

        });
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem itemSelectSession = menu.findItem(R.id.menu_selectSession);
        itemSelectSession.setVisible(false);
        MenuItem itemFromLogUrl = menu.findItem(R.id.menu_fromLogUrl);
        itemFromLogUrl.setVisible(false);
    }


    /**
     * Save the current state in the database and schedule synchronization if needed.
     *
     * @param callback Observer which is called after save/synchronization
     */
    @Override
    protected void saveLogjob(@Nullable ICallback callback) {
        Log.d(getClass().getSimpleName(), "CUSTOM saveData()");
        String newTitle = getTitle();
        String newURL = getURL();
        boolean newPost = getPost();
        boolean newUseSignificantMotion = getUseSignificantMotion();
        boolean newUseSignificantMotionMixed = getUseSignificantMotionMixed();
        int newMinTime = 0;
        // Store the interval as zero if we're not using it (ie. when sampling with significant motion and
        // not using an interval)
        if (!newUseSignificantMotion || getUseSignificantMotionInterval()) {
            newMinTime = getMintime();
        }
        int newMinDistance = getMindistance();
        int newMinAccuracy = getMinaccuracy();
        boolean newKeepGpsOn = newUseSignificantMotion ? false : getKeepGpsOn();
        int newTimeout = getLocationRequestTimeout();

        // if this is an existing logjob
        if (logjob.getId() != 0) {
            if (logjob.getTitle().equals(newTitle) &&
                    logjob.getUrl().equals(newURL) &&
                    logjob.getPost() == newPost &&
                    logjob.getMinTime() == newMinTime &&
                    logjob.keepGpsOnBetweenFixes() == newKeepGpsOn &&
                    logjob.getMinDistance() == newMinDistance &&
                    logjob.getMinAccuracy() == newMinAccuracy &&
                    logjob.useSignificantMotion() == newUseSignificantMotion &&
                    logjob.useSignificantMotionMixed() == newUseSignificantMotionMixed &&
                    logjob.getLocationRequestTimeout() == newTimeout
                    ) {
                Log.v(getClass().getSimpleName(), "... not saving logjob, since nothing has changed");
            } else {
                Log.i(TAG, "====== update logjob");
                logjob = db.updateLogjobAndSync(logjob, newTitle, "", newURL, "",
                        newPost, newMinTime, newMinDistance, newMinAccuracy, newKeepGpsOn,
                        newUseSignificantMotion, newUseSignificantMotionMixed, newTimeout, callback);
                notifyLoggerService(logjob.getId());
                //Log.i(TAG, "AFFFFFFTTTTTTEEERRRRR : "+logjob);
                //listener.onLogjobUpdated(logjob);
            }
        }
        // this is a new logjob
        else {
            DBLogjob newLogjob = new DBLogjob(0, newTitle, newURL, "", "",
                    newMinTime, newMinDistance, newMinAccuracy, newKeepGpsOn,
                    newUseSignificantMotion, newUseSignificantMotionMixed, newTimeout, newPost, false, 0);
            long newId = db.addLogjob(newLogjob);
            notifyLoggerService(newId);
        }
    }

    public static EditCustomLogjobFragment newInstance(long logjobId) {
        EditCustomLogjobFragment f = new EditCustomLogjobFragment();
        Bundle b = new Bundle();
        b.putLong(PARAM_LOGJOB_ID, logjobId);
        f.setArguments(b);
        return f;
    }

    public static EditCustomLogjobFragment newInstanceWithNewLogjob(DBLogjob newLogjob) {
        EditCustomLogjobFragment f = new EditCustomLogjobFragment();
        Bundle b = new Bundle();
        b.putSerializable(PARAM_NEWLOGJOB, newLogjob);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.i(TAG,"CUSTOM ACT CREATEDDDDDDD");

        editPost = (CheckBoxPreference) this.findPreference("post");
        editPost.setChecked(logjob.getPost());
    }

    private boolean getPost() {
        return editPost.isChecked();
    }

}
