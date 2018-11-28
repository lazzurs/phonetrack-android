package it.eneiluj.nextcloud.phonetrack.android.fragment;

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

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import it.eneiluj.nextcloud.phonetrack.R;
import it.eneiluj.nextcloud.phonetrack.android.activity.EditLogjobActivity;
import it.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import it.eneiluj.nextcloud.phonetrack.model.DBSession;
import it.eneiluj.nextcloud.phonetrack.persistence.NoteSQLiteOpenHelper;
import it.eneiluj.nextcloud.phonetrack.util.ICallback;

//public abstract class EditLogjobFragment extends Fragment implements CategoryDialogFragment.CategoryDialogListener {
//public class EditLogjobFragment extends PreferencesFragment {
public class EditLogjobFragment extends PreferenceFragment {

    public interface NoteFragmentListener {
        void close();

        void onLogjobUpdated(DBLogjob logjob);
    }

    public static final String PARAM_NOTE_ID = "noteId";
    public static final String PARAM_NEWNOTE = "newNote";
    private static final String SAVEDKEY_NOTE = "logjob";
    private static final String SAVEDKEY_ORIGINAL_NOTE = "original_note";

    protected DBLogjob logjob;
    @Nullable
    private DBLogjob originalLogjob;
    private NoteSQLiteOpenHelper db;
    private NoteFragmentListener listener;

    private static final String LOG_TAG_AUTOSAVE = "AutoSave";

    private static final long DELAY = 2000; // Wait for this time after typing before saving
    private static final long DELAY_AFTER_SYNC = 5000; // Wait for this time after saving before checking for next save

    private Handler handler;
    private boolean saveActive, unsavedEdit;

    EditTextPreference editTitle;
    EditTextPreference editNextURL;
    EditTextPreference editToken;
    EditTextPreference editDevicename;
    ListPreference editSessionList;

    private DialogInterface.OnClickListener dialogClickListener;
    private AlertDialog.Builder confirmDeleteAlertBuilder;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            long id = getArguments().getLong(PARAM_NOTE_ID);
            if (id > 0) {
                logjob = originalLogjob = db.getLogjob(id);
            } else {
                DBLogjob cloudLogjob = (DBLogjob) getArguments().getSerializable(PARAM_NEWNOTE);
                if (cloudLogjob == null) {
                    throw new IllegalArgumentException(PARAM_NOTE_ID + " is not given and argument " + PARAM_NEWNOTE + " is missing.");
                }
                logjob = db.getLogjob(db.addLogjob(cloudLogjob));
                originalLogjob = null;
            }
        } else {
            logjob = (DBLogjob) savedInstanceState.getSerializable(SAVEDKEY_NOTE);
            originalLogjob = (DBLogjob) savedInstanceState.getSerializable(SAVEDKEY_ORIGINAL_NOTE);
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
                //do something
                System.out.println("LALA "+newValue);
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
        // session selected
        Preference sessionPref= this.findPreference("sessionList");
        sessionPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                ListPreference pref = (ListPreference) findPreference("sessionList");
                pref.setSummary((CharSequence) newValue);
                System.out.println("NEWVAL SESSION : "+newValue);
                // TODO call a local method to set fields according to session values
                saveLogjob(null);
                return true;
            }

        });

        // delete confirmation
        dialogClickListener = new DialogInterface.OnClickListener() {
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
        confirmDeleteAlertBuilder = new AlertDialog.Builder(getContext());
        confirmDeleteAlertBuilder.setMessage("Are you sure?").setPositiveButton("Yes", dialogClickListener)
               .setNegativeButton("No", dialogClickListener);

        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (NoteFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.getClass() + " must implement " + NoteFragmentListener.class);
        }
        db = NoteSQLiteOpenHelper.getInstance(activity);
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
        outState.putSerializable(SAVEDKEY_NOTE, logjob);
        outState.putSerializable(SAVEDKEY_ORIGINAL_NOTE, originalLogjob);
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
        MenuItem itemEnabled = menu.findItem(R.id.menu_enabled);
        prepareEnabledOption(itemEnabled);
    }

    /*private void prepareFavoriteOption(MenuItem item) {
        item.setIcon(logjob.isFavorite() ? R.drawable.ic_star_white_24dp : R.drawable.ic_star_border_white_24dp);
        item.setChecked(logjob.isFavorite());
    }*/

    private void prepareEnabledOption(MenuItem item) {
        item.setChecked(logjob.isEnabled());
        //item.setIcon(logjob.isEnabled() ? R.drawable.menu_ico_checked : R.drawable.check_off);
        //System.out.println("CHECKEEEEEEEDDDD : "+logjob.isEnabled());
        //System.out.println("CHECKEEEEEEAAAAAAitem : "+item.isChecked());
    }

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
                    db.updateLogjobAndSync(originalLogjob, null, null, null, null, null);
                }
                listener.close();
                return true;
            case R.id.menu_delete:
                //db.deleteLogjobAndSync(logjob.getId());
                //listener.close();
                confirmDeleteAlertBuilder.show();
                return true;
            case R.id.menu_enabled:
                db.toggleEnabled(logjob, null);
                listener.onLogjobUpdated(logjob);
                prepareEnabledOption(item);
                return true;
            //case R.id.menu_category:
            //    showCategorySelector();
            //    return true;
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
    }

    /**
     * Save the current state in the database and schedule synchronization if needed.
     *
     * @param callback Observer which is called after save/synchronization
     */
    protected void saveLogjob(@Nullable ICallback callback) {
        Log.d(getClass().getSimpleName(), "saveData()");
        String newTitle = getTitle();
        String newNextURL = getNextURL();
        String newToken = getToken();
        String newDevicename = getDevicename();
        System.out.println("newtitle : "+newTitle);
        if(logjob.getTitle().equals(newTitle) &&
                logjob.getNextURL().equals(newNextURL) &&
                logjob.getToken().equals(newToken) &&
                logjob.getDeviceName().equals(newDevicename)) {
            Log.v(getClass().getSimpleName(), "... not saving, since nothing has changed");
        } else {
            System.out.println("====== update logjob");
            logjob = db.updateLogjobAndSync(logjob, newTitle, newToken, newNextURL, newDevicename , callback);
            //System.out.println("AFFFFFFTTTTTTEEERRRRR : "+logjob);
            listener.onLogjobUpdated(logjob);
        }
    }

    /**
     * Opens a dialog in order to chose a category
     */
    /*private void showCategorySelector() {
        final String fragmentId = "fragment_category";
        FragmentManager manager = getFragmentManager();
        Fragment frag = manager.findFragmentByTag(fragmentId);
        if (frag != null) {
            manager.beginTransaction().remove(frag).commit();
        }
        Bundle arguments = new Bundle();
        arguments.putString(CategoryDialogFragment.PARAM_CATEGORY, logjob.getCategory());
        CategoryDialogFragment categoryFragment = new CategoryDialogFragment();
        categoryFragment.setArguments(arguments);
        categoryFragment.setTargetFragment(this, 0);
        categoryFragment.show(manager, fragmentId);
    }*/

    /*@Override
    public void onCategoryChosen(String category) {
        db.setCategory(logjob, category, null);
        listener.onLogjobUpdated(logjob);
    }*/

    public static EditLogjobFragment newInstance(long logjobId) {
        EditLogjobFragment f = new EditLogjobFragment();
        Bundle b = new Bundle();
        b.putLong(PARAM_NOTE_ID, logjobId);
        f.setArguments(b);
        return f;
    }

    public static EditLogjobFragment newInstanceWithNewNote(DBLogjob newLogjob) {
        EditLogjobFragment f = new EditLogjobFragment();
        Bundle b = new Bundle();
        b.putSerializable(PARAM_NEWNOTE, newLogjob);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ButterKnife.bind(this, getView());

        if (logjob.getTitle().isEmpty()) {
        //    getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        // workaround for issue yydcdut/RxMarkdown#41
        //logjob.setContent(logjob.getTitle().replace("\r\n", "\n"));

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

        List<DBSession> sessionList = db.getSessions();
        List<String> ent = new ArrayList<>();
        List<String> val = new ArrayList<>();
        for (DBSession session : sessionList) {
            ent.add(session.getName());
            val.add(session.getName());
        }

        editSessionList = (ListPreference) this.findPreference("sessionList");

        if (ent.size() > 0) {
            CharSequence[] entcs = ent.toArray(new CharSequence[ent.size()]);
            CharSequence[] valcs = val.toArray(new CharSequence[val.size()]);
            editSessionList.setEntries(entcs);
            editSessionList.setEntryValues(valcs);
        }
        else {
            getPreferenceScreen().removePreference(editSessionList);
        }
        //System.out.println("KKKKKKKKK "+editTitle.getNegativeButtonText());
        //editTitle.setText(logjob.getTitle());
        //editTitle.setEnabled(true);

        /*RxMarkdown.live(editTitle)
                .config(MarkDownUtil.getMarkDownConfiguration(getActivity().getApplicationContext()).build())
                .factory(EditFactory.create())
                .intoObservable()
                .subscribe(new Subscriber<CharSequence>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onNext(CharSequence charSequence) {
                        editTitle.setText(charSequence, TextView.BufferType.SPANNABLE);
                    }
                });*/
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

}
