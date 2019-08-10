package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
//import android.preference.EditTextPreference;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
//import com.takisoft.fix.support.v7.preference.EditTextPreference;
//import android.preference.ListPreference;
//import android.preference.Preference;
import androidx.preference.Preference;
//import android.preference.PreferenceFragment;
//import android.support.v7.preference.PreferenceFragmentCompat;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;
import androidx.annotation.Nullable;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

//import butterknife.ButterKnife;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;

import static android.webkit.URLUtil.isValidUrl;

//public abstract class EditLogjobFragment extends Fragment implements CategoryDialogFragment.CategoryDialogListener {
//public class EditLogjobFragment extends PreferencesFragment {
public abstract class EditLogjobFragment extends PreferenceFragmentCompat {

    public interface LogjobFragmentListener {
        void close();

        void onLogjobUpdated(DBLogjob logjob);
    }

    private static final String TAG = EditLogjobFragment.class.getSimpleName();

    public static final String PARAM_LOGJOB_ID = "logjobId";
    public static final String PARAM_NEWLOGJOB = "newLogjob";
    private static final String SAVEDKEY_LOGJOB = "logjob";
    private static final String SAVEDKEY_ORIGINAL_LOGJOB = "original_logjob";

    public static final int MINIMUM_TIME_DEFAULT_STANDARD = 60;
    public static final int MINIMUM_TIME_DEFAULT_SIG_MOTION = 300;

    protected DBLogjob logjob;
    //@Nullable
    //protected DBLogjob originalLogjob;
    protected PhoneTrackSQLiteOpenHelper db;
    protected LogjobFragmentListener listener;

    private static final String LOG_TAG_AUTOSAVE = "AutoSave";

    private static final long DELAY = 2000; // Wait for this time after typing before saving
    private static final long DELAY_AFTER_SYNC = 5000; // Wait for this time after saving before checking for next save

    private Handler handler;
    private boolean saveActive, unsavedEdit;

    protected EditTextPreference editTitle;
    protected EditTextPreference editURL;
    protected EditTextPreference editToken;
    protected EditTextPreference editDevicename;
    protected EditTextPreference editMintime;
    protected EditTextPreference editMindistance;
    protected EditTextPreference editMinaccuracy;
    protected CheckBoxPreference editKeepGpsOn;
    protected SwitchPreferenceCompat editUseSignificantMotion;
    protected SwitchPreferenceCompat editUseSignificantMotionInterval;
    protected SwitchPreferenceCompat editUseSignificantMotionMixed;
    protected androidx.preference.EditTextPreference editLocationRequestTimeout;

    private DialogInterface.OnClickListener deleteDialogClickListener;
    private AlertDialog.Builder confirmDeleteAlertBuilder;

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootkey) {
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = getListView();
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(),
                DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            long id = getArguments().getLong(PARAM_LOGJOB_ID);
            if (id > 0) {
                logjob = db.getLogjob(id);
            } else {
                DBLogjob newLogjob = (DBLogjob) getArguments().getSerializable(PARAM_NEWLOGJOB);
                if (newLogjob == null) {
                    throw new IllegalArgumentException(PARAM_LOGJOB_ID + " is not given and argument " + PARAM_NEWLOGJOB + " is missing.");
                }
                //logjob = db.getLogjob(db.addLogjob(newLogjob));
                logjob = newLogjob;
            }
        } else {
            logjob = (DBLogjob) savedInstanceState.getSerializable(SAVEDKEY_LOGJOB);
            //originalLogjob = (DBLogjob) savedInstanceState.getSerializable(SAVEDKEY_ORIGINAL_LOGJOB);
        }
        setHasOptionsMenu(true);
        Log.i(TAG,"SUPERCLASS on create : " + logjob);

        ///////////////
        //addPreferencesFromResource(R.xml.activity_edit);

    }

    public void endOnCreate() {

        Preference.OnPreferenceClickListener clickListener =  new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                EditText input = ((com.takisoft.fix.support.v7.preference.EditTextPreference) preference).getEditText();
                input.setSelectAllOnFocus(true);
                input.requestFocus();
                input.setSelected(true);
                // show keyboard
                InputMethodManager inputMethodManager = (InputMethodManager) preference.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                return true;
            }
        };
        Preference titlePref = findPreference("title");
        titlePref.setOnPreferenceClickListener(clickListener);
        titlePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("title");
                String newValueString = (String) newValue;
                if (newValueString == null || newValueString.equals("")) {
                    showToast(getString(R.string.error_invalid_title), Toast.LENGTH_LONG);
                    return false;
                }
                else {
                    // trick to make change effective before saving
                    // otherwise edittext is not up to date when saving...
                    pref.setText((String) newValue);
                    pref.setSummary((CharSequence) newValue);
                    //saveLogjob(null);
                    return true;
                }
            }

        });
        Preference URLPref = findPreference("URL");
        URLPref.setOnPreferenceClickListener(clickListener);
        URLPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("URL");
                String newValueString = (String) newValue;
                if (newValueString == null
                        || newValueString.equals("")
                        || !isValidUrl(newValueString)) {
                    showToast(getString(R.string.error_invalid_url), Toast.LENGTH_LONG);
                    return false;
                }
                else {
                    pref.setSummary((CharSequence) newValue);
                    pref.setText((String) newValue);
                    //saveLogjob(null);
                    return true;
                }
            }

        });
        Preference minTimePref = findPreference("mintime");
        minTimePref.setOnPreferenceClickListener(clickListener);
        minTimePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("mintime");
                try {
                    int newMinTime = Integer.valueOf((String)newValue);
                    pref.setSummary(String.valueOf(newMinTime));
                    pref.setText(String.valueOf(newMinTime));
                    //saveLogjob(null);
                    return true;
                }
                catch (Exception e) {
                    showToast(getString(R.string.error_invalid_mintime), Toast.LENGTH_LONG);
                    return false;
                }
            }

        });
        Preference minDistancePref = findPreference("mindistance");
        minDistancePref.setOnPreferenceClickListener(clickListener);
        minDistancePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) preference;
                try {
                    int newMinDistance = Integer.valueOf((String)newValue);
                    pref.setSummary(String.valueOf(newMinDistance));
                    pref.setText(String.valueOf(newMinDistance));
                    //saveLogjob(null);
                    return true;
                }
                catch (Exception e) {
                    showToast(getString(R.string.error_invalid_mindistance), Toast.LENGTH_LONG);
                    return false;
                }
            }

        });
        Preference minAccuracyPref = findPreference("minaccuracy");
        minAccuracyPref.setOnPreferenceClickListener(clickListener);
        minAccuracyPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("minaccuracy");
                try {
                    int newMinAccuracy = Integer.valueOf((String)newValue);
                    pref.setSummary(String.valueOf(newMinAccuracy));
                    pref.setText(String.valueOf(newMinAccuracy));
                    //saveLogjob(null);
                    return true;
                }
                catch (Exception e) {
                    showToast(getString(R.string.error_invalid_minaccuracy), Toast.LENGTH_LONG);
                    return false;
                }
            }
        });

        Preference significantMotionPref = findPreference("usesignificantmotion");
        significantMotionPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                updateVisiblePreferencesForSignificantMotion((Boolean) newValue);
                return true;
            }
        });

        Preference significantMotionUseIntervalPref = findPreference("significantmotioninterval");
        significantMotionUseIntervalPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                updateVisiblePreferencesForSignificantMotion(getUseSignificantMotion(), (Boolean) newValue);
                return true;
            }
        });

        // delete confirmation
        deleteDialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        //Yes button clicked
                        db.deleteLogjob(logjob.getId());
                        listener.close();
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        break;
                }
            }
        };
        //confirmDeleteAlertBuilder = new AlertDialog.Builder(getActivity());
        confirmDeleteAlertBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this.getActivity(), R.style.AppThemeDialog));
        confirmDeleteAlertBuilder.setMessage(getString(R.string.confirm_delete_logjob_dialog_title))
                .setPositiveButton(getString(R.string.simple_yes), deleteDialogClickListener)
                .setNegativeButton(getString(R.string.simple_no), deleteDialogClickListener);

        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (LogjobFragmentListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement " + LogjobFragmentListener.class);
        }
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        listener.onLogjobUpdated(logjob);
    }

    @Override
    public void onPause() {
        super.onPause();
        //saveLogjob(null);
        //notifyLoggerService(logjob.getId());
    }

    protected void notifyLoggerService(long jobId) {
        Intent intent = new Intent(getActivity(), LoggerService.class);
        intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, true);
        intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, jobId);
        getActivity().startService(intent);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //saveLogjob(null);
        outState.putSerializable(SAVEDKEY_LOGJOB, logjob);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_logjob_fragment, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_share).setVisible(false);
    }

    /**
     * Main-Menu-Handler
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_save:
                if (getTitle() == null || getTitle().equals("")) {
                    showToast(getString(R.string.error_invalid_title), Toast.LENGTH_LONG);
                }
                else if (getURL() == null || getURL().equals("") || !isValidUrl(getURL())) {
                    showToast(getString(R.string.error_invalid_url), Toast.LENGTH_LONG);
                }
                else if (getMindistance() < 0) {
                    showToast(getString(R.string.error_invalid_mindistance), Toast.LENGTH_LONG);
                }
                else if (getMintime() < 1) {
                    showToast(getString(R.string.error_invalid_mintime), Toast.LENGTH_LONG);
                }
                else if (getMinaccuracy() < 1) {
                    showToast(getString(R.string.error_invalid_minaccuracy), Toast.LENGTH_LONG);
                }
                else if (getUseSignificantMotion() && getMintime() < 30) {
                    showToast(getString(R.string.error_invalid_mintime), Toast.LENGTH_LONG);
                }
                else if (!getUseSignificantMotion() && getMintime() < 1) {
                    showToast(getString(R.string.error_invalid_mintime), Toast.LENGTH_LONG);
                }
                else {
                    saveLogjob(null);
                    listener.close();
                }
                return true;
            case R.id.menu_delete:
                if (logjob.getId() != 0) {
                    confirmDeleteAlertBuilder.show();
                }
                else {
                    listener.close();
                }
                return true;
            case R.id.menu_share:
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getTitle());
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, getURL());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startActivity(Intent.createChooser(shareIntent, logjob.getTitle()));
                } else {
                    ShareActionProvider actionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
                    actionProvider.setShareIntent(shareIntent);
                }

                return false;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onCloseLogjob() {
        Log.d(getClass().getSimpleName(), "onCLOSE()");
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
    }

    /**
     * Save the current state in the database and schedule synchronization if needed.
     *
     * @param callback Observer which is called after save/synchronization
     */
    protected abstract void saveLogjob(@Nullable ICallback callback);

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.i(TAG,"ACT CREATEDDDDDDD");
        //ButterKnife.bind(this, getView());

        // hide the keyboard when this window gets the focus
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        editTitle = (EditTextPreference) this.findPreference("title");
        editTitle.setText(logjob.getTitle());
        if (logjob.getTitle().isEmpty()) {
            editTitle.setSummary(getString(R.string.mandatory));
        }
        else {
            editTitle.setSummary(logjob.getTitle());
        }
        editURL = (EditTextPreference) this.findPreference("URL");
        editURL.setText(logjob.getUrl());
        editURL.setSummary(logjob.getUrl());

        editMintime = (EditTextPreference) this.findPreference("mintime");
        editMintime.setText(String.valueOf(logjob.getMinTime()));
        editMintime.setSummary(String.valueOf(logjob.getMinTime()));

        editMindistance = (EditTextPreference) this.findPreference("mindistance");
        editMindistance.setText(String.valueOf(logjob.getMinDistance()));
        editMindistance.setSummary(String.valueOf(logjob.getMinDistance()));

        editMinaccuracy = (EditTextPreference) this.findPreference("minaccuracy");
        editMinaccuracy.setText(String.valueOf(logjob.getMinAccuracy()));
        editMinaccuracy.setSummary(String.valueOf(logjob.getMinAccuracy()));

        editKeepGpsOn = (CheckBoxPreference) this.findPreference("keepgpson");
        editKeepGpsOn.setChecked(logjob.keepGpsOnBetweenFixes());

        editUseSignificantMotion = (SwitchPreferenceCompat) this.findPreference("usesignificantmotion");
        editUseSignificantMotionInterval = (SwitchPreferenceCompat) this.findPreference("significantmotioninterval");
        editUseSignificantMotionMixed = (SwitchPreferenceCompat) this.findPreference("usesignificantmotionmixed");
        editLocationRequestTimeout = (androidx.preference.EditTextPreference) this.findPreference("significantmotiontimeout");

        // Setup significant motion option, only show if device supports it
        if (deviceSupportsSignificantMotion()) {
            editUseSignificantMotion.setChecked(logjob.useSignificantMotion());
            editUseSignificantMotionInterval.setChecked(logjob.getMinTime() > 0);

            editUseSignificantMotionMixed.setChecked(logjob.useSignificantMotionMixed());

            String timeoutVal = String.valueOf(logjob.getLocationRequestTimeout());
            editLocationRequestTimeout.setText(timeoutVal);
            editLocationRequestTimeout.setSummary(timeoutVal);

            updateVisiblePreferencesForSignificantMotion(logjob.useSignificantMotion());
        } else {
            Log.i(TAG, "Device doesn't support significant motion");
            editUseSignificantMotion.setVisible(false);
            editUseSignificantMotionInterval.setVisible(false);
            editUseSignificantMotionMixed.setVisible(false);
            editLocationRequestTimeout.setVisible(false);
        }
    }

    protected String getTitle() {
        return editTitle.getText();
    }
    protected String getURL() {
        return editURL.getText();
    }
    protected int getMintime() {
        if (editMintime.getText() == null || editMintime.getText().equals("")) {
            return -1;
        }
        return Integer.valueOf(editMintime.getText());
    }
    protected int getMindistance() {
        if (editMindistance.getText() == null || editMindistance.getText().equals("")) {
            return -1;
        }
        return Integer.valueOf(editMindistance.getText());
    }
    protected int getMinaccuracy() {
        if (editMinaccuracy.getText() == null || editMinaccuracy.getText().equals("")) {
            return -1;
        }
        return Integer.valueOf(editMinaccuracy.getText());
    }

    protected boolean getKeepGpsOn() {
        return editKeepGpsOn.isChecked();
    }

    protected boolean getUseSignificantMotion() {
        return editUseSignificantMotion.isChecked();
    }

    protected boolean getUseSignificantMotionInterval() {
        return editUseSignificantMotionInterval.isChecked();
    }

    protected boolean getUseSignificantMotionMixed() {
        return editUseSignificantMotionMixed.isChecked();
    }

    protected int getLocationRequestTimeout() {
        return Integer.parseInt(editLocationRequestTimeout.getText());
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getActivity();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    private void updateVisiblePreferencesForSignificantMotion(boolean sigMotionEnabled, Boolean useInterval) {
        editMinaccuracy.setVisible(!sigMotionEnabled);
        editMintime.setVisible(useInterval);
        editUseSignificantMotionMixed.setVisible(sigMotionEnabled && useInterval);
        editKeepGpsOn.setVisible(!sigMotionEnabled);
        editLocationRequestTimeout.setVisible(sigMotionEnabled);
        editUseSignificantMotionInterval.setVisible(sigMotionEnabled);

        // If changing significant motion setting update default value for minimum time
        if (sigMotionEnabled != getUseSignificantMotion()) {
            String newValue = Integer.toString(sigMotionEnabled ? MINIMUM_TIME_DEFAULT_SIG_MOTION : MINIMUM_TIME_DEFAULT_STANDARD);
            editMintime.setText(newValue);
            editMintime.setSummary(newValue);
        }
    }

    private void updateVisiblePreferencesForSignificantMotion(boolean sigMotionEnabled) {
        updateVisiblePreferencesForSignificantMotion(sigMotionEnabled, getUseSignificantMotionInterval());
    }

    /**
     * Verify if the device supports the significant motion sensor
     */
    private boolean deviceSupportsSignificantMotion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
            return false;

        SensorManager sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        return sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) != null;
    }
}
