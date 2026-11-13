package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import java.time.Duration;

/** A new user sees what to do instead of a blank list. */
@RunWith(AndroidJUnit4.class)
public class LogjobsListEmptyStateTest {

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

    /** The list loads in the background: wait until the empty view has its final state. */
    private View emptyViewAfterLoad(int expectedVisibility) throws InterruptedException {
        View empty = controller.get().findViewById(R.id.empty_view);
        for (int i = 0; i < 100 && empty.getVisibility() != expectedVisibility; i++) {
            Thread.sleep(10);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));
        }
        return empty;
    }

    @Test
    public void emptyListExplainsWhatToDo() throws Exception {
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        assertEquals(View.VISIBLE, emptyViewAfterLoad(View.VISIBLE).getVisibility());
    }

    @Test
    public void goneOnceThereIsALogjob() throws Exception {
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        assertEquals(View.VISIBLE, emptyViewAfterLoad(View.VISIBLE).getVisibility());

        ListActivityTestSupport.addLogjob(app, "Hike", false);
        controller.pause().resume();
        assertEquals(View.GONE, emptyViewAfterLoad(View.GONE).getVisibility());
        assertEquals(1, ListActivityTestSupport.awaitRows(controller.get(), 1).getAdapter().getItemCount());
    }
}
