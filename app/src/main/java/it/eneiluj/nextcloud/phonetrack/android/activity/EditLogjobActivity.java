package it.eneiluj.nextcloud.phonetrack.android.activity;

import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import it.eneiluj.nextcloud.phonetrack.R;
import it.eneiluj.nextcloud.phonetrack.android.fragment.BaseNoteFragment;
//import it.eneiluj.nextcloud.phonetrack.android.fragment.NoteEditFragment;
//import it.eneiluj.nextcloud.phonetrack.android.fragment.NotePreviewFragment;
import it.eneiluj.nextcloud.phonetrack.model.Category;
import it.eneiluj.nextcloud.phonetrack.model.DBLogjob;

public class EditLogjobActivity extends AppCompatActivity implements BaseNoteFragment.NoteFragmentListener {

    public static final String PARAM_NOTE_ID = "noteId";
    public static final String PARAM_CATEGORY = "category";

    private BaseNoteFragment fragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            launchNoteFragment();
        } else {
            fragment = (BaseNoteFragment) getFragmentManager().findFragmentById(android.R.id.content);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(getClass().getSimpleName(), "onNewIntent: " + intent.getLongExtra(PARAM_NOTE_ID, 0));
        setIntent(intent);
        if (fragment != null) {
            getFragmentManager().beginTransaction().detach(fragment).commit();
            fragment = null;
        }
        launchNoteFragment();
    }

    private long getNoteId() {
        return getIntent().getLongExtra(PARAM_NOTE_ID, 0);
    }

    /**
     * Starts the logjob fragment for an existing logjob or a new logjob.
     * The actual behavior is triggered by the activity's intent.
     */
    private void launchNoteFragment() {
        long noteId = getNoteId();
        if (noteId > 0) {
            launchExistingLogjob(noteId);
        } else {
            launchNewLogjob();
        }
    }

    /**
     * Starts a {@link NoteEditFragment} or {@link NotePreviewFragment} for an existing logjob.
     * The type of fragment (view-mode) is chosen based on the user preferences.
     *
     * @param noteId ID of the existing logjob.
     */
    /*private void launchExistingLogjob(long noteId) {
        final String prefKeyNoteMode = getString(R.string.pref_key_note_mode);
        final String prefKeyLastMode = getString(R.string.pref_key_last_note_mode);
        final String prefValueEdit = getString(R.string.pref_value_mode_edit);
        final String prefValuePreview = getString(R.string.pref_value_mode_preview);
        final String prefValueLast = getString(R.string.pref_value_mode_last);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String mode = preferences.getString(prefKeyNoteMode, prefValueEdit);
        String lastMode = preferences.getString(prefKeyLastMode, prefValueEdit);
        boolean editMode = true;
        if (prefValuePreview.equals(mode) || (prefValueLast.equals(mode) && prefValuePreview.equals(lastMode))) {
            editMode = false;
        }
        launchExistingLogjob(noteId, editMode);
    }*/

    /**
     * Starts a {@link NoteEditFragment} or {@link NotePreviewFragment} for an existing logjob.
     *
     * @param logjobId ID of the existing logjob.
     * @param edit   View-mode of the fragment:
     *               <code>true</code> for {@link NoteEditFragment},
     *               <code>false</code> for {@link NotePreviewFragment}.
     */
    private void launchExistingLogjob(long logjobId) {
        // save state of the fragment in order to resume with the same logjob and originalNote
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = BaseNoteFragment.newInstance(logjobId);
        /*if (edit) {
            fragment = NoteEditFragment.newInstance(logjobId);
        } else {
            fragment = NotePreviewFragment.newInstance(logjobId);
        }*/
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    /**
     * Starts the {@link NoteEditFragment} with a new logjob.
     * Content ("share" functionality), category and favorite attribute can be preset.
     */
    private void launchNewLogjob() {
        Intent intent = getIntent();

        String category = null;
        boolean favorite = false;
        if (intent.hasExtra(PARAM_CATEGORY)) {
            Category categoryPreselection = (Category) intent.getSerializableExtra(PARAM_CATEGORY);
            category = categoryPreselection.category;
            favorite = categoryPreselection.favorite != null ? categoryPreselection.favorite : false;
        }

        String content = "";
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            content = intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        DBLogjob newLogjob = new DBLogjob(0, "empty",  "url", "to", "devname", false);
        fragment = BaseNoteFragment.newInstanceWithNewNote(newLogjob);
        getFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.menu_note_activity, menu);
        //return super.onCreateOptionsMenu(menu);
    }*/

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                close();
                return true;
            /*case R.id.menu_preview:
                launchExistingLogjob(getNoteId(), false);
                return true;
            case R.id.menu_edit:
                launchExistingLogjob(getNoteId(), true);
                return true;*/
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /**
     * Send result and closes the Activity
     */
    public void close() {
        /*SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final String prefKeyLastMode = getString(R.string.pref_key_last_note_mode);
        if (fragment instanceof NoteEditFragment) {
            preferences.edit().putString(prefKeyLastMode, getString(R.string.pref_value_mode_edit)).apply();
        } else {
            preferences.edit().putString(prefKeyLastMode, getString(R.string.pref_value_mode_preview)).apply();
        }*/
        fragment.onCloseNote();
        finish();
    }

    @Override
    public void onLogjobUpdated(DBLogjob logjob) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(logjob.getTitle());
            actionBar.setSubtitle(Long.toString(logjob.getId()));
        }
    }
}