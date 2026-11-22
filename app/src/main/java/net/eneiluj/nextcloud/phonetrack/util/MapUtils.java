package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.MapActivity;
import net.eneiluj.nextcloud.phonetrack.android.fragment.PreferencesFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MapUtils {

    private static final String TAG = PreferencesFragment.class.getSimpleName();

    public static void showDeleteMapFileDialog(Context context) {
        android.app.AlertDialog.Builder selectBuilder = new android.app.AlertDialog.Builder(new ContextThemeWrapper(context, R.style.AppThemeDialog));
        selectBuilder.setTitle(context.getString(R.string.settings_osmdroid_delete_label));

        List<File> fileList = getMapFileNames(context);
        List<String> fileNameList = new ArrayList<>();
        for (File f: fileList) {
            fileNameList.add(f.getName());
        }
        if (fileNameList.size() > 0) {
            CharSequence[] entcs = fileNameList.toArray(new CharSequence[fileNameList.size()]);
            selectBuilder.setSingleChoiceItems(entcs, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // user checked an item
                    String filenameToDelelte = fileNameList.get(which);
                    Log.v(TAG, "about to delete "+filenameToDelelte);
                    File fToDel = fileList.get(which);
                    fToDel.delete();
                    Toast.makeText(context, context.getString(R.string.settings_osmdroid_delete_success), Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                }
            });
            selectBuilder.setNegativeButton(context.getString(R.string.simple_cancel), null);
            selectBuilder.show();
        } else {
            Toast.makeText(context, context.getString(R.string.settings_no_osmdroid_files_found), Toast.LENGTH_LONG).show();
        }
    }

    private static List<File> getMapFileNames(Context context) {
        Set<File> maps = new HashSet<>();
        File[] externalStorageVolumes =
                ContextCompat.getExternalFilesDirs(context, null);
        File primaryExternalStorage = externalStorageVolumes[0];
        Log.e(TAG, "ACC2 "+primaryExternalStorage.getAbsolutePath()+" "+primaryExternalStorage.exists());
        if (primaryExternalStorage.exists()) {
            Log.e(TAG,"prima exists");
            File f = new File(primaryExternalStorage.getAbsolutePath()+ File.separator);
            if (f.exists()) {
                Log.e(TAG,"prima file exists");
                maps.addAll(MapActivity.scan(f));
            }
        }

        List<File> mapList = new ArrayList<>();
        mapList.addAll(maps);
        return mapList;
    }

    /**
     * Reduces a provider-supplied display name to a safe file name, or returns null if there is none.
     * The name comes from another app, so it is untrusted: only its last path segment is kept and it
     * must be a ".map" file (the only type MapActivity.scan() looks for).
     */
    static String sanitiseMapFileName(String name) {
        if (name == null) {
            return null;
        }
        String last = new File(name.replace('\\', '/')).getName();
        if (last.isEmpty() || last.equals(".") || last.equals("..") || last.indexOf('\0') >= 0) {
            return null;
        }
        if (!last.toLowerCase(Locale.ROOT).endsWith(".map") || last.length() <= ".map".length()) {
            return null;
        }
        return last;
    }

    /** Suffix of the copy a map is written to before it replaces the real file. */
    static final String IMPORT_PART_SUFFIX = ".part";

    private static final AtomicBoolean mapImportRunning = new AtomicBoolean(false);
    // the screens to tell when the import finishes; weak, and only used on the main thread
    private static final Set<MapImportListener> mapImportListeners =
            Collections.newSetFromMap(new WeakHashMap<>());

    /** Told on the main thread when a map import started by {@link #startMapImport} has finished. */
    public interface MapImportListener {
        void onMapImportFinished(boolean imported);
    }

    public static boolean isMapImportRunning() {
        return mapImportRunning.get();
    }

    /**
     * A screen showing the import progress adds itself when it is created (again, after a rotation)
     * and removes itself when it is destroyed, so the import never calls a dead screen.
     */
    public static void addMapImportListener(MapImportListener listener) {
        mapImportListeners.add(listener);
    }

    public static void removeMapImportListener(MapImportListener listener) {
        mapImportListeners.remove(listener);
    }

    /**
     * Copies a picked map file into the app's storage off the main thread: map files can be
     * hundreds of MB. Only one import runs at a time. The result is shown as a toast, so it is
     * seen even if the screen that started the import was closed or recreated meanwhile.
     *
     * @return false if another import is still running (nothing is started)
     */
    public static boolean startMapImport(Context context, Uri selectedfile) {
        Context appContext = context.getApplicationContext();
        if (!mapImportRunning.compareAndSet(false, true)) {
            Toast.makeText(appContext, appContext.getString(R.string.map_import_running), Toast.LENGTH_LONG).show();
            return false;
        }
        // not on the serial queue: the copy can take minutes and the other tasks must not wait for it
        new BackgroundTask<Void, Integer>() {
            @Override
            protected Integer doInBackground(Void[] params) {
                return copyMapFile(appContext, selectedfile);
            }

            @Override
            protected void onPostExecute(Integer message) {
                mapImportRunning.set(false);
                Toast.makeText(appContext, appContext.getString(message), Toast.LENGTH_LONG).show();
                boolean imported = message == R.string.osmdroid_file_load_success;
                for (MapImportListener listener : new ArrayList<>(mapImportListeners)) {
                    listener.onMapImportFinished(imported);
                }
            }
        }.executeInParallel();
        return true;
    }

    /**
     * Copies the picked file into the app's storage. Runs on a background thread.
     *
     * @return the message for the user: osmdroid_file_load_success if the map was imported
     */
    @StringRes
    static int copyMapFile(Context context, Uri selectedfile) {
        if (selectedfile == null) {
            return R.string.osmdroid_file_load_exception;
        }
        // get size and name of file
        String name = null;
        long size = -1;
        try (Cursor returnCursor =
                     context.getContentResolver().query(selectedfile, null, null, null, null)) {
            if (returnCursor != null && returnCursor.moveToFirst()) {
                int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0) {
                    name = returnCursor.getString(nameIndex);
                }
                if (sizeIndex >= 0 && !returnCursor.isNull(sizeIndex)) {
                    size = returnCursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "map import: could not query provider " + e);
        }

        String safeName = sanitiseMapFileName(name);
        if (safeName == null) {
            return R.string.osmdroid_file_load_exception;
        }

        // copy file in app data storage
        File[] externalStorageVolumes =
                ContextCompat.getExternalFilesDirs(context, null);
        File primaryExternalStorage = externalStorageVolumes[0];
        if (primaryExternalStorage == null || !primaryExternalStorage.exists()) {
            return R.string.osmdroid_file_load_exception;
        }
        try {
            File fdest = new File(primaryExternalStorage, safeName);
            // belt and braces: the destination must resolve to a direct child of the target directory
            File parent = fdest.getCanonicalFile().getParentFile();
            if (parent == null || !parent.equals(primaryExternalStorage.getCanonicalFile())) {
                Log.e(TAG, "map import refused: destination outside target directory");
                return R.string.osmdroid_file_load_exception;
            }
            // copies left by an import the app was killed during (one import runs at a time)
            File[] leftovers = primaryExternalStorage.listFiles((dir, n) -> n.endsWith(IMPORT_PART_SUFFIX));
            if (leftovers != null) {
                for (File leftover : leftovers) {
                    leftover.delete();
                }
            }
            if (size > primaryExternalStorage.getFreeSpace()) {
                return R.string.osmdroid_file_load_no_space;
            }

            try (InputStream inputStream = context.getContentResolver().openInputStream(selectedfile)) {
                if (inputStream == null) {
                    throw new IOException("no input stream");
                }
                writeMapFile(inputStream, fdest);
            }
            return R.string.osmdroid_file_load_success;
        } catch (Exception e) {
            Log.e(TAG, "map import failed: " + e);
            return R.string.osmdroid_file_load_exception;
        }
    }

    /**
     * Copies the stream next to {@code dest} and only then renames the copy over it, so a copy that
     * fails or comes out empty keeps the map that was there and leaves no partial file behind.
     */
    static void writeMapFile(InputStream inputStream, File dest) throws IOException {
        File part = new File(dest.getParentFile(), dest.getName() + IMPORT_PART_SUFFIX);
        boolean replaced = false;
        try {
            try (FileOutputStream outputStream = new FileOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
                // on disk before the rename makes it the map
                outputStream.getFD().sync();
            }
            if (part.length() == 0) {
                throw new IOException("the map file is empty");
            }
            // rename(2) within a directory replaces the old file in one step
            if (!part.renameTo(dest)) {
                throw new IOException("could not replace " + dest.getName());
            }
            replaced = true;
        } finally {
            if (!replaced) {
                part.delete();
            }
        }
    }
}
