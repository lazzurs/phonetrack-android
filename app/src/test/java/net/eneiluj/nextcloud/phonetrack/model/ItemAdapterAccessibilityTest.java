package net.eneiluj.nextcloud.phonetrack.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.CorrectingLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;

import java.util.ArrayList;
import java.util.List;

/** The log job row must be understandable without seeing it: names, state and counts as text. */
@RunWith(AndroidJUnit4.class)
public class ItemAdapterAccessibilityTest {

    private PhoneTrackSQLiteOpenHelper db;
    private AppCompatActivity activity;
    private ItemAdapter adapter;
    private int deleteActionPosition = -1;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
        db = PhoneTrackSQLiteOpenHelper.getInstance(activity);
        adapter = new ItemAdapter(new ItemAdapter.LogjobClickListener() {
            @Override public void onLogjobClick(int position, View v) { }
            @Override public void onLogjobEnabledClick(int position, View v) { }
            @Override public void onLogjobMoreButtonClick(int position, View v) { }
            @Override public boolean onLogjobLongClick(int position, View v) { return false; }
            @Override public void onLogjobDeleteAction(int position) { deleteActionPosition = position; }
        }, db);
    }

    @After
    public void tearDown() {
        DbTestSupport.resetDatabase();
    }

    private ItemAdapter.LogjobViewHolder bind(String title, boolean enabled, int unsent) {
        long id = db.addLogjob(new DBLogjob(0, title, "http://127.0.0.1:9/log", "", "",
                60, 0, 50, false, false, false, 60, false, enabled, 0, null, null, false));
        for (int i = 0; i < unsent; i++) {
            Location location = new Location(LocationManager.GPS_PROVIDER);
            location.setLatitude(53.35);
            location.setLongitude(-6.26);
            location.setTime(System.currentTimeMillis());
            db.addLocation(id, new CorrectingLocation(location), 80.0);
        }
        List<Item> items = new ArrayList<>();
        items.add(db.getLogjob(id));
        adapter.setItemList(items);
        ItemAdapter.LogjobViewHolder holder = (ItemAdapter.LogjobViewHolder)
                adapter.onCreateViewHolder(new FrameLayout(activity), 1);
        adapter.onBindViewHolder(holder, 0);
        return holder;
    }

    @Test
    public void moreButtonAndSwitchNameTheJob() {
        ItemAdapter.LogjobViewHolder holder = bind("Hike", true, 0);
        assertEquals("More options for Hike", holder.moreButton.getContentDescription().toString());
        assertEquals("Hike", holder.logjobEnabled.getContentDescription().toString());
    }

    @Test
    public void rowSummarisesStateAndPendingCount() {
        ItemAdapter.LogjobViewHolder holder = bind("Hike", true, 12);
        String summary = holder.itemView.getContentDescription().toString();
        assertTrue(summary, summary.startsWith("Hike, enabled, "));
        assertTrue(summary, summary.contains("searching for location") || summary.contains("location found"));
        assertTrue(summary, summary.endsWith("12 positions waiting to upload"));
    }

    @Test
    public void disabledRowSaysSoAndHasNoFixText() {
        ItemAdapter.LogjobViewHolder holder = bind("Commute", false, 1);
        assertEquals("Commute, disabled, 1 position waiting to upload",
                holder.itemView.getContentDescription().toString());
    }

    @Test
    public void statusIconsAreHiddenFromTalkBack() {
        ItemAdapter.LogjobViewHolder holder = bind("Hike", true, 2);
        for (int id : new int[]{R.id.locSuccessIcon, R.id.nosyncIcon, R.id.syncIcon, R.id.nbNotSync, R.id.nbSync}) {
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                    holder.itemView.findViewById(id).getImportantForAccessibility());
        }
    }

    @Test
    public void pendingAndUploadedUseDifferentGlyphs() {
        // Colour must not be the only difference between "waiting to upload" and "uploaded".
        Context c = activity;
        assertFalse(c.getDrawable(R.drawable.ic_cloud_upload_grey_24dp).getConstantState()
                .equals(c.getDrawable(R.drawable.ic_cloud_done_grey_24dp).getConstantState()));
        ItemAdapter.LogjobViewHolder holder = bind("Hike", true, 0);
        // layout wires the two different drawables to the two icons (ids are stable, so check via resources)
        assertEquals(R.id.nosyncIcon, holder.nosyncIcon.getId());
        assertEquals(R.id.syncIcon, holder.syncIcon.getId());
        assertFalse(holder.nosyncIcon.getDrawable().getConstantState()
                .equals(holder.syncIcon.getDrawable().getConstantState()));
    }

    @Test
    public void rowShowsAFailingUploadUntilTheNextSuccess() {
        ItemAdapter.LogjobViewHolder holder = bind("Hike", true, 1);
        assertEquals(View.GONE, holder.uploadError.getVisibility());

        long id = ((DBLogjob) adapter.getItem(0)).getId();
        db.setLastSyncError(id, System.currentTimeMillis() / 1000, "The server refused the login (HTTP 401)");
        adapter.onBindViewHolder(holder, 0);
        assertEquals(View.VISIBLE, holder.uploadError.getVisibility());
        assertEquals("Upload failing: The server refused the login (HTTP 401)", holder.uploadError.getText().toString());
        String summary = holder.itemView.getContentDescription().toString();
        assertTrue(summary, summary.endsWith("Upload failing: The server refused the login (HTTP 401)"));

        db.resetLastSyncError(id);
        adapter.onBindViewHolder(holder, 0);
        assertEquals(View.GONE, holder.uploadError.getVisibility());
    }

    @Test
    public void rowOffersToggleAsAccessibilityAction() {
        ItemAdapter.LogjobViewHolder holder = bind("Hike", false, 0);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        holder.itemView.onInitializeAccessibilityNodeInfo(info);
        boolean found = false;
        for (AccessibilityNodeInfo.AccessibilityAction a : info.getActionList()) {
            found |= "Turn on or off".contentEquals(a.getLabel());
        }
        assertTrue(found);
    }

    @Test
    public void rowOffersDeleteAsAccessibilityAction() {
        bind("Hike", false, 0);
        // attached to a RecyclerView so that the holder knows its position
        androidx.recyclerview.widget.RecyclerView list = new androidx.recyclerview.widget.RecyclerView(activity);
        list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(activity));
        list.setAdapter(adapter);
        list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, 1080, 1920);
        View row = list.getChildAt(0);

        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        row.onInitializeAccessibilityNodeInfo(info);
        int deleteId = -1;
        for (AccessibilityNodeInfo.AccessibilityAction a : info.getActionList()) {
            if ("Delete".contentEquals(String.valueOf(a.getLabel()))) {
                deleteId = a.getId();
            }
        }
        assertTrue("no Delete action", deleteId != -1);
        assertTrue(row.performAccessibilityAction(deleteId, null));
        assertEquals(0, deleteActionPosition);
    }
}
