package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.appcompat.widget.Toolbar;

import android.os.Environment;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBSyslog;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.BackgroundTask;
import net.eneiluj.nextcloud.phonetrack.util.EdgeToEdgeUtil;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class SyslogManagerActivity extends AppCompatActivity {

    private static final String TAG = SyslogManagerActivity.class.getSimpleName();

    private static String contentToExport = "";

    private PhoneTrackSQLiteOpenHelper db;

    Toolbar toolbar;
    TextView textView;
    ScrollView scrollView;
    MenuItem toggleSyslogItem;

    private SharedPreferences prefs;

    private final SimpleDateFormat sdfComplete = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss (Z)", Locale.ROOT);

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_syslog, menu);
        toggleSyslogItem = menu.findItem(R.id.menu_toggle_logs);
        toggleSyslogItem.setChecked(SystemLogger.getEnabled());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_share_logs) {
            shareLogs();
            return true;
        } else if (itemId == R.id.menu_save_logs) {
            // The Storage Access Framework picker grants access to the chosen file,
            // no storage permission needed (and WRITE_EXTERNAL_STORAGE is not declared).
            saveLogs();
            return true;
        } else if (itemId == R.id.menu_toggle_logs) {
            boolean isSyslogEnabled = SystemLogger.getEnabled();
            // lines logged just before the log was switched off may still wait in memory
            SystemLogger.flush();
            if (!isSyslogEnabled && !db.getSyslogs(null, null).isEmpty()) {
                // switching logging on wipes the previous entries: ask first
                new android.app.AlertDialog.Builder(new android.view.ContextThemeWrapper(this, R.style.AppThemeDialog))
                        .setTitle(R.string.syslog_clear_title)
                        .setMessage(R.string.syslog_clear_message)
                        .setPositiveButton(R.string.syslog_clear_confirm, (dialog, which) -> {
                            SystemLogger.clear(this);
                            textView.setText("");
                            SystemLogger.setEnabled(true);
                            toggleSyslogItem.setChecked(true);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            }
            SystemLogger.setEnabled(!isSyslogEnabled);
            toggleSyslogItem.setChecked(!isSyslogEnabled);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final ActivityResultLauncher<Intent> saveFileLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            Log.d(TAG, "saveFileLauncher result, is " + result.getResultCode() + " == " + RESULT_OK + " ?");
                            Intent data = result.getData();
                            if (result.getResultCode() == RESULT_OK && data != null) {
                                Uri savedFile = data.getData();
                                Log.v(TAG, "Save to " + savedFile);
                                saveToFileUri(contentToExport, savedFile);
                            }
                        }
                    });

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "[ACT RESULT]");
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = PhoneTrackSQLiteOpenHelper.getInstance(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.activity_syslog);
        EdgeToEdgeUtil.enable(this);

        toolbar = findViewById(R.id.syslog_toolbar);
        textView = findViewById(R.id.syslog_text);
        scrollView = findViewById(R.id.syslog_scrollview);

        updateSyslogContent();
        setupActionBar();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void setupActionBar() {
        Log.i(TAG, "[setupactionbar]");
        setSupportActionBar(toolbar);
    }

    public void onResume(){
        Log.i(TAG, "[onResume begin]");
        super.onResume();
        // while the log is on screen its lines are stored and shown at once
        SystemLogger.setListener(syslogListener);
        Log.i(TAG, "[onResume end]");
    }

    public void onPause(){
        Log.i(TAG, "[onPause begin]");
        super.onPause();
        SystemLogger.removeListener(syslogListener);
        Log.i(TAG, "[onPause end]");
    }

    /**
     * The log can be long: read it and build the text off the main thread, then show it at once
     * (appending line by line also re-laid out the TextView and posted a scroll per line).
     */
    private void updateSyslogContent() {
        new BackgroundTask<Void, CharSequence>() {
            @Override
            protected CharSequence doInBackground(Void... voids) {
                SystemLogger.flush();
                SpannableStringBuilder content = new SpannableStringBuilder();
                for (DBSyslog sl : db.getSyslogs(null, null)) {
                    content.append(formatLine(sl.getMessage(), sl.getTimestamp()));
                }
                return content;
            }

            @Override
            protected void onPostExecute(CharSequence content) {
                if (isDestroyed()) {
                    return;
                }
                textView.setText(content);
                scrollToEnd();
            }
        }.executeInParallel();
    }

    private CharSequence formatLine(String message, long timestamp) {
        synchronized (sdfComplete) {
            return Html.fromHtml("<b>[" + sdfComplete.format(timestamp * 1000) + "]</b> " + message + "<br/>",
                    Html.FROM_HTML_MODE_LEGACY);
        }
    }

    private void scrollToEnd() {
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void shareLogs() {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "phonetrack logs");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, getSaveContent());
        startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_share_syslogs)));
    }

    private String getSaveContent() {
        String content = "";

        // get settings
        String providersPref = prefs.getString(getString(R.string.pref_key_providers), "1");
        String providerName = "unknown";
        switch (providersPref != null ? providersPref : "") {
            case "1":
                providerName = "GPS";
                break;
            case "2":
                providerName = "Network";
                break;
            case "3":
                providerName = "GPS and Network";
                break;
        }
        content += "Location provider: " + providerName + "\n";
        boolean respectPowerSaveMode = prefs.getBoolean(getString(R.string.pref_key_power_saving_awareness), false);
        content += "Respect power saving state: " + respectPowerSaveMode + "\n";
        boolean respectAirplaneMode = prefs.getBoolean(getString(R.string.pref_key_offline_mode_awareness), false);
        content += "Respect airplane mode state: " + respectAirplaneMode + "\n";
        boolean searchBackoff = prefs.getBoolean(getString(R.string.pref_key_search_backoff), true);
        content += "Search less often without a position: " + searchBackoff + "\n";
        content += "\n";

        // get logjobs
        List<DBLogjob> logjobs = db.getLogjobs();
        for (DBLogjob lj: logjobs) {
            content += lj.toPrivateString() + "\n";
            content += "\n";
        }

        content += "\n";

        // get all syslogs, including the ones still waiting to be stored
        SystemLogger.flush();
        List<DBSyslog> syslogs = db.getSyslogs(null, null);
        for (DBSyslog sl: syslogs) {
            content += "[" + sdfComplete.format(sl.getTimestamp() * 1000) + "] " + sl.getMessage() + "\n";
        }
        return content;
    }

    private void saveLogs() {
        Log.d(TAG, "Saving logs!!!!");
        contentToExport = getSaveContent();
        String fileName = "phonetrack logs.txt";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when your app creates the document.
        //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);

        saveFileLauncher.launch(intent);
    }

    private void saveToFileUri(String content, Uri fileUri) {
        try {
            OutputStream fOut = getContentResolver().openOutputStream(fileUri);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            myOutWriter.append(content);
            myOutWriter.close();
            if (fOut != null) {
                fOut.flush();
                fOut.close();
            }
            String savedFileName = fileUri.getLastPathSegment();
            if (savedFileName != null) {
                savedFileName = savedFileName.replace(
                        Environment.getExternalStorageDirectory().toString(),
                        ""
                );
            }
            showToast(
                getString(
                    R.string.syslog_saved_success,
                    savedFileName
                ),
                Toast.LENGTH_LONG
            );
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
            showToast(e.toString(), Toast.LENGTH_LONG);
        }
    }

    /** Lines stored while the log is on screen, handed over on the logger's thread. */
    private final SystemLogger.Listener syslogListener = new SystemLogger.Listener() {
        @Override
        public void onNewLines(@NonNull List<DBSyslog> lines) {
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                for (DBSyslog line : lines) {
                    textView.append(formatLine(line.getMessage(), line.getTimestamp()));
                }
                scrollToEnd();
            });
        }
    };

    private void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
