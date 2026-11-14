package net.eneiluj.nextcloud.phonetrack.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjobLocation;
import net.eneiluj.nextcloud.phonetrack.util.CorrectingLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * "Keep uploaded positions on this phone": by default nothing is deleted; otherwise an accepted
 * upload deletes the log job's uploaded positions older than the window, never a position waiting
 * for upload, never one of a custom log job, and the counts shown stay right.
 */
@RunWith(AndroidJUnit4.class)
public class UploadedPositionRetentionTest {

    private static final long DAY = 24 * 60 * 60;
    private static final long HOUR = 60 * 60;
    /** Ages of the test positions, in days; each one an hour off the day, away from any limit. */
    private static final int[] AGES_DAYS = {400, 366, 364, 91, 89, 31, 29, 1, 0};

    private Context context;
    private PhoneTrackSQLiteOpenHelper db;
    private long now;

    @Before
    public void setUp() {
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
        context = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        now = System.currentTimeMillis() / 1000;
    }

    @After
    public void tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        PhoneTrackSQLiteOpenHelper.resetInstanceForTesting();
    }

    private void keep(String days) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.pref_key_keep_uploaded), days).commit();
    }

    private long phoneTrackLogjob() {
        return db.addLogjob(new DBLogjob(0, "phonetrack", "https://cloud.example", "tok", "dev",
                60, 0, 50, false, false, false, 60, false, true, 0, null, null, false));
    }

    private long mapsLogjob() {
        return db.addLogjob(new DBLogjob(0, "maps", "", "", "",
                60, 0, 50, false, false, false, 60, false, true, 0, null, null, false));
    }

    private long customLogjob() {
        return db.addLogjob(new DBLogjob(0, "custom", "https://example.org/log", "", "",
                60, 0, 50, false, false, false, 60, false, true, 0, null, null, false));
    }

    /** What the logger does: a position of the current run, waiting for upload. */
    private void store(long logjobId, long timeSeconds) {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(53.35);
        location.setLongitude(-6.26);
        location.setTime(timeSeconds * 1000);
        db.addLocation(logjobId, new CorrectingLocation(location), 80.0);
    }

    private static long hourOffDay(long now, int days) {
        // older than the day by an hour, except "0 days" which is an hour old
        return now - days * DAY - HOUR;
    }

    /** One position at each of AGES_DAYS, waiting for upload. */
    private void storeAtEveryAge(long logjobId) {
        for (int age : AGES_DAYS) {
            store(logjobId, hourOffDay(now, age));
        }
    }

    private List<Long> pendingIds(long logjobId) {
        List<Long> ids = new ArrayList<>();
        for (DBLogjobLocation loc : db.getLocationsToSyncOfLogjob(logjobId)) {
            ids.add(loc.getId());
        }
        return ids;
    }

    /** What WebTrackSync does once the server accepted every waiting position of the log job. */
    private void uploadAll(long logjobId) {
        db.markSynced(db.getLogjob(logjobId), pendingIds(logjobId));
    }

    private TreeSet<Long> keptTimes(long logjobId) {
        TreeSet<Long> times = new TreeSet<>();
        for (DBLogjobLocation loc : db.getLocationsOfLogjob(logjobId)) {
            times.add(loc.getTimestamp());
        }
        return times;
    }

    private TreeSet<Long> timesYoungerThan(int days) {
        TreeSet<Long> times = new TreeSet<>();
        for (int age : AGES_DAYS) {
            if (age < days) {
                times.add(hourOffDay(now, age));
            }
        }
        return times;
    }

    private TreeSet<Long> allTimes() {
        return timesYoungerThan(Integer.MAX_VALUE);
    }

    private long pendingInTable() {
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM LOCATIONS WHERE SYNCED = 0", null)) {
            assertTrue(c.moveToFirst());
            return c.getLong(0);
        }
    }

    @Test
    public void defaultKeepsEverything() {
        long id = phoneTrackLogjob();
        storeAtEveryAge(id);
        uploadAll(id);

        assertEquals(allTimes(), keptTimes(id));
        assertEquals(UploadedPositionRetention.KEEP_FOREVER, UploadedPositionRetention.keepDays(context));
    }

    @Test
    public void foreverAndValuesNotUnderstoodKeepEverything() {
        long id = phoneTrackLogjob();
        for (String value : Arrays.asList("0", "", "-30", "thirty")) {
            keep(value);
            storeAtEveryAge(id);
            uploadAll(id);
        }
        assertEquals(4 * AGES_DAYS.length, db.getLocationsOfLogjob(id).size());
    }

    @Test
    public void eachChoiceDeletesExactlyTheUploadedPositionsOlderThanIt() {
        for (int days : new int[]{365, 90, 30}) {
            keep(String.valueOf(days));
            long id = phoneTrackLogjob();
            storeAtEveryAge(id);

            uploadAll(id);

            assertEquals(days + " days", timesYoungerThan(days), keptTimes(id));
        }
    }

    @Test
    public void choicesOfferedAreForeverAYearNinetyAndThirtyDays() {
        String[] values = context.getResources().getStringArray(R.array.keepUploadedValues);
        assertEquals(Arrays.asList("0", "365", "90", "30"), Arrays.asList(values));
        assertEquals("1 year", UploadedPositionRetention.describe(context, 365));
        assertEquals("90 days", UploadedPositionRetention.describe(context, 90));
        assertEquals("30 days", UploadedPositionRetention.describe(context, 30));
    }

    @Test
    public void positionsWaitingForUploadAreNeverDeleted() {
        keep("30");
        long id = phoneTrackLogjob();
        storeAtEveryAge(id);
        List<Long> waiting = pendingIds(id);
        // the server accepted only the newest one; the old ones are still waiting
        db.markSynced(db.getLogjob(id), waiting.subList(waiting.size() - 1, waiting.size()));

        assertEquals(allTimes(), keptTimes(id));
        assertEquals(AGES_DAYS.length - 1, db.getLogjobLocationNotSyncedCount(id));

        // positions from before a reset of the statistics, waiting too
        db.resetLogjobCurrentRun(id);
        store(id, now - HOUR);
        db.markSynced(db.getLogjob(id), pendingIds(id).subList(AGES_DAYS.length - 1, AGES_DAYS.length));
        assertEquals(AGES_DAYS.length - 1, db.getLogjobLocationNotSyncedCount(id));
        // the waiting ones and the one just sent
        assertEquals(AGES_DAYS.length, db.getLocationsOfLogjob(id).size());
    }

    @Test
    public void customLogjobsKeepEverythingAndOtherLogjobsAreNotTouched() {
        keep("30");
        long custom = customLogjob();
        long maps = mapsLogjob();
        long phoneTrack = phoneTrackLogjob();
        storeAtEveryAge(custom);
        storeAtEveryAge(maps);
        storeAtEveryAge(phoneTrack);
        // this one cannot upload (no account, server refusing them): everything waits
        long neverUploads = phoneTrackLogjob();
        storeAtEveryAge(neverUploads);

        uploadAll(custom);
        assertEquals(allTimes(), keptTimes(custom));
        // nothing was uploaded for the other log jobs: none of their positions is deleted
        assertEquals(allTimes(), keptTimes(maps));
        assertEquals(allTimes(), keptTimes(phoneTrack));

        uploadAll(maps);
        assertEquals(timesYoungerThan(30), keptTimes(maps));
        assertEquals(allTimes(), keptTimes(phoneTrack));

        uploadAll(phoneTrack);
        assertEquals(timesYoungerThan(30), keptTimes(phoneTrack));
        assertEquals(allTimes(), keptTimes(custom));
        assertEquals(allTimes(), keptTimes(neverUploads));
    }

    @Test
    public void ruleForEachKindOfLogjob() {
        keep("90");
        DBLogjob custom = db.getLogjob(customLogjob());
        DBLogjob maps = db.getLogjob(mapsLogjob());
        DBLogjob phoneTrack = db.getLogjob(phoneTrackLogjob());

        assertFalse(UploadedPositionRetention.appliesTo(custom));
        assertTrue(UploadedPositionRetention.appliesTo(maps));
        assertTrue(UploadedPositionRetention.appliesTo(phoneTrack));
        assertEquals(UploadedPositionRetention.KEEP_FOREVER, UploadedPositionRetention.keepDays(context, custom));
        assertEquals(90, UploadedPositionRetention.keepDays(context, maps));
        assertEquals(90, UploadedPositionRetention.keepDays(context, phoneTrack));
    }

    @Test
    public void countsStayRightAfterDeleting() {
        long id = phoneTrackLogjob();
        long other = phoneTrackLogjob();
        storeAtEveryAge(id);
        storeAtEveryAge(other);
        // the cached count of waiting positions is known before the upload
        assertEquals(2 * AGES_DAYS.length, db.getLocationNotSyncedCount());

        keep("30");
        List<Long> ids = pendingIds(id);
        // two requests, as WebTrackSync sends them; the oldest positions go first
        db.markSynced(db.getLogjob(id), ids.subList(0, 4));
        db.markSynced(db.getLogjob(id), ids.subList(4, ids.size()));
        store(id, now);

        // waiting positions: the tracking notification, the log job list, the upload policy
        assertEquals(pendingInTable(), db.getLocationNotSyncedCount());
        assertEquals(AGES_DAYS.length + 1, db.getLocationNotSyncedCount());
        assertEquals(1, db.getLogjobLocationNotSyncedCount(id));
        assertEquals(AGES_DAYS.length, db.getLogjobLocationNotSyncedCount(other));
        Map<Long, long[]> stats = db.getPendingUploadStats();
        assertEquals(1, stats.get(id)[0]);
        assertEquals(now, stats.get(id)[1]);
        assertEquals(AGES_DAYS.length, stats.get(other)[0]);

        // "locations sent" counts what was sent, deleted or not
        assertEquals(AGES_DAYS.length, db.getNbSync(id));
        assertEquals(AGES_DAYS.length, db.getNbTotalSync());
        // the current run (info dialog: points, distance, duration) is what was kept
        int kept = timesYoungerThan(30).size() + 1;
        assertEquals(kept, db.getLogjobLocationCurrentRunCount(id));
        assertEquals(kept, db.getCurrentRunLocationsOfLogjob(id).size());
    }

    @Test
    public void choosingForeverAgainStopsDeleting() {
        long id = phoneTrackLogjob();
        keep("30");
        storeAtEveryAge(id);
        uploadAll(id);
        assertEquals(timesYoungerThan(30), keptTimes(id));

        keep("0");
        // positions recorded offline long ago, uploaded now
        store(id, now - 200 * DAY);
        store(id, now - 40 * DAY);
        uploadAll(id);

        TreeSet<Long> expected = timesYoungerThan(30);
        expected.add(now - 200 * DAY);
        expected.add(now - 40 * DAY);
        assertEquals(expected, keptTimes(id));

        // a longer window deletes only what is older than it
        keep("90");
        store(id, now);
        uploadAll(id);
        expected.remove(now - 200 * DAY);
        expected.add(now);
        assertEquals(expected, keptTimes(id));
    }

    @Test
    public void positionsOlderThanTheWindowAreDeletedAsSoonAsTheyAreUploaded() {
        keep("30");
        long id = phoneTrackLogjob();
        // a backlog recorded offline 40 days ago
        store(id, now - 40 * DAY);
        store(id, now - 40 * DAY + 60);
        assertEquals(2, db.getLogjobLocationNotSyncedCount(id));

        uploadAll(id);

        assertTrue(db.getLocationsOfLogjob(id).isEmpty());
        assertEquals(2, db.getNbSync(id));
        assertEquals(0, db.getLocationNotSyncedCount());
    }

    @Test
    public void theDeleteOnlyReadsTheRowsOlderThanTheWindow() {
        StringBuilder plan = new StringBuilder();
        try (Cursor c = db.getReadableDatabase().rawQuery("EXPLAIN QUERY PLAN "
                + PhoneTrackSQLiteOpenHelper.delete_uploaded_before, new String[]{"1", "1700000000"})) {
            int detail = c.getColumnIndexOrThrow("detail");
            while (c.moveToNext()) {
                plan.append(c.getString(detail)).append('\n');
            }
        }
        String text = plan.toString();
        assertTrue(text, text.contains("INDEX " + PhoneTrackSQLiteOpenHelper.index_locations_logjob));
        assertTrue(text, text.contains("TIME<?"));
    }
}
