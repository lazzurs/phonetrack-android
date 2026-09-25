package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertEquals;

import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;

@RunWith(AndroidJUnit4.class)
public class EdgeToEdgeUtilTest {

    @Test
    public void padsContentBySystemBarsAndKeyboard() {
        ComponentActivity activity = Robolectric.buildActivity(ComponentActivity.class).setup().get();
        EdgeToEdgeUtil.enable(activity);
        View content = activity.findViewById(android.R.id.content);

        WindowInsetsCompat bars = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 60, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 40))
                .build();
        ViewCompat.dispatchApplyWindowInsets(content, bars);
        assertEquals(60, content.getPaddingTop());
        assertEquals(40, content.getPaddingBottom());

        WindowInsetsCompat withIme = new WindowInsetsCompat.Builder(bars)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 700))
                .build();
        ViewCompat.dispatchApplyWindowInsets(content, withIme);
        assertEquals(60, content.getPaddingTop());
        assertEquals(700, content.getPaddingBottom());
    }
}
