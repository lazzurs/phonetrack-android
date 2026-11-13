package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import android.view.View;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
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
import org.robolectric.annotation.Config;

/** Row swipes follow the reading direction: towards the end turns on or off, towards the start deletes. */
@RunWith(AndroidJUnit4.class)
public class SwipeDirectionTest {

    private Application app;
    private ActivityController<LogjobsListViewActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        DbTestSupport.resetDatabase();
    }

    @Test
    public void toggleLabelIsRevealedBySwipingTowardsTheEnd() {
        assertTrue(LogjobsListViewActivity.swipeRevealsToggle(40f, false));
        assertFalse(LogjobsListViewActivity.swipeRevealsToggle(-40f, false));
        assertTrue(LogjobsListViewActivity.swipeRevealsToggle(-40f, true));
        assertFalse(LogjobsListViewActivity.swipeRevealsToggle(40f, true));
    }

    private RecyclerView.ViewHolder firstRow(LogjobsListViewActivity activity) throws InterruptedException {
        RecyclerView list = ListActivityTestSupport.awaitRows(activity, 1);
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1920);
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(0);
        assertNotNull(holder);
        return holder;
    }

    @Test
    @Config(qualifiers = "ar-ldrtl")
    public void inRightToLeftTheToggleIsASwipeToTheLeft() throws Exception {
        long id = ListActivityTestSupport.addLogjob(app, "Hike", false);
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        LogjobsListViewActivity activity = controller.get();
        RecyclerView list = ListActivityTestSupport.awaitRows(activity, 1);
        RecyclerView.ViewHolder holder = firstRow(activity);

        // relative directions (the swipe part of the flags is shifted by one action state)
        int flags = activity.swipeCallback.getMovementFlags(list, holder);
        assertEquals(ItemTouchHelper.START | ItemTouchHelper.END, (flags >> 8) & 0xff);
        int absolute = activity.swipeCallback.convertToAbsoluteDirection(ItemTouchHelper.END, View.LAYOUT_DIRECTION_RTL);
        assertEquals(ItemTouchHelper.LEFT, absolute);

        // the helper reports the relative direction: END turns the job on
        activity.swipeCallback.onSwiped(holder, ItemTouchHelper.END);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(PhoneTrackSQLiteOpenHelper.getInstance(app).getLogjob(id).isEnabled());
    }

    @Test
    public void swipingTowardsTheStartDeletesADisabledJob() throws Exception {
        long id = ListActivityTestSupport.addLogjob(app, "Hike", false);
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        LogjobsListViewActivity activity = controller.get();
        RecyclerView.ViewHolder holder = firstRow(activity);

        activity.swipeCallback.onSwiped(holder, ItemTouchHelper.START);
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(PhoneTrackSQLiteOpenHelper.getInstance(app).getLogjob(id));
    }
}
