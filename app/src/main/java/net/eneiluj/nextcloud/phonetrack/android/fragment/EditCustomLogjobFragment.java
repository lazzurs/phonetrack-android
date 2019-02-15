package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.os.Bundle;
//import android.preference.EditTextPreference;
import androidx.preference.CheckBoxPreference;
//import android.preference.ListPreference;
//import android.preference.Preference;
import androidx.preference.Preference;
//import android.preference.PreferenceFragment;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;

public class EditCustomLogjobFragment extends EditLogjobFragment {

    private CheckBoxPreference editPost;

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootkey) {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.activity_custom_edit);

        endOnCreate();

        System.out.println("CUSTOM on create : "+logjob);

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
        int newMinTime = getMintime();
        int newMinDistance = getMindistance();
        int newMinAccuracy = getMinaccuracy();
        boolean newKeepGpsOn = getKeepGpsOn();

        // if this is an existing logjob
        if (logjob.getId() != 0) {
            if (logjob.getTitle().equals(newTitle) &&
                    logjob.getUrl().equals(newURL) &&
                    logjob.getPost() == newPost &&
                    logjob.getMinTime() == newMinTime &&
                    logjob.keepGpsOnBetweenFixes() == newKeepGpsOn &&
                    logjob.getMinDistance() == newMinDistance &&
                    logjob.getMinAccuracy() == newMinAccuracy
                    ) {
                Log.v(getClass().getSimpleName(), "... not saving logjob, since nothing has changed");
            } else {
                System.out.println("====== update logjob");
                logjob = db.updateLogjobAndSync(logjob, newTitle, "", newURL, "",
                        newPost, newMinTime, newMinDistance, newMinAccuracy, newKeepGpsOn, callback);
                notifyLoggerService(logjob.getId());
                //System.out.println("AFFFFFFTTTTTTEEERRRRR : "+logjob);
                //listener.onLogjobUpdated(logjob);
            }
        }
        // this is a new logjob
        else {
            DBLogjob newLogjob = new DBLogjob(0, newTitle, newURL, "", "",
                    newMinTime, newMinDistance, newMinAccuracy, newKeepGpsOn,
                    newPost, false, 0);
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
        System.out.println("CUSTOM ACT CREATEDDDDDDD");

        editPost = (CheckBoxPreference) this.findPreference("post");
        editPost.setChecked(logjob.getPost());
    }

    private boolean getPost() {
        return editPost.isChecked();
    }

}
