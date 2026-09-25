package net.eneiluj.nextcloud.phonetrack.util;

import android.app.Dialog;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Shows and hides the on-screen keyboard. Replaces InputMethodManager.toggleSoftInput(),
 * deprecated since API 31, which toggles: a "hide" call could open the keyboard instead.
 */
public final class KeyboardUtil {

    private KeyboardUtil() {
    }

    /**
     * Focus the dialog's text field and show the keyboard. The keyboard belongs to the dialog
     * window, so it closes with the dialog: no need to hide it on OK / Cancel.
     */
    public static void showForDialog(Dialog dialog, EditText field) {
        Window window = dialog.getWindow();
        field.setSelectAllOnFocus(true);
        field.requestFocus();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            WindowCompat.getInsetsController(window, field).show(WindowInsetsCompat.Type.ime());
        }
    }

    /** Hide the keyboard of the window this view is in. */
    public static void hide(Window window, View view) {
        WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime());
    }
}
