package net.eneiluj.nextcloud.phonetrack.persistence;

import static org.junit.Assert.assertEquals;
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
 * The number of positions waiting for upload, which the tracking notification shows after every
 * position: counted in the database once, then kept in memory, and always the same as a count of
 * the table after positions are stored, sent, deleted or reset, and in a new process.
 */
@RunWith(AndroidJUnit4.class)
public class PendingCountTest {

    private Context context;
    private PhoneTrackSQLiteOpenHelper db;
    private long logjobId;
    private long otherLogjobId;
    private long nextTimeMs = 1_700_000_000_000L;

    @Before
    public void setUp() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        context = ApplicationProvider.getApplicationContext();
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        logjobId = addLogjob("hike");
        otherLogjobId = addLogjob("car");
    }

    @After
    public void tearDown() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
    }

    private long addLogjob(String title) {
        return db.addLogjob(new DBLogjob(0, title, "https://example.org/log", "", "",
                60, 0, 50, false, false, false, 60, false, false, 3, null, null, false));
    }

    private void store(long ljId) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(53.35);
        location.setLongitude(-6.26);
        location.setTime(nextTimeMs);
        nextTimeMs += 60_000;
        db.addLocation(ljId, new CorrectingLocation(location), 80.0);
    }

    private long tableCount() {
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM LOCATIONS WHERE SYNCED = 0", null)) {
            c.moveToFirst();
            return c.getLong(0);
        }
    }

    private void assertCountRight() {
        assertEquals(tableCount(), db.getLocationNotSyncedCount());
    }

    private List<Long> pendingIds(long ljId) {
        List<Long> ids = new ArrayList<>();
        for (DBLogjobLocation loc : db.getLocationsToSyncOfLogjob(ljId)) {
            ids.add(loc.getId());
        }
        return ids;
    }

    @Test
    public void storingPositionsDoesNotCountTheTableAgain() {
        store(logjobId);
        assertEquals(1, db.getLocationNotSyncedCount());
        int queries = db.pendingCountQueries;

        for (int i = 0; i < 50; i++) {
            store(logjobId);
            store(otherLogjobId);
            // what the notification does after every position
            assertEquals(2 + 2 * i + 1, db.getLocationNotSyncedCount());
        }
        assertEquals(queries, db.pendingCountQueries);
        assertCountRight();
    }

    @Test
    public void staysRightThroughUploadsDeletesAndResets() {
        for (int i = 0; i < 6; i++) {
            store(logjobId);
            store(otherLogjobId);
        }
        assertCountRight();

        // an upload request accepted (current run: kept, marked sent)
        List<Long> ids = pendingIds(logjobId);
        db.markSynced(db.getLogjob(logjobId), ids.subList(0, 4));
        assertCountRight();
        // sent again (a retry after a lost answer): nothing changes
        db.markSynced(db.getLogjob(logjobId), ids.subList(0, 4));
        assertCountRight();
        store(logjobId);
        assertCountRight();

        // positions from before a reset, sent and so deleted
        db.resetLogjobCurrentRun(otherLogjobId);
        assertCountRight();
        db.markSynced(db.getLogjob(otherLogjobId), pendingIds(otherLogjobId).subList(0, 2));
        assertCountRight();

        db.addLocation(new DBLogjobLocation(0, logjobId, 53.0, -6.0, 1_690_000_000L,
                null, null, null, null, null, 70.0, "test", false, false));
        assertCountRight();
        db.addLocation(new DBLogjobLocation(0, logjobId, 53.0, -6.0, 1_690_000_060L,
                null, null, null, null, null, 70.0, "test", true, false));
        assertCountRight();
        db.deleteLocation(pendingIds(logjobId).get(0));
        assertCountRight();

        db.deleteLogjob(otherLogjobId);
        assertCountRight();
        store(logjobId);
        assertCountRight();
    }

    @Test
    public void newProcessCountsAgain() {
        for (int i = 0; i < 3; i++) {
            store(logjobId);
        }
        assertEquals(3, db.getLocationNotSyncedCount());

        // a new process: a new helper on the same database file
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        assertEquals(3, db.getLocationNotSyncedCount());
        assertEquals(1, db.pendingCountQueries);
        store(logjobId);
        assertEquals(4, db.getLocationNotSyncedCount());
        assertEquals(1, db.pendingCountQueries);
    }

    @Test
    public void positionNotStoredIsNotCounted() {
        store(logjobId);
        assertEquals(1, db.getLocationNotSyncedCount());

        // the logjob update in the same transaction fails: the position is not stored either
        db.getWritableDatabase().execSQL("CREATE TRIGGER fail_update BEFORE UPDATE ON LOGJOBS "
                + "BEGIN SELECT RAISE(ABORT, 'simulated failure'); END");
        try {
            store(logjobId);
            fail("the write should have failed");
        } catch (SQLException expected) {
            // as a full disk would
        }
        db.getWritableDatabase().execSQL("DROP TRIGGER fail_update");

        assertEquals(1, tableCount());
        assertCountRight();
    }
}
