package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
//import android.preference.EditTextPreference;
import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;
//import android.preference.ListPreference;
//import android.preference.Preference;
import androidx.preference.Preference;
//import android.preference.PreferenceFragment;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.Toast;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.SessionServerSyncHelper;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;

import static android.webkit.URLUtil.isValidUrl;

public class EditMapsLogjobFragment extends EditLogjobFragment {


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        System.out.println("MAPS logjob on create : "+logjob);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_custom_edit, container, false);
        super.onCreateView(view);

        editUrlLayout.setVisibility(View.GONE);
        editPostLayout.setVisibility(View.GONE);
        editJsonLayout.setVisibility(View.GONE);
        editPasswordLayout.setVisibility(View.GONE);
        editLoginLayout.setVisibility(View.GONE);

        showHideValidationButtons();

        return view;
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
                logjob = db.updateLogjobAndSync(
                        logjob, newTitle, "", "", "",
                        false, newMinTime, newMinDistance, newMinAccuracy, newKeepGpsOn,
                        newUseSignificantMotion, newUseSignificantMotionMixed, newTimeout,
                        null, null, false, callback
                );
                notifyLoggerService(logjob.getId());
            }
        }
        // this is a new logjob
        else {
            DBLogjob newLogjob = new DBLogjob(
                    0, newTitle, "", "", "",
                    newMinTime, newMinDistance, newMinAccuracy, newKeepGpsOn,
                    newUseSignificantMotion, newUseSignificantMotionMixed,
                    newTimeout, false, false, 0, null, null,
                    false
            );
            long newId = db.addLogjob(newLogjob);
            notifyLoggerService(newId);
        }
        saveLastValues(
                newMinTime, newMinDistance, newMinAccuracy,
                newKeepGpsOn, newUseSignificantMotion, newUseSignificantMotionMixed,
                newTimeout
        );
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected boolean isFormValid() {
        boolean valid = true;
        if (getTitle() == null || getTitle().equals("")) {
            editTitleHint.setBackgroundColor(0x55FF0000);
            valid = false;
        }
        else {
            editTitleHint.setBackgroundColor(getResources().getColor(R.color.bg_normal));
        }
        if (getMinaccuracy() < 1) {
            editMinaccuracyHint.setBackgroundColor(0x55FF0000);
            valid = false;
        }
        else {
            editMinaccuracyHint.setBackgroundColor(getResources().getColor(R.color.bg_normal));
        }
        if (getMindistance() < 0) {
            editMindistanceHint.setBackgroundColor(0x55FF0000);
            valid = false;
        }
        else {
            editMindistanceHint.setBackgroundColor(getResources().getColor(R.color.bg_normal));
        }
        if (getUseSignificantMotion()) {
            if (getUseSignificantMotionInterval() && getMintime() < 30) {
                minTimeTextInputLayout.setBackgroundColor(0x55FF0000);
                valid = false;
            } else {
                minTimeTextInputLayout.setBackgroundColor(getResources().getColor(R.color.bg_normal));
            }
        }
        else {
            if (getMintime() < 1) {
                minTimeTextInputLayout.setBackgroundColor(0x55FF0000);
                valid = false;
            } else {
                minTimeTextInputLayout.setBackgroundColor(getResources().getColor(R.color.bg_normal));
            }
        }
        if (getLocationRequestTimeout() < 0 || getLocationRequestTimeout() >= getMintime()) {
            editLocationTimeoutHint.setBackgroundColor(0x55FF0000);
            valid = false;
        }
        else {
            editLocationTimeoutHint.setBackgroundColor(getResources().getColor(R.color.bg_normal));
        }

        return valid;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        System.out.println("MAPS logjob edit ACT CREATEDDDDDDD");
    }

}
