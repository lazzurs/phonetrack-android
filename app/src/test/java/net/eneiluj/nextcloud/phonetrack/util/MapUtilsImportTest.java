package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.test.core.app.ApplicationProvider;

import net.eneiluj.nextcloud.phonetrack.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The display name of a picked document comes from another app and must never decide where
 * the import writes. The copy runs off the main thread and only replaces the old map once it
 * is complete (issue #128).
 */
@RunWith(RobolectricTestRunner.class)
public class MapUtilsImportTest {

    private static final String AUTHORITY = "test.maps";
    private static final byte[] BYTES = "map-bytes".getBytes(StandardCharsets.UTF_8);

    public static class FakeProvider extends ContentProvider {
        public static volatile String displayName;
        public static volatile boolean nullCursor;
        public static volatile boolean omitNameColumn;
        public static volatile File source;
        /** when set, the copy waits for it before it can read the file */
        public static volatile CountDownLatch gate;
        /** the thread the file was last opened on */
        public static volatile Thread openThread;

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
            if (nullCursor) {
                return null;
            }
            MatrixCursor c = omitNameColumn
                    ? new MatrixCursor(new String[]{OpenableColumns.SIZE})
                    : new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            long size = source.length();
            c.addRow(omitNameColumn ? new Object[]{size} : new Object[]{displayName, size});
            return c;
        }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            openThread = Thread.currentThread();
            CountDownLatch g = gate;
            if (g != null) {
                try {
                    g.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new FileNotFoundException("interrupted");
                }
            }
            return ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] args) {
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] args) {
            return 0;
        }
    }

    private Context context;
    private File target;
    private Uri uri;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        target = context.getExternalFilesDir(null);
        target.mkdirs();
        File src = File.createTempFile("src", ".bin");
        try (FileOutputStream out = new FileOutputStream(src)) {
            out.write(BYTES);
        }
        FakeProvider.source = src;
        FakeProvider.nullCursor = false;
        FakeProvider.omitNameColumn = false;
        FakeProvider.gate = null;
        FakeProvider.openThread = null;
        ShadowToast.reset();
        Robolectric.setupContentProvider(FakeProvider.class, AUTHORITY);
        uri = Uri.parse("content://" + AUTHORITY + "/doc");
    }

    private boolean importAs(String name) {
        FakeProvider.displayName = name;
        return MapUtils.copyMapFile(context, uri) == R.string.osmdroid_file_load_success;
    }

    @After
    public void tearDown() throws Exception {
        // never leave an import running into the next test
        if (FakeProvider.gate != null) {
            FakeProvider.gate.countDown();
        }
        awaitImport();
        File[] files = target.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }

    /** Runs the main looper until the background import has reported its result. */
    public static void awaitImport() throws InterruptedException {
        for (int i = 0; i < 1000 && MapUtils.isMapImportRunning(); i++) {
            Thread.sleep(10);
            shadowOf(Looper.getMainLooper()).idle();
        }
        assertFalse("the import did not finish", MapUtils.isMapImportRunning());
    }

    private File writeMap(String name, String content) throws Exception {
        File f = new File(target, name);
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private void setSource(byte[] content) throws Exception {
        File src = File.createTempFile("src", ".bin");
        Files.write(src.toPath(), content);
        FakeProvider.source = src;
    }

    private String[] partFiles() {
        return target.list((dir, n) -> n.endsWith(MapUtils.IMPORT_PART_SUFFIX));
    }

    /** Gives some bytes, then fails like a provider whose file went away mid-copy. */
    private static InputStream failingAfter(int bytes) {
        return new InputStream() {
            private int given;

            @Override
            public int read() throws IOException {
                if (given >= bytes) {
                    throw new IOException("provider went away");
                }
                given++;
                return 'x';
            }
        };
    }

    @Test
    public void traversalNameCannotTouchFilesOutsideTarget() throws Exception {
        File victim = new File(target.getParentFile(), "databases/NEXTCLOUD_PHONETRACK");
        victim.getParentFile().mkdirs();
        Files.write(victim.toPath(), "precious".getBytes(StandardCharsets.UTF_8));

        assertFalse(importAs("../../databases/NEXTCLOUD_PHONETRACK"));
        assertEquals("precious", new String(Files.readAllBytes(victim.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void traversalNameEndingInMapIsFlattenedIntoTarget() throws Exception {
        File outside = new File(target.getParentFile(), "evil.map");
        assertTrue(importAs("../evil.map"));
        assertFalse(outside.exists());
        assertArrayEquals(BYTES, Files.readAllBytes(new File(target, "evil.map").toPath()));
    }

    @Test
    public void subdirectoryNameIsFlattened() throws Exception {
        assertTrue(importAs("sub/dir/evil.map"));
        assertArrayEquals(BYTES, Files.readAllBytes(new File(target, "evil.map").toPath()));
        assertFalse(new File(target, "sub").exists());
    }

    @Test
    public void dotNamesAreRejected() {
        String[] before = target.list();
        assertFalse(importAs(".."));
        assertFalse(importAs("."));
        assertFalse(importAs(""));
        assertFalse(importAs(null));
        assertFalse(importAs(".map"));
        assertTrue(Arrays.equals(before, target.list()));
    }

    @Test
    public void nonMapNameIsRejected() {
        assertFalse(importAs("notamap.xml"));
        assertFalse(new File(target, "notamap.xml").exists());
    }

    @Test
    public void plainMapIsImported() throws Exception {
        assertTrue(importAs("region.map"));
        assertArrayEquals(BYTES, Files.readAllBytes(new File(target, "region.map").toPath()));
    }

    @Test
    public void uppercaseSuffixIsAccepted() {
        assertTrue(importAs("REGION.MAP"));
    }

    @Test
    public void nullCursorAndMissingNameColumnFailCleanly() {
        FakeProvider.nullCursor = true;
        assertFalse(importAs("region.map"));
        FakeProvider.nullCursor = false;
        FakeProvider.omitNameColumn = true;
        assertFalse(importAs("region.map"));
    }

    @Test
    public void sanitiserKeepsLastSegmentOnly() {
        assertEquals("a.map", MapUtils.sanitiseMapFileName("x/../y\\a.map"));
        assertNull(MapUtils.sanitiseMapFileName("a/.."));
    }

    @Test
    public void copyFailingPartWayKeepsTheOldMapAndLeavesNoPartialFile() throws Exception {
        File map = writeMap("region.map", "old map");

        try {
            MapUtils.writeMapFile(failingAfter(100_000), map);
            fail("the failed copy was not reported");
        } catch (IOException expected) {
            // the import shows its error
        }

        assertEquals("old map", new String(Files.readAllBytes(map.toPath()), StandardCharsets.UTF_8));
        assertEquals(0, partFiles().length);
    }

    @Test
    public void emptyFileKeepsTheOldMap() throws Exception {
        File map = writeMap("region.map", "old map");
        setSource(new byte[0]);

        assertFalse(importAs("region.map"));

        assertEquals("old map", new String(Files.readAllBytes(map.toPath()), StandardCharsets.UTF_8));
        assertEquals(0, partFiles().length);
    }

    @Test
    public void unreadableFileKeepsTheOldMap() throws Exception {
        File map = writeMap("region.map", "old map");
        FakeProvider.source = new File(target, "does-not-exist.bin");

        assertFalse(importAs("region.map"));

        assertEquals("old map", new String(Files.readAllBytes(map.toPath()), StandardCharsets.UTF_8));
        assertEquals(0, partFiles().length);
    }

    @Test
    public void successfulCopyReplacesTheOldMap() throws Exception {
        File map = writeMap("region.map", "old map");

        assertTrue(importAs("region.map"));

        assertArrayEquals(BYTES, Files.readAllBytes(map.toPath()));
        assertEquals(0, partFiles().length);
    }

    @Test
    public void partialCopyLeftByAKilledImportIsRemoved() throws Exception {
        writeMap("other.map" + MapUtils.IMPORT_PART_SUFFIX, "half a map");

        assertTrue(importAs("region.map"));

        assertEquals(0, partFiles().length);
    }

    @Test
    public void importCopiesOffTheMainThreadAndReportsWhenDone() throws Exception {
        File map = writeMap("region.map", "old map");
        FakeProvider.displayName = "region.map";
        FakeProvider.gate = new CountDownLatch(1);
        List<Boolean> results = new ArrayList<>();
        MapUtils.MapImportListener listener = results::add;
        MapUtils.addMapImportListener(listener);
        try {
            // returns at once although the copy cannot finish yet
            assertTrue(MapUtils.startMapImport(context, uri));
            assertTrue(MapUtils.isMapImportRunning());
            assertEquals("old map", new String(Files.readAllBytes(map.toPath()), StandardCharsets.UTF_8));

            // a second import waits for the first one
            assertFalse(MapUtils.startMapImport(context, uri));
            assertEquals(context.getString(R.string.map_import_running), ShadowToast.getTextOfLatestToast());

            FakeProvider.gate.countDown();
            awaitImport();
        } finally {
            MapUtils.removeMapImportListener(listener);
        }

        assertNotNull(FakeProvider.openThread);
        assertNotSame(Looper.getMainLooper().getThread(), FakeProvider.openThread);
        assertArrayEquals(BYTES, Files.readAllBytes(map.toPath()));
        assertEquals(Collections.singletonList(true), results);
        assertEquals(context.getString(R.string.osmdroid_file_load_success), ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void failedImportIsReportedAndKeepsTheOldMap() throws Exception {
        File map = writeMap("region.map", "old map");
        FakeProvider.displayName = "region.map";
        setSource(new byte[0]);
        List<Boolean> results = new ArrayList<>();
        MapUtils.MapImportListener listener = results::add;
        MapUtils.addMapImportListener(listener);
        try {
            assertTrue(MapUtils.startMapImport(context, uri));
            awaitImport();
        } finally {
            MapUtils.removeMapImportListener(listener);
        }

        assertEquals(Collections.singletonList(false), results);
        assertEquals(context.getString(R.string.osmdroid_file_load_exception), ShadowToast.getTextOfLatestToast());
        assertEquals("old map", new String(Files.readAllBytes(map.toPath()), StandardCharsets.UTF_8));
    }
}
