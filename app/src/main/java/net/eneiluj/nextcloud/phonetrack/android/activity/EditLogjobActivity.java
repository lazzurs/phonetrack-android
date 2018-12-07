package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;

import android.widget.Toast;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.fragment.EditLogjobFragment;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;

public class EditLogjobActivity extends AppCompatActivity implements EditLogjobFragment.LogjobFragmentListener {

    public static final String PARAM_LOGJOB_ID = "logjobId";
    public static final String PARAM_TYPE = "type";

    private EditLogjobFragment fragment;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            launchLogjobFragment();
        } else {
            fragment = (EditLogjobFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(getClass().getSimpleName(), "onNewIntent: " + intent.getLongExtra(PARAM_LOGJOB_ID, 0));
        setIntent(intent);
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction().detach(fragment).commit();
            fragment = null;
        }
        launchLogjobFragment();
    }

    private long getLogjobId() {
        return getIntent().getLongExtra(PARAM_LOGJOB_ID, 0);
    }

    /**
     * Starts the logjob fragment for an existing logjob or a new logjob.
     * The actual behavior is triggered by the activity's intent.
     */
    private void launchLogjobFragment() {
        long logjobId = getLogjobId();
        if (logjobId > 0) {
            launchExistingLogjob(logjobId);
        } else {
            launchNewLogjob();
        }
    }

    /**
     * Starts a {@link EditLogjobFragment} for an existing logjob.
     *
     * @param logjobId ID of the existing logjob.
     */
    private void launchExistingLogjob(long logjobId) {
        // save state of the fragment in order to resume with the same logjob and originalLogjob
        Fragment.SavedState savedState = null;
        if (fragment != null) {
            savedState = getSupportFragmentManager().saveFragmentInstanceState(fragment);
        }
        fragment = EditLogjobFragment.newInstance(logjobId);
        if (savedState != null) {
            fragment.setInitialSavedState(savedState);
        }
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    /**
     * Starts the {@link EditLogjobFragment} with a new logjob.
     *
     */
    private void launchNewLogjob() {
        Intent intent = getIntent();

        DBLogjob newLogjob = new DBLogjob(0, "",  "https://yournextcloud.org", "supersessiontoken", "mydevname", 60, 5, 50, false, 0);

        String url = "";
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            url = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!newLogjob.setAttrFromUrl(url)) {
                showToast(getString(R.string.error_invalid_pt_url), Toast.LENGTH_LONG);
            }
        }

        fragment = EditLogjobFragment.newInstanceWithNewLogjob(newLogjob);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, fragment).commit();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.menu_logjob_activity, menu);
        //return super.onCreateOptionsMenu(menu);
    }*/

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                close();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /**
     * Send result and closes the Activity
     */
    public void close() {
        fragment.onCloseLogjob();
        finish();
    }

    @Override
    public void onLogjobUpdated(DBLogjob logjob) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(logjob.getTitle());
            actionBar.setSubtitle(logjob.getDeviceName());
        }
    }

    private void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}