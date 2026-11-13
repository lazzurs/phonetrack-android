package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Text and icons on the app colour must stay readable whatever colour the user or server picks. */
@RunWith(AndroidJUnit4.class)
public class ThemeUtilsContrastTest {

    // the colour dialog's presets (PreferencesFragment.PRESET_COLORS) plus a few extremes
    private static final int[] COLORS = {
            0xFF0082C9, 0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
            0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688,
            0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39, 0xFFFFC107, 0xFFFF9800,
            0xFFFF5722, 0xFF795548, 0xFF9E9E9E, 0xFF607D8B, 0xFF000000,
            0xFFFFFFFF, 0xFFFFFF00, 0xFF777777, 0xFF0000FF,
    };

    private final Context context = ApplicationProvider.getApplicationContext();

    @After
    public void tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    private static double contrast(int foreground, int background) {
        return ColorUtils.calculateContrast(foreground, background);
    }

    @Test
    public void foregroundIsWhiteUnlessWhiteIsBelowThreeToOne() {
        for (int color : COLORS) {
            int fg = ThemeUtils.onPrimaryColor(color);
            String hex = String.format("#%06X", color & 0xFFFFFF);
            assertTrue(hex, fg == Color.BLACK || fg == Color.WHITE);
            // WCAG minimum for icons and large text
            assertTrue(hex + " " + contrast(fg, color), contrast(fg, color) >= 3.0);
            assertEquals(hex, contrast(Color.WHITE, color) >= 3.0 ? Color.WHITE : Color.BLACK, fg);
        }
    }

    @Test
    public void defaultBlueKeepsWhiteAndLightColoursGetBlack() {
        assertEquals(Color.WHITE, ThemeUtils.onPrimaryColor(0xFF0082C9)); // default Nextcloud blue, 4.2:1
        assertEquals(Color.WHITE, ThemeUtils.onPrimaryColor(0xFF3F51B5)); // indigo
        assertEquals(Color.WHITE, ThemeUtils.onPrimaryColor(Color.BLACK));
        assertEquals(Color.BLACK, ThemeUtils.onPrimaryColor(0xFFCDDC39)); // lime
        assertEquals(Color.BLACK, ThemeUtils.onPrimaryColor(0xFFFFEB3B)); // yellow
        assertEquals(Color.BLACK, ThemeUtils.onPrimaryColor(0xFFFFFF00));
        assertEquals(Color.BLACK, ThemeUtils.onPrimaryColor(0xFFFFC107)); // amber
        assertEquals(Color.BLACK, ThemeUtils.onPrimaryColor(0xFF03A9F4)); // light blue, white 2.6:1
        assertEquals(Color.BLACK, ThemeUtils.onPrimaryColor(Color.WHITE));
    }

    @Test
    public void translucentColoursAreJudgedAsOpaque() {
        assertEquals(ThemeUtils.onPrimaryColor(0xFFCDDC39), ThemeUtils.onPrimaryColor(0x22CDDC39));
        assertEquals(ThemeUtils.onPrimaryColor(0xFF3F51B5), ThemeUtils.onPrimaryColor(0x223F51B5));
    }

    @Test
    public void drawerHeaderTextIsReadableAcrossTheWholeGradient() {
        for (int color : COLORS) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putInt(context.getString(R.string.pref_key_color), color).commit();
            LinearLayout header = new LinearLayout(context);
            LinearLayout inner = new LinearLayout(context);
            TextView title = new TextView(context);
            inner.addView(title);
            header.addView(inner);

            ThemeUtils.styleDrawerHeader(context, header);

            int fg = title.getCurrentTextColor();
            assertEquals(ThemeUtils.onPrimaryColor(color), fg);
            int[] gradient = ((GradientDrawable) header.getBackground()).getColors();
            String hex = String.format("#%06X", color & 0xFFFFFF);
            assertEquals(hex, color, gradient[0]);
            // the gradient only moves away from the text colour
            for (int stop : gradient) {
                assertTrue(hex, contrast(fg, stop) >= contrast(fg, color));
                assertTrue(hex, contrast(fg, stop) >= 3.0);
            }
        }
    }
}
