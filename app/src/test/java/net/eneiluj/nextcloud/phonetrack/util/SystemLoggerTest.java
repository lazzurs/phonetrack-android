package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.model.DBSyslog;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The in-app activity log: nothing is stored while it is off; while it is on, lines are stored in
 * batches, the table is capped, and only a listening activity log screen is told about new lines.
 */
@RunWith(AndroidJUnit4.class)
public class SystemLoggerTest {

    private Application app;
    private PhoneTrackSQLiteOpenHelper db;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
        SystemLogger.resetForTesting();
        SystemLogger.init(app);
        db = PhoneTrackSQLiteOpenHelper.getInstance(app);
    }

    @After
    public void tearDown() {
        SystemLogger.resetForTesting();
        DbTestSupport.resetDatabase();
    }

    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("timed out");
            }
            Thread.sleep(10);
        }
    }

    private List<String> stored() {
        List<String> messages = new ArrayList<>();
        for (DBSyslog line : db.getSyslogs(null, null)) {
            messages.add(line.getMessage());
        }
        return messages;
    }

    @Test
    public void offStoresNothingAndSendsNoBroadcast() {
        assertFalse(SystemLogger.getEnabled());
        SystemLogger.d("T", "debug");
        SystemLogger.i("T", "info");
        SystemLogger.w("T", "warning");
        SystemLogger.e("T", "error");

        assertEquals(0, SystemLogger.pendingCount());
        SystemLogger.flush();
        assertTrue(stored().isEmpty());
        assertTrue(shadowOf(app).getBroadcastIntents().isEmpty());
    }

    @Test
    public void linesAreStoredInBatchesInOrder() {
        SystemLogger.setEnabled(true);
        SystemLogger.d("T", "one");
        SystemLogger.i("T", "two");
        SystemLogger.v("T", "three");

        // waiting in memory, not yet one write each (the batch is stored after a few seconds)
        assertEquals(3, SystemLogger.pendingCount());
        assertTrue(stored().isEmpty());

        SystemLogger.flush();
        assertEquals(0, SystemLogger.pendingCount());
        assertEquals(List.of("one", "two", "three"), stored());
        assertEquals("d", db.getSyslogs(null, null).get(0).getLevel());
        assertEquals("T", db.getSyslogs(null, null).get(0).getTag());
        // nobody listens: no broadcast per line
        assertTrue(shadowOf(app).getBroadcastIntents().isEmpty());
    }

    @Test
    public void waitingLinesAreStoredAfterTheDelay() throws InterruptedException {
        SystemLogger.setFlushDelayForTesting(50);
        SystemLogger.setEnabled(true);
        SystemLogger.d("T", "later");

        waitFor(() -> stored().contains("later"));
        assertEquals(0, SystemLogger.pendingCount());
    }

    @Test
    public void manyLinesAreStoredWithoutWaitingForTheDelay() throws InterruptedException {
        SystemLogger.setFlushDelayForTesting(60_000);
        SystemLogger.setEnabled(true);
        for (int i = 0; i < SystemLogger.FLUSH_LINES; i++) {
            SystemLogger.d("T", "line " + i);
        }

        waitFor(() -> stored().size() == SystemLogger.FLUSH_LINES);
    }

    @Test
    public void errorsAndWarningsAreStoredAtOnce() throws InterruptedException {
        SystemLogger.setFlushDelayForTesting(60_000);
        SystemLogger.setEnabled(true);
        SystemLogger.d("T", "before");
        SystemLogger.e("T", "it failed");

        // the error takes the lines before it along, in order
        waitFor(() -> stored().size() == 2);
        assertEquals(List.of("before", "it failed"), stored());

        SystemLogger.w("T", "careful");
        waitFor(() -> stored().contains("careful"));
    }

    @Test
    public void tableKeepsTheNewestLines() {
        SystemLogger.setEnabled(true);
        int total = SystemLogger.MAX_ROWS + 50;
        for (int i = 0; i < total; i++) {
            SystemLogger.d("T", "line " + i);
        }
        SystemLogger.flush();

        List<String> messages = stored();
        assertEquals(SystemLogger.MAX_ROWS, messages.size());
        assertTrue(messages.contains("line " + (total - 1)));
        assertTrue(messages.contains("line 50"));
        assertFalse(messages.contains("line 49"));
    }

    @Test
    public void capHoldsAcrossBatches() {
        for (int batch = 0; batch < 3; batch++) {
            List<DBSyslog> lines = new ArrayList<>();
            for (int i = 0; i < 80; i++) {
                lines.add(new DBSyslog("b" + batch + " l" + i, "T", "d", 1_700_000_000L + batch));
            }
            db.addSyslogs(lines, 100);
        }

        List<String> messages = stored();
        assertEquals(100, messages.size());
        assertTrue(messages.contains("b2 l79"));
        assertTrue(messages.contains("b1 l60"));
        assertFalse(messages.contains("b1 l59"));
    }

    @Test
    public void listenerGetsLinesAtOnceWithoutBroadcasts() throws InterruptedException {
        SystemLogger.setFlushDelayForTesting(60_000);
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        SystemLogger.Listener listener = lines -> {
            for (DBSyslog line : lines) {
                received.add(line.getMessage());
            }
        };
        SystemLogger.setListener(listener);
        SystemLogger.setEnabled(true);
        SystemLogger.d("T", "live");

        waitFor(() -> received.contains("live"));
        assertEquals(List.of("live"), stored());
        assertTrue(shadowOf(app).getBroadcastIntents().isEmpty());

        // once the screen stops listening, lines wait for the next batch again
        SystemLogger.removeListener(listener);
        SystemLogger.d("T", "batched");
        assertEquals(1, SystemLogger.pendingCount());
        assertFalse(received.contains("batched"));
    }

    @Test
    public void removingAReplacedListenerKeepsTheNewOne() throws InterruptedException {
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        SystemLogger.Listener old = lines -> { };
        SystemLogger.setListener(old);
        SystemLogger.setListener(lines -> {
            for (DBSyslog line : lines) {
                received.add(line.getMessage());
            }
        });
        SystemLogger.removeListener(old);
        SystemLogger.setEnabled(true);
        SystemLogger.d("T", "still listening");

        waitFor(() -> received.contains("still listening"));
    }

    @Test
    public void clearDropsStoredAndWaitingLines() {
        SystemLogger.setEnabled(true);
        SystemLogger.d("T", "stored");
        SystemLogger.flush();
        SystemLogger.d("T", "waiting");

        SystemLogger.clear(app);
        SystemLogger.flush();

        assertTrue(stored().isEmpty());
    }
}
