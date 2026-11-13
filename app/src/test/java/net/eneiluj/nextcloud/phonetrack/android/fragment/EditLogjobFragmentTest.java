package net.eneiluj.nextcloud.phonetrack.android.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.textfield.TextInputLayout;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.EditCustomLogjobActivity;
import net.eneiluj.nextcloud.phonetrack.android.activity.EditLogjobActivity;
import net.eneiluj.nextcloud.phonetrack.android.activity.EditMapsLogjobActivity;
import net.eneiluj.nextcloud.phonetrack.android.activity.EditPhoneTrackLogjobActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;

/** The log job editor: unsaved changes on Back, per-field errors, number parsing, placeholders. */
@RunWith(AndroidJUnit4.class)
public class EditLogjobFragmentTest {

    private final Application app = ApplicationProvider.getApplicationContext();
    private PhoneTrackSQLiteOpenHelper db;
    private ActivityController<? extends EditLogjobActivity> controller;
    private EditLogjobActivity activity;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        db = PhoneTrackSQLiteOpenHelper.getInstance(app);
        ShadowToast.reset();
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.pause().stop().destroy();
        }
        DbTestSupport.resetDatabase();
    }

    private long addCustomJob(String title, String url) {
        return db.addLogjob(new DBLogjob(
                0, title, url, "", "",
                60, 5, 50, false, false, false, 30,
                false, false, 0, null, null, false));
    }

    private long addPhoneTrackJob(String title) {
        return db.addLogjob(new DBLogjob(
                0, title, "https://cloud.example.test", "tok3n", "phone",
                60, 5, 50, false, false, false, 30,
                false, false, 0, null, null, false));
    }

    private <T extends EditLogjobActivity> void open(Class<T> cls, long logjobId) {
        Intent intent = new Intent(app, cls);
        if (logjobId > 0) {
            intent.putExtra(EditLogjobActivity.PARAM_LOGJOB_ID, logjobId);
        }
        ActivityController<T> c = Robolectric.buildActivity(cls, intent).setup();
        controller = c;
        activity = c.get();
        idle();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private EditLogjobFragment fragment() {
        return (EditLogjobFragment) activity.getSupportFragmentManager().findFragmentById(android.R.id.content);
    }

    private EditText field(int id) {
        return activity.findViewById(id);
    }

    private TextInputLayout layout(int id) {
        return activity.findViewById(id);
    }

    private void type(int fieldId, String text) {
        field(fieldId).setText(text);
        idle();
    }

    private String error(int layoutId) {
        CharSequence error = layout(layoutId).getError();
        return error == null ? null : error.toString();
    }

    private void tapSave() {
        View fab = activity.findViewById(R.id.fab_edit_ok);
        assertEquals("Save stays visible", View.VISIBLE, fab.getVisibility());
        fab.performClick();
        idle();
    }

    private void pressBack() {
        activity.getOnBackPressedDispatcher().onBackPressed();
        idle();
    }

    private AlertDialog latestDialog() {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        return dialog != null && dialog.isShowing() ? dialog : null;
    }

    private static boolean shows(AlertDialog dialog, int which) {
        Button button = dialog.getButton(which);
        return button != null && button.getVisibility() == View.VISIBLE;
    }

    private String plural(int id, int n) {
        return app.getResources().getQuantityString(id, n, n);
    }

    // Back and Up

    @Test
    public void backWithoutChangesClosesWithoutAsking() {
        open(EditCustomLogjobActivity.class, addCustomJob("Old", "https://example.test/log"));
        pressBack();
        assertNull(latestDialog());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void backOnANewUntouchedJobClosesWithoutAsking() {
        open(EditCustomLogjobActivity.class, 0);
        pressBack();
        assertNull(latestDialog());
        assertTrue(activity.isFinishing());
        assertEquals(0, db.getLogjobs().size());
    }

    @Test
    public void backWithChangesAsksAndKeepEditingStays() {
        long id = addCustomJob("Old", "https://example.test/log");
        open(EditCustomLogjobActivity.class, id);
        type(R.id.editTitle, "New");
        assertTrue(fragment().hasUnsavedChanges());

        pressBack();
        AlertDialog dialog = latestDialog();
        assertNotNull("Back with changes asks", dialog);
        assertFalse(activity.isFinishing());
        assertTrue(shows(dialog, AlertDialog.BUTTON_POSITIVE));
        assertTrue(shows(dialog, AlertDialog.BUTTON_NEGATIVE));
        assertTrue(shows(dialog, AlertDialog.BUTTON_NEUTRAL));
        assertEquals(app.getString(R.string.edit_unsaved_save), dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        assertEquals(app.getString(R.string.edit_unsaved_discard), dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
        assertEquals(app.getString(R.string.edit_unsaved_keep_editing), dialog.getButton(AlertDialog.BUTTON_NEUTRAL).getText().toString());

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        idle();
        assertFalse(activity.isFinishing());
        assertEquals("New", field(R.id.editTitle).getText().toString());
        assertEquals("Old", db.getLogjob(id).getTitle());
    }

    @Test
    public void saveInTheBackDialogSavesAndCloses() {
        long id = addCustomJob("Old", "https://example.test/log");
        open(EditCustomLogjobActivity.class, id);
        type(R.id.editTitle, "New");
        pressBack();
        latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals("New", db.getLogjob(id).getTitle());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void discardInTheBackDialogClosesWithoutSaving() {
        long id = addCustomJob("Old", "https://example.test/log");
        open(EditCustomLogjobActivity.class, id);
        type(R.id.editTitle, "New");
        pressBack();
        latestDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle();
        assertEquals("Old", db.getLogjob(id).getTitle());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void upArrowAsksLikeBack() {
        long id = addCustomJob("Old", "https://example.test/log");
        open(EditCustomLogjobActivity.class, id);
        type(R.id.editTitle, "New");
        activity.onOptionsItemSelected(new RoboMenuItem(android.R.id.home));
        idle();
        assertNotNull(latestDialog());
        assertFalse(activity.isFinishing());
    }

    @Test
    public void upArrowWithoutChangesCloses() {
        open(EditCustomLogjobActivity.class, addCustomJob("Old", "https://example.test/log"));
        activity.onOptionsItemSelected(new RoboMenuItem(android.R.id.home));
        idle();
        assertNull(latestDialog());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void undoingAChangeMeansNothingToAskAbout() {
        open(EditCustomLogjobActivity.class, addCustomJob("Old", "https://example.test/log"));
        type(R.id.editTitle, "New");
        type(R.id.editTitle, "Old");
        assertFalse(fragment().hasUnsavedChanges());
        pressBack();
        assertNull(latestDialog());
        assertTrue(activity.isFinishing());
    }

    @Test
    public void checkboxChangesCountAsUnsavedChanges() {
        open(EditCustomLogjobActivity.class, addCustomJob("Old", "https://example.test/log"));
        ((android.widget.CheckBox) activity.findViewById(R.id.keepgpson)).performClick();
        idle();
        pressBack();
        assertNotNull(latestDialog());
    }

    @Test
    public void invalidChangesOfferOnlyDiscardOrKeepEditing() {
        long id = addCustomJob("Old", "https://example.test/log");
        open(EditCustomLogjobActivity.class, id);
        type(R.id.editTitle, "");
        pressBack();
        AlertDialog dialog = latestDialog();
        assertNotNull(dialog);
        assertFalse("an invalid form cannot be saved", shows(dialog, AlertDialog.BUTTON_POSITIVE));
        assertTrue(shows(dialog, AlertDialog.BUTTON_NEGATIVE));

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        idle();
        assertFalse(activity.isFinishing());
        assertEquals(app.getString(R.string.edit_error_title_required), error(R.id.input_layout_title));
        assertTrue(field(R.id.editTitle).hasFocus());
        assertEquals("Old", db.getLogjob(id).getTitle());
    }

    @Test
    public void unsavedChangesSurviveRecreation() {
        open(EditCustomLogjobActivity.class, addCustomJob("Old", "https://example.test/log"));
        type(R.id.editTitle, "New");
        controller.recreate();
        activity = controller.get();
        idle();
        assertEquals("New", field(R.id.editTitle).getText().toString());
        pressBack();
        assertNotNull(latestDialog());
    }

    // Save and per-field errors

    @Test
    public void newJobShowsNoErrorsBeforeAnyEdit() {
        open(EditCustomLogjobActivity.class, 0);
        assertNull(error(R.id.input_layout_title));
        assertNull(error(R.id.input_layout_url));
    }

    @Test
    public void saveOnAnInvalidNewJobExplainsAndSavesNothing() {
        open(EditCustomLogjobActivity.class, 0);
        tapSave();
        assertEquals(app.getString(R.string.edit_error_title_required), error(R.id.input_layout_title));
        assertEquals(app.getString(R.string.edit_error_url_invalid), error(R.id.input_layout_url));
        assertTrue("the first invalid field gets the focus", field(R.id.editTitle).hasFocus());
        assertFalse(activity.isFinishing());
        assertEquals(0, db.getLogjobs().size());
    }

    @Test
    public void validNewJobIsSaved() {
        open(EditCustomLogjobActivity.class, 0);
        type(R.id.editTitle, "Mine");
        type(R.id.editUrl, "https://example.test/log?lat=%LAT");
        tapSave();
        assertTrue(activity.isFinishing());
        assertEquals(1, db.getLogjobs().size());
        assertEquals("https://example.test/log?lat=%LAT", db.getLogjobs().get(0).getUrl());
    }

    @Test
    public void errorAppearsWhileTypingInAField() {
        open(EditCustomLogjobActivity.class, addCustomJob("Old", "https://example.test/log"));
        type(R.id.editMinTime, "0");
        assertEquals(plural(R.plurals.edit_error_min_seconds, 1), error(R.id.input_layout_min_time));
        // other fields stay quiet
        assertNull(error(R.id.input_layout_title));
        type(R.id.editMinTime, "120");
        assertNull(error(R.id.input_layout_min_time));
    }

    @Test
    public void eachNumberFieldExplainsWhatIsWrong() {
        long id = addCustomJob("Old", "https://example.test/log");
        open(EditCustomLogjobActivity.class, id);

        type(R.id.editMinTime, "0");
        assertEquals(plural(R.plurals.edit_error_min_seconds, 1), error(R.id.input_layout_min_time));
        type(R.id.editMinTime, String.valueOf(EditLogjobFragment.MAX_SECONDS + 1));
        assertEquals(plural(R.plurals.edit_error_max_seconds, EditLogjobFragment.MAX_SECONDS), error(R.id.input_layout_min_time));
        type(R.id.editMinTime, "60");

        type(R.id.editMinDistance, "");
        assertEquals(app.getString(R.string.edit_error_number_required), error(R.id.input_layout_min_distance));
        type(R.id.editMinDistance, String.valueOf(EditLogjobFragment.MAX_METRES + 1));
        assertEquals(plural(R.plurals.edit_error_max_metres, EditLogjobFragment.MAX_METRES), error(R.id.input_layout_min_distance));
        type(R.id.editMinDistance, "0");
        assertNull(error(R.id.input_layout_min_distance));

        type(R.id.editMinAccuracy, "0");
        assertEquals(plural(R.plurals.edit_error_min_metres, 1), error(R.id.input_layout_min_accuracy));
        type(R.id.editMinAccuracy, "50");

        type(R.id.editSigMotionTimeout, "60");
        assertEquals(app.getString(R.string.edit_error_timeout_not_below_min_time), error(R.id.input_layout_sign_motion_timeout));
        type(R.id.editSigMotionTimeout, "");
        assertEquals(app.getString(R.string.edit_error_number_required), error(R.id.input_layout_sign_motion_timeout));
        type(R.id.editSigMotionTimeout, "0");
        assertNull(error(R.id.input_layout_sign_motion_timeout));

        tapSave();
        assertTrue(activity.isFinishing());
        assertEquals(0, db.getLogjob(id).getLocationRequestTimeout());
    }

    @Test
    public void urlMustBeAnAddress() {
        open(EditCustomLogjobActivity.class, addCustomJob("Old", "https://example.test/log"));
        type(R.id.editUrl, "not an address");
        assertEquals(app.getString(R.string.edit_error_url_invalid), error(R.id.input_layout_url));
    }

    @Test
    public void phoneTrackJobExplainsTokenAndDeviceName() {
        long id = addPhoneTrackJob("Old");
        open(EditPhoneTrackLogjobActivity.class, id);
        type(R.id.editToken, "");
        type(R.id.editDeviceName, "");
        assertEquals(app.getString(R.string.edit_error_token_required), error(R.id.input_layout_token));
        assertEquals(app.getString(R.string.edit_error_device_name_required), error(R.id.input_layout_device_name));
        tapSave();
        assertFalse(activity.isFinishing());
        assertEquals("tok3n", db.getLogjob(id).getToken());
    }

    @Test
    public void mapsJobDoesNotAskForAnAddress() {
        open(EditMapsLogjobActivity.class, 0);
        type(R.id.editTitle, "Maps");
        tapSave();
        assertTrue(activity.isFinishing());
        assertEquals(1, db.getLogjobs().size());
    }

    @Test
    public void rangesAndUnitsAreShown() {
        open(EditCustomLogjobActivity.class, addCustomJob("Old", "https://example.test/log"));
        assertEquals(app.getString(R.string.edit_unit_seconds), layout(R.id.input_layout_min_time).getSuffixText().toString());
        assertEquals(app.getString(R.string.edit_unit_metres), layout(R.id.input_layout_min_distance).getSuffixText().toString());
        assertEquals(app.getString(R.string.edit_range_seconds, 1, EditLogjobFragment.MAX_SECONDS),
                layout(R.id.input_layout_min_time).getHelperText().toString());
        assertEquals(app.getString(R.string.setting_min_time), layout(R.id.input_layout_min_time).getHint().toString());
    }

    @Test
    public void significantMotionNeedsAtLeastThirtySeconds() {
        android.hardware.SensorManager sensors = (android.hardware.SensorManager) app.getSystemService(android.content.Context.SENSOR_SERVICE);
        shadowOf(sensors).addSensor(org.robolectric.shadows.ShadowSensor.newInstance(android.hardware.Sensor.TYPE_SIGNIFICANT_MOTION));
        long id = addCustomJob("Old", "https://example.test/log");
        open(EditCustomLogjobActivity.class, id);
        ((android.widget.CheckBox) activity.findViewById(R.id.editSignMotionMode)).setChecked(true);
        ((android.widget.CheckBox) activity.findViewById(R.id.significantmotioninterval)).setChecked(true);
        idle();
        type(R.id.editMinTime, "20");
        assertEquals(plural(R.plurals.edit_error_min_seconds, EditLogjobFragment.MIN_TIME_SIG_MOTION), error(R.id.input_layout_min_time));
        assertEquals(app.getString(R.string.edit_range_seconds, EditLogjobFragment.MIN_TIME_SIG_MOTION, EditLogjobFragment.MAX_SECONDS),
                layout(R.id.input_layout_min_time).getHelperText().toString());

        // hybrid mode calls it the maximum time
        type(R.id.editMinTime, "300");
        ((android.widget.CheckBox) activity.findViewById(R.id.usesignificantmotionmixed)).setChecked(true);
        type(R.id.editSigMotionTimeout, "300");
        assertEquals(app.getString(R.string.edit_error_timeout_not_below_max_time), error(R.id.input_layout_sign_motion_timeout));
    }

    // Numbers too long for an int

    @Test
    public void tenDigitsDoNotCrashAndAreRejected() {
        long id = addCustomJob("Old", "https://example.test/log");
        open(EditCustomLogjobActivity.class, id);
        type(R.id.editMinTime, "9999999999");
        type(R.id.editMinDistance, "9999999999");
        type(R.id.editMinAccuracy, "9999999999");
        type(R.id.editSigMotionTimeout, "9999999999");
        assertFalse(fragment().isFormValid());
        assertEquals(plural(R.plurals.edit_error_max_seconds, EditLogjobFragment.MAX_SECONDS), error(R.id.input_layout_min_time));
        assertEquals(plural(R.plurals.edit_error_max_metres, EditLogjobFragment.MAX_METRES), error(R.id.input_layout_min_distance));
        assertEquals(plural(R.plurals.edit_error_max_seconds, EditLogjobFragment.MAX_SECONDS), error(R.id.input_layout_sign_motion_timeout));
        tapSave();
        assertFalse(activity.isFinishing());
        assertEquals(60, db.getLogjob(id).getMinTime());
    }

    @Test
    public void parseNumberNeverThrows() {
        assertEquals(Integer.valueOf(42), EditLogjobFragment.parseNumber("42"));
        assertNull(EditLogjobFragment.parseNumber("9999999999"));
        assertNull(EditLogjobFragment.parseNumber(""));
        assertNull(EditLogjobFragment.parseNumber("abc"));
        assertNull(EditLogjobFragment.parseNumber(null));
    }

    // Placeholders

    @Test
    public void newPhoneTrackJobDoesNotPrefillAnExampleServer() {
        open(EditPhoneTrackLogjobActivity.class, 0);
        assertEquals("", field(R.id.editUrl).getText().toString());
        assertEquals("", field(R.id.editToken).getText().toString());
        assertEquals(app.getString(R.string.default_nextcloud_url),
                layout(R.id.input_layout_url).getPlaceholderText().toString());
        assertFalse(fragment().isFormValid());

        type(R.id.editTitle, "Mine");
        tapSave();
        assertEquals(app.getString(R.string.edit_error_url_invalid), error(R.id.input_layout_url));
        assertEquals(app.getString(R.string.edit_error_token_required), error(R.id.input_layout_token));
        assertEquals(0, db.getLogjobs().size());
    }

    @Test
    public void newCustomJobShowsTheExampleAddressOnlyAsAPlaceholder() {
        open(EditCustomLogjobActivity.class, 0);
        assertEquals("", field(R.id.editUrl).getText().toString());
        assertTrue(layout(R.id.input_layout_url).getPlaceholderText().toString().contains("%LAT"));
        type(R.id.editTitle, "Mine");
        tapSave();
        assertEquals(0, db.getLogjobs().size());
    }

    @Test
    public void unparsableLoggingLinkSaysSo() {
        open(EditPhoneTrackLogjobActivity.class, 0);
        fragment().onOptionsItemSelected(new RoboMenuItem(R.id.menu_fromLogUrl));
        idle();
        AlertDialog dialog = latestDialog();
        EditText link = (EditText) shadowOf(dialog).getView();
        link.setText("not a link");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(app.getString(R.string.error_invalid_pt_url), ShadowToast.getTextOfLatestToast());
        assertEquals("", field(R.id.editToken).getText().toString());
    }

    @Test
    public void loggingLinkFillsTheForm() {
        open(EditPhoneTrackLogjobActivity.class, 0);
        EditPhoneTrackLogjobFragment f = (EditPhoneTrackLogjobFragment) fragment();
        assertTrue(f.setFieldsFromPhoneTrackLoggingUrl(
                "https://cloud.example.test/index.php/apps/phonetrack/log/osmand/abc123/myphone?lat={0}"));
        idle();
        assertEquals(app.getString(R.string.logjob_title_from_logging_link), field(R.id.editTitle).getText().toString());
        assertEquals("abc123", field(R.id.editToken).getText().toString());
        assertEquals("myphone", field(R.id.editDeviceName).getText().toString());
        assertFalse(f.setFieldsFromPhoneTrackLoggingUrl("not a link"));
    }
}
