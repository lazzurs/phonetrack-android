package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;

import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.fragment.PreferencesFragment;
import net.eneiluj.nextcloud.phonetrack.util.EdgeToEdgeUtil;
import net.eneiluj.nextcloud.phonetrack.util.ThemeUtils;


/**
 * Allows to change application settings.
 */
public class PreferencesActivity extends AppCompatActivity {

    private static final String TAG = PreferencesActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        View view = LayoutInflater.from(this).inflate(R.layout.activity_preferences, null);
        setContentView(view);
        EdgeToEdgeUtil.enable(this);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container_view, new PreferencesFragment(), "preftag")
                .commit();

        // onBackPressed() is no longer called with predictive back (default from targetSdk 36)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                NavUtils.navigateUpFromSameTask(PreferencesActivity.this);
            }
        });
    }
}
