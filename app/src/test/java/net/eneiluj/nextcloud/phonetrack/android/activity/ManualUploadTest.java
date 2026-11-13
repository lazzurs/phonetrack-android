package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

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
import net.eneiluj.nextcloud.phonetrack.util.CorrectingLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;
import java.util.List;

/** Pull-to-refresh uploads the points that "send points by bunch" holds back. */
@RunWith(AndroidJUnit4.class)
public class ManualUploadTest {

    private final Context context = ApplicationProvider.getApplicationContext();
    private ActivityController<LogjobsListViewActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
                new Configuration.Builder().setExecutor(new SynchronousExecutor()).build());
    }

    @After
    public void tearDown() {
        controller.destroy();
        DbTestSupport.resetDatabase();
    }

    @Test
    public void pullToRefreshQueuesAForcedUpload() throws Exception {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.pref_key_group_sync), "10").commit();
        PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        long id = db.addLogjob(new DBLogjob(0, "custom", "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, false, false, 60, false, false, 0, null, null, false));
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setTime(System.currentTimeMillis());
        db.addLocation(id, new CorrectingLocation(location), 80.0);

        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        SwipeRefreshLayout layout = controller.get().findViewById(R.id.swiperefreshlayout);
        Field listener = SwipeRefreshLayout.class.getDeclaredField("mListener");
        listener.setAccessible(true);
        ((SwipeRefreshLayout.OnRefreshListener) listener.get(layout)).onRefresh();

        WorkManager workManager = WorkManager.getInstance(context);
        // the only upload queued is the forced one (see UploadSchedulerTest for what it sends)
        List<WorkInfo> manual = workManager.getWorkInfosForUniqueWork("websync-manual").get();
        assertEquals(1, manual.size());
        assertEquals(WorkInfo.State.ENQUEUED, manual.get(0).getState());
        assertTrue(workManager.getWorkInfosForUniqueWork("websync-all").get().isEmpty());
    }
}
