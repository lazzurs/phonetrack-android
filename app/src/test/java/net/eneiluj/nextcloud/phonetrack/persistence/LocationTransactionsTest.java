package net.eneiluj.nextcloud.phonetrack.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.location.Location;
import android.location.LocationManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjobLocation;
import net.eneiluj.nextcloud.phonetrack.util.CorrectingLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * The multi-statement writes on the positions table are each one transaction: one commit per
 * position or per upload request, and all or nothing when a statement fails.
 */
@RunWith(AndroidJUnit4.class)
public class LocationTransactionsTest {

    private PhoneTrackSQLiteOpenHelper db;
    private long logjobId;

    @Before
    public void setUp() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        Context context = ApplicationProvider.getApplicationContext();
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        logjobId = db.addLogjob(new DBLogjob(0, "custom", "https://example.org/log", "", "",
                60, 0, 50, false, false, false, 60, false, false, 3, null, null, false));
    }

    @After
    public void tearDown() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
    }

    private static CorrectingLocation location(long timeMs) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(53.35);
        location.setLongitude(-6.26);
        location.setTime(timeMs);
        return new CorrectingLocation(location);
    }

    /** 5 positions of the current run and 5 from before the last "reset current run". */
    private List<Long> fiveCurrentAndFiveOlder() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            db.addLocation(logjobId, location(now + i * 1000L), 80.0);
            db.addLocation(new DBLogjobLocation(0, logjobId, 53.0, -6.0, now / 1000 - 3600 + i,
                    null, null, null, null, null, 70.0, "test", false, false));
        }
        List<Long> ids = new ArrayList<>();
        for (DBLogjobLocation loc : db.getLocationsToSyncOfLogjob(logjobId)) {
            ids.add(loc.getId());
        }
        assertEquals(10, ids.size());
        return ids;
    }

    private long count(String where) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM LOCATIONS WHERE LOGJOBID = ? AND " + where,
                new String[]{String.valueOf(logjobId)})) {
            assertTrue(c.moveToFirst());
            return c.getLong(0);
        }
    }

    /** Make the next write on LOGJOBS fail, as a full disk or I/O error would. */
    private void failNext(String operation) {
        db.getWritableDatabase().execSQL("CREATE TRIGGER fail_" + operation + " BEFORE " + operation
                + " ON LOGJOBS BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");
    }

    private void stopFailing(String operation) {
        db.getWritableDatabase().execSQL("DROP TRIGGER fail_" + operation);
    }

    @Test
    public void markSyncedKeepsTheCurrentRunAndDeletesTheRest() {
        List<Long> ids = fiveCurrentAndFiveOlder();
        DBLogjob logjob = db.getLogjob(logjobId);
        long before = System.currentTimeMillis() / 1000;

        db.markSynced(logjob, ids);

        // the current run is still shown on the map and in the info dialog
        assertEquals(5, count("1 = 1"));
        assertEquals(5, count("SYNCED = 1 AND CURRENTRUN = 1"));
        assertEquals(0, db.getLogjobLocationNotSyncedCount(logjobId));
        assertNull(db.getPendingUploadStats(logjobId));
        assertEquals(13, db.getNbSync(logjobId));
        assertEquals(13, logjob.getNbSync());
        assertTrue(db.getLastSyncTimestamp(logjobId) >= before);
    }

    @Test
    public void markSyncedChangesNothingWhenItFails() {
        List<Long> ids = fiveCurrentAndFiveOlder();
        DBLogjob logjob = db.getLogjob(logjobId);
        failNext("UPDATE");
        try {
            db.markSynced(logjob, ids);
            fail("the failure was swallowed");
        } catch (SQLException expected) {
            // rolled back
        } finally {
            stopFailing("UPDATE");
        }

        assertEquals(10, count("SYNCED = 0"));
        assertEquals(10, db.getLogjobLocationNotSyncedCount(logjobId));
        assertEquals(3, db.getNbSync(logjobId));
        assertEquals(3, logjob.getNbSync());
        assertEquals(0, db.getLastSyncTimestamp(logjobId));
    }

    @Test
    public void markSyncedAddsToTheCountInTheDatabase() {
        List<Long> ids = fiveCurrentAndFiveOlder();
        // read before the user reset the current run in the info dialog
        DBLogjob stale = db.getLogjob(logjobId);
        db.resetLogjobCurrentRun(logjobId);

        db.markSynced(stale, ids.subList(0, 2));

        assertEquals(2, db.getNbSync(logjobId));
    }

    @Test
    public void markSyncedIgnoresPositionsThatAreGone() {
        List<Long> ids = fiveCurrentAndFiveOlder();
        db.deleteLocation(ids.get(0));

        db.markSynced(db.getLogjob(logjobId), ids);

        assertEquals(0, db.getLogjobLocationNotSyncedCount(logjobId));
    }

    @Test
    public void storedPositionAndLastPositionTimeAreOneWrite() {
        failNext("UPDATE");
        try {
            db.addLocation(logjobId, location(1_700_000_000_000L), 80.0);
            fail("the failure was swallowed");
        } catch (SQLException expected) {
            // rolled back
        } finally {
            stopFailing("UPDATE");
        }
        assertEquals(0, count("1 = 1"));

        db.addLocation(logjobId, location(1_700_000_000_000L), 80.0);
        assertEquals(1, count("SYNCED = 0 AND CURRENTRUN = 1"));
        assertEquals(1_700_000_000L, db.getLastLocTimestamp(logjobId));
    }

    @Test
    public void resetCurrentRunIsAllOrNothing() {
        fiveCurrentAndFiveOlder();
        List<Long> currentRun = new ArrayList<>();
        for (DBLogjobLocation loc : db.getCurrentRunLocationsOfLogjob(logjobId)) {
            currentRun.add(loc.getId());
        }
        db.markSynced(db.getLogjob(logjobId), currentRun.subList(0, 4));
        long synced = count("SYNCED = 1");
        assertEquals(4, synced);

        failNext("UPDATE");
        try {
            db.resetLogjobCurrentRun(logjobId);
            fail("the failure was swallowed");
        } catch (SQLException expected) {
            // rolled back
        } finally {
            stopFailing("UPDATE");
        }
        assertEquals(synced, count("SYNCED = 1"));
        assertEquals(7, db.getNbSync(logjobId));

        db.resetLogjobCurrentRun(logjobId);
        // synced positions are dropped, unsent ones kept for upload but no longer "current run"
        assertEquals(0, count("SYNCED = 1"));
        assertEquals(6, db.getLogjobLocationNotSyncedCount(logjobId));
        assertEquals(0, count("CURRENTRUN = 1"));
        assertEquals(0, db.getNbSync(logjobId));
    }

    @Test
    public void deleteLogjobIsAllOrNothing() {
        fiveCurrentAndFiveOlder();
        failNext("DELETE");
        try {
            db.deleteLogjob(logjobId);
            fail("the failure was swallowed");
        } catch (SQLException expected) {
            // rolled back
        } finally {
            stopFailing("DELETE");
        }
        assertNotNull(db.getLogjob(logjobId));
        assertEquals(10, db.getLogjobLocationNotSyncedCount(logjobId));

        db.deleteLogjob(logjobId);
        assertNull(db.getLogjob(logjobId));
        assertEquals(0, count("1 = 1"));
    }

    @Test
    public void pendingStatsOfOneLogjob() {
        long other = db.addLogjob(new DBLogjob(0, "other", "https://example.org/log", "", "",
                60, 0, 50, false, false, false, 60, false, false, 0, null, null, false));
        db.addLocation(other, location(1_600_000_000_000L), 80.0);
        assertNull(db.getPendingUploadStats(logjobId));

        db.addLocation(logjobId, location(1_700_000_100_000L), 80.0);
        db.addLocation(logjobId, location(1_700_000_000_000L), 80.0);

        long[] stats = db.getPendingUploadStats(logjobId);
        assertNotNull(stats);
        assertEquals(2, stats[0]);
        assertEquals(1_700_000_000L, stats[1]);
    }
}
