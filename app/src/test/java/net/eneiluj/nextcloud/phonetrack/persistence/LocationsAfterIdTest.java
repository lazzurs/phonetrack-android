package net.eneiluj.nextcloud.phonetrack.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.model.DBLogjobLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * The open map reads only the positions stored since its last read: the newest rows of the table,
 * through the primary key, not every position of the logjob.
 */
@RunWith(AndroidJUnit4.class)
public class LocationsAfterIdTest {

    private static final long START = 1_700_000_000L;

    private PhoneTrackSQLiteOpenHelper helper;
    private SQLiteDatabase db;

    @Before
    public void setUp() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        Context context = ApplicationProvider.getApplicationContext();
        helper = PhoneTrackSQLiteOpenHelper.getInstance(context);
        db = helper.getWritableDatabase();
    }

    @After
    public void tearDown() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
    }

    private long insertLocation(long logjobId, long time) {
        db.execSQL("INSERT INTO LOCATIONS (LOGJOBID, LAT, LON, TIME, ACCURACY, SYNCED, CURRENTRUN) "
                + "VALUES (?, 53.35, -6.26, ?, 8.0, 1, 1)", new Object[]{logjobId, time});
        try (Cursor c = db.rawQuery("SELECT last_insert_rowid()", null)) {
            assertTrue(c.moveToFirst());
            return c.getLong(0);
        }
    }

    private static String queryPlan(SQLiteDatabase db, String sql, String... args) {
        StringBuilder plan = new StringBuilder();
        try (Cursor c = db.rawQuery("EXPLAIN QUERY PLAN " + sql, args)) {
            int detail = c.getColumnIndexOrThrow("detail");
            while (c.moveToNext()) {
                plan.append(c.getString(detail)).append('\n');
            }
        }
        return plan.toString();
    }

    @Test
    public void returnsOnlyTheNewerPositionsOfTheLogjobInTimeOrder() {
        long beforeLastThree = 0;
        db.beginTransaction();
        try {
            for (int i = 0; i < 1000; i++) {
                long id = insertLocation(1, START + i * 60L);
                // another logjob logs at the same time
                insertLocation(2, START + i * 60L + 1);
                if (i == 996) {
                    beforeLastThree = id;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        List<DBLogjobLocation> newer = helper.getLocationsOfLogjobAfter(1, beforeLastThree);
        assertEquals(3, newer.size());
        assertEquals(START + 997 * 60L, newer.get(0).getTimestamp());
        assertEquals(START + 998 * 60L, newer.get(1).getTimestamp());
        assertEquals(START + 999 * 60L, newer.get(2).getTimestamp());
        for (DBLogjobLocation loc : newer) {
            assertEquals(1, loc.getLogjobId());
            assertTrue(loc.getId() > beforeLastThree);
        }

        long newest = newer.get(2).getId();
        assertTrue(helper.getLocationsOfLogjobAfter(1, newest).isEmpty());
        // one more stored: only that one is read
        insertLocation(1, START + 1000 * 60L);
        assertEquals(1, helper.getLocationsOfLogjobAfter(1, newest).size());
    }

    @Test
    public void readsTheNewestRowsByIdNotTheWholeLogjob() {
        String plan = queryPlan(db, "SELECT * FROM LOCATIONS WHERE "
                + PhoneTrackSQLiteOpenHelper.selection_logjob_after_id + " ORDER BY TIME ASC", "1", "500");
        assertTrue(plan, plan.contains("INTEGER PRIMARY KEY (rowid>?)"));
        assertFalse(plan, plan.contains(PhoneTrackSQLiteOpenHelper.index_locations_logjob));
    }
}
