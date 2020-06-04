package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.MapActivity;
import net.eneiluj.nextcloud.phonetrack.android.fragment.PreferencesFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static boolean importMapFile(Context context, Uri selectedfile) {
        // get size and name of file
        Cursor returnCursor =
                context.getContentResolver().query(selectedfile, null, null, null, null);

        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        Long size = returnCursor.getLong(sizeIndex);

        // copy file in app data storage
        File[] externalStorageVolumes =
                ContextCompat.getExternalFilesDirs(context, null);
        File primaryExternalStorage = externalStorageVolumes[0];
        Log.e(TAG, "ACC2 "+primaryExternalStorage.getAbsolutePath()+" "+primaryExternalStorage.exists());
        if (primaryExternalStorage.exists()) {
            Log.e(TAG,"prima exists");
            File f = new File(primaryExternalStorage.getAbsolutePath()+ File.separator);
            if (f.exists()) {
                Log.e(TAG,"prima file exists ");
                try {
                    File fdest = new File(primaryExternalStorage.getAbsolutePath() + File.separator + name);
                    Log.e(TAG, "path " + primaryExternalStorage.getAbsolutePath() + File.separator + name + " EXISTS " + fdest.exists());
                    if (fdest.exists()) {
                        fdest.delete();
                    }
                    if (size > primaryExternalStorage.getFreeSpace()) {
                        Toast.makeText(context, context.getString(R.string.osmdroid_file_load_no_space), Toast.LENGTH_LONG).show();
                        return false;
                    }
                    boolean ok = fdest.createNewFile();
                    Log.e(TAG, "prima can write " + fdest.canWrite() + " " + ok + " free space " + primaryExternalStorage.getFreeSpace());

                    // COPY
                    InputStream inputStream = context.getContentResolver().openInputStream(selectedfile);
                    FileOutputStream outputStream = new FileOutputStream(fdest);
                    try {
                        byte[] buffer = new byte[4 * 1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        outputStream.flush();
                    } finally {
                        inputStream.close();
                    }
                    Toast.makeText(context, context.getString(R.string.osmdroid_file_load_success), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "AFTER file write ");
                    return true;
                } catch (Exception e) {
                    Log.e(TAG,"EXECPTIONNNN "+e);
                    Toast.makeText(context, context.getString(R.string.osmdroid_file_load_exception), Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        }
        return false;
    }
}
