package net.eneiluj.nextcloud.phonetrack.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * The display name of a picked document comes from another app and must never decide where
 * the import writes.
 */
@RunWith(RobolectricTestRunner.class)
public class MapUtilsImportTest {

    private static final String AUTHORITY = "test.maps";
    private static final byte[] BYTES = "map-bytes".getBytes(StandardCharsets.UTF_8);

    public static class FakeProvider extends ContentProvider {
        static volatile String displayName;
        static volatile boolean nullCursor;
        static volatile boolean omitNameColumn;
        static volatile File source;

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
            c.addRow(omitNameColumn ? new Object[]{(long) BYTES.length}
                    : new Object[]{displayName, (long) BYTES.length});
            return c;
        }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
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
        Robolectric.setupContentProvider(FakeProvider.class, AUTHORITY);
        uri = Uri.parse("content://" + AUTHORITY + "/doc");
    }

    private boolean importAs(String name) {
        FakeProvider.displayName = name;
        return MapUtils.importMapFile(context, uri);
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
        assertFalse(MapUtils.importMapFile(context, uri));
        FakeProvider.nullCursor = false;
        FakeProvider.omitNameColumn = true;
        assertFalse(MapUtils.importMapFile(context, uri));
    }

    @Test
    public void sanitiserKeepsLastSegmentOnly() {
        assertEquals("a.map", MapUtils.sanitiseMapFileName("x/../y\\a.map"));
        assertNull(MapUtils.sanitiseMapFileName("a/.."));
    }
}
