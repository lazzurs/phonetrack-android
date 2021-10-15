package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.kizitonwose.colorpreferencecompat.ColorPreferenceCompat;
import com.larswerkman.lobsterpicker.LobsterPicker;
import com.larswerkman.lobsterpicker.sliders.LobsterShadeSlider;

import at.bitfire.cert4android.CustomCertManager;
import net.eneiluj.nextcloud.phonetrack.R;

import net.eneiluj.nextcloud.phonetrack.android.activity.EditMapsLogjobActivity;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.android.activity.MapActivity;
import net.eneiluj.nextcloud.phonetrack.android.activity.SyslogManagerActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.util.MapUtils;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrack;
import net.eneiluj.nextcloud.phonetrack.util.ThemeUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PreferencesFragment extends PreferenceFragmentCompat implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback{

    public final static String UPDATED_PROVIDERS = "net.eneiluj.nextcloud.phonetrack.UPDATED_PROVIDERS";
    public final static String UPDATED_PROVIDERS_VALUE = "net.eneiluj.nextcloud.phonetrack.UPDATED_PROVIDERS_VALUE";

    public final static int PERMISSION_SMS_SEND_AND_RECEIVE = 4;
    private final static int import_file_cmd = 123;

    private static final String TAG = PreferencesFragment.class.getSimpleName();

    private List<String> providersList;
    private ActionBar toolbar;

    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        caller.setPreferenceScreen(pref);
        return true;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootkey) {

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
        addPreferencesFromResource(R.xml.preferences);

        // hide the keyboard when this window gets the focus
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Preference openSyslog = findPreference(getString(R.string.pref_key_open_syslog));
        openSyslog.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent createIntent = new Intent(getContext(), SyslogManagerActivity.class);
                startActivity(createIntent);
                return true;
            }
        });


        Preference resetTrust = findPreference(getString(R.string.pref_key_reset_trust));
        resetTrust.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                CustomCertManager.Companion.resetCertificates(getActivity());
                Toast.makeText(getActivity(), getString(R.string.settings_cert_reset_toast), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

        Preference loadOsmdroidPref = findPreference(getString(R.string.pref_key_osmdroid_load));
        loadOsmdroidPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent()
                        .setType("*/*")
                        .setAction(Intent.ACTION_GET_CONTENT);

                startActivityForResult(Intent.createChooser(intent, "Select a file"), import_file_cmd);
                return true;
            }
        });

        Preference deleteOsmdroidPref = findPreference(getString(R.string.pref_key_osmdroid_delete));
        deleteOsmdroidPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                MapUtils.showDeleteMapFileDialog(getContext());
                return true;
            }
        });

        final CheckBoxPreference useServerColorPref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_use_server_color));

        Boolean useServerColor = sp.getBoolean(getString(R.string.pref_key_use_server_color), false);
        if (useServerColor) {
            findPreference(getString(R.string.pref_key_color)).setVisible(false);
        }

        useServerColorPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean useServerColor = (Boolean) newValue;
                if (useServerColor) {
                    findPreference(getString(R.string.pref_key_color)).setVisible(false);
                }
                else {
                    findPreference(getString(R.string.pref_key_color)).setVisible(true);
                }
                return true;
            }
        });

        final SwitchPreferenceCompat themePref = (SwitchPreferenceCompat) findPreference(getString(R.string.pref_key_theme));

        Boolean darkTheme = sp.getBoolean(getString(R.string.pref_key_theme), false);

        setThemePreferenceSummary(themePref, darkTheme);
        setThemePreferenceIcon(themePref, darkTheme);
        themePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean darkTheme = (Boolean) newValue;
                PhoneTrack.setAppTheme(darkTheme);
                setThemePreferenceSummary(themePref, darkTheme);
                setThemePreferenceIcon(themePref, darkTheme);

                if (getActivity() != null) {
                    getActivity().recreate();
                }
                return true;
            }
        });

        final Preference providersPref = findPreference(getString(R.string.pref_key_providers));
        providersPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                setProvidersSummary(providersPref, (String) newValue);
                Intent intent = new Intent(getActivity(), LoggerService.class);
                intent.putExtra(PreferencesFragment.UPDATED_PROVIDERS, true);
                intent.putExtra(PreferencesFragment.UPDATED_PROVIDERS_VALUE, (String) newValue);
                getActivity().startService(intent);
                return true;
            }
        });

        final EditTextPreference smsKeywordPref = (EditTextPreference) findPreference(getString(R.string.pref_key_sms_keyword));
        String keyword = sp.getString(getString(R.string.pref_key_sms_keyword), "phonetrack");
        smsKeywordPref.setSummary(keyword);
        smsKeywordPref.setDialogMessage(
                getString(R.string.settings_sms_keyword_long)+"\n"
                        + getString(R.string.settings_sms_keyword_long2)+"\n"
                        + getString(R.string.settings_sms_keyword_long3, "alarm", "startlogjobs", "stoplogjobs", "createlogjob")
        );
        smsKeywordPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                String newValueString = (String) newValue;
                if (newValueString == null || newValueString.equals("")) {
                    showToast(getString(R.string.error_invalid_sms_keyword), Toast.LENGTH_LONG);
                    return false;
                }
                else {
                    preference.setSummary((CharSequence) newValue);
                    return true;
                }
            }

        });
        final CheckBoxPreference smsPref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_sms));
        smsPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean listenToSms = (Boolean) newValue;
                if (listenToSms) {
                    if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.SEND_SMS)
                            != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECEIVE_SMS)
                            != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CONTACTS)
                            != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.GET_ACCOUNTS)
                            != PackageManager.PERMISSION_GRANTED) {

                        if (LoggerService.DEBUG) {
                            Log.d(TAG, "[request send sms permission]");
                        }
                        ActivityCompat.requestPermissions(
                                getActivity(),
                                new String[]{
                                        Manifest.permission.SEND_SMS,
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.GET_ACCOUNTS
                                },
                                PERMISSION_SMS_SEND_AND_RECEIVE
                        );
                    }

                    smsKeywordPref.setVisible(true);
                }
                else {
                    smsKeywordPref.setVisible(false);
                }
                return true;
            }
        });

        if (!smsPref.isChecked()) {
            smsKeywordPref.setVisible(false);
        }

        final EditTextPreference groupSyncPref = (EditTextPreference) findPreference(getString(R.string.pref_key_group_sync));
        String groupSyncValStr = sp.getString(getString(R.string.pref_key_group_sync), "0");
        long groupSyncVal = Long.valueOf(groupSyncValStr);
        groupSyncPref.setSummary(String.valueOf(groupSyncVal));
        groupSyncPref.setDialogMessage(getString(R.string.settings_group_sync_long));
        groupSyncPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                String newValueString = (String) newValue;
                if (newValueString == null || newValueString.equals("")) {
                    showToast(getString(R.string.error_invalid_group_sync), Toast.LENGTH_LONG);
                    return false;
                }
                else {
                    long valLong;
                    if (newValueString.length() > 6) {
                        showToast(getString(R.string.error_invalid_group_sync), Toast.LENGTH_LONG);
                        return false;
                    }
                    else {
                        valLong = Long.valueOf(newValueString);
                    }
                    //groupSyncPref.setText(String.valueOf(valInt));
                    // changing the value here does not have any effect
                    /*SharedPreferences.Editor editor = sp.edit();
                    editor.putString(getString(R.string.pref_key_group_sync), String.valueOf(valLong));
                    editor.apply();*/
                    preference.setSummary(String.valueOf(valLong));
                    return true;
                }
            }

        });

        ListPreference providersListPref = (ListPreference) providersPref;
        providersList = new ArrayList<>();
        providersList.add(getString(R.string.providers_gps));
        providersList.add(getString(R.string.providers_network));
        providersList.add(getString(R.string.providers_gps_network));
        CharSequence[] providerEntries = providersList.toArray(new CharSequence[providersList.size()]);
        providersListPref.setEntries(providerEntries);

        String providersValue = sp.getString(getString(R.string.pref_key_providers), "1");

        setProvidersSummary(providersPref, providersValue);

        findPreference(getString(R.string.pref_key_color)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showColorDialog(preference);
                return true;
            }
        });

        // update enabled logjobs if we start/stop respecting power saving mode
        final CheckBoxPreference powerModePref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_power_saving_awareness));
        powerModePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean respectPowerMode = (Boolean) newValue;

                PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(getActivity());
                List<DBLogjob> logjobs = db.getLogjobs();

                for (DBLogjob lj: logjobs) {
                    if (lj.isEnabled()) {
                        Intent intent = new Intent(getActivity(), LoggerService.class);
                        intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, true);
                        intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, lj.getId());
                        getActivity().startService(intent);
                    }
                }

                return true;
            }
        });

        // update enabled logjobs if we start/stop respecting airplane mode
        final CheckBoxPreference airplaneModePref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_offline_mode_awareness));
        airplaneModePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean respectAirplaneMode = (Boolean) newValue;

                PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(getActivity());
                List<DBLogjob> logjobs = db.getLogjobs();

                for (DBLogjob lj: logjobs) {
                    if (lj.isEnabled()) {
                        Intent intent = new Intent(getActivity(), LoggerService.class);
                        intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, true);
                        intent.putExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, lj.getId());
                        getActivity().startService(intent);
                    }
                }

                return true;
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "[ACT RESULT]");
        // Check which request we're responding to
        if (requestCode == import_file_cmd && resultCode == Activity.RESULT_OK) {
            Uri selectedfile = data.getData();
            boolean ok = MapUtils.importMapFile(getContext(), selectedfile);
            if (ok && getActivity() != null) {
                getActivity().recreate();
            }
        }
    }

    private void setThemePreferenceSummary(SwitchPreferenceCompat themePref, Boolean darkTheme) {
        if (darkTheme) {
            themePref.setSummary(getString(R.string.pref_value_theme_dark));
        } else {
            themePref.setSummary(getString(R.string.pref_value_theme_light));
        }
    }

    private void setProvidersSummary(Preference providersPref, String value) {
        int intVal = Integer.parseInt(value);
        providersPref.setSummary(providersList.get(intVal-1));
    }

    private void showColorDialog(final Preference preference) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View colorView = inflater.inflate(R.layout.dialog_color, null);

        int color = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getInt(getString(R.string.pref_key_color), Color.BLUE);
        final LobsterPicker lobsterPicker = colorView.findViewById(R.id.lobsterPicker);
        LobsterShadeSlider shadeSlider = colorView.findViewById(R.id.shadeSlider);

        lobsterPicker.addDecorator(shadeSlider);
        lobsterPicker.setColorHistoryEnabled(true);
        lobsterPicker.setHistory(color);
        lobsterPicker.setColor(color);

        new AlertDialog.Builder(getActivity())
                .setView(colorView)
                .setTitle(getString(R.string.settings_colorpicker_title))
                .setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ((ColorPreferenceCompat) preference).setValue(lobsterPicker.getColor());
                        if (getActivity() != null) {
                            getActivity().recreate();
                        }
                    }
                })
                .setNegativeButton(getString(R.string.simple_cancel), null)
                .show();
    }
    public void disableSms() {
        final CheckBoxPreference smsPref = (CheckBoxPreference) findPreference(getString(R.string.pref_key_sms));
        smsPref.setChecked(false);
    }

    private void setThemePreferenceIcon(Preference preference, boolean darkThemeActive) {
        if (darkThemeActive) {
            preference.setIcon(R.drawable.ic_brightness_2_grey_24dp);
        } else {
            preference.setIcon(R.drawable.ic_sunny_grey_24dp);
        }
    }

    protected void showToast(CharSequence text, int duration) {
        Context context = getActivity();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
