package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
//import android.preference.Preference;
//import android.support.v4.app.Fragment;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
//import android.preference.PreferenceFragment;
import androidx.preference.PreferenceFragmentCompat;

//import android.preference.PreferenceManager;
import androidx.preference.PreferenceManager;
//import android.preference.SwitchPreference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.kizitonwose.colorpreferencecompat.ColorPreferenceCompat;
import com.larswerkman.lobsterpicker.LobsterPicker;
import com.larswerkman.lobsterpicker.sliders.LobsterShadeSlider;

import at.bitfire.cert4android.CustomCertManager;
import net.eneiluj.nextcloud.phonetrack.R;

import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrack;

import java.util.ArrayList;
import java.util.List;

public class PreferencesFragment extends PreferenceFragmentCompat implements PreferenceFragmentCompat.OnPreferenceStartScreenCallback{

    public final static String UPDATED_PROVIDERS = "net.eneiluj.nextcloud.phonetrack.UPDATED_PROVIDERS";
    public final static String UPDATED_PROVIDERS_VALUE = "net.eneiluj.nextcloud.phonetrack.UPDATED_PROVIDERS_VALUE";

    private List<String> providersList;

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

        Preference resetTrust = findPreference(getString(R.string.pref_key_reset_trust));
        resetTrust.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                CustomCertManager.Companion.resetCertificates(getActivity());
                Toast.makeText(getActivity(), getString(R.string.settings_cert_reset_toast), Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        final SwitchPreferenceCompat themePref = (SwitchPreferenceCompat) findPreference(getString(R.string.pref_key_theme));
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        Boolean darkTheme = sp.getBoolean(getString(R.string.pref_key_theme), false);

        setThemePreferenceSummary(themePref, darkTheme);
        themePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Boolean darkTheme = (Boolean) newValue;
                PhoneTrack.setAppTheme(darkTheme);
                setThemePreferenceSummary(themePref, darkTheme);
                //getActivity().setResult(Activity.RESULT_OK);
                //getActivity().finish();
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
    }

    private void setThemePreferenceSummary(SwitchPreferenceCompat themePref, Boolean darkTheme) {
        if (darkTheme) {
            themePref.setSummary(getString(R.string.pref_value_theme_dark));
        } else {
            themePref.setSummary(getString(R.string.pref_value_theme_light));
        }
    }

    private void setProvidersSummary(Preference providersPref, String value) {
        int intVal = Integer.valueOf(value);
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
                    }
                })
                .setNegativeButton(getString(R.string.simple_cancel), null)
                .show();
    }
}
