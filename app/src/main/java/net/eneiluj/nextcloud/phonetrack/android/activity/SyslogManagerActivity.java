package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.appcompat.widget.Toolbar;

import android.os.Environment;
import android.text.Html;
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
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class SyslogManagerActivity extends AppCompatActivity {

    public static final String BROADCAST_TIMESTAMP = "net.eneiluj.nextcloud.phonetrack.broadcast.timestamp";
    public static final String BROADCAST_NEW_SYSLOG = "net.eneiluj.nextcloud.phonetrack.broadcast.new_syslog";
    public static final String BROADCAST_MESSAGE = "net.eneiluj.nextcloud.phonetrack.broadcast.message";

    private final static int PERMISSION_WRITE = 3;
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
            Log.d(TAG, "SAVEEEEEE");
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestWritePermissions();
            } else {
                saveLogs();
            }
            return true;
        } else if (itemId == R.id.menu_toggle_logs) {
            boolean isSyslogEnabled = SystemLogger.getEnabled();
            if (!isSyslogEnabled) {
                db.clearSyslog();
                textView.setText("");
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

        toolbar = findViewById(R.id.syslog_toolbar);
        textView = findViewById(R.id.syslog_text);
        scrollView = findViewById(R.id.syslog_scrollview);

        updateSyslogContent();
        setupActionBar();
    }

    private void requestWritePermissions() {
        ActivityCompat.requestPermissions(
                SyslogManagerActivity.this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                PERMISSION_WRITE
        );
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_WRITE) {
            if (grantResults.length > 0) {
                Log.d(TAG, "[permission STORAGE result] " + grantResults[0]);
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "write permission granted");
                    saveLogs();
                } else {
                    Log.e(TAG, "write permission refused");
                }
            }
        }
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
        registerBroadcastReceiver();
        Log.i(TAG, "[onResume end]");
    }

    public void onPause(){
        Log.i(TAG, "[onPause begin]");
        super.onPause();

        try {
            unregisterReceiver(mBroadcastReceiver);
        } catch (RuntimeException e) {
            if (LoggerService.DEBUG) { Log.d(TAG, "RECEIVER PROBLEM, let's ignore it..."); }
        }
        Log.i(TAG, "[onPause end]");
    }

    private void updateSyslogContent() {
        textView.setText("");
        List<DBSyslog> syslogs = db.getSyslogs(null, null);
        for (DBSyslog sl: syslogs) {
            addLine(sl.getMessage(), sl.getTimestamp());
        }
    }

    private void addLine(String message, long timestamp) {
        textView.append(
                Html.fromHtml("<b>[" + sdfComplete.format(timestamp * 1000) + "]</b> " + message + "<br/>")
        );
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
        content += "\n";

        // get logjobs
        List<DBLogjob> logjobs = db.getLogjobs();
        for (DBLogjob lj: logjobs) {
            content += lj.toPrivateString() + "\n";
            content += "\n";
        }

        content += "\n";

        // get all syslogs
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

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_NEW_SYSLOG);
        registerReceiver(mBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Broadcast receiver
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //if (LoggerService.DEBUG) { Log.e(TAG, "broadcast received " + intent); }
            if (intent == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case BROADCAST_NEW_SYSLOG:
                    long timestamp = intent.getLongExtra(BROADCAST_TIMESTAMP, 0);
                    String message = intent.getStringExtra(BROADCAST_MESSAGE);
                    if (LoggerService.DEBUG) { Log.d(TAG, "[inSyslog broadcast new syslog " + message + "]"); }
                    addLine(message, timestamp);
                    break;
                default:
                    Log.d(TAG, "Unknown intent action in mBroadcastReceiver::onReceive");
                    break;
            }
        }
    };

    private void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
