package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.recyclerview.widget.RecyclerView;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import java.time.Duration;

/** Shared helpers for tests of the log job list. */
final class ListActivityTestSupport {

    private ListActivityTestSupport() {
    }

    static long addLogjob(Context context, String title, boolean enabled) {
        return PhoneTrackSQLiteOpenHelper.getInstance(context).addLogjob(new DBLogjob(0, title,
                "http://127.0.0.1:9/log", "", "", 60, 0, 50, false, false, false, 60,
                false, enabled, 0, null, null, false));
    }

    /** The list is loaded in the background: wait until it shows this many rows. */
    static RecyclerView awaitRows(LogjobsListViewActivity activity, int rows) throws InterruptedException {
        RecyclerView list = activity.findViewById(R.id.recycler_view);
        for (int i = 0; i < 100 && (list.getAdapter() == null || list.getAdapter().getItemCount() < rows); i++) {
            Thread.sleep(10);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));
        }
        shadowOf(Looper.getMainLooper()).idle();
        return list;
    }
}
