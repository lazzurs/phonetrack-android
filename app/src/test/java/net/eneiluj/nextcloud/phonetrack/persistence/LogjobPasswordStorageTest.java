package net.eneiluj.nextcloud.phonetrack.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.util.CredentialStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Custom logjob passwords must not be in the database, which is part of the cloud backup.
 */
@RunWith(AndroidJUnit4.class)
public class LogjobPasswordStorageTest {

    private Context context;
    private PhoneTrackSQLiteOpenHelper db;

    @Before
    public void setUp() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        context = ApplicationProvider.getApplicationContext();
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
    }

    @After
    public void tearDown() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
    }

    private static DBLogjob customLogjob(String login, String password) {
        return new DBLogjob(0, "custom", "https://example.org/log", null, null,
                60, 0, 50, false, false, false, 60, true, false, 0, login, password, false);
    }

    private String passwordColumn(long id) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT PASSWORD FROM LOGJOBS WHERE ID = ?", new String[]{String.valueOf(id)})) {
            assertTrue(c.moveToFirst());
            return c.isNull(0) ? null : c.getString(0);
        }
    }

    @Test
    public void passwordIsKeptOutOfTheDatabase() {
        long id = db.addLogjob(customLogjob("me", "s3cret"));

        assertNull(passwordColumn(id));
        assertEquals("s3cret", db.getLogjob(id).getPassword());
        assertEquals("me", db.getLogjob(id).getLogin());
    }

    @Test
    public void updateAndDeleteKeepTheCredentialStoreInStep() {
        long id = db.addLogjob(customLogjob("me", "old"));
        DBLogjob lj = db.getLogjob(id);

        db.updateLogjobAndSync(lj, lj.getTitle(), lj.getToken(), lj.getUrl(), lj.getDeviceName(),
                lj.getPost(), lj.getMinTime(), lj.getMinDistance(), lj.getMinAccuracy(),
                lj.keepGpsOnBetweenFixes(), lj.useSignificantMotion(), lj.useSignificantMotionMixed(),
                lj.getLocationRequestTimeout(), "me", "new", lj.getJson(), null);
        assertNull(passwordColumn(id));
        assertEquals("new", db.getLogjob(id).getPassword());

        db.deleteLogjob(id);
        assertNull(CredentialStore.getLogjobPassword(context, id));
    }

    @Test
    public void upgradeMovesExistingPasswordsOutOfTheDatabase() {
        long id = db.addLogjob(customLogjob("me", null));
        SQLiteDatabase sqlite = db.getWritableDatabase();
        // what a version 19 database looks like
        sqlite.execSQL("UPDATE LOGJOBS SET PASSWORD = 'legacy' WHERE ID = " + id);

        PhoneTrackSQLiteOpenHelper.moveLogjobPasswordsToCredentialStore(sqlite, context);

        assertNull(passwordColumn(id));
        assertEquals("legacy", CredentialStore.getLogjobPassword(context, id));
        assertEquals("legacy", db.getLogjob(id).getPassword());
    }
}
