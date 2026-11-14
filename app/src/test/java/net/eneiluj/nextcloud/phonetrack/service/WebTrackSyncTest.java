package net.eneiluj.nextcloud.phonetrack.service;

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
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.CorrectingLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What an upload run records in the database: sent positions are marked per accepted request
 * (one transaction for a whole PhoneTrack batch), and a refused request leaves its positions and
 * the following ones waiting.
 */
@RunWith(AndroidJUnit4.class)
public class WebTrackSyncTest {

    private Context context;
    private PhoneTrackSQLiteOpenHelper db;
    private ServerSocket server;
    private final List<String> paths = new CopyOnWriteArrayList<>();
    /** the request with this number (1 = first) is refused; 0 = accept all */
    private volatile int refuseRequest;
    private final AtomicInteger requests = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        DbTestSupport.resetDatabase();
        context = ApplicationProvider.getApplicationContext();
        db = PhoneTrackSQLiteOpenHelper.getInstance(context);
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread thread = new Thread(this::serve, "sync-test-server");
        thread.setDaemon(true);
        thread.start();
    }

    @After
    public void tearDown() throws Exception {
        server.close();
        DbTestSupport.resetDatabase();
    }

    /** Minimal PhoneTrack server: reads one POST per connection, answers {"done":1}. */
    private void serve() {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                String requestLine = in.readLine();
                if (requestLine == null) {
                    continue;
                }
                int length = 0;
                String line = in.readLine();
                while (line != null && !line.isEmpty()) {
                    if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                        length = Integer.parseInt(line.substring(15).trim());
                    }
                    line = in.readLine();
                }
                for (int i = 0; i < length; i++) {
                    in.read();
                }
                paths.add(requestLine.split(" ")[1]);
                int done = requests.incrementAndGet() == refuseRequest ? 0 : 1;
                String body = "{\"done\":" + done + "}";
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                        + "Content-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body)
                        .getBytes(StandardCharsets.ISO_8859_1));
                socket.getOutputStream().flush();
            } catch (IOException e) {
                // closed by tearDown, or the client went away
            }
        }
    }

    private long phoneTrackLogjob() {
        return db.addLogjob(new DBLogjob(0, "phonetrack", "http://127.0.0.1:" + server.getLocalPort(),
                "tok", "dev", 60, 0, 50, false, false, false, 60, false, false, 0, null, null, false));
    }

    private void addCurrentRunPoints(long logjobId, int n) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            Location location = new Location(LocationManager.GPS_PROVIDER);
            location.setLatitude(53.35);
            location.setLongitude(-6.26);
            location.setTime(now - (n - i) * 1000L);
            db.addLocation(logjobId, new CorrectingLocation(location), 80.0);
        }
    }

    private long rows(long logjobId, String where) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM LOCATIONS WHERE LOGJOBID = ? AND " + where,
                new String[]{String.valueOf(logjobId)})) {
            assertTrue(c.moveToFirst());
            return c.getLong(0);
        }
    }

    @Test
    public void batchIsMarkedSentInOneGo() {
        long id = phoneTrackLogjob();
        addCurrentRunPoints(id, 7);
        long before = System.currentTimeMillis() / 1000;

        WebTrackSync sync = new WebTrackSync(context);
        assertTrue(sync.sync(id, false));

        assertEquals(1, paths.size());
        assertTrue(paths.get(0), paths.get(0).contains("/logPostMultiple/tok/dev"));
        assertEquals(7, sync.getSentCount());
        assertEquals(0, db.getLogjobLocationNotSyncedCount(id));
        // the current run stays for the map and the info dialog
        assertEquals(7, rows(id, "SYNCED = 1 AND CURRENTRUN = 1"));
        assertEquals(7, db.getNbSync(id));
        assertTrue(db.getLastSyncTimestamp(id) >= before);
    }

    @Test
    public void refusedBatchKeepsEveryPointWaiting() {
        long id = phoneTrackLogjob();
        addCurrentRunPoints(id, 7);
        refuseRequest = 1;

        WebTrackSync sync = new WebTrackSync(context);
        assertFalse(sync.sync(id, false));

        assertEquals(0, sync.getSentCount());
        assertEquals(7, db.getLogjobLocationNotSyncedCount(id));
        assertEquals(0, db.getNbSync(id));
        assertEquals(0, db.getLastSyncTimestamp(id));
    }

    @Test
    public void singlePointsAreMarkedPerAcceptedRequest() {
        long id = phoneTrackLogjob();
        addCurrentRunPoints(id, 3);
        refuseRequest = 2;

        WebTrackSync sync = new WebTrackSync(context);
        assertFalse(sync.sync(id, false));

        assertEquals(2, paths.size());
        assertTrue(paths.get(0), paths.get(0).contains("/logPost/tok/dev"));
        assertEquals(1, sync.getSentCount());
        assertEquals(1, rows(id, "SYNCED = 1"));
        assertEquals(2, db.getLogjobLocationNotSyncedCount(id));
        assertEquals(1, db.getNbSync(id));
        // the oldest one went first
        List<DBLogjobLocation> waiting = db.getLocationsToSyncOfLogjob(id);
        assertTrue(waiting.get(0).getTimestamp() > rowTime(id, "SYNCED = 1"));
    }

    @Test
    public void sentPointsOutsideTheCurrentRunAreDeleted() {
        long id = phoneTrackLogjob();
        long now = System.currentTimeMillis() / 1000;
        for (int i = 0; i < 3; i++) {
            // left over from before "reset current run"
            db.addLocation(new DBLogjobLocation(0, id, 53.0, -6.0, now - 60 + i,
                    null, null, null, null, null, 70.0, "test", false, false));
        }

        assertTrue(new WebTrackSync(context).sync(id, false));

        assertEquals(3, paths.size());
        assertEquals(0, rows(id, "1 = 1"));
        assertEquals(3, db.getNbSync(id));
    }

    @Test
    public void acceptedBatchDeletesUploadedPointsOlderThanTheKeptWindow() {
        long id = phoneTrackLogjob();
        long now = System.currentTimeMillis() / 1000;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(context.getString(R.string.pref_key_keep_uploaded), "30").commit();
        try {
            // recorded offline: four 40 days ago, three today
            for (int i = 0; i < 7; i++) {
                long time = i < 4 ? now - 40 * 86400L + i : now - 60 + i;
                db.addLocation(new DBLogjobLocation(0, id, 53.0, -6.0, time,
                        null, null, null, null, null, 70.0, "test", false, true));
            }

            assertTrue(new WebTrackSync(context).sync(id, false));

            assertEquals(1, paths.size());
            assertEquals(3, rows(id, "SYNCED = 1 AND CURRENTRUN = 1"));
            assertEquals(0, rows(id, "TIME < " + (now - 30 * 86400L)));
            assertEquals(0, db.getLogjobLocationNotSyncedCount(id));
            // all seven were sent
            assertEquals(7, db.getNbSync(id));
        } finally {
            PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        }
    }

    private long rowTime(long logjobId, String where) {
        try (Cursor c = db.getReadableDatabase().rawQuery(
                "SELECT TIME FROM LOCATIONS WHERE LOGJOBID = ? AND " + where,
                new String[]{String.valueOf(logjobId)})) {
            assertTrue(c.moveToFirst());
            return c.getLong(0);
        }
    }
}
