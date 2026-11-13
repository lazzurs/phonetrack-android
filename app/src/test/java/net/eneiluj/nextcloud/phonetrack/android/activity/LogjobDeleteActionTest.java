package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;

/** The row's "Delete" accessibility action asks first, like the other delete paths. */
@RunWith(AndroidJUnit4.class)
public class LogjobDeleteActionTest {

    private Application app;
    private ActivityController<LogjobsListViewActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() {
        controller.destroy();
        DbTestSupport.resetDatabase();
    }

    @Test
    public void deleteActionConfirmsThenDeletes() throws Exception {
        long id = ListActivityTestSupport.addLogjob(app, "Hike", true);
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        LogjobsListViewActivity activity = controller.get();
        ListActivityTestSupport.awaitRows(activity, 1);

        activity.onLogjobDeleteAction(0);
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull("deleted without asking", dialog);
        assertNotNull(PhoneTrackSQLiteOpenHelper.getInstance(app).getLogjob(id));

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(PhoneTrackSQLiteOpenHelper.getInstance(app).getLogjob(id));
        assertEquals(0, ListActivityTestSupport.awaitRows(activity, 0).getAdapter().getItemCount());
    }
}
