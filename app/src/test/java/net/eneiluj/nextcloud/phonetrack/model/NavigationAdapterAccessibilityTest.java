package net.eneiluj.nextcloud.phonetrack.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Paint;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class NavigationAdapterAccessibilityTest {

    private static final NavigationAdapter.ClickListener NOOP = new NavigationAdapter.ClickListener() {
        @Override public void onItemClick(NavigationAdapter.NavigationItem item) { }
        @Override public void onIconClick(NavigationAdapter.NavigationItem item) { }
    };

    @Test
    public void recycledViewDoesNotKeepBoldOrSelectedState() {
        AppCompatActivity activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
        NavigationAdapter adapter = new NavigationAdapter(NOOP);
        adapter.setItems(Arrays.asList(
                new NavigationAdapter.NavigationItem("a", "A", 1, R.drawable.ic_allgrey_24dp),
                new NavigationAdapter.NavigationItem("b", "B", null, R.drawable.ic_allgrey_24dp)));
        FrameLayout parent = new FrameLayout(activity);
        NavigationAdapter.ViewHolder holder = adapter.onCreateViewHolder(parent, 0);

        adapter.setSelectedItem("a");
        adapter.onBindViewHolder(holder, 0);
        assertTrue((holder.name.getPaintFlags() & Paint.FAKE_BOLD_TEXT_FLAG) != 0);
        assertTrue((holder.count.getPaintFlags() & Paint.FAKE_BOLD_TEXT_FLAG) != 0);
        assertEquals("selected", ViewCompat.getStateDescription(holder.itemView).toString());

        // the same holder is recycled for the unselected item
        adapter.onBindViewHolder(holder, 1);
        assertEquals(0, holder.name.getPaintFlags() & Paint.FAKE_BOLD_TEXT_FLAG);
        assertEquals(0, holder.count.getPaintFlags() & Paint.FAKE_BOLD_TEXT_FLAG);
        assertNull(ViewCompat.getStateDescription(holder.itemView));
    }

    @Test
    public void iconIsNotASeparateFocusStopAndOnlyClickableWhenItHasItsOwnAction() {
        AppCompatActivity activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
        NavigationAdapter adapter = new NavigationAdapter(NOOP);
        adapter.setItems(Arrays.asList(
                new NavigationAdapter.NavigationItem("plain", "Plain", null, R.drawable.ic_allgrey_24dp),
                new NavigationAdapter.NavigationItem("folder", "Folder", 2, NavigationAdapter.ICON_MULTIPLE)));
        NavigationAdapter.ViewHolder holder = adapter.onCreateViewHolder(new FrameLayout(activity), 0);

        adapter.onBindViewHolder(holder, 0);
        assertFalse(holder.icon.isClickable());
        assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO, holder.icon.getImportantForAccessibility());

        adapter.onBindViewHolder(holder, 1);
        assertTrue(holder.icon.isClickable());
        // ... and a recycled plain row loses it again
        adapter.onBindViewHolder(holder, 0);
        assertFalse(holder.icon.isClickable());
    }
}
