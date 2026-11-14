package net.eneiluj.nextcloud.phonetrack.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Version 23 adds TIME to the index of a log job's positions, for "Keep uploaded positions on
 * this phone". Upgrading a version 22 database keeps every position (the setting starts at
 * "Forever"), replaces the old index instead of adding a second one, and can run again.
 */
@RunWith(AndroidJUnit4.class)
public class LogjobTimeIndexUpgradeTest {

    private static final String DB_NAME = "NEXTCLOUD_PHONETRACK";

    private Context context;

    @Before
    public void setUp() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_NAME);
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    @After
    public void tearDown() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        context.deleteDatabase(DB_NAME);
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    /** The schema of version 22, as onCreate made it, with a year of uploaded positions. */
    private void createVersion22Database() {
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
                    + "LASTSYNCERR TEXT, LASTSYNCERRCOUNT INTEGER DEFAULT 0, TOKEN TEXT, "
                    + "LOGIN TEXT DEFAULT NULL, PASSWORD TEXT DEFAULT NULL, "
                    + "USESIGMOTION INTEGER DEFAULT 0,USESIGMOTIONMIXED INTEGER DEFAULT 0,LOCTIMEOUT INTEGER)");
            db.execSQL("CREATE TABLE LOCATIONS ( ID INTEGER PRIMARY KEY AUTOINCREMENT, LOGJOBID INTEGER, "
                    + "LAT FLOAT, LON FLOAT, TIME INTEGER, BEARING FLOAT, ALTITUDE FLOAT, SPEED FLOAT, "
                    + "ACCURACY FLOAT, SATELLITES INTEGER, USERAGENT TEXT, SYNCED INTEGER DEFAULT 0, "
                    + "CURRENTRUN INTEGER DEFAULT 0, BATTERY FLOAT)");
            db.execSQL("CREATE TABLE SYSLOG ( ID INTEGER PRIMARY KEY AUTOINCREMENT, TAG VARCHAR(64), "
                    + "MESSAGE TEXT, LEVEL VARCHAR(1), TIMESTAMP INTEGER)");
            db.execSQL("CREATE INDEX IF NOT EXISTS SESSIONS_TOKEN_idx ON SESSIONS(TOKEN)");
            db.execSQL("CREATE INDEX IF NOT EXISTS LOGJOBS_TOKEN_idx ON LOGJOBS(TOKEN)");
            // the indexes of versions 21 and 22
            db.execSQL("CREATE INDEX IF NOT EXISTS LOCATIONS_PENDING_idx ON LOCATIONS(LOGJOBID, TIME) WHERE SYNCED = 0");
            db.execSQL("CREATE INDEX IF NOT EXISTS LOCATIONS_LOGJOBID_CURRENTRUN_idx ON LOCATIONS(LOGJOBID, CURRENTRUN)");

            db.execSQL("INSERT INTO LOGJOBS (ID, TITLE, URL, DEVICENAME, TOKEN, MINTIME, MINDISTANCE, MINACCURACY, "
                    + "ENABLED, NBSYNC, LASTSYNC, LASTLOC) VALUES "
                    + "(1, 'phone', 'https://cloud.example', 'pixel', 'tok', 60, 0, 50, 1, 400, 1700000000, 1700000600)");
            long now = System.currentTimeMillis() / 1000;
            // a year of uploaded positions of the current run, one a day, and two waiting
            for (int day = 400; day > 0; day--) {
                insertLocation(db, 1, now - day * 86400L, 1, 1);
            }
            insertLocation(db, 1, now - 500 * 86400L, 0, 1);
            insertLocation(db, 1, now - 60, 0, 1);
            db.setVersion(22);
        }
    }

    private static void insertLocation(SQLiteDatabase db, long logjobId, long time, int synced, int currentRun) {
        db.execSQL("INSERT INTO LOCATIONS (LOGJOBID, LAT, LON, TIME, ALTITUDE, ACCURACY, SATELLITES, "
                        + "USERAGENT, SYNCED, CURRENTRUN, BATTERY) VALUES (?, 53.35, -6.26, ?, 12.5, 8.0, 9, "
                        + "'PhoneTrack/0.4.11', ?, ?, 77.0)",
                new Object[]{logjobId, time, synced, currentRun});
    }

    private PhoneTrackSQLiteOpenHelper openWithHelper() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        PhoneTrackSQLiteOpenHelper helper = PhoneTrackSQLiteOpenHelper.getInstance(context);
        // opening runs onUpgrade
        helper.getWritableDatabase();
        return helper;
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

    private static List<String> indexColumns(SQLiteDatabase db, String index) {
        List<String> columns = new ArrayList<>();
        try (Cursor c = db.rawQuery("PRAGMA index_info(" + index + ")", null)) {
            int name = c.getColumnIndexOrThrow("name");
            while (c.moveToNext()) {
                columns.add(c.getString(name));
            }
        }
        return columns;
    }

    private static long count(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            assertTrue(c.moveToFirst());
            return c.getLong(0);
        }
    }

    private static void assertDataKept(PhoneTrackSQLiteOpenHelper helper) {
        DBLogjob phone = helper.getLogjob(1);
        assertNotNull(phone);
        assertEquals("pixel", phone.getDeviceName());
        assertEquals(400, phone.getNbSync());
        assertEquals(402, helper.getLocationsOfLogjob(1).size());
        assertEquals(402, helper.getCurrentRunLocationsOfLogjob(1).size());
        assertEquals(2, helper.getLogjobLocationNotSyncedCount(1));
        assertEquals(2, helper.getLocationNotSyncedCount());
    }

    @Test
    public void upgradeFromVersion22KeepsEveryPositionAndReplacesTheIndex() {
        createVersion22Database();
        PhoneTrackSQLiteOpenHelper helper = openWithHelper();
        SQLiteDatabase db = helper.getWritableDatabase();

        assertEquals(23, db.getVersion());
        assertEquals(PhoneTrackSQLiteOpenHelper.database_version, db.getVersion());
        assertDataKept(helper);

        List<String> indexes = locationIndexes(db);
        assertEquals(indexes.toString(), 2, indexes.size());
        assertTrue(indexes.toString(), indexes.get(0).startsWith(PhoneTrackSQLiteOpenHelper.index_locations_logjob + ":"));
        assertTrue(indexes.toString(), indexes.get(1).startsWith(PhoneTrackSQLiteOpenHelper.index_locations_pending + ":"));
        assertEquals(0, count(db, "SELECT COUNT(*) FROM sqlite_master WHERE name = '"
                + PhoneTrackSQLiteOpenHelper.index_locations_logjob_v21 + "'"));
        assertEquals(Arrays.asList("LOGJOBID", "CURRENTRUN", "TIME"),
                indexColumns(db, PhoneTrackSQLiteOpenHelper.index_locations_logjob));
        assertEquals(1, count(db, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'SESSIONS_TOKEN_idx'"));
        assertEquals(1, count(db, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'LOGJOBS_TOKEN_idx'"));
    }

    @Test
    public void upgradeCanRunAgainWithoutChangingAnything() {
        createVersion22Database();
        PhoneTrackSQLiteOpenHelper helper = openWithHelper();
        SQLiteDatabase db = helper.getWritableDatabase();
        List<String> indexesBefore = locationIndexes(db);

        helper.onUpgrade(db, 22, 23);

        assertEquals(indexesBefore, locationIndexes(db));
        assertDataKept(helper);
    }

    @Test
    public void newInstallHasTheSameIndexesAsAnUpgradedOne() {
        createVersion22Database();
        List<String> upgraded = locationIndexes(openWithHelper().getReadableDatabase());

        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        context.deleteDatabase(DB_NAME);
        PhoneTrackSQLiteOpenHelper fresh = PhoneTrackSQLiteOpenHelper.getInstance(context);

        assertEquals(upgraded, locationIndexes(fresh.getReadableDatabase()));
    }

    @Test
    public void uploadsAfterTheUpgradeDeleteNothingUntilTheSettingIsChanged() {
        createVersion22Database();
        PhoneTrackSQLiteOpenHelper helper = openWithHelper();
        DBLogjob phone = helper.getLogjob(1);
        long waiting = helper.getLocationsToSyncOfLogjob(1).get(0).getId();

        helper.markSynced(phone, Collections.singletonList(waiting));

        assertEquals(402, helper.getLocationsOfLogjob(1).size());
        assertEquals(1, helper.getLocationNotSyncedCount());
    }
}
