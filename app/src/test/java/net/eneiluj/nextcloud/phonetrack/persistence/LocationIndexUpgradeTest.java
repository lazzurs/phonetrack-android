package net.eneiluj.nextcloud.phonetrack.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Version 21 adds indexes to the positions table. Upgrading a version 20 database must keep the
 * log jobs, sessions and positions (unsent ones and the current run shown on the map), and
 * running the upgrade twice must change nothing.
 */
@RunWith(AndroidJUnit4.class)
public class LocationIndexUpgradeTest {

    private static final String DB_NAME = "NEXTCLOUD_PHONETRACK";

    private Context context;

    @Before
    public void setUp() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_NAME);
    }

    @After
    public void tearDown() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        context.deleteDatabase(DB_NAME);
    }

    /** The schema of version 20, as onCreate made it, with some data in it. */
    private void createVersion20Database() {
        File file = context.getDatabasePath(DB_NAME);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            db.execSQL("CREATE TABLE SESSIONS ( ID INTEGER PRIMARY KEY AUTOINCREMENT, NAME TEXT, NEXTURL TEXT, "
                    + "PUBLICTOKEN TEXT, ISFROMSHARE INTEGER DEFAULT 0, ISPUBLIC INTEGER DEFAULT 1, TOKEN TEXT)");
            db.execSQL("CREATE TABLE LOGJOBS ( ID INTEGER PRIMARY KEY AUTOINCREMENT, TITLE TEXT, URL TEXT, "
                    + "DEVICENAME TEXT, MINTIME INTEGER, MINDISTANCE INTEGER, MINACCURACY INTEGER, "
                    + "KEEPGPSON INTEGER DEFAULT 0, POST INTEGER DEFAULT 0, JSON INTEGER DEFAULT 0, "
                    + "ENABLED INTEGER DEFAULT 0, NBSYNC INTEGER DEFAULT 0, LASTSYNC INTEGER DEFAULT 0, "
                    + "LASTLOC INTEGER DEFAULT 0, LASTACTIVATIONSYSTEM INTEGER DEFAULT 0, "
                    + "LASTACTIVATIONGPS INTEGER DEFAULT 0, LASTSYNCERRTIME INTEGER DEFAULT 0, "
                    + "LASTSYNCERR TEXT, TOKEN TEXT, LOGIN TEXT DEFAULT NULL, PASSWORD TEXT DEFAULT NULL, "
                    + "USESIGMOTION INTEGER DEFAULT 0,USESIGMOTIONMIXED INTEGER DEFAULT 0,LOCTIMEOUT INTEGER)");
            db.execSQL("CREATE TABLE LOCATIONS ( ID INTEGER PRIMARY KEY AUTOINCREMENT, LOGJOBID INTEGER, "
                    + "LAT FLOAT, LON FLOAT, TIME INTEGER, BEARING FLOAT, ALTITUDE FLOAT, SPEED FLOAT, "
                    + "ACCURACY FLOAT, SATELLITES INTEGER, USERAGENT TEXT, SYNCED INTEGER DEFAULT 0, "
                    + "CURRENTRUN INTEGER DEFAULT 0, BATTERY FLOAT)");
            db.execSQL("CREATE TABLE SYSLOG ( ID INTEGER PRIMARY KEY AUTOINCREMENT, TAG VARCHAR(64), "
                    + "MESSAGE TEXT, LEVEL VARCHAR(1), TIMESTAMP INTEGER)");
            db.execSQL("CREATE INDEX IF NOT EXISTS SESSIONS_TOKEN_idx ON SESSIONS(TOKEN)");
            db.execSQL("CREATE INDEX IF NOT EXISTS LOGJOBS_TOKEN_idx ON LOGJOBS(TOKEN)");

            db.execSQL("INSERT INTO SESSIONS (ID, NAME, NEXTURL, TOKEN) VALUES (1, 'walks', 'https://cloud.example', 'stok')");
            db.execSQL("INSERT INTO LOGJOBS (ID, TITLE, URL, DEVICENAME, TOKEN, MINTIME, MINDISTANCE, MINACCURACY, "
                    + "ENABLED, NBSYNC, LASTSYNC, LASTLOC) VALUES "
                    + "(1, 'phone', 'https://cloud.example', 'pixel', 'tok', 60, 0, 50, 1, 2, 1700000000, 1700000600)");
            db.execSQL("INSERT INTO LOGJOBS (ID, TITLE, URL, DEVICENAME, TOKEN, MINTIME, MINDISTANCE, MINACCURACY) "
                    + "VALUES (2, 'custom', 'https://example.org/log', '', '', 120, 10, 30)");
            // logjob 1: two synced points of the current run (shown on the map), two unsent
            // points of the current run, three unsent points from before the last reset
            insertLocation(db, 1, 1700000000, 1, 1);
            insertLocation(db, 1, 1700000060, 1, 1);
            insertLocation(db, 1, 1700000120, 0, 1);
            insertLocation(db, 1, 1700000180, 0, 1);
            insertLocation(db, 1, 1690000000, 0, 0);
            insertLocation(db, 1, 1690000060, 0, 0);
            insertLocation(db, 1, 1690000120, 0, 0);
            // logjob 2: one unsent point
            insertLocation(db, 2, 1700000300, 0, 1);
            // a logjob deleted while the logger was storing a point
            insertLocation(db, 99, 1700000400, 0, 1);
            insertLocation(db, 99, 1700000460, 0, 1);
            db.setVersion(20);
        }
    }

    private static void insertLocation(SQLiteDatabase db, long logjobId, long time, int synced, int currentRun) {
        db.execSQL("INSERT INTO LOCATIONS (LOGJOBID, LAT, LON, TIME, ALTITUDE, ACCURACY, SATELLITES, "
                        + "USERAGENT, SYNCED, CURRENTRUN, BATTERY) VALUES (?, 53.35, -6.26, ?, 12.5, 8.0, 9, "
                        + "'PhoneTrack/0.3.6', ?, ?, 77.0)",
                new Object[]{logjobId, time, synced, currentRun});
    }

    private PhoneTrackSQLiteOpenHelper openWithHelper() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        PhoneTrackSQLiteOpenHelper helper = PhoneTrackSQLiteOpenHelper.getInstance(context);
        // opening runs onUpgrade
        helper.getWritableDatabase();
        return helper;
    }

    private static long count(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            assertTrue(c.moveToFirst());
            return c.getLong(0);
        }
    }

    private static List<String> locationIndexes(SQLiteDatabase db) {
        List<String> result = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT name, sql FROM sqlite_master WHERE type = 'index' "
                + "AND tbl_name = 'LOCATIONS' ORDER BY name", null)) {
            while (c.moveToNext()) {
                result.add(c.getString(0) + ": " + c.getString(1));
            }
        }
        return result;
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

    private static void assertUsesPendingIndex(SQLiteDatabase db, String sql, String... args) {
        String plan = queryPlan(db, sql, args);
        assertTrue(plan, plan.contains("INDEX " + PhoneTrackSQLiteOpenHelper.index_locations_pending));
        assertFalse(plan, plan.contains("TEMP B-TREE"));
    }

    private void assertDataKept(PhoneTrackSQLiteOpenHelper helper) {
        DBSession session = helper.getSession(1);
        assertNotNull(session);
        assertEquals("walks", session.getName());
        assertEquals("stok", session.getToken());

        DBLogjob phone = helper.getLogjob(1);
        assertNotNull(phone);
        assertEquals("phone", phone.getTitle());
        assertEquals("pixel", phone.getDeviceName());
        assertTrue(phone.isEnabled());
        assertEquals(2, phone.getNbSync());
        assertEquals(1700000000, helper.getLastSyncTimestamp(1));
        assertEquals(1700000600, helper.getLastLocTimestamp(1));
        DBLogjob custom = helper.getLogjob(2);
        assertNotNull(custom);
        assertEquals("https://example.org/log", custom.getUrl());
        assertEquals(120, custom.getMinTime());

        // every position of the existing logjobs is still there, unsent ones and the current run
        assertEquals(7, helper.getLocationsOfLogjob(1).size());
        assertEquals(4, helper.getCurrentRunLocationsOfLogjob(1).size());
        assertEquals(4, helper.getLogjobLocationCurrentRunCount(1));
        assertEquals(5, helper.getLogjobLocationNotSyncedCount(1));
        assertEquals(1, helper.getLogjobLocationNotSyncedCount(2));
        // oldest first, as they are sent
        assertEquals(1690000000, helper.getLocationsToSyncOfLogjob(1).get(0).getTimestamp());
        assertEquals(12.5, helper.getLocationsOfLogjob(1).get(0).getAltitude(), 0.0);
        // the points of the deleted logjob are gone, they could never be sent
        assertEquals(6, helper.getLocationNotSyncedCount());
        assertNull(helper.getPendingUploadStats().get(99L));
        assertEquals(0, count(helper.getReadableDatabase(), "SELECT COUNT(*) FROM LOCATIONS WHERE LOGJOBID = 99"));
    }

    @Test
    public void upgradeFromVersion20KeepsDataAndAddsTheIndexes() {
        createVersion20Database();

        PhoneTrackSQLiteOpenHelper helper = openWithHelper();
        SQLiteDatabase db = helper.getWritableDatabase();

        assertEquals(21, db.getVersion());
        assertDataKept(helper);

        List<String> indexes = locationIndexes(db);
        assertEquals(indexes.toString(), 2, indexes.size());
        assertTrue(indexes.toString(), indexes.get(0).startsWith(PhoneTrackSQLiteOpenHelper.index_locations_logjob + ":"));
        assertTrue(indexes.toString(), indexes.get(1).startsWith(PhoneTrackSQLiteOpenHelper.index_locations_pending + ":"));
        // the earlier indexes are still there
        assertEquals(1, count(db, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'SESSIONS_TOKEN_idx'"));
        assertEquals(1, count(db, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'LOGJOBS_TOKEN_idx'"));
    }

    @Test
    public void perPositionQueriesUseThePendingIndex() {
        createVersion20Database();
        SQLiteDatabase db = openWithHelper().getReadableDatabase();

        // after every stored position and every upload pass
        assertUsesPendingIndex(db, "SELECT COUNT(*) FROM LOCATIONS WHERE LOGJOBID = ? AND SYNCED = 0", "1");
        // tracking notification
        assertUsesPendingIndex(db, "SELECT COUNT(*) FROM LOCATIONS WHERE SYNCED = 0");
        // upload policy
        assertUsesPendingIndex(db, "SELECT COUNT(*), MIN(TIME) FROM LOCATIONS WHERE LOGJOBID = ? AND SYNCED = 0", "1");
        assertUsesPendingIndex(db, "SELECT LOGJOBID, COUNT(*), MIN(TIME) FROM LOCATIONS WHERE SYNCED = 0 GROUP BY LOGJOBID");
        // what to send, oldest first
        assertUsesPendingIndex(db, "SELECT * FROM LOCATIONS WHERE LOGJOBID = ? AND SYNCED = 0 ORDER BY TIME ASC", "1");

        String mapPlan = queryPlan(db, "SELECT * FROM LOCATIONS WHERE LOGJOBID = ? AND CURRENTRUN = 1", "1");
        assertTrue(mapPlan, mapPlan.contains("INDEX " + PhoneTrackSQLiteOpenHelper.index_locations_logjob));
    }

    @Test
    public void upgradeCanRunAgainWithoutChangingAnything() {
        createVersion20Database();
        PhoneTrackSQLiteOpenHelper helper = openWithHelper();
        SQLiteDatabase db = helper.getWritableDatabase();
        List<String> indexesBefore = locationIndexes(db);
        long rowsBefore = count(db, "SELECT COUNT(*) FROM LOCATIONS");

        helper.onUpgrade(db, 20, 21);

        assertEquals(indexesBefore, locationIndexes(db));
        assertEquals(rowsBefore, count(db, "SELECT COUNT(*) FROM LOCATIONS"));
        assertDataKept(helper);
    }

    @Test
    public void newInstallHasTheSameIndexesAsAnUpgradedOne() {
        createVersion20Database();
        List<String> upgraded = locationIndexes(openWithHelper().getReadableDatabase());

        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        context.deleteDatabase(DB_NAME);
        PhoneTrackSQLiteOpenHelper fresh = PhoneTrackSQLiteOpenHelper.getInstance(context);

        assertEquals(21, fresh.getReadableDatabase().getVersion());
        assertEquals(upgraded, locationIndexes(fresh.getReadableDatabase()));
    }
}
