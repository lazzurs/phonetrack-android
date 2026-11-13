package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.service.WebTrackSync;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class LogjobDeletionTest {

    private PhoneTrackSQLiteOpenHelper db;
    private AppCompatActivity activity;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
        db = PhoneTrackSQLiteOpenHelper.getInstance(activity);
    }

    @After
    public void tearDown() {
        DbTestSupport.resetDatabase();
    }

    private DBLogjob logjob(boolean enabled, int unsent) {
        long id = db.addLogjob(new DBLogjob(0, "job", "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, false, false, 60, false, enabled, 0, null, null, false));
        for (int i = 0; i < unsent; i++) {
            Location location = new Location(LocationManager.GPS_PROVIDER);
            location.setLatitude(53.35);
            location.setLongitude(-6.26);
            location.setTime(System.currentTimeMillis());
            db.addLocation(id, new CorrectingLocation(location), 80.0);
        }
        return db.getLogjob(id);
    }

    @Test
    public void countsUnsentPositionsAcrossLogjobs() {
        DBLogjob a = logjob(false, 2);
        DBLogjob b = logjob(true, 3);
        assertEquals(5, LogjobDeletion.countUnsent(db, Arrays.asList(a, b)));
    }

    @Test
    public void confirmationMentionsUnsentPositionsAndNothingIsDeletedUntilConfirmed() {
        DBLogjob job = logjob(true, 37);
        AtomicInteger deleted = new AtomicInteger();

        LogjobDeletion.confirmAndDelete(activity, Collections.singletonList(job), deleted::incrementAndGet);

        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog);
        String message = ((TextView) dialog.findViewById(android.R.id.message)).getText().toString();
        assertTrue(message, message.contains("37 positions have not been uploaded"));
        assertNotNull(db.getLogjob(job.getId()));
        assertEquals(0, deleted.get());

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertNotNull(db.getLogjob(job.getId()));
        assertEquals(37, db.getLogjobLocationNotSyncedCount(job.getId()));
        assertEquals(0, deleted.get());
    }

    @Test
    public void confirmingDeletesAnActiveLogjobAndTellsTheService() {
        DBLogjob job = logjob(true, 1);
        DBLogjob other = logjob(false, 0);
        AtomicInteger deleted = new AtomicInteger();

        LogjobDeletion.confirmAndDelete(activity, Arrays.asList(job, other), deleted::incrementAndGet);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        String message = ((TextView) dialog.findViewById(android.R.id.message)).getText().toString();
        assertTrue(message, message.contains("Delete 2 log jobs?"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(android.os.Looper.getMainLooper()).idle();

        assertEquals(1, deleted.get());
        assertNull(db.getLogjob(job.getId()));
        assertNull(db.getLogjob(other.getId()));
        assertEquals(0, db.getLogjobLocationNotSyncedCount(job.getId()));

        long notified = 0;
        int services = 0;
        Intent intent;
        while ((intent = shadowOf((android.app.Application) activity.getApplicationContext()).getNextStartedService()) != null) {
            assertEquals(LoggerService.class.getName(), intent.getComponent().getClassName());
            assertTrue(intent.getBooleanExtra(LogjobsListViewActivity.UPDATED_LOGJOBS, false));
            if (intent.getLongExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, 0) == job.getId()) {
                notified++;
            }
            services++;
        }
        assertEquals(1, notified);
        assertEquals(2, services);
    }

    @Test
    public void uploadingADeletedLogjobDoesNotThrow() {
        long id = logjob(false, 0).getId();
        db.deleteLogjob(id);
        new WebTrackSync(activity).sync(id, false);
    }
}
