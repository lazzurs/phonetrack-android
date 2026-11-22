package net.eneiluj.nextcloud.phonetrack.android.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjobLocation;
import net.eneiluj.nextcloud.phonetrack.model.ItemAdapter;
import net.eneiluj.nextcloud.phonetrack.persistence.DbTestSupport;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowPopupMenu;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/** "Export as GPX file" in a log job's menu (issue #127). */
@RunWith(AndroidJUnit4.class)
public class GpxExportTest {

    private static final String GPX_NS = "http://www.topografix.com/GPX/1/1";
    private static final Uri FILE = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AHike.gpx");

    private Application app;
    private PhoneTrackSQLiteOpenHelper db;
    private ActivityController<LogjobsListViewActivity> controller;

    @Before
    public void setUp() {
        DbTestSupport.resetDatabase();
        app = ApplicationProvider.getApplicationContext();
        db = PhoneTrackSQLiteOpenHelper.getInstance(app);
    }

    @After
    public void tearDown() {
        if (controller != null) {
            controller.destroy();
        }
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit();
        DbTestSupport.resetDatabase();
    }

    private void addPosition(long logjobId, double lat, long time, boolean synced, boolean currentRun) {
        db.addLocation(new DBLogjobLocation(0, logjobId, lat, -6.26, time, null, 20.0, null, 5.0, 8L, 80.0,
                "PhoneTrack", synced, currentRun));
    }

    private LogjobsListViewActivity launchWithOneRow() throws InterruptedException {
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        LogjobsListViewActivity activity = controller.get();
        ListActivityTestSupport.awaitRows(activity, 1);
        return activity;
    }

    /** Taps "Export as GPX file" in the menu of the first log job of the list. */
    private void tapExport(LogjobsListViewActivity activity) throws Exception {
        tapExport(activity, 0);
    }

    private void tapExport(LogjobsListViewActivity activity, int row) {
        activity.onLogjobMoreButtonClick(row, activity.findViewById(R.id.recycler_view));
        PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popup);
        MenuItem item = popup.getMenu().findItem(R.id.menuExportToGpx);
        assertNotNull(item);
        assertTrue(item.isVisible());
        assertEquals("Export as GPX file", item.getTitle().toString());
        shadowOf(popup).getOnMenuItemClickListener().onMenuItemClick(item);
    }

    /** The activity also starts other things (permission screens): look for the file picker. */
    private static ShadowActivity.IntentForResult createDocumentRequest(Activity activity) {
        ShadowActivity.IntentForResult started;
        while ((started = shadowOf(activity).getNextStartedActivityForResult()) != null) {
            if (Intent.ACTION_CREATE_DOCUMENT.equals(started.intent.getAction())) {
                return started;
            }
        }
        return null;
    }

    /** The file is written in the background: wait for the message that says it's done. */
    private static String awaitMessage(LogjobsListViewActivity activity) throws InterruptedException {
        for (int i = 0; i < 500; i++) {
            String text = snackbarText(activity);
            if (text != null) {
                return text;
            }
            Thread.sleep(10);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));
        }
        return null;
    }

    private static String snackbarText(LogjobsListViewActivity activity) {
        List<String> found = new ArrayList<>();
        collectSnackbarTexts(activity.getWindow().getDecorView(), found);
        return found.isEmpty() ? null : found.get(found.size() - 1);
    }

    private static void collectSnackbarTexts(View view, List<String> found) {
        if (view.getId() == com.google.android.material.R.id.snackbar_text && view instanceof TextView) {
            found.add(((TextView) view).getText().toString());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectSnackbarTexts(group.getChildAt(i), found);
            }
        }
    }

    private static Document parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    @Test
    public void menuItemAsksForAGpxFileNamedAfterTheLogjob() throws Exception {
        ListActivityTestSupport.addLogjob(app, "Hike: day 1", false);
        LogjobsListViewActivity activity = launchWithOneRow();

        tapExport(activity);

        ShadowActivity.IntentForResult request = createDocumentRequest(activity);
        assertNotNull("the file picker was not opened", request);
        assertEquals("application/gpx+xml", request.intent.getType());
        assertTrue(request.intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertEquals("Hike_ day 1 " + LocalDate.now() + ".gpx", request.intent.getStringExtra(Intent.EXTRA_TITLE));
    }

    @Test
    public void writesThePositionsOfTheLogjobOnThisPhoneInTimeOrder() throws Exception {
        long hike = ListActivityTestSupport.addLogjob(app, "Hike & bike", false);
        // stored out of time order; one uploaded, one waiting from a previous run
        addPosition(hike, 53.2, 1763803860L, false, true);
        addPosition(hike, 53.1, 1763803800L, true, true);
        addPosition(hike, 53.0, 1763803740L, false, false);
        long other = ListActivityTestSupport.addLogjob(app, "Other", false);
        addPosition(other, 10.0, 1763803800L, false, true);
        controller = Robolectric.buildActivity(LogjobsListViewActivity.class).setup();
        LogjobsListViewActivity activity = controller.get();
        ListActivityTestSupport.awaitRows(activity, 2);
        tapExport(activity, rowOf(activity, hike));
        ShadowActivity.IntentForResult request = createDocumentRequest(activity);
        assertNotNull(request);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        shadowOf(app.getContentResolver()).registerOutputStream(FILE, out);
        shadowOf(activity).receiveResult(request.intent, Activity.RESULT_OK, new Intent().setData(FILE));

        String message = awaitMessage(activity);
        assertNotNull("no message after the export", message);
        assertTrue(message, message.startsWith("3 positions saved."));
        assertTrue(message, message.contains("only has the positions still on this phone"));
        Document doc = parse(out.toByteArray());
        assertEquals("gpx", doc.getDocumentElement().getLocalName());
        assertEquals("1.1", doc.getDocumentElement().getAttribute("version"));
        assertEquals("Hike & bike", doc.getElementsByTagNameNS(GPX_NS, "name").item(0).getTextContent());
        NodeList points = doc.getElementsByTagNameNS(GPX_NS, "trkpt");
        assertEquals(3, points.getLength());
        assertEquals("53", ((Element) points.item(0)).getAttribute("lat"));
        assertEquals("53.1", ((Element) points.item(1)).getAttribute("lat"));
        assertEquals("53.2", ((Element) points.item(2)).getAttribute("lat"));
        assertEquals("2025-11-22T09:29:00Z",
                ((Element) points.item(0)).getElementsByTagNameNS(GPX_NS, "time").item(0).getTextContent());
    }

    /** Position of a log job in the list */
    private static int rowOf(LogjobsListViewActivity activity, long logjobId) throws Exception {
        Field adapterField = LogjobsListViewActivity.class.getDeclaredField("adapter");
        adapterField.setAccessible(true);
        ItemAdapter adapter = (ItemAdapter) adapterField.get(activity);
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (!adapter.getItem(i).isSection() && ((DBLogjob) adapter.getItem(i)).getId() == logjobId) {
                return i;
            }
        }
        throw new AssertionError("log job " + logjobId + " is not in the list");
    }

    @Test
    public void emptyLogjobSaysTheFileHasNoPositions() throws Exception {
        ListActivityTestSupport.addLogjob(app, "Empty", false);
        LogjobsListViewActivity activity = launchWithOneRow();
        tapExport(activity);
        ShadowActivity.IntentForResult request = createDocumentRequest(activity);
        assertNotNull(request);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        shadowOf(app.getContentResolver()).registerOutputStream(FILE, out);
        shadowOf(activity).receiveResult(request.intent, Activity.RESULT_OK, new Intent().setData(FILE));

        String message = awaitMessage(activity);
        assertNotNull(message);
        assertTrue(message, message.startsWith("The file was saved without positions"));
        Document doc = parse(out.toByteArray());
        assertEquals(0, doc.getElementsByTagNameNS(GPX_NS, "trkpt").getLength());
    }

    @Test
    public void cancellingThePickerWritesNothing() throws Exception {
        long id = ListActivityTestSupport.addLogjob(app, "Hike", false);
        addPosition(id, 53.0, 1763803740L, false, true);
        LogjobsListViewActivity activity = launchWithOneRow();
        tapExport(activity);
        ShadowActivity.IntentForResult request = createDocumentRequest(activity);
        assertNotNull(request);

        shadowOf(activity).receiveResult(request.intent, Activity.RESULT_CANCELED, null);
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(snackbarText(activity));
    }

    /**
     * The log job is remembered by id across the picker, also when the activity is recreated
     * meanwhile (as after the process was killed): no positions are held in memory.
     */
    @Test
    public void exportSurvivesTheActivityBeingRecreatedWhileTheFileIsChosen() throws Exception {
        long id = ListActivityTestSupport.addLogjob(app, "Hike", false);
        addPosition(id, 53.0, 1763803740L, false, true);
        LogjobsListViewActivity activity = launchWithOneRow();
        tapExport(activity);
        ShadowActivity.IntentForResult request = createDocumentRequest(activity);
        assertNotNull(request);
        // a position stored while the picker is open is in the file too
        addPosition(id, 53.1, 1763803800L, false, true);

        controller.recreate();
        LogjobsListViewActivity recreated = controller.get();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        shadowOf(app.getContentResolver()).registerOutputStream(FILE, out);
        recreated.getActivityResultRegistry().dispatchResult(request.requestCode, Activity.RESULT_OK,
                new Intent().setData(FILE));

        String message = awaitMessage(recreated);
        assertNotNull(message);
        assertTrue(message, message.startsWith("2 positions saved."));
        assertEquals(2, parse(out.toByteArray()).getElementsByTagNameNS(GPX_NS, "trkpt").getLength());
    }
}
