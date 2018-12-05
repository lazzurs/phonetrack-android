package net.eneiluj.nextcloud.phonetrack.android.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;

//public abstract class EditLogjobFragment extends Fragment implements CategoryDialogFragment.CategoryDialogListener {
//public class EditLogjobFragment extends PreferencesFragment {
public class EditLogjobFragment extends PreferenceFragment {

    public interface LogjobFragmentListener {
        void close();

        void onLogjobUpdated(DBLogjob logjob);
    }

    public static final String PARAM_LOGJOB_ID = "logjobId";
    public static final String PARAM_NEWLOGJOB = "newLogjob";
    private static final String SAVEDKEY_LOGJOB = "logjob";
    private static final String SAVEDKEY_ORIGINAL_LOGJOB = "original_logjob";

    protected DBLogjob logjob;
    @Nullable
    private DBLogjob originalLogjob;
    private PhoneTrackSQLiteOpenHelper db;
    private LogjobFragmentListener listener;

    private static final String LOG_TAG_AUTOSAVE = "AutoSave";

    private static final long DELAY = 2000; // Wait for this time after typing before saving
    private static final long DELAY_AFTER_SYNC = 5000; // Wait for this time after saving before checking for next save

    private Handler handler;
    private boolean saveActive, unsavedEdit;

    EditTextPreference editTitle;
    EditTextPreference editNextURL;
    EditTextPreference editToken;
    EditTextPreference editDevicename;
    EditTextPreference editMintime;
    EditTextPreference editMindistance;
    EditTextPreference editMinaccuracy;
    ListPreference editSessionList;

    private DialogInterface.OnClickListener deleteDialogClickListener;
    private AlertDialog.Builder confirmDeleteAlertBuilder;

    private AlertDialog.Builder selectBuilder;
    private AlertDialog selectDialog;

    private AlertDialog.Builder fromUrlBuilder;
    private AlertDialog fromUrlDialog;
    private EditText fromUrlEdit;

    private List<DBSession> sessionList;
    private List<String> sessionNameList;
    private List<String> sessionIdList;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            long id = getArguments().getLong(PARAM_LOGJOB_ID);
            if (id > 0) {
                logjob = originalLogjob = db.getLogjob(id);
            } else {
                DBLogjob cloudLogjob = (DBLogjob) getArguments().getSerializable(PARAM_NEWLOGJOB);
                if (cloudLogjob == null) {
                    throw new IllegalArgumentException(PARAM_LOGJOB_ID + " is not given and argument " + PARAM_NEWLOGJOB + " is missing.");
                }
                logjob = db.getLogjob(db.addLogjob(cloudLogjob));
                originalLogjob = null;
            }
        } else {
            logjob = (DBLogjob) savedInstanceState.getSerializable(SAVEDKEY_LOGJOB);
            originalLogjob = (DBLogjob) savedInstanceState.getSerializable(SAVEDKEY_ORIGINAL_LOGJOB);
        }
        setHasOptionsMenu(true);
        System.out.println("AAAAAAAAAAAAAAA on create : "+logjob);

        ///////////////
        addPreferencesFromResource(R.xml.activity_edit);

        Preference titlePref = findPreference("title");
        titlePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("title");
                pref.setSummary((CharSequence) newValue);
                // trick to make change effective before saving
                // otherwise edittext is not up to date when saving...
                pref.setText((String) newValue);
                saveLogjob(null);
                return true;
            }

        });
        Preference nextURLPref = findPreference("nextURL");
        nextURLPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("nextURL");
                pref.setSummary((CharSequence) newValue);
                pref.setText((String) newValue);
                saveLogjob(null);
                return true;
            }

        });
        Preference tokenPref = findPreference("token");
        tokenPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("token");
                pref.setSummary((CharSequence) newValue);
                pref.setText((String) newValue);
                saveLogjob(null);
                return true;
            }

        });
        Preference devicenamePref = findPreference("devicename");
        devicenamePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("devicename");
                pref.setSummary((CharSequence) newValue);
                pref.setText((String) newValue);
                saveLogjob(null);
                return true;
            }

        });
        Preference minTimePref = findPreference("mintime");
        minTimePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("mintime");
                pref.setSummary((CharSequence) newValue);
                pref.setText((String) newValue);
                saveLogjob(null);
                return true;
            }

        });
        Preference minDistancePref = findPreference("mindistance");
        minDistancePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) preference;
                pref.setSummary((CharSequence) newValue);
                pref.setText((String) newValue);
                saveLogjob(null);
                return true;
            }

        });
        Preference minAccuracyPref = findPreference("minaccuracy");
        minAccuracyPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                EditTextPreference pref = (EditTextPreference) findPreference("minaccuracy");
                pref.setSummary((CharSequence) newValue);
                pref.setText((String) newValue);
                saveLogjob(null);
                return true;
            }

        });
        // session selected
        Preference sessionPref= this.findPreference("sessionList");
        sessionPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                ListPreference pref = (ListPreference) findPreference("sessionList");
                DBSession s = db.getSession(Long.valueOf((String)newValue));
                System.out.println("NEWVAL SESSION : "+newValue);
                pref.setSummary((CharSequence) s.getName());
                setFieldsFromSession(s);
                saveLogjob(null);
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
                        db.deleteLogjobAndSync(logjob.getId());
                        listener.close();
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        //No button clicked
                        break;
                }
            }
        };
        confirmDeleteAlertBuilder = new AlertDialog.Builder(getActivity());
        confirmDeleteAlertBuilder.setMessage("Are you sure?").setPositiveButton("Yes", deleteDialogClickListener)
               .setNegativeButton("No", deleteDialogClickListener);

        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (LogjobFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.getClass() + " must implement " + LogjobFragmentListener.class);
        }
        db = PhoneTrackSQLiteOpenHelper.getInstance(activity);
    }

    @Override
    public void onResume() {
        super.onResume();
        listener.onLogjobUpdated(logjob);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveLogjob(null);
        notifyLoggerService(logjob.getId());
    }

    private void notifyLoggerService(long jobId) {
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
        saveLogjob(null);
        outState.putSerializable(SAVEDKEY_LOGJOB, logjob);
        outState.putSerializable(SAVEDKEY_ORIGINAL_LOGJOB, originalLogjob);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_note_fragment, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //MenuItem itemFavorite = menu.findItem(R.id.menu_favorite);
        //prepareFavoriteOption(itemFavorite);
        if (db.getSessions().size() == 0) {
            MenuItem itemSelectSession = menu.findItem(R.id.menu_selectSession);
            itemSelectSession.setVisible(false);
        }
    }

    /*private void prepareFavoriteOption(MenuItem item) {
        item.setIcon(logjob.isFavorite() ? R.drawable.ic_star_white_24dp : R.drawable.ic_star_border_white_24dp);
        item.setChecked(logjob.isFavorite());
    }*/


    /**
     * Main-Menu-Handler
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_cancel:
                if (originalLogjob == null) {
                    db.deleteLogjobAndSync(logjob.getId());
                } else {
                    System.out.println("ORIG ENAB : "+originalLogjob.isEnabled());
                    db.updateLogjobAndSync(originalLogjob, null, null, null, null, 0,0,0,null);
                }
                listener.close();
                return true;
            case R.id.menu_delete:
                confirmDeleteAlertBuilder.show();
                return true;
            case R.id.menu_fromLogUrl:
                fromUrlDialog.show();
                return true;
            case R.id.menu_selectSession:
                selectDialog.show();
                return true;
            case R.id.menu_share:
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, logjob.getTitle());
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, logjob.getNextURL());

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
        // TODO if all fields are empty (or just title/URL) : delete
        if (originalLogjob == null && getTitle().isEmpty()) {
            //db.deleteNoteAndSync(logjob.getId());
        }
        Log.d(getClass().getSimpleName(), "onCLOSE()");
    }

    /**
     * Save the current state in the database and schedule synchronization if needed.
     *
     * @param callback Observer which is called after save/synchronization
     */
    protected void saveLogjob(@Nullable ICallback callback) {
        //String s = null;
        //int a = Integer.valueOf(s);
        Log.d(getClass().getSimpleName(), "saveData()");
        String newTitle = getTitle();
        String newNextURL = getNextURL();
        String newToken = getToken();
        String newDevicename = getDevicename();
        int newMinTime = Integer.valueOf(getMintime());
        int newMinDistance = Integer.valueOf(getMindistance());
        int newMinAccuracy = Integer.valueOf(getMinaccuracy());
        if(logjob.getTitle().equals(newTitle) &&
                logjob.getNextURL().equals(newNextURL) &&
                logjob.getToken().equals(newToken) &&
                logjob.getMinTime() == newMinTime &&
                logjob.getMinDistance() == newMinDistance &&
                logjob.getMinAccuracy() == newMinAccuracy &&
                logjob.getDeviceName().equals(newDevicename)) {
            Log.v(getClass().getSimpleName(), "... not saving, since nothing has changed");
        } else {
            System.out.println("====== update logjob");
            logjob = db.updateLogjobAndSync(logjob, newTitle, newToken, newNextURL, newDevicename, newMinTime, newMinDistance, newMinAccuracy, callback);
            //System.out.println("AFFFFFFTTTTTTEEERRRRR : "+logjob);
            listener.onLogjobUpdated(logjob);
        }
    }

    public static EditLogjobFragment newInstance(long logjobId) {
        EditLogjobFragment f = new EditLogjobFragment();
        Bundle b = new Bundle();
        b.putLong(PARAM_LOGJOB_ID, logjobId);
        f.setArguments(b);
        return f;
    }

    public static EditLogjobFragment newInstanceWithNewLogjob(DBLogjob newLogjob) {
        EditLogjobFragment f = new EditLogjobFragment();
        Bundle b = new Bundle();
        b.putSerializable(PARAM_NEWLOGJOB, newLogjob);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        System.out.println("ACT CREATEDDDDDDD");
        ButterKnife.bind(this, getView());

        // hide the keyboard when this window gets the focus
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        editTitle = (EditTextPreference) this.findPreference("title");
        editTitle.setText(logjob.getTitle());
        editTitle.setSummary(logjob.getTitle());
        editNextURL = (EditTextPreference) this.findPreference("nextURL");
        editNextURL.setText(logjob.getNextURL());
        editNextURL.setSummary(logjob.getNextURL());
        editToken = (EditTextPreference) this.findPreference("token");
        editToken.setText(logjob.getToken());
        editToken.setSummary(logjob.getToken());
        editDevicename = (EditTextPreference) this.findPreference("devicename");
        editDevicename.setText(logjob.getDeviceName());
        editDevicename.setSummary(logjob.getDeviceName());

        editMintime = (EditTextPreference) this.findPreference("mintime");
        editMintime.setText(String.valueOf(logjob.getMinTime()));
        editMintime.setSummary(String.valueOf(logjob.getMinTime()));

        editMindistance = (EditTextPreference) this.findPreference("mindistance");
        editMindistance.setText(String.valueOf(logjob.getMinDistance()));
        editMindistance.setSummary(String.valueOf(logjob.getMinDistance()));

        editMinaccuracy = (EditTextPreference) this.findPreference("minaccuracy");
        editMinaccuracy.setText(String.valueOf(logjob.getMinAccuracy()));
        editMinaccuracy.setSummary(String.valueOf(logjob.getMinAccuracy()));

        // manage session list
        sessionList = db.getSessions();
        sessionNameList = new ArrayList<>();
        sessionIdList = new ArrayList<>();
        for (DBSession session : sessionList) {
            sessionNameList.add(session.getName());
            sessionIdList.add(String.valueOf(session.getId()));
        }

        // manage session list PREFERENCE
        // it's better to do it with a dialog triggered by a menu entry
        // rather than a confusing fake preference field...
        editSessionList = (ListPreference) this.findPreference("sessionList");

        /*if (sessionNameList.size() > 0) {
            CharSequence[] entcs = sessionNameList.toArray(new CharSequence[sessionNameList.size()]);
            CharSequence[] valcs = sessionIdList.toArray(new CharSequence[sessionIdList.size()]);
            editSessionList.setEntries(entcs);
            editSessionList.setEntryValues(valcs);
        }
        else {
            getPreferenceScreen().removePreference(editSessionList);
        }*/
        getPreferenceScreen().removePreference(editSessionList);

        // manage session list DIALOG
        selectBuilder = new AlertDialog.Builder(getContext());
        selectBuilder.setTitle("Choose a session");

        if (sessionNameList.size() > 0) {
            CharSequence[] entcs = sessionNameList.toArray(new CharSequence[sessionNameList.size()]);
            selectBuilder.setSingleChoiceItems(entcs, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // user checked an item
                    System.out.println("CHECKED :" + which);
                    setFieldsFromSession(sessionList.get(which));
                    saveLogjob(null);
                    dialog.dismiss();
                }
            });

            // add OK and Cancel buttons
            selectBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // user clicked OK
                    System.out.println("CHECKED OK :" + which);
                }
            });
            selectBuilder.setNegativeButton("Cancel", null);

            // create the alert dialog
            selectDialog = selectBuilder.create();
        }

        // manage from URL DIALOG
        fromUrlEdit = new EditText(getContext());
        fromUrlBuilder = new AlertDialog.Builder(getContext());
        fromUrlBuilder.setMessage("Enter Your Message");
        fromUrlBuilder.setTitle("Enter Your Title");

        fromUrlBuilder.setView(fromUrlEdit);

        fromUrlBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                setFieldsFromUrl(fromUrlEdit.getText().toString());
                saveLogjob(null);
            }
        });

        fromUrlBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // what ever you want to do with No option.
            }
        });

        // create the alert dialog
        fromUrlDialog = fromUrlBuilder.create();

    }

    private String getTitle() {
        return editTitle.getText();
    }
    private String getNextURL() {
        return editNextURL.getText();
    }
    private String getToken() {
        return editToken.getText();
    }
    private String getDevicename() {
        return editDevicename.getText();
    }
    private String getMintime() {
        return editMintime.getText();
    }
    private String getMindistance() {
        return editMindistance.getText();
    }
    private String getMinaccuracy() {
        return editMinaccuracy.getText();
    }

    private void setFieldsFromSession(DBSession s) {
        editTitle.setText("Log to "+s.getName());
        editTitle.setSummary("Log to "+s.getName());
        editNextURL.setText(s.getNextURL());
        editNextURL.setSummary(s.getNextURL());
        editToken.setText(s.getToken());
        editToken.setSummary(s.getToken());
    }

    private void setFieldsFromUrl(String url) {
        //System.out.println("UUUUUUUUUUUUU : "+url);
        String[] spl = url.split("/app/phonetrack/");
        System.out.println(spl.length);
        if (spl.length == 2) {
            String nextURL = spl[0];
            if (nextURL.contains("index.php")) {
                nextURL = nextURL.replace("index.php", "");
            }

            String right = spl[1];
            String[] spl2 = right.split("/");
            if (spl2.length > 2) {
                String token = spl2[1];
                String[] spl3 = spl2[2].split("\\?");
                if (spl3.length > 1) {
                    String devname = spl3[0];
                    editTitle.setText("From logging URL");
                    editTitle.setSummary("From logging URL");
                    editDevicename.setText(devname);
                    editDevicename.setSummary(devname);
                    editToken.setText(token);
                    editToken.setSummary(token);
                    editNextURL.setText(nextURL);
                    editNextURL.setSummary(nextURL);
                }
            }
        }
    }

}
