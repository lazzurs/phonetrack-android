package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.service.WebTrackSync;
import net.eneiluj.nextcloud.phonetrack.util.CorrectingLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Upload errors in the log job list: info dialog, pull to refresh without an account, summary. */
@RunWith(AndroidJUnit4.class)
public class UploadErrorDisplayTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private ActivityController<LogjobsListViewActivity> controller;
    private PhoneTrackSQLiteOpenHelper db;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        DbTestSupport.resetDatabase();
    }

    private long customLogjob(String title) {
        return db.addLogjob(new DBLogjob(0, title, "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, false, false, 60, false, false, 0, null, null, false));
    }

    private void addPosition(long logjobId) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(53.35);
        location.setLongitude(-6.26);
        location.setTime(System.currentTimeMillis());
        db.addLocation(logjobId, new CorrectingLocation(location), 80.0);
    }

    private LogjobsListViewActivity launch() {
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        return controller.get();
    }

    @Test
    public void infoDialogGivesTheAgeOfTheErrorNotOfTheLastPosition() {
        long id = customLogjob("custom");
        long now = System.currentTimeMillis() / 1000;
        db.setLastLocTimestamp(id, now - 3600);
        db.setLastSyncError(id, now - 60, "Could not connect to the server");
        db.setLastSyncError(id, now - 60, "Could not connect to the server");
        LogjobsListViewActivity activity = launch();

        activity.onLogjobInfoButtonClick(db.getLogjob(id));
        ShadowLooper.idleMainLooper();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        TextView error = dialog.findViewById(R.id.infoLastSyncErrText);
        String text = error.getText().toString();

        assertTrue(text, text.contains("00:01:0"));
        assertFalse(text, text.contains("01:00:0"));
        assertTrue(text, text.contains("Could not connect to the server"));
        assertTrue(text, text.contains("2 failed attempts in a row"));
        // in the user's locale, not "yyyy-MM-dd HH:mm:ss z"
        assertFalse(text, text.matches("(?s).*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*"));
    }

    @Test
    public void pullToRefreshUploadsWithoutANextcloudAccount() throws Exception {
        long id = customLogjob("custom");
        addPosition(id);
        LogjobsListViewActivity activity = launch();
        SwipeRefreshLayout layout = activity.findViewById(R.id.swiperefreshlayout);

        assertTrue(layout.isEnabled());

        Field listener = SwipeRefreshLayout.class.getDeclaredField("mListener");
        listener.setAccessible(true);
        ((SwipeRefreshLayout.OnRefreshListener) listener.get(layout)).onRefresh();
        List<WorkInfo> manual = WorkManager.getInstance(context).getWorkInfosForUniqueWork("websync-manual").get();
        assertEquals(1, manual.size());
    }

    @Test
    public void pullToRefreshStaysOffWithNothingToDo() {
        customLogjob("custom");
        LogjobsListViewActivity activity = launch();
        SwipeRefreshLayout layout = activity.findViewById(R.id.swiperefreshlayout);

        assertFalse(layout.isEnabled());
    }

    private void broadcastFailure(long id, String message) {
        Intent intent = new Intent(WebTrackSync.BROADCAST_SYNC_FAILED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, id);
        intent.putExtra(LoggerService.BROADCAST_ERROR_MESSAGE, message);
        context.sendBroadcast(intent);
    }

    private void broadcastRunDone() {
        Intent intent = new Intent(WebTrackSync.BROADCAST_SYNC_DONE);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
        ShadowLooper.idleMainLooper();
        // a new Snackbar replaces the one shown, once that one has slid out
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Text of the Snackbar shown last, or null */
    private static String snackbarText(LogjobsListViewActivity activity) {
        List<String> found = new ArrayList<>();
        collectSnackbarTexts(activity.getWindow().getDecorView(), found);
        return found.isEmpty() ? null : found.get(found.size() - 1);
    }

    private static void collectSnackbarTexts(View view, List<String> found) {
        if (view.getId() == com.google.android.material.R.id.snackbar_text && view instanceof TextView) {
            found.add(((TextView) view).getText().toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectSnackbarTexts(group.getChildAt(i), found);
            }
        }
    }

    @Test
    public void oneSummaryPerRunInsteadOfAToastPerLogjob() throws Exception {
        long hike = customLogjob("hike");
        long car = customLogjob("car");
        LogjobsListViewActivity activity = launch();
        Field field = LogjobsListViewActivity.class.getDeclaredField("uploadFailures");
        field.setAccessible(true);

        broadcastFailure(hike, "Could not connect to the server");
        ShadowLooper.idleMainLooper();
        // nothing shown until the run is over
        assertEquals(1, ((java.util.Map<?, ?>) field.get(activity)).size());
        assertNull(snackbarText(activity));
        broadcastRunDone();
        assertTrue(((java.util.Map<?, ?>) field.get(activity)).isEmpty());
        assertEquals("Upload failed for ‘hike’: Could not connect to the server", snackbarText(activity));

        broadcastFailure(hike, "Could not connect to the server");
        broadcastFailure(car, "The server refused the login (HTTP 401)");
        ShadowLooper.idleMainLooper();
        assertEquals(2, ((java.util.Map<?, ?>) field.get(activity)).size());
        broadcastRunDone();
        assertTrue(((java.util.Map<?, ?>) field.get(activity)).isEmpty());
        assertEquals("Upload failed for 2 log jobs", snackbarText(activity));
    }
}
