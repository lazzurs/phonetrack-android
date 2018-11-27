package it.eneiluj.nextcloud.phonetrack.android.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.yydcdut.markdown.syntax.edit.EditFactory;
import com.yydcdut.rxmarkdown.RxMDEditText;
import com.yydcdut.rxmarkdown.RxMarkdown;

import butterknife.BindView;
import butterknife.ButterKnife;
import it.eneiluj.nextcloud.phonetrack.R;
import it.eneiluj.nextcloud.phonetrack.model.CloudSession;
import it.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import it.eneiluj.nextcloud.phonetrack.util.ICallback;
import it.eneiluj.nextcloud.phonetrack.util.MarkDownUtil;
import rx.Subscriber;

public class NoteEditFragment extends BaseNoteFragment {

    private static final String LOG_TAG_AUTOSAVE = "AutoSave";

    private static final long DELAY = 2000; // Wait for this time after typing before saving
    private static final long DELAY_AFTER_SYNC = 5000; // Wait for this time after saving before checking for next save

    private Handler handler;
    private boolean saveActive, unsavedEdit;

    //@BindView(R.id.editContent)
    //RxMDEditText editContent;
    EditTextPreference editContent;

    public static NoteEditFragment newInstance(long logjobId) {
        NoteEditFragment f = new NoteEditFragment();
        Bundle b = new Bundle();
        b.putLong(PARAM_NOTE_ID, logjobId);
        f.setArguments(b);
        return f;
    }

    public static NoteEditFragment newInstanceWithNewNote(DBLogjob newLogjob) {
        NoteEditFragment f = new NoteEditFragment();
        Bundle b = new Bundle();
        b.putSerializable(PARAM_NEWNOTE, newLogjob);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //inflater.inflate(R.layout.activity_edit, container, false);
        addPreferencesFromResource(R.xml.activity_edit);


        Preference titlePref = findPreference("title");
        titlePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                //do something
                System.out.println("LALA "+newValue);
                //EditTextPreference pref = (EditTextPreference) findPreference("title");
                preference.setSummary((CharSequence) newValue);
                //saveLogjob(null);
                return true;
            }

        });
        Preference nextURLPref = findPreference("nextURL");
        nextURLPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                                              Object newValue) {
                //EditTextPreference pref = (EditTextPreference) findPreference("nexturl");
                preference.setSummary((CharSequence) newValue);
                //saveLogjob(null);
                return true;
            }

        });


        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        //menu.findItem(R.id.menu_edit).setVisible(false);
        //menu.findItem(R.id.menu_preview).setVisible(true);
    }

    /*@Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_edit, container, false);
    }*/

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ButterKnife.bind(this, getView());

        if (logjob.getTitle().isEmpty()) {
            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        // workaround for issue yydcdut/RxMarkdown#41
        //logjob.setContent(logjob.getContent().replace("\r\n", "\n"));

        editContent = (EditTextPreference) this.findPreference("title");
        editContent.setText(logjob.getTitle());
        editContent.setSummary(logjob.getTitle());
        //System.out.println("KKKKKKKKK "+editContent.getNegativeButtonText());
        //editContent.setText(logjob.getTitle());
        //editContent.setEnabled(true);

        /*RxMarkdown.live(editContent)
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
                        editContent.setText(charSequence, TextView.BufferType.SPANNABLE);
                    }
                });*/
    }

    /*private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(final Editable s) {
            unsavedEdit = true;
            if (!saveActive) {
                handler.removeCallbacks(runAutoSave);
                handler.postDelayed(runAutoSave, DELAY);
            }
        }
    };*/

    /*@Override
    public void onResume() {
        super.onResume();
        editContent.addTextChangedListener(textWatcher);
    }

    @Override
    public void onPause() {
        super.onPause();
        editContent.removeTextChangedListener(textWatcher);
        cancelTimers();
    }*/

    /*private final Runnable runAutoSave = new Runnable() {
        @Override
        public void run() {
            if (unsavedEdit) {
                Log.d(LOG_TAG_AUTOSAVE, "runAutoSave: start AutoSave");
                autoSave();
            } else {
                Log.d(LOG_TAG_AUTOSAVE, "runAutoSave: nothing changed");
            }
        }
    };

    private void cancelTimers() {
        handler.removeCallbacks(runAutoSave);
    }*/

    /**
     * Gets the current content of the EditText field in the UI.
     *
     * @return String of the current content.
     */
    @Override
    protected String getContent() {
        return editContent.getText();
    }

    @Override
    protected void saveLogjob(@Nullable ICallback callback) {
        super.saveLogjob(callback);
        unsavedEdit = false;
    }

    /**
     * Saves the current changes and show the status in the ActionBar
     */
    /*private void autoSave() {
        Log.d(LOG_TAG_AUTOSAVE, "STARTAUTOSAVE");
        saveActive = true;
        saveLogjob(new ICallback() {
            @Override
            public void onFinish() {
                onSaved();
            }

            @Override
            public void onScheduled() {
                onSaved();
            }

            private void onSaved() {
                // AFTER SYNCHRONIZATION
                Log.d(LOG_TAG_AUTOSAVE, "FINISHED AUTOSAVE");
                saveActive = false;

                // AFTER "DELAY_AFTER_SYNC" SECONDS: allow next auto-save or start it directly
                handler.postDelayed(runAutoSave, DELAY_AFTER_SYNC);

            }
        });
    }*/
}
