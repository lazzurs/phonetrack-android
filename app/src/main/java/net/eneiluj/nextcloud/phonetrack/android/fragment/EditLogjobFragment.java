package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
//import android.preference.EditTextPreference;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
//import com.takisoft.fix.support.v7.preference.EditTextPreference;
//import android.preference.ListPreference;
//import android.preference.Preference;
import androidx.preference.Preference;
//import android.preference.PreferenceFragment;
//import android.support.v7.preference.PreferenceFragmentCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;
import androidx.annotation.Nullable;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.ShareActionProvider;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

//import butterknife.ButterKnife;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrack;
import net.eneiluj.nextcloud.phonetrack.util.ThemeUtils;


public abstract class EditLogjobFragment extends Fragment {

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

    public static final String SETTINGS_LAST_MINTIME = "settingsLastMintime";
    public static final String SETTINGS_LAST_MINDISTANCE = "settingsLastMindist";
    public static final String SETTINGS_LAST_MINACCURACY = "settingsLastMinacc";
    public static final String SETTINGS_LAST_KEEPGPSON = "settingsLastKeepGpsOn";
    public static final String SETTINGS_LAST_SIGMOTION = "settingsLastSigMotion";
    public static final String SETTINGS_LAST_SIGMOTION_MIXED = "settingsLastSigMotionMixed";
    public static final String SETTINGS_LAST_TIMEOUT = "settingsLastTimeout";

    protected DBLogjob logjob;

    protected PhoneTrackSQLiteOpenHelper db;
    protected LogjobFragmentListener listener;

    private static final String LOG_TAG_AUTOSAVE = "AutoSave";

    private Handler handler;

    protected EditText editTitle;
    protected EditText editURL;
    protected EditText editMintime;
    protected EditText editMindistance;
    protected EditText editMinaccuracy;
    protected CheckBox editKeepGpsOn;
    protected CheckBox editUseSignificantMotion;
    protected CheckBox editUseSignificantMotionInterval;
    protected CheckBox editUseSignificantMotionMixed;
    protected EditText editLocationRequestTimeout;

    protected LinearLayout editUseSignificantMotionLayout;
    protected LinearLayout editUseSignificantMotionIntervalLayout;
    protected LinearLayout editUseSignificantMotionMixedLayout;
    protected LinearLayout editLocationRequestTimeoutLayout;
    protected LinearLayout editMintimeLayout;
    protected LinearLayout editMinaccuracyLayout;
    protected LinearLayout editKeepGpsOnLayout;
    protected LinearLayout editUrlLayout;
    protected LinearLayout editLoginLayout;
    protected LinearLayout editPasswordLayout;
    protected LinearLayout editPostLayout;
    protected LinearLayout editJsonLayout;
    protected LinearLayout editTitleLayout;
    protected TextInputLayout editTitleHint;
    protected TextInputLayout editUrlHint;
    protected TextInputLayout editMindistanceHint;
    protected TextInputLayout editMinaccuracyHint;
    protected TextInputLayout editLocationTimeoutHint;
    protected TextView editMinTimeSummary;

    protected FloatingActionButton fabOk;

    protected TextInputLayout minTimeTextInputLayout;

    private DialogInterface.OnClickListener deleteDialogClickListener;
    private AlertDialog.Builder confirmDeleteAlertBuilder;

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


    public void onCreateView(View view) {
        fabOk = view.findViewById(R.id.fab_edit_ok);

        boolean darkTheme = PhoneTrack.getAppTheme(getContext());
        // if dark theme and main color is black, make fab button lighter/gray
        if (darkTheme && ThemeUtils.primaryColor(getContext()) == Color.BLACK) {
            fabOk.setBackgroundTintList(ColorStateList.valueOf(Color.DKGRAY));
        } else {
            fabOk.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.primaryColor(getContext())));
        }
        fabOk.setRippleColor(ThemeUtils.primaryDarkColor(getContext()));

        editTitle = view.findViewById(R.id.editTitle);
        editTitle.setText(logjob.getTitle());
        editURL = view.findViewById(R.id.editUrl);
        editURL.setText(logjob.getUrl());


        editMintime = view.findViewById(R.id.editMinTime);
        editMintime.setText(String.valueOf(logjob.getMinTime()));


        editMindistance = view.findViewById(R.id.editMinDistance);
        editMindistance.setText(String.valueOf(logjob.getMinDistance()));

        editMinaccuracy = view.findViewById(R.id.editMinAccuracy);
        editMinaccuracy.setText(String.valueOf(logjob.getMinAccuracy()));

        editKeepGpsOn = view.findViewById(R.id.keepgpson);
        editKeepGpsOn.setChecked(logjob.keepGpsOnBetweenFixes());

        editUseSignificantMotion = view.findViewById(R.id.editSignMotionMode);
        editUseSignificantMotionInterval = view.findViewById(R.id.significantmotioninterval);
        editUseSignificantMotionMixed = view.findViewById(R.id.usesignificantmotionmixed);
        editLocationRequestTimeout = view.findViewById(R.id.editSigMotionTimeout);

        editUseSignificantMotionLayout = view.findViewById(R.id.editSignMotionModeLayout);
        editUseSignificantMotionIntervalLayout = view.findViewById(R.id.editApplyMinTimeLayout);
        editUseSignificantMotionMixedLayout = view.findViewById(R.id.editMixedModeLayout);
        editLocationRequestTimeoutLayout = view.findViewById(R.id.editSigMotionTimeoutLayout);
        editMintimeLayout = view.findViewById(R.id.editMinTimeLayout);
        editMinaccuracyLayout = view.findViewById(R.id.editMinAccuracyLayout);
        editKeepGpsOnLayout = view.findViewById(R.id.keepGpsOnLayout);
        editPostLayout = view.findViewById(R.id.usePostLayout);
        editJsonLayout = view.findViewById(R.id.jsonLayout);
        editLoginLayout = view.findViewById(R.id.editLoginLayout);
        editPasswordLayout = view.findViewById(R.id.editPasswordLayout);
        editUrlLayout = view.findViewById(R.id.editUrlLayout);
        editTitleLayout = view.findViewById(R.id.editTitleLayout);
        editTitleHint = view.findViewById(R.id.input_layout_title);
        editUrlHint = view.findViewById(R.id.input_layout_url);
        editMindistanceHint = view.findViewById(R.id.input_layout_min_distance);
        editMinaccuracyHint = view.findViewById(R.id.input_layout_min_accuracy);
        editMinaccuracyHint = view.findViewById(R.id.input_layout_min_accuracy);
        editLocationTimeoutHint = view.findViewById(R.id.input_layout_sign_motion_timeout);

        editMinTimeSummary = view.findViewById(R.id.editMinTimeSummary);

        minTimeTextInputLayout = view.findViewById(R.id.input_layout_min_time);

        // Setup significant motion option, only show if device supports it
        if (deviceSupportsSignificantMotion()) {
            editUseSignificantMotion.setChecked(logjob.useSignificantMotion());
            editUseSignificantMotionInterval.setChecked(logjob.getMinTime() > 0);

            editUseSignificantMotionMixed.setChecked(logjob.useSignificantMotionMixed());

            String timeoutVal = String.valueOf(logjob.getLocationRequestTimeout());
            editLocationRequestTimeout.setText(timeoutVal);

            updateVisiblePreferencesForSignificantMotion(logjob.useSignificantMotion());
        } else {
            Log.i(TAG, "Device doesn't support significant motion");
            editUseSignificantMotionLayout.setVisibility(View.GONE);
            editUseSignificantMotionIntervalLayout.setVisibility(View.GONE);
            editUseSignificantMotionMixedLayout.setVisibility(View.GONE);
            editLocationRequestTimeoutLayout.setVisibility(View.GONE);
        }

        // EVENTS

        fabOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveLogjob(null);
                listener.close();
            }
        });

        editTitle.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "title change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        editURL.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "url change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        editMintime.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "min time change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        editMindistance.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "min distance change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        editMinaccuracy.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "min accuracy change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        editLocationRequestTimeout.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "sign motion timeout change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        editUseSignificantMotion.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Log.d(TAG, "sign motion mode change");
                        showHideValidationButtons();
                        updateVisiblePreferencesForSignificantMotion(isChecked);
                    }
                }
        );

        editUseSignificantMotionInterval.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Log.d(TAG, "sign motion mode change");
                        showHideValidationButtons();
                        updateVisiblePreferencesForSignificantMotion(getUseSignificantMotion(), isChecked, getUseSignificantMotionMixed());
                    }
                }
        );

        editUseSignificantMotionMixed.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Log.d(TAG, "sign motion mode change");
                        showHideValidationButtons();
                        updateVisiblePreferencesForSignificantMotion(getUseSignificantMotion(), getUseSignificantMotionInterval(), isChecked);
                    }
                }
        );

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

    protected void saveLastValues(int minTime, int minDistance, int minAccuracy,
                                  boolean keepGpsOn, boolean sigMotion, boolean sigMotionMixed,
                                  int timeout) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(SETTINGS_LAST_MINTIME, minTime);
        editor.putInt(SETTINGS_LAST_MINDISTANCE, minDistance);
        editor.putInt(SETTINGS_LAST_MINACCURACY, minAccuracy);
        editor.putBoolean(SETTINGS_LAST_KEEPGPSON, keepGpsOn);
        editor.putBoolean(SETTINGS_LAST_SIGMOTION, sigMotion);
        editor.putBoolean(SETTINGS_LAST_SIGMOTION_MIXED, sigMotionMixed);
        editor.putInt(SETTINGS_LAST_TIMEOUT, timeout);
        editor.apply();
    }

    protected void showHideValidationButtons() {
        if (isFormValid()) {
            fabOk.show();
        }
        else {
            fabOk.hide();
        }
    }

    protected abstract boolean isFormValid();

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.i(TAG,"ACT CREATEDDDDDDD");

        // hide the keyboard when this window gets the focus
        //getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);



    }

    protected String getTitle() {
        return editTitle.getText().toString();
    }
    protected String getURL() {
        return editURL.getText().toString();
    }
    protected int getMintime() {
        if (editMintime.getText() == null || editMintime.getText().toString().equals("")) {
            return -1;
        }
        return Integer.valueOf(editMintime.getText().toString());
    }
    protected int getMindistance() {
        if (editMindistance.getText() == null || editMindistance.getText().toString().equals("")) {
            return -1;
        }
        return Integer.valueOf(editMindistance.getText().toString());
    }
    protected int getMinaccuracy() {
        if (editMinaccuracy.getText() == null || editMinaccuracy.getText().toString().equals("")) {
            return -1;
        }
        return Integer.valueOf(editMinaccuracy.getText().toString());
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
        try {
            return Integer.parseInt(editLocationRequestTimeout.getText().toString());
        }
        catch (Exception e) {
            return 60;
        }
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getActivity();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    private void updateVisiblePreferencesForSignificantMotion(boolean sigMotionEnabled, Boolean useInterval, Boolean mixedMode) {
        editMinaccuracyLayout.setVisibility(!sigMotionEnabled ? View.VISIBLE : View.GONE);
        editMintimeLayout.setVisibility((!sigMotionEnabled || useInterval) ? View.VISIBLE : View.GONE);
        editUseSignificantMotionMixedLayout.setVisibility((sigMotionEnabled && useInterval) ? View.VISIBLE : View.GONE);
        editKeepGpsOnLayout.setVisibility(!sigMotionEnabled ? View.VISIBLE : View.GONE);
        editLocationRequestTimeoutLayout.setVisibility(sigMotionEnabled ? View.VISIBLE : View.GONE);
        editUseSignificantMotionIntervalLayout.setVisibility(sigMotionEnabled ? View.VISIBLE : View.GONE);

        // If changing significant motion setting update default value for minimum time
        if (sigMotionEnabled != getUseSignificantMotion()) {
            String newValue = Integer.toString(sigMotionEnabled ? MINIMUM_TIME_DEFAULT_SIG_MOTION : MINIMUM_TIME_DEFAULT_STANDARD);
            editMintime.setText(newValue);
        }

        String minTimeValidInterval = " [1, ∞]";
        if (sigMotionEnabled) {
            minTimeValidInterval = " [30, ∞]";
        }

        if (sigMotionEnabled && useInterval && mixedMode) {
            minTimeTextInputLayout.setHint(getString(R.string.setting_min_time_mixed) + minTimeValidInterval);
            editUseSignificantMotionIntervalLayout.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                editMintime.setTooltipText(getString(R.string.setting_min_time_mixed_long));
                editMinTimeSummary.setText(getString(R.string.setting_min_time_mixed_long));
            }
        }
        else {
            minTimeTextInputLayout.setHint(getString(R.string.setting_min_time) + minTimeValidInterval);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                editMintime.setTooltipText(getString(R.string.setting_min_time_long));
                editMinTimeSummary.setText(getString(R.string.setting_min_time_long));
            }
        }
    }

    private void updateVisiblePreferencesForSignificantMotion(boolean sigMotionEnabled) {
        updateVisiblePreferencesForSignificantMotion(sigMotionEnabled, getUseSignificantMotionInterval(), getUseSignificantMotionMixed());
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
