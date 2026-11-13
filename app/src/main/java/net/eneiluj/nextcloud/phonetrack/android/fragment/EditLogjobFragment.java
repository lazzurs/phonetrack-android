package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.annotation.SuppressLint;
import java.util.Collections;
import net.eneiluj.nextcloud.phonetrack.util.LogjobDeletion;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
//import android.preference.EditTextPreference;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.PluralsRes;
import androidx.annotation.VisibleForTesting;
import androidx.core.os.BundleCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

//import butterknife.ButterKnife;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.EditLogjobActivity;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LocationSamplePolicy;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.util.KeyboardUtil;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrack;
import net.eneiluj.nextcloud.phonetrack.util.ThemeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static android.webkit.URLUtil.isValidUrl;


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
    private static final String SAVEDKEY_INITIAL_FORM = "initial_form";
    private static final String SAVEDKEY_TOUCHED_FIELDS = "touched_fields";
    private static final String SAVEDKEY_REVEAL_ERRORS = "reveal_errors";

    public static final int MINIMUM_TIME_DEFAULT_STANDARD = 60;
    public static final int MINIMUM_TIME_DEFAULT_SIG_MOTION = 300;

    /** Smallest minimum time in significant motion mode (with "apply minimum time"). */
    public static final int MIN_TIME_SIG_MOTION = 30;
    /** Largest value for the time fields: one week. */
    public static final int MAX_SECONDS = 7 * 24 * 3600;
    /** Largest value for the distance and accuracy fields: 1000 km. */
    public static final int MAX_METRES = 1_000_000;

    /**
     * The form as it was shown before the user changed anything (one entry per field), to tell
     * whether Back would throw changes away.
     */
    private ArrayList<String> initialForm;
    /** IDs of the fields the user has changed: their errors are shown as they type. */
    private final Set<Integer> touchedFields = new HashSet<>();
    /** Set once Save was tapped: from then on every invalid field shows its error. */
    private boolean revealAllErrors;
    /** Asks what to do with unsaved changes on Back or Up; enabled only while there are some. */
    private OnBackPressedCallback unsavedChangesCallback;
    protected TextView httpWarning;

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

    private ActionBar toolbar;
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
    protected TextView signMotionIntervalSummary;
    protected TextView signMotionSummary;
    protected TextView usesignificantmotionmixedSummary;
    protected TextView keepgpsonSummary;
    protected Button setPreset;

    protected FloatingActionButton fabOk;

    protected TextInputLayout minTimeTextInputLayout;


    // small local SQLite read on the main thread: the logjob to edit (one row) is needed to build the screen
    @SuppressLint("ThreadConstraint")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            long id = getArguments().getLong(PARAM_LOGJOB_ID);
            if (id > 0) {
                logjob = db.getLogjob(id);
            } else {
                DBLogjob newLogjob = BundleCompat.getSerializable(getArguments(), PARAM_NEWLOGJOB, DBLogjob.class);
                if (newLogjob == null) {
                    throw new IllegalArgumentException(PARAM_LOGJOB_ID + " is not given and argument " + PARAM_NEWLOGJOB + " is missing.");
                }
                //logjob = db.getLogjob(db.addLogjob(newLogjob));
                logjob = newLogjob;
            }
        } else {
            logjob = BundleCompat.getSerializable(savedInstanceState, SAVEDKEY_LOGJOB, DBLogjob.class);
            //originalLogjob = (DBLogjob) savedInstanceState.getSerializable(SAVEDKEY_ORIGINAL_LOGJOB);
            initialForm = savedInstanceState.getStringArrayList(SAVEDKEY_INITIAL_FORM);
            ArrayList<Integer> touched = savedInstanceState.getIntegerArrayList(SAVEDKEY_TOUCHED_FIELDS);
            if (touched != null) {
                touchedFields.addAll(touched);
            }
            revealAllErrors = savedInstanceState.getBoolean(SAVEDKEY_REVEAL_ERRORS);
        }
        setHasOptionsMenu(true);
        Log.i(TAG,"SUPERCLASS on create : " + logjob);

        ///////////////
        //addPreferencesFromResource(R.xml.activity_edit);

    }


    public void onCreateView(View view) {
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        ((EditLogjobActivity)getActivity()).setSupportActionBar(toolbar);

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
        signMotionSummary = view.findViewById(R.id.signMotionSummary);
        editUseSignificantMotionIntervalLayout = view.findViewById(R.id.editApplyMinTimeLayout);
        signMotionIntervalSummary = view.findViewById(R.id.signMotionIntervalSummary);
        editUseSignificantMotionMixedLayout = view.findViewById(R.id.editMixedModeLayout);
        usesignificantmotionmixedSummary = view.findViewById(R.id.usesignificantmotionmixedSummary);
        editLocationRequestTimeoutLayout = view.findViewById(R.id.editSigMotionTimeoutLayout);
        editMintimeLayout = view.findViewById(R.id.editMinTimeLayout);
        editMinaccuracyLayout = view.findViewById(R.id.editMinAccuracyLayout);
        editKeepGpsOnLayout = view.findViewById(R.id.keepGpsOnLayout);
        keepgpsonSummary = view.findViewById(R.id.keepgpsonSummary);
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
        setPreset = view.findViewById(R.id.setPreset);

        editMinTimeSummary = view.findViewById(R.id.editMinTimeSummary);

        minTimeTextInputLayout = view.findViewById(R.id.input_layout_min_time);
        httpWarning = view.findViewById(R.id.logjob_url_warn_http);

        editMindistanceHint.setHelperText(getString(R.string.edit_range_metres, 0, MAX_METRES));
        editMinaccuracyHint.setHelperText(getString(R.string.edit_range_metres, 1, MAX_METRES));

        String timeoutVal = String.valueOf(logjob.getLocationRequestTimeout());
        editLocationRequestTimeout.setText(timeoutVal);
        // Setup significant motion option, only show if device supports it
        if (deviceSupportsSignificantMotion()) {
            editUseSignificantMotion.setChecked(logjob.useSignificantMotion());
            editUseSignificantMotionInterval.setChecked(logjob.getMinTime() > 0);

            editUseSignificantMotionMixed.setChecked(logjob.useSignificantMotionMixed());

            updateVisiblePreferencesForSignificantMotion(logjob.useSignificantMotion());
        } else {
            Log.i(TAG, "Device doesn't support significant motion");
            editUseSignificantMotionLayout.setVisibility(View.GONE);
            editUseSignificantMotionIntervalLayout.setVisibility(View.GONE);
            editUseSignificantMotionMixedLayout.setVisibility(View.GONE);
            //editLocationRequestTimeoutLayout.setVisibility(View.GONE);
        }

        // EVENTS

        fabOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveIfValidAndClose();
            }
        });

        setPreset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder selectBuilder = new AlertDialog.Builder(new ContextThemeWrapper(getContext(), R.style.AppThemeDialog));
                selectBuilder.setTitle(getString(R.string.setting_set_preset));

                List<String> choicesList = new ArrayList<>();
                choicesList.add(getString(R.string.menu_preset_bike));
                choicesList.add(getString(R.string.menu_preset_walk));
                choicesList.add(getString(R.string.menu_preset_drive));
                choicesList.add(getString(R.string.menu_preset_precision));
                if (deviceSupportsSignificantMotion()) {
                    choicesList.add(getString(R.string.menu_preset_battery));
                }

                CharSequence[] choices = choicesList.toArray(new CharSequence[choicesList.size()]);
                selectBuilder.setSingleChoiceItems(choices, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                applyBikePreset();
                                showPresetHint();
                                break;
                            case 1:
                                applyWalkPreset();
                                showPresetHint();
                                break;
                            case 2:
                                applyDrivePreset();
                                showPresetHint();
                                break;
                            case 3:
                                applyPrecisionPreset();
                                showPresetHint();
                                break;
                            case 4:
                                applyBatteryPreset();
                                showPresetHint();
                                break;
                        }
                        dialog.dismiss();
                    }
                });
                selectBuilder.setNegativeButton(getString(R.string.simple_cancel), null);
                selectBuilder.create().show();

            }
        });

        // text fields are watched in onViewCreated (watchTextFields)

        // also updates the warning about long intervals
        editKeepGpsOn.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        onFormChanged();
                    }
                }
        );

        editUseSignificantMotion.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Log.d(TAG, "sign motion mode change");
                        onFormChanged();
                        updateVisiblePreferencesForSignificantMotion(isChecked);
                    }
                }
        );

        editUseSignificantMotionInterval.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Log.d(TAG, "sign motion mode change");
                        onFormChanged();
                        updateVisiblePreferencesForSignificantMotion(getUseSignificantMotion(), isChecked, getUseSignificantMotionMixed());
                    }
                }
        );

        signMotionIntervalSummary.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        editUseSignificantMotionInterval.performClick();
                    }
                }
        );

        signMotionSummary.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        editUseSignificantMotion.performClick();
                    }
                }
        );

        usesignificantmotionmixedSummary.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        editUseSignificantMotionMixed.performClick();
                    }
                }
        );

        keepgpsonSummary.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        editKeepGpsOn.performClick();
                    }
                }
        );

        editUseSignificantMotionMixed.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        Log.d(TAG, "sign motion mode change");
                        onFormChanged();
                        updateVisiblePreferencesForSignificantMotion(getUseSignificantMotion(), getUseSignificantMotionInterval(), isChecked);
                    }
                }
        );

        handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Runs after the subclass has filled in the form and before any saved view state
     * (text typed before a rotation) is put back, so the form is still as it was loaded.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (initialForm == null) {
            initialForm = formState(view);
        }
        watchTextFields(view);
        unsavedChangesCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                askAboutUnsavedChanges();
            }
        };
        // added after the activity's own callback, so it runs first while it is enabled
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), unsavedChangesCallback);
        onFormChanged();
    }

    /** Re-checks the form whenever any text field changes, and remembers which ones the user edited. */
    private void watchTextFields(View view) {
        if (view instanceof EditText) {
            final EditText field = (EditText) view;
            field.addTextChangedListener(new TextWatcher() {
                public void afterTextChanged(Editable s) {
                    touchedFields.add(field.getId());
                    onFormChanged();
                }

                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
            });
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                watchTextFields(group.getChildAt(i));
            }
        }
    }

    /** The value of every text field and checkbox of the form, to compare with the form as loaded. */
    private static ArrayList<String> formState(View root) {
        ArrayList<String> state = new ArrayList<>();
        collectFormState(root, state);
        return state;
    }

    private static void collectFormState(View view, List<String> state) {
        if (view instanceof EditText) {
            state.add(view.getId() + "=" + ((EditText) view).getText());
        } else if (view instanceof CompoundButton) {
            state.add(view.getId() + ":" + ((CompoundButton) view).isChecked());
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectFormState(group.getChildAt(i), state);
            }
        }
    }

    /** True when the form differs from the log job as it was loaded (or, for a new job, as it was offered). */
    @VisibleForTesting
    boolean hasUnsavedChanges() {
        View view = getView();
        return view != null && initialForm != null && !initialForm.equals(formState(view));
    }

    private void askAboutUnsavedChanges() {
        boolean valid = validate(false).isValid();
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(getContext(), R.style.AppThemeDialog))
                .setTitle(R.string.edit_unsaved_title)
                .setNegativeButton(R.string.edit_unsaved_discard, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.close();
                        }
                    }
                });
        if (valid) {
            builder.setMessage(R.string.edit_unsaved_message)
                    .setPositiveButton(R.string.edit_unsaved_save, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            saveIfValidAndClose();
                        }
                    })
                    .setNeutralButton(R.string.edit_unsaved_keep_editing, null);
        } else {
            builder.setMessage(R.string.edit_unsaved_invalid_message)
                    .setNeutralButton(R.string.edit_unsaved_keep_editing, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            focusFirstInvalid(validate(true));
                        }
                    });
        }
        builder.show();
    }

    /** The Save button: saves and closes, or shows what is wrong and moves to the first invalid field. */
    private void saveIfValidAndClose() {
        Validation validation = validate(true);
        if (!validation.isValid()) {
            focusFirstInvalid(validation);
            return;
        }
        saveLogjob(null);
        if (listener != null) {
            listener.close();
        }
    }

    private static void focusFirstInvalid(Validation validation) {
        TextInputLayout first = validation.getFirstInvalid();
        if (first != null && first.getEditText() != null) {
            first.getEditText().requestFocus();
        }
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
        outState.putStringArrayList(SAVEDKEY_INITIAL_FORM, initialForm);
        outState.putIntegerArrayList(SAVEDKEY_TOUCHED_FIELDS, new ArrayList<>(touchedFields));
        outState.putBoolean(SAVEDKEY_REVEAL_ERRORS, revealAllErrors);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_logjob_fragment, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menu_share).setVisible(false);
        if (!deviceSupportsSignificantMotion()) {
            menu.findItem(R.id.menu_battery).setVisible(false);
        }
    }

    /**
     * Main-Menu-Handler
     */
    @Override
    // small local SQLite reads/deletes on the main thread, like the other delete paths of the UI
    @SuppressLint("ThreadConstraint")
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_delete) {
            if (logjob.getId() != 0) {
                LogjobDeletion.confirmAndDelete(requireActivity(), Collections.singletonList(logjob), listener::close);
            }
            else {
                listener.close();
            }
            return true;
        }
        else if (itemId == R.id.menu_share) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getTitle());
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, getURL());

            startActivity(Intent.createChooser(shareIntent, logjob.getTitle()));

            return false;
        }
        else if (itemId == R.id.menu_bike) {
            applyBikePreset();
            showPresetHint();
            return true;
        }
        else if (itemId == R.id.menu_walk) {
            applyWalkPreset();
            showPresetHint();
            return true;
        }
        else if (itemId == R.id.menu_drive) {
            applyDrivePreset();
            showPresetHint();
            return true;
        }
        else if (itemId == R.id.menu_battery) {
            applyBatteryPreset();
            showPresetHint();
            return true;
        }
        else if (itemId == R.id.menu_precision) {
            applyPrecisionPreset();
            showPresetHint();
            return true;
        }
        else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void showPresetHint() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean userKnows = prefs.getBoolean(getString(R.string.pref_key_preset_hint_disabled), false);
        if (!userKnows) {
            AlertDialog.Builder builder;
            builder = new AlertDialog.Builder(new ContextThemeWrapper(getContext(), R.style.AppThemeDialog));
            builder.setTitle(getString(R.string.preset_hint_title))
                    .setMessage(getString(R.string.preset_hint_content))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .setNeutralButton(R.string.dont_show_again, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putBoolean(getString(R.string.pref_key_preset_hint_disabled), true);
                            editor.apply();
                        }
                    })
                    .setIcon(R.drawable.ic_info_outline_grey600_24dp)
                    .show();
        }
    }

    private void applyBikePreset() {
        editUseSignificantMotion.setChecked(false);
        editMindistance.setText("3");
        editMintime.setText("5");
        editMinaccuracy.setText("30");
        editKeepGpsOn.setChecked(false);
        editLocationRequestTimeout.setText("0");
        onFormChanged();
    }

    private void applyWalkPreset() {
        editUseSignificantMotion.setChecked(false);
        editMindistance.setText("3");
        editMintime.setText("10");
        editMinaccuracy.setText("20");
        editKeepGpsOn.setChecked(false);
        editLocationRequestTimeout.setText("0");
        onFormChanged();
    }

    private void applyDrivePreset() {
        if (deviceSupportsSignificantMotion()) {
            editUseSignificantMotion.setChecked(true);
            editUseSignificantMotionInterval.setChecked(true);
            editUseSignificantMotionMixed.setChecked(false);
        } else {
            editUseSignificantMotion.setChecked(false);
        }
        editMindistance.setText("50");
        editMintime.setText("60");
        editMinaccuracy.setText("50");
        editKeepGpsOn.setChecked(false);
        editLocationRequestTimeout.setText("0");
        onFormChanged();
    }

    private void applyBatteryPreset() {
        editUseSignificantMotion.setChecked(true);
        editUseSignificantMotionInterval.setChecked(true);
        editUseSignificantMotionMixed.setChecked(false);
        editMindistance.setText("20");
        editMintime.setText("300");
        editMinaccuracy.setText("100");
        editKeepGpsOn.setChecked(false);
        editLocationRequestTimeout.setText("60");
        onFormChanged();
    }

    private void applyPrecisionPreset() {
        editUseSignificantMotion.setChecked(false);
        editMindistance.setText("1");
        editMintime.setText("3");
        editMinaccuracy.setText("20");
        editKeepGpsOn.setChecked(true);
        editLocationRequestTimeout.setText("0");
        onFormChanged();
    }

    public void onCloseLogjob() {
        Log.d(getClass().getSimpleName(), "onCLOSE()");
        if (getActivity() != null && getView() != null) {
            KeyboardUtil.hide(getActivity().getWindow(), getView());
        }
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

    /**
     * Called whenever a field changes: updates the helper texts, the errors of the fields the user
     * has edited, the http:// warning and whether Back asks about unsaved changes.
     */
    protected void onFormChanged() {
        if (editLocationTimeoutHint == null) {
            return;
        }
        boolean mixed = isMixedMode();
        int minTimeLowest = getUseSignificantMotion() ? MIN_TIME_SIG_MOTION : 1;
        minTimeTextInputLayout.setHelperText(getString(R.string.edit_range_seconds, minTimeLowest, MAX_SECONDS));
        int gpsOnPercent = LocationSamplePolicy.gpsOnPercentWithoutFixWarning(getMintime(), getLocationRequestTimeout());
        editLocationTimeoutHint.setHelperText(gpsOnPercent > 0
                ? getString(R.string.setting_timeout_gps_on_warning, gpsOnPercent)
                : getString(mixed ? R.string.edit_range_timeout_mixed : R.string.edit_range_timeout));
        boolean keepGpsOnWarning = getKeepGpsOn() && getMintime() >= KEEP_GPS_ON_WARNING_MIN_TIME;
        keepgpsonSummary.setText(keepGpsOnWarning
                ? getString(R.string.setting_keepgps_summary) + "\n" + getString(R.string.setting_keepgps_long_interval_warning)
                : getString(R.string.setting_keepgps_summary));
        validate(false);
        updateHttpWarning();
        if (unsavedChangesCallback != null) {
            unsavedChangesCallback.setEnabled(hasUnsavedChanges());
        }
    }

    /** True when every field is valid; shows nothing the user has not asked for yet. */
    protected boolean isFormValid() {
        return validate(false).isValid();
    }

    /**
     * Checks every field and shows the error of each invalid one the user has edited, or of
     * every invalid one once Save was tapped.
     */
    protected Validation validate(boolean revealAll) {
        if (revealAll) {
            revealAllErrors = true;
        }
        Validation validation = new Validation(revealAllErrors);
        validation.check(editTitleHint, getTitle().isEmpty() ? getString(R.string.edit_error_title_required) : null);
        validateConnectionFields(validation);
        validation.check(editMindistanceHint, rangeError(editMindistance, 0, MAX_METRES,
                R.plurals.edit_error_min_metres, R.plurals.edit_error_max_metres));
        if (isMinTimeUsed()) {
            int lowest = getUseSignificantMotion() ? MIN_TIME_SIG_MOTION : 1;
            validation.check(minTimeTextInputLayout, rangeError(editMintime, lowest, MAX_SECONDS,
                    R.plurals.edit_error_min_seconds, R.plurals.edit_error_max_seconds));
        } else {
            validation.check(minTimeTextInputLayout, null);
        }
        validation.check(editMinaccuracyHint, rangeError(editMinaccuracy, 1, MAX_METRES,
                R.plurals.edit_error_min_metres, R.plurals.edit_error_max_metres));
        String timeoutError = rangeError(editLocationRequestTimeout, 0, MAX_SECONDS,
                R.plurals.edit_error_min_seconds, R.plurals.edit_error_max_seconds);
        int minTime = getMintime();
        if (timeoutError == null && minTime >= 0 && getLocationRequestTimeout() >= minTime) {
            timeoutError = getString(isMixedMode()
                    ? R.string.edit_error_timeout_not_below_max_time
                    : R.string.edit_error_timeout_not_below_min_time);
        }
        validation.check(editLocationTimeoutHint, timeoutError);
        return validation;
    }

    /**
     * Checks the fields that say where positions are sent (address, token, login...), which
     * differ between log job types. They come right after the title on screen.
     */
    protected void validateConnectionFields(Validation validation) {
    }

    /** @return the error for an address field, or null if it is a valid address */
    @Nullable
    protected String urlError(String url) {
        return url.isEmpty() || !isValidUrl(url) ? getString(R.string.edit_error_url_invalid) : null;
    }

    /**
     * @return the error for a whole number field that must be between min and max, or null if it is
     * valid. Never throws, however many digits were typed.
     */
    @Nullable
    private String rangeError(EditText field, int min, int max, @PluralsRes int tooSmall, @PluralsRes int tooLarge) {
        String text = field.getText() == null ? "" : field.getText().toString().trim();
        if (!text.matches("-?[0-9]+")) {
            return getString(R.string.edit_error_number_required);
        }
        Integer value = parseNumber(text);
        if (value == null) {
            // more digits than an int holds
            return text.startsWith("-")
                    ? getResources().getQuantityString(tooSmall, min, min)
                    : getResources().getQuantityString(tooLarge, max, max);
        }
        if (value < min) {
            return getResources().getQuantityString(tooSmall, min, min);
        }
        if (value > max) {
            return getResources().getQuantityString(tooLarge, max, max);
        }
        return null;
    }

    /** @return the whole number in the text, or null if it is empty, not a number or too large. */
    @Nullable
    @VisibleForTesting
    static Integer parseNumber(@Nullable CharSequence text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The minimum time field is hidden, and not used, in significant motion mode without an interval. */
    private boolean isMinTimeUsed() {
        return !getUseSignificantMotion() || getUseSignificantMotionInterval();
    }

    private boolean isMixedMode() {
        return getUseSignificantMotion() && getUseSignificantMotionInterval() && getUseSignificantMotionMixed();
    }

    /**
     * @return the warning to show under the address, or null for none. Positions sent to an
     * http:// address travel unencrypted.
     */
    @Nullable
    protected String httpWarningText() {
        return isPlainHttp(getURL()) ? getString(R.string.logjob_url_warn_http) : null;
    }

    protected static boolean isPlainHttp(String url) {
        return url.trim().toLowerCase(Locale.ROOT).startsWith("http://");
    }

    private void updateHttpWarning() {
        if (httpWarning == null) {
            return;
        }
        String warning = httpWarningText();
        httpWarning.setText(warning);
        httpWarning.setVisibility(warning == null ? View.GONE : View.VISIBLE);
    }

    /** The result of checking the form: whether it is valid and the first field that is not. */
    protected final class Validation {
        private final boolean revealAll;
        private boolean valid = true;
        private TextInputLayout firstInvalid;

        private Validation(boolean revealAll) {
            this.revealAll = revealAll;
        }

        /**
         * Records the result for one field and shows its error, as text under the field, when
         * the user has edited that field or tapped Save.
         *
         * @param error what is wrong with the field, or null if it is valid
         */
        public void check(TextInputLayout layout, @Nullable String error) {
            if (error != null) {
                valid = false;
                if (firstInvalid == null) {
                    firstInvalid = layout;
                }
            }
            EditText field = layout.getEditText();
            boolean show = error != null
                    && (revealAll || (field != null && touchedFields.contains(field.getId())));
            if (show) {
                if (!error.equals(String.valueOf(layout.getError()))) {
                    layout.setError(error);
                }
            } else if (layout.isErrorEnabled()) {
                layout.setError(null);
                layout.setErrorEnabled(false);
            }
        }

        public boolean isValid() {
            return valid;
        }

        @Nullable
        public TextInputLayout getFirstInvalid() {
            return firstInvalid;
        }
    }

    /** From this minimum time, keeping GPS on costs more battery than it is worth. */
    private static final int KEEP_GPS_ON_WARNING_MIN_TIME = 60;

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
    /** @return the minimum time, or -1 if the field does not hold a valid number */
    protected int getMintime() {
        return numberOrMinusOne(editMintime);
    }
    protected int getMindistance() {
        return numberOrMinusOne(editMindistance);
    }
    protected int getMinaccuracy() {
        return numberOrMinusOne(editMinaccuracy);
    }

    private static int numberOrMinusOne(EditText field) {
        Integer value = parseNumber(field.getText());
        return value == null ? -1 : value;
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

    /** @return the location timeout, or -1 if the field does not hold a valid number */
    protected int getLocationRequestTimeout() {
        return numberOrMinusOne(editLocationRequestTimeout);
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getActivity();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    private void updateVisiblePreferencesForSignificantMotion(boolean sigMotionEnabled, Boolean useInterval, Boolean mixedMode) {
        //editMinaccuracyLayout.setVisibility(!sigMotionEnabled ? View.VISIBLE : View.GONE);
        editMintimeLayout.setVisibility((!sigMotionEnabled || useInterval) ? View.VISIBLE : View.GONE);
        editUseSignificantMotionMixedLayout.setVisibility((sigMotionEnabled && useInterval) ? View.VISIBLE : View.GONE);
        editKeepGpsOnLayout.setVisibility(!sigMotionEnabled ? View.VISIBLE : View.GONE);
        //editLocationRequestTimeoutLayout.setVisibility(sigMotionEnabled ? View.VISIBLE : View.GONE);
        editUseSignificantMotionIntervalLayout.setVisibility(sigMotionEnabled ? View.VISIBLE : View.GONE);

        // If changing significant motion setting update default value for minimum time
        if (sigMotionEnabled != getUseSignificantMotion()) {
            String newValue = Integer.toString(sigMotionEnabled ? MINIMUM_TIME_DEFAULT_SIG_MOTION : MINIMUM_TIME_DEFAULT_STANDARD);
            editMintime.setText(newValue);
        }

        // the allowed range is the field's helper text (see onFormChanged)
        if (sigMotionEnabled && useInterval && mixedMode) {
            minTimeTextInputLayout.setHint(getString(R.string.setting_min_time_mixed));
            editUseSignificantMotionIntervalLayout.setVisibility(View.GONE);
            editMintime.setTooltipText(getString(R.string.setting_min_time_mixed_long));
            editMinTimeSummary.setText(getString(R.string.setting_min_time_mixed_long));
        }
        else {
            minTimeTextInputLayout.setHint(getString(R.string.setting_min_time));
            editMintime.setTooltipText(getString(R.string.setting_min_time_long));
            editMinTimeSummary.setText(getString(R.string.setting_min_time_long));
        }
    }

    private void updateVisiblePreferencesForSignificantMotion(boolean sigMotionEnabled) {
        updateVisiblePreferencesForSignificantMotion(sigMotionEnabled, getUseSignificantMotionInterval(), getUseSignificantMotionMixed());
    }

    /**
     * Verify if the device supports the significant motion sensor
     */
    private boolean deviceSupportsSignificantMotion() {
        SensorManager sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        return sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) != null;
    }
}
