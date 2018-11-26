package it.eneiluj.nextcloud.phonetrack.android.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import it.eneiluj.nextcloud.phonetrack.R;
import it.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import it.eneiluj.nextcloud.phonetrack.persistence.NoteSQLiteOpenHelper;
import it.eneiluj.nextcloud.phonetrack.util.ICallback;

//public abstract class BaseNoteFragment extends Fragment implements CategoryDialogFragment.CategoryDialogListener {
public abstract class BaseNoteFragment extends Fragment{

    public interface NoteFragmentListener {
        void close();

        void onLogjobUpdated(DBLogjob note);
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
                db.deleteLogjobAndSync(logjob.getId());
                listener.close();
                return true;
            case R.id.menu_favorite:
                db.toggleEnabled(logjob, null);
                listener.onLogjobUpdated(logjob);
                prepareEnabledOption(item);
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

    public void onCloseNote() {
        // TODO if all fields are empty (or just title/URL) : delete
        if (originalLogjob == null && getContent().isEmpty()) {
            //db.deleteNoteAndSync(logjob.getId());
        }
    }

    /**
     * Save the current state in the database and schedule synchronization if needed.
     *
     * @param callback Observer which is called after save/synchronization
     */
    protected void saveLogjob(@Nullable ICallback callback) {
        // TODO check if something has changed
        Log.d(getClass().getSimpleName(), "saveData()");
        String newContent = getContent();
        if(logjob.getTitle().equals(newContent)) {
            Log.v(getClass().getSimpleName(), "... not saving, since nothing has changed");
        } else {
            // TODO get field values
            logjob = db.updateLogjobAndSync(logjob, null, null, null, null , callback);
            listener.onLogjobUpdated(logjob);
        }
    }

    protected abstract String getContent();

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
}
