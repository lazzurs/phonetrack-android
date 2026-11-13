package net.eneiluj.nextcloud.phonetrack.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class RtlLayoutTest {

    /** In a right-to-left locale the status overlays must sit at the start of the row, away from the switch. */
    @Test
    @Config(qualifiers = "ar-ldrtl")
    public void statusOverlaysDoNotCoverTheTitleInRtl() {
        AppCompatActivity activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
        FrameLayout parent = new FrameLayout(activity);
        View row = LayoutInflater.from(activity).inflate(R.layout.fragment_logjobs_list_logjob_item, parent, false);
        assertEquals(View.LAYOUT_DIRECTION_RTL, activity.getResources().getConfiguration().getLayoutDirection());
        // a detached root cannot resolve the locale itself, so give it the direction the window would
        parent.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        parent.addView(row);
        // a long title that wants all the space it can get
        ((android.widget.TextView) row.findViewById(R.id.logjobTitle))
                .setText("A very long log job title that cannot possibly fit on one line");
        parent.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        parent.layout(0, 0, parent.getMeasuredWidth(), parent.getMeasuredHeight());

        View switchView = row.findViewById(R.id.logjobEnabled);
        View locIcon = row.findViewById(R.id.locSuccessIcon);
        Rect sw = bounds(row, switchView);
        Rect loc = bounds(row, locIcon);
        // RTL: the switch is on the left edge, and so are the overlays that were pinned to it
        assertTrue("switch should be on the left in RTL: " + sw, sw.left < row.getWidth() / 2);
        assertTrue("overlay should be on the left in RTL: " + loc, loc.left < row.getWidth() / 2);
    }

    private static Rect bounds(View root, View v) {
        Rect r = new Rect();
        v.getDrawingRect(r);
        ((android.view.ViewGroup) root).offsetDescendantRectToMyCoords(v, r);
        return r;
    }

    /** Cheap lint substitute for the layouts that matter: no physical left/right attributes may creep back. */
    @Test
    public void layoutsUseStartAndEndNotLeftAndRight() throws Exception {
        File dir = new File("src/main/res/layout");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".xml"));
        assertTrue("layout dir not found: " + dir.getAbsolutePath(), files != null && files.length > 0);
        Pattern bad = Pattern.compile(
                "android:(layout_)?(margin|padding)(Left|Right)=|android:layout_(alignParent|align|to)(Left|Right)(Of)?=|"
                        + "android:(layout_)?gravity=\"[^\"]*\\b(left|right)\\b");
        List<String> offenders = new ArrayList<>();
        for (File f : files) {
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if (bad.matcher(text).find()) {
                offenders.add(f.getName());
            }
        }
        assertTrue("left/right attributes in " + offenders, offenders.isEmpty());
    }
}
