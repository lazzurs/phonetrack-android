package net.eneiluj.nextcloud.phonetrack.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.SyncError;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

/**
 * Version 22 counts the uploads of a log job that failed in a row. Upgrading a version 21
 * database keeps the log jobs and their last error, and running the upgrade twice changes nothing.
 */
@RunWith(AndroidJUnit4.class)
public class UploadErrorCountUpgradeTest {

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

    /** The log job table of version 21, as onCreate made it: one failing log job, one fine. */
    private void createVersion21Database() {
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
            PhoneTrackSQLiteOpenHelper.createLocationIndexes(db);

            db.execSQL("INSERT INTO LOGJOBS (ID, TITLE, URL, DEVICENAME, TOKEN, MINTIME, MINDISTANCE, MINACCURACY, "
                    + "ENABLED, NBSYNC, LASTSYNC, LASTSYNCERRTIME, LASTSYNCERR) VALUES "
                    + "(1, 'failing', 'https://example.org/log', '', '', 60, 0, 50, 1, 2, 1700000000, "
                    + "1700000600, 'Could not connect to the server')");
            db.execSQL("INSERT INTO LOGJOBS (ID, TITLE, URL, DEVICENAME, TOKEN, MINTIME, MINDISTANCE, MINACCURACY, "
                    + "LASTSYNCERRTIME, LASTSYNCERR) VALUES (2, 'fine', 'https://example.org/log2', '', '', 120, 10, 30, 0, '')");
            db.setVersion(21);
        }
    }

    private PhoneTrackSQLiteOpenHelper openWithHelper() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        PhoneTrackSQLiteOpenHelper helper = PhoneTrackSQLiteOpenHelper.getInstance(context);
        helper.getWritableDatabase();
        return helper;
    }

    private static int countColumns(SQLiteDatabase db, String column) {
        int n = 0;
        try (Cursor c = db.rawQuery("PRAGMA table_info(LOGJOBS)", null)) {
            while (c.moveToNext()) {
                if (column.equals(c.getString(c.getColumnIndexOrThrow("name")))) {
                    n++;
                }
            }
        }
        return n;
    }

    private void assertDataKept(PhoneTrackSQLiteOpenHelper helper) {
        DBLogjob failing = helper.getLogjob(1);
        assertNotNull(failing);
        assertEquals("failing", failing.getTitle());
        assertTrue(failing.isEnabled());
        assertEquals(2, failing.getNbSync());
        SyncError error = helper.getLastSyncError(1);
        assertEquals(1700000600, error.getTimestamp());
        assertEquals("Could not connect to the server", error.getMessage());
        // the error of the earlier version counts as one failure
        assertEquals(1, error.getConsecutiveFailures());

        assertEquals("https://example.org/log2", helper.getLogjob(2).getUrl());
        assertFalse(helper.getLastSyncError(2).isFailing());
        assertEquals(0, helper.getLastSyncError(2).getConsecutiveFailures());
    }

    @Test
    public void upgradeFromVersion21KeepsTheLastErrorAndCountsIt() {
        createVersion21Database();
        PhoneTrackSQLiteOpenHelper helper = openWithHelper();

        assertEquals(PhoneTrackSQLiteOpenHelper.database_version, helper.getReadableDatabase().getVersion());
        assertEquals(1, countColumns(helper.getReadableDatabase(), "LASTSYNCERRCOUNT"));
        assertDataKept(helper);
    }

    @Test
    public void upgradeCanRunAgainWithoutChangingAnything() {
        createVersion21Database();
        PhoneTrackSQLiteOpenHelper helper = openWithHelper();
        SQLiteDatabase db = helper.getWritableDatabase();

        helper.onUpgrade(db, 21, 22);

        assertEquals(1, countColumns(db, "LASTSYNCERRCOUNT"));
        assertDataKept(helper);
    }

    @Test
    public void failuresAreCountedUntilTheNextSuccess() {
        PhoneTrackSQLiteOpenHelper helper = openWithHelper();
        long id = helper.addLogjob(new DBLogjob(0, "custom", "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, false, false, 60, false, true, 0, null, null, false));
        long other = helper.addLogjob(new DBLogjob(0, "other", "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, false, false, 60, false, true, 0, null, null, false));

        helper.setLastSyncError(id, 100, "first");
        helper.setLastSyncError(id, 200, "second");
        helper.setLastSyncError(other, 150, "other one");
        assertEquals(2, helper.getLastSyncError(id).getConsecutiveFailures());
        assertEquals(200, helper.getLastSyncError(id).getTimestamp());
        assertEquals("second", helper.getLastSyncError(id).getMessage());

        List<DBLogjob> failing = helper.getLogjobsWithFailingUploads(2);
        assertEquals(1, failing.size());
        assertEquals(id, failing.get(0).getId());
        assertEquals(2, helper.getLogjobsWithFailingUploads(1).size());

        helper.resetLastSyncError(id);
        SyncError reset = helper.getLastSyncError(id);
        assertFalse(reset.isFailing());
        assertEquals(0, reset.getConsecutiveFailures());
        assertTrue(helper.getLogjobsWithFailingUploads(2).isEmpty());
        // one failure after a success is a first failure again
        helper.setLastSyncError(id, 300, "again");
        assertEquals(1, helper.getLastSyncError(id).getConsecutiveFailures());
    }
}
