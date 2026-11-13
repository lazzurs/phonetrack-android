package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.util.SmsInfoStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;

import java.util.Arrays;
import java.util.List;

/** Exported activities must not act on extras another app supplied. */
@RunWith(AndroidJUnit4.class)
public class ExportedActivityExtrasTest {

    private final Application app = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        ShadowToast.reset();
    }

    @After
    public void tearDown() {
        DbTestSupport.resetDatabase();
    }

    private void launchList(Intent intent) {
        Robolectric.buildActivity(LogjobsListViewActivity.class, intent).setup();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void forgedSmsExtrasShowNoDialog() {
        Intent forged = new Intent(app, LogjobsListViewActivity.class);
        forged.putExtra("net.eneiluj.nextcloud.phonetrack.smsinfoContent", "Your account is at risk");
        forged.putExtra("net.eneiluj.nextcloud.phonetrack.smsinfoFrom", "+44 7700 900123");
        forged.putExtra(SmsInfoStore.EXTRA_TOKEN, "guess");
        launchList(forged);
        assertNull(ShadowAlertDialog.getLatestAlertDialog());
    }

    @Test
    public void tokenWithNothingStoredShowsNoDialog() {
        Intent forged = new Intent(app, LogjobsListViewActivity.class);
        forged.putExtra(SmsInfoStore.EXTRA_TOKEN, "00");
        launchList(forged);
        assertNull(ShadowAlertDialog.getLatestAlertDialog());
    }

    @Test
    public void realNotificationIntentShowsTheDialog() {
        Intent real = SmsInfoStore.save(app, "position text", "Alice");
        launchList(real);
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog());
    }

    @Test
    public void staleTokenIsRejectedAfterANewSms() {
        Intent old = SmsInfoStore.save(app, "first", "Alice");
        SmsInfoStore.save(app, "second", "Bob");
        launchList(old);
        assertNull(ShadowAlertDialog.getLatestAlertDialog());
    }

    private Intent share(Class<?> target, String text) {
        Intent intent = new Intent(Intent.ACTION_SEND, null, app, target);
        intent.setType("text/plain");
        if (text != null) {
            intent.putExtra(Intent.EXTRA_TEXT, text);
        }
        return intent;
    }

    @Test
    public void shareIgnoresLogjobId() {
        List<Class<? extends EditLogjobActivity>> editors =
                Arrays.asList(EditPhoneTrackLogjobActivity.class, EditCustomLogjobActivity.class);
        for (Class<? extends EditLogjobActivity> cls : editors) {
            Intent intent = share(cls, null);
            intent.putExtra(EditLogjobActivity.PARAM_LOGJOB_ID, 1L);
            EditLogjobActivity activity = Robolectric.buildActivity(cls, intent).setup().get();
            assertEquals(cls.getSimpleName(), 0L, activity.getLogjobId());
        }
    }

    @Test
    public void intentSentToTheShareAliasIgnoresLogjobId() {
        // an explicit intent to the exported alias can carry any action and extras
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setComponent(new ComponentName(app, "net.eneiluj.nextcloud.phonetrack.android.activity.ShareCustomLogjobAlias"));
        intent.putExtra(EditLogjobActivity.PARAM_LOGJOB_ID, 1L);
        EditLogjobActivity activity = Robolectric.buildActivity(EditCustomLogjobActivity.class, intent).setup().get();
        assertEquals(0L, activity.getLogjobId());
    }

    @Test
    public void appsOwnIntentStillOpensAnExistingLogjob() {
        Intent intent = new Intent(app, EditPhoneTrackLogjobActivity.class);
        intent.putExtra(EditLogjobActivity.PARAM_LOGJOB_ID, 7L);
        EditLogjobActivity activity = Robolectric.buildActivity(EditPhoneTrackLogjobActivity.class, intent).get();
        assertEquals(7L, activity.getLogjobId());
    }

    @Test
    public void shareWithoutTextDoesNotCrash() {
        Robolectric.buildActivity(EditPhoneTrackLogjobActivity.class, share(EditPhoneTrackLogjobActivity.class, null)).setup();
        assertEquals(app.getString(R.string.error_invalid_pt_url), ShadowToast.getTextOfLatestToast());

        ShadowToast.reset();
        Robolectric.buildActivity(EditCustomLogjobActivity.class, share(EditCustomLogjobActivity.class, null)).setup();
        assertEquals(app.getString(R.string.error_invalid_url), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void sharedLinkTellsTheUserItCameFromAnotherApp() {
        Robolectric.buildActivity(EditCustomLogjobActivity.class,
                share(EditCustomLogjobActivity.class, "https://example.test/log?lat=%LAT")).setup();
        assertEquals(app.getString(R.string.shared_url_prefilled), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void newLogjobFromTheAppShowsNoPrefillNotice() {
        Robolectric.buildActivity(EditCustomLogjobActivity.class, new Intent(app, EditCustomLogjobActivity.class)).setup();
        assertEquals(0, ShadowToast.shownToastCount());
    }
}
