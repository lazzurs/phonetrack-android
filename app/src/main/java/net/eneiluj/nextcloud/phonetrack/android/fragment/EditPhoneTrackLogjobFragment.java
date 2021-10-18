package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputLayout;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.persistence.SessionServerSyncHelper;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;

import java.util.ArrayList;
import java.util.List;

import static android.webkit.URLUtil.isValidUrl;


public class EditPhoneTrackLogjobFragment extends EditLogjobFragment {

    private static final String TAG = EditPhoneTrackLogjobFragment.class.getSimpleName();

    private EditText editToken;
    private EditText editDevicename;

    private AlertDialog.Builder selectBuilder;
    private AlertDialog selectDialog;

    private AlertDialog.Builder fromUrlBuilder;
    private AlertDialog fromUrlDialog;
    private EditText fromUrlEdit;

    private TextInputLayout editDeviceNameHint;
    private TextInputLayout editTokenHint;

    private List<DBSession> sessionList;
    private List<String> sessionNameList;
    private List<String> sessionIdList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "PHONEFRAG on create : "+logjob);


    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_edit, container, false);
        super.onCreateView(view);

        editToken = view.findViewById(R.id.editToken);
        editToken.setText(logjob.getToken());
        editDevicename = view.findViewById(R.id.editDeviceName);
        editDevicename.setText(logjob.getDeviceName());

        editDeviceNameHint = view.findViewById(R.id.input_layout_device_name);
        editTokenHint = view.findViewById(R.id.input_layout_token);


        editToken.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "token change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        editDevicename.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "device name change");
                showHideValidationButtons();
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        // manage session list
        sessionList = db.getSessions();
        sessionNameList = new ArrayList<>();
        sessionIdList = new ArrayList<>();
        for (DBSession session : sessionList) {
            sessionNameList.add(session.getName());
            sessionIdList.add(String.valueOf(session.getId()));
        }

        // manage session list DIALOG
        selectBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this.getActivity(), R.style.AppThemeDialog));
        selectBuilder.setTitle(getString(R.string.edit_logjob_choose_session_dialog_title));

        if (sessionNameList.size() > 0) {
            CharSequence[] entcs = sessionNameList.toArray(new CharSequence[sessionNameList.size()]);
            selectBuilder.setSingleChoiceItems(entcs, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // user checked an item
                    setFieldsFromSession(sessionList.get(which));
                    dialog.dismiss();
                }
            });
            selectBuilder.setNegativeButton(getString(R.string.simple_cancel), null);
            selectDialog = selectBuilder.create();
        }

        // manage from URL DIALOG
        fromUrlEdit = new EditText(getContext());
        fromUrlBuilder = new AlertDialog.Builder(new ContextThemeWrapper(this.getActivity(), R.style.AppThemeDialog));
        fromUrlBuilder.setMessage(getString(R.string.dialog_msg_import_pt_url));
        fromUrlBuilder.setTitle(getString(R.string.dialog_title_import_pt_url));

        fromUrlBuilder.setView(fromUrlEdit);

        fromUrlBuilder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                setFieldsFromPhoneTrackLoggingUrl(fromUrlEdit.getText().toString());
                // restore keyboard auto hide behaviour
                InputMethodManager inputMethodManager = (InputMethodManager) fromUrlEdit.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }
        });
        fromUrlBuilder.setNegativeButton(getString(R.string.simple_cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // restore keyboard auto hide behaviour
                InputMethodManager inputMethodManager = (InputMethodManager) fromUrlEdit.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }
        });
        fromUrlDialog = fromUrlBuilder.create();

        // show select session dialog if there are sessions
        if (sessionNameList.size() > 0 && logjob.getTitle().equals("")) {
            if (sessionNameList.size() == 1) {
                setFieldsFromSession(sessionList.get(0));
            }
            else {
                selectDialog.show();
            }
        }

        showHideValidationButtons();

        return view;
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
        if (getURL() == null || getURL().equals("") || !isValidUrl(getURL())) {
            editUrlHint.setBackgroundColor(0x55FF0000);
            valid = false;
        }
        else {
            editUrlHint.setBackgroundColor(getResources().getColor(R.color.bg_normal));
        }
        if (getToken() == null || getToken().equals("")) {
            editTokenHint.setBackgroundColor(0x55FF0000);
            valid = false;
        }
        else {
            editTokenHint.setBackgroundColor(getResources().getColor(R.color.bg_normal));
        }
        if (getDevicename() == null || getDevicename().equals("")) {
            editDeviceNameHint.setBackgroundColor(0x55FF0000);
            valid = false;
        }
        else {
            editDeviceNameHint.setBackgroundColor(getResources().getColor(R.color.bg_normal));
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
        if (getMindistance() < 0) {
            editMindistanceHint.setBackgroundColor(0x55FF0000);
            valid = false;
        }
        else {
            editMindistanceHint.setBackgroundColor(getResources().getColor(R.color.bg_normal));
        }
        if (getMinaccuracy() < 1) {
            editMinaccuracyHint.setBackgroundColor(0x55FF0000);
            valid = false;
        }
        else {
            editMinaccuracyHint.setBackgroundColor(getResources().getColor(R.color.bg_normal));
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
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (db.getSessionsNotShared().size() == 0) {
            MenuItem itemSelectSession = menu.findItem(R.id.menu_selectSession);
            itemSelectSession.setVisible(false);
        }
        menu.findItem(R.id.menu_share).setVisible(db.getPhonetrackServerSyncHelper().isConfigured(getActivity()));
        menu.findItem(R.id.menu_share).setTitle(R.string.menu_share_dev_title);
    }

    /**
     * Save the current state in the database and schedule synchronization if needed.
     *
     * @param callback Observer which is called after save/synchronization
     */
    @Override
    protected void saveLogjob(@Nullable ICallback callback) {
        Log.d(getClass().getSimpleName(), "saveData()");
        String newTitle = getTitle();
        String newUrl = getURL();
        String newToken = getToken();
        String newDevicename = getDevicename();
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
                    logjob.getUrl().equals(newUrl) &&
                    logjob.getToken().equals(newToken) &&
                    logjob.getMinTime() == newMinTime &&
                    logjob.keepGpsOnBetweenFixes() == newKeepGpsOn &&
                    logjob.getMinDistance() == newMinDistance &&
                    logjob.getMinAccuracy() == newMinAccuracy &&
                    logjob.getDeviceName().equals(newDevicename) &&
                    logjob.useSignificantMotion() == newUseSignificantMotion &&
                    logjob.useSignificantMotionMixed() == newUseSignificantMotionMixed &&
                    logjob.getLocationRequestTimeout() == newTimeout) {
                Log.v(getClass().getSimpleName(), "... not saving logjob, since nothing has changed");
            } else {
                Log.i(TAG, "====== update logjob");
                logjob = db.updateLogjobAndSync(
                        logjob, newTitle, newToken, newUrl, newDevicename,
                        false, newMinTime, newMinDistance, newMinAccuracy, newKeepGpsOn,
                        newUseSignificantMotion, newUseSignificantMotionMixed, newTimeout,
                        null, null, false, callback
                );
                notifyLoggerService(logjob.getId());
                //listener.onLogjobUpdated(logjob);
            }
        } else {
            // this is a new logjob
            DBLogjob newLogjob = new DBLogjob(
                    0, newTitle, newUrl, newToken, newDevicename,
                    newMinTime, newMinDistance, newMinAccuracy, newKeepGpsOn, newUseSignificantMotion,
                    newUseSignificantMotionMixed, newTimeout, false, false, 0,
                    null, null, false
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

    public static EditPhoneTrackLogjobFragment newInstance(long logjobId) {
        EditPhoneTrackLogjobFragment f = new EditPhoneTrackLogjobFragment();
        Bundle b = new Bundle();
        b.putLong(PARAM_LOGJOB_ID, logjobId);
        f.setArguments(b);
        return f;
    }

    public static EditPhoneTrackLogjobFragment newInstanceWithNewLogjob(DBLogjob newLogjob) {
        EditPhoneTrackLogjobFragment f = new EditPhoneTrackLogjobFragment();
        Bundle b = new Bundle();
        b.putSerializable(PARAM_NEWLOGJOB, newLogjob);
        f.setArguments(b);
        return f;
    }

    private ICallback shareCallBack = new ICallback() {
        @Override
        public void onFinish() {
        }

        public void onFinish(String publicUrl, String message) {
            if (publicUrl != null) {
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.share_dev_title));
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, publicUrl);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_dev_title)));
            }
            else {
                showToast(getString(R.string.error_share_dev_helper, message), Toast.LENGTH_LONG);
            }
        }

        @Override
        public void onScheduled() {
        }
    };

    /**
     * Main-Menu-Handler
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_fromLogUrl:
                fromUrlDialog.show();
                fromUrlEdit.setSelectAllOnFocus(true);
                fromUrlEdit.requestFocus();
                // show keyboard
                InputMethodManager inputMethodManager = (InputMethodManager) fromUrlEdit.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                return true;
            case R.id.menu_selectSession:
                selectDialog.show();
                return true;
            case R.id.menu_share:
                String token = getToken();
                String devicename = getDevicename();
                String nextURL = getURL().replaceAll("/+$", "");
                SessionServerSyncHelper syncHelper = db.getPhonetrackServerSyncHelper();
                if (syncHelper.isConfigured(this.getActivity())) {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
                    String configUrl;
                    if (preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false)) {
                        configUrl = preferences.getString(SettingsActivity.SETTINGS_SSO_URL, SettingsActivity.DEFAULT_SETTINGS)
                                .replaceAll("/+$", "");
                    }
                    else {
                        configUrl = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS)
                                .replaceAll("/+$", "");
                    }
                    if (nextURL.equals(configUrl)) {
                        if (!syncHelper.shareDevice(token, devicename, shareCallBack)) {
                            showToast(getString(R.string.error_share_dev_network), Toast.LENGTH_LONG);
                        }
                    }
                    else {
                        Log.d(getClass().getSimpleName(), "NOT THE SAME NEXTCLOUD URL");
                        showToast(getString(R.string.error_share_dev_same_url), Toast.LENGTH_LONG);
                    }
                }
                else {
                    showToast(getString(R.string.error_share_dev_configured), Toast.LENGTH_LONG);
                }

                return false;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.i(TAG, "PHONETRACK ACT CREATEDDDDDDD");


    }

    private String getToken() {
        return editToken.getText().toString();
    }
    private String getDevicename() {
        return editDevicename.getText().toString();
    }

    private void setFieldsFromSession(DBSession s) {
        editTitle.setText(getString(R.string.logjob_title_log_to, s.getName()));
        editURL.setText(s.getNextURL());
        editToken.setText(s.getToken());
    }

    private void setFieldsFromPhoneTrackLoggingUrl(String url) {
        String[] spl = url.split("/apps/phonetrack/");
        if (spl.length == 2) {
            String nextURL = spl[0];
            if (nextURL.contains("index.php")) {
                nextURL = nextURL.replace("index.php", "");
            }

            String right = spl[1];
            String[] spl2 = right.split("/");
            if (spl2.length > 2) {
                String token;
                String[] splEnd;
                // example .../apps/phonetrack/logGet/token/devname?lat=0.1...
                if (spl2.length == 3) {
                    token = spl2[1];
                    splEnd = spl2[2].split("\\?");
                }
                // example .../apps/phonetrack/log/osmand/token/devname?lat=0.1...
                else {
                    token = spl2[2];
                    splEnd = spl2[3].split("\\?");
                }
                String devname = splEnd[0];
                editTitle.setText("From PhoneTrack logging URL");
                editDevicename.setText(devname);
                editToken.setText(token);
                editURL.setText(nextURL);
            }
        }
    }
}
