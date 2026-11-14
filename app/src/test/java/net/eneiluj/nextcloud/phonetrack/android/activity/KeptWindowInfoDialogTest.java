package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Configuration;
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
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

/**
 * With "Keep uploaded positions on this phone" shorter than forever, the info dialog says that its
 * points, distance and duration only cover the kept window, for the log jobs it applies to.
 */
@RunWith(AndroidJUnit4.class)
public class KeptWindowInfoDialogTest {

    private static final String NOTE = "Only positions less than 30 days old are counted";

    private final Context context = ApplicationProvider.getApplicationContext();
    private ActivityController<LogjobsListViewActivity> controller;
    private PhoneTrackSQLiteOpenHelper db;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
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

    private void keep(String days) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.pref_key_keep_uploaded), days).commit();
    }

    private long logjob(String url, String token, String deviceName) {
        long id = db.addLogjob(new DBLogjob(0, "walks", url, token, deviceName,
                60, 0, 50, false, false, false, 60, false, false, 0, null, null, false));
        for (int i = 0; i < 2; i++) {
            Location location = new Location(LocationManager.GPS_PROVIDER);
            location.setLatitude(53.35 + i * 0.01);
            location.setLongitude(-6.26);
            location.setTime(System.currentTimeMillis() - (2 - i) * 60_000L);
            db.addLocation(id, new CorrectingLocation(location), 80.0);
        }
        return id;
    }

    private String pointsText(long logjobId) {
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        controller.get().onLogjobInfoButtonClick(db.getLogjob(logjobId));
        ShadowLooper.idleMainLooper();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        TextView points = dialog.findViewById(R.id.infoNbPointsText);
        return points.getText().toString();
    }

    @Test
    public void foreverAddsNothing() {
        long id = logjob("https://cloud.example", "tok", "dev");
        String text = pointsText(id);
        assertTrue(text, text.startsWith("2 points"));
        assertFalse(text, text.contains("Only positions"));
    }

    @Test
    public void shorterWindowIsMentionedForAPhoneTrackLogjob() {
        keep("30");
        long id = logjob("https://cloud.example", "tok", "dev");
        String text = pointsText(id);
        assertTrue(text, text.startsWith("2 points"));
        assertTrue(text, text.contains(NOTE));
    }

    @Test
    public void customLogjobKeepsEverythingSoNothingIsMentioned() {
        keep("30");
        long id = logjob("https://example.org/log", "", "");
        String text = pointsText(id);
        assertFalse(text, text.contains("Only positions"));
    }
}
