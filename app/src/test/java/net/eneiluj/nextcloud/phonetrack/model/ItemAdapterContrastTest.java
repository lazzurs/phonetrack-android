package net.eneiluj.nextcloud.phonetrack.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

/** The log job row stays readable on light app colours and in the dark theme. */
@RunWith(AndroidJUnit4.class)
public class ItemAdapterContrastTest {

    private static final int[] APP_COLOURS = {0xFF0082C9, 0xFFCDDC39, 0xFFFFC107, 0xFF03A9F4,
            0xFF4CAF50, 0xFF9E9E9E, 0xFF3F51B5, 0xFFFF9800};

    @Test
    public void enabledSwitchThumbContrastsWithItsTrackForEveryColour() {
        for (int colour : APP_COLOURS) {
            int[] colours = ItemAdapter.checkedSwitchColors(colour);
            assertEquals(colour, colours[0]);
            String hex = String.format("#%06X", colour & 0xFFFFFF);
            assertTrue(hex, ColorUtils.calculateContrast(colours[1], colours[0]) >= 3.0);
        }
    }

    @Test
    public void lightColourGetsDarkThumbAndDarkColourKeepsWhite() {
        assertEquals(Color.BLACK, ItemAdapter.checkedSwitchColors(0xFFCDDC39)[1]);
        assertEquals(Color.WHITE, ItemAdapter.checkedSwitchColors(0xFF3F51B5)[1]);
    }

    private double pendingIconContrast() {
        AppCompatActivity activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
        View row = LayoutInflater.from(activity).inflate(
                R.layout.fragment_logjobs_list_logjob_item, new FrameLayout(activity), false);
        ImageView icon = row.findViewById(R.id.nosyncIcon);
        int tint = ImageViewCompat.getImageTintList(icon).getDefaultColor();
        return ColorUtils.calculateContrast(tint, ContextCompat.getColor(activity, R.color.bg_normal));
    }

    @Test
    @Config(qualifiers = "night")
    public void pendingIconIsVisibleOnTheDarkBackground() {
        assertTrue(pendingIconContrast() >= 3.0);
    }

    @Test
    public void pendingIconIsVisibleOnTheLightBackground() {
        assertTrue(pendingIconContrast() >= 3.0);
    }
}
