package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
//import android.preference.EditTextPreference;
import androidx.preference.CheckBoxPreference;
//import android.preference.ListPreference;
//import android.preference.Preference;
import androidx.preference.Preference;
//import android.preference.PreferenceFragment;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.SessionServerSyncHelper;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;

import static android.webkit.URLUtil.isValidUrl;

public class EditMapsLogjobFragment extends EditLogjobFragment {

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootkey) {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.activity_custom_edit);

        endOnCreate();

        System.out.println("MAPS logjob on create : "+logjob);

        Preference urlPref = findPreference("URL");
        urlPref.setVisible(false);
        Preference postPref = findPreference("post");
        postPref.setVisible(false);
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
        Log.d(getClass().getSimpleName(), "MAPS logjob saveData()");
        String newTitle = getTitle();
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
                System.out.println("====== update logjob");
                logjob = db.updateLogjobAndSync(logjob, newTitle, "", "", "",
                        false, newMinTime, newMinDistance, newMinAccuracy, newKeepGpsOn,
                        newUseSignificantMotion, newUseSignificantMotionMixed, newTimeout, callback);
                notifyLoggerService(logjob.getId());
            }
        }
        // this is a new logjob
        else {
            DBLogjob newLogjob = new DBLogjob(0, newTitle, "", "", "",
                    newMinTime, newMinDistance, newMinAccuracy, newKeepGpsOn,
                    newUseSignificantMotion, newUseSignificantMotionMixed,
                    newTimeout, false, false, 0);
            long newId = db.addLogjob(newLogjob);
            notifyLoggerService(newId);
        }
    }

    public static EditMapsLogjobFragment newInstance(long logjobId) {
        EditMapsLogjobFragment f = new EditMapsLogjobFragment();
        Bundle b = new Bundle();
        b.putLong(PARAM_LOGJOB_ID, logjobId);
        f.setArguments(b);
        return f;
    }

    public static EditMapsLogjobFragment newInstanceWithNewLogjob(DBLogjob newLogjob) {
        EditMapsLogjobFragment f = new EditMapsLogjobFragment();
        Bundle b = new Bundle();
        b.putSerializable(PARAM_NEWLOGJOB, newLogjob);
        f.setArguments(b);
        return f;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_save:
                if (getTitle() == null || getTitle().equals("")) {
                    showToast(getString(R.string.error_invalid_title), Toast.LENGTH_LONG);
                }
                else if (getMindistance() < 0) {
                    showToast(getString(R.string.error_invalid_mindistance), Toast.LENGTH_LONG);
                }
                else if (getMinaccuracy() < 1) {
                    showToast(getString(R.string.error_invalid_minaccuracy), Toast.LENGTH_LONG);
                }
                else {
                    saveLogjob(null);
                    listener.close();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        System.out.println("MAPS logjob edit ACT CREATEDDDDDDD");
    }

}
