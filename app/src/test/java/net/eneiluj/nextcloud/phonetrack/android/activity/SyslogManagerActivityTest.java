package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.DialogInterface;
import android.os.Looper;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBSyslog;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.SystemLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.List;
import java.util.function.BooleanSupplier;

/** The activity log screen: switching the log on, and lines showing up while it is open. */
@RunWith(AndroidJUnit4.class)
public class SyslogManagerActivityTest {

    private Application app;
    private PhoneTrackSQLiteOpenHelper db;
    private ActivityController<SyslogManagerActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        SystemLogger.resetForTesting();
        app = ApplicationProvider.getApplicationContext();
        db = PhoneTrackSQLiteOpenHelper.getInstance(app);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.pause().stop().destroy();
        }
        SystemLogger.resetForTesting();
        DbTestSupport.resetDatabase();
    }

    /** Run main-thread work posted by background threads until the condition holds. */
    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long end = System.currentTimeMillis() + 5_000;
        while (true) {
            shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) {
                return;
            }
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("timed out");
            }
            Thread.sleep(10);
        }
    }

    private String shownText() {
        TextView text = controller.get().findViewById(R.id.syslog_text);
        return text.getText().toString();
    }

    @Test
    public void openScreenShowsStoredAndNewLines() throws InterruptedException {
        SystemLogger.setEnabled(true);
        // still waiting in memory when the screen opens: read too
        SystemLogger.d("T", "from before");
        controller = Robolectric.buildActivity(SyslogManagerActivity.class).setup();
        waitFor(() -> shownText().contains("from before"));

        // while the screen is open, a new line is stored and shown at once
        SystemLogger.d("T", "while open");
        waitFor(() -> shownText().contains("while open"));
        List<DBSyslog> stored = db.getSyslogs(null, null);
        assertEquals("while open", stored.get(stored.size() - 1).getMessage());
        assertTrue(shadowOf(app).getBroadcastIntents().isEmpty());
    }

    @Test
    public void switchingOnClearsTheOldLog() throws InterruptedException {
        SystemLogger.setEnabled(true);
        SystemLogger.d("T", "old line");
        SystemLogger.setEnabled(false);
        controller = Robolectric.buildActivity(SyslogManagerActivity.class).setup();
        waitFor(() -> shownText().contains("old line"));

        // asks first (DestructiveActionConfirmationTest), then clears
        assertTrue(shadowOf(controller.get()).clickMenuItem(R.id.menu_toggle_logs));
        ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(SystemLogger.getEnabled());
        assertTrue(db.getSyslogs(null, null).isEmpty());
        assertFalse(shownText().contains("old line"));

        assertTrue(shadowOf(controller.get()).clickMenuItem(R.id.menu_toggle_logs));
        assertFalse(SystemLogger.getEnabled());
    }

    @Test
    public void closedScreenNoLongerListens() throws InterruptedException {
        SystemLogger.setEnabled(true);
        controller = Robolectric.buildActivity(SyslogManagerActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idle();
        controller.pause();

        SystemLogger.d("T", "after pause");
        Thread.sleep(100);
        shadowOf(Looper.getMainLooper()).idle();
        // batched again: not stored yet, not shown
        assertTrue(db.getSyslogs(null, null).isEmpty());
        assertFalse(shownText().contains("after pause"));
        controller.resume();
    }
}
