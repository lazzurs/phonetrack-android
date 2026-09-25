package net.eneiluj.nextcloud.phonetrack.util;

import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Apps targeting API 35+ are always laid out edge-to-edge: the window extends behind the
 * status and navigation bars, and the status/navigation bar colors set in the theme are
 * ignored. This keeps the existing layouts clear of the system bars, display cutouts and
 * the on-screen keyboard.
 */
public final class EdgeToEdgeUtil {

    private EdgeToEdgeUtil() {
    }

    /**
     * Call from onCreate(), after super.onCreate(). Makes the system bars transparent with
     * icon colors matching the (day/night) theme, and pads the activity's content view by
     * the system bar, display cutout and IME insets.
     */
    public static void enable(ComponentActivity activity) {
        EdgeToEdge.enable(activity);
        View content = activity.findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            // consumed here so views with fitsSystemWindows don't pad a second time
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
