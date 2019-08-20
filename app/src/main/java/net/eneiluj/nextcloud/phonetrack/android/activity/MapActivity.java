package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
//import android.support.v4.widget.DrawerLayout;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.fragment.PreferencesFragment;
import net.eneiluj.nextcloud.phonetrack.model.ColoredLocation;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.model.NavigationAdapter;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.IGetLastPosCallback;
import net.eneiluj.nextcloud.phonetrack.util.ThemeUtils;

import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.mapsforge.MapsForgeTileProvider;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.tileprovider.util.StorageUtils;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.FileFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import static android.text.format.DateUtils.isToday;

//import butterknife.BindView;
//import butterknife.ButterKnife;

public class MapActivity extends AppCompatActivity {
    MapView map = null;

    private final static int PERMISSION_WRITE = 3;
    private static final String TAG = MapActivity.class.getSimpleName();

    public static final String PARAM_SESSIONID = "net.eneiluj.nextcloud.phonetrack.mapSessionId";
    public static final String ID_ITEM_ALL_DEVICES = "net.eneiluj.nextcloud.phonetrack.id_item_all_devices";

    private MyLocationNewOverlay mLocationOverlay;
    private CompassOverlay mCompassOverlay;
    private RotationGestureOverlay mRotationGestureOverlay;
    private ScaleBarOverlay mScaleBarOverlay;
    private Context ctx;

    private ImageButton btLayers;
    private ImageButton btDisplayMyLoc;
    private ImageButton btFollowMe;
    private ImageButton btZoom;
    private ImageButton btZoomAuto;

    private Map<String, ColoredLocation> locations;
    private Map<String, Marker> markers;
    private Map<String, CustomLocationMarkerDrawable> markerDrawables;

    private DBSession session;
    private PhoneTrackSQLiteOpenHelper db;

    private String selectedDeviceItemId;

    //@BindView(R.id.mapActivityActionBar)
    Toolbar toolbar;
    //@BindView(R.id.drawerLayoutMap)
    DrawerLayout drawerLayoutMap;
    //@BindView(R.id.account)
    TextView account;
    //@BindView(R.id.relativelayoutMap)
    RelativeLayout relativeLayoutMap;

    //@BindView(R.id.navigationList)
    RecyclerView listNavigationDevices;
    //@BindView(R.id.navigationMenu)
    RecyclerView listNavigationMenu;

    private NavigationAdapter adapterDevices;

    private ActionBarDrawerToggle drawerToggle;
    private SharedPreferences prefs;

    private final SimpleDateFormat sdfComplete = new SimpleDateFormat("yyyy-MM-dd\nHH:mm:ss z");
    private final SimpleDateFormat sdfCompleteSimple = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final SimpleDateFormat sdfHour = new SimpleDateFormat("HH:mm:ss");
    private Drawable toggleCircle;

    private Map<String, OnlineTileSourceBase> layersMap;
    private String selectedLayer;
    private MapTileProviderBasic defaultTileProvider;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ctx = getApplicationContext();
        db = PhoneTrackSQLiteOpenHelper.getInstance(ctx);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        long sessionid = getIntent().getLongExtra(PARAM_SESSIONID, 0);
        session = db.getSession(sessionid);

        toggleCircle = ContextCompat.getDrawable(ctx, R.drawable.ic_plain_circle_grey_24dp)
                .getConstantState().newDrawable();
        toggleCircle.setColorFilter(
                new PorterDuffColorFilter(
                        ThemeUtils.primaryColor(ctx),
                        PorterDuff.Mode.SRC_IN
                )
        );

        setContentView(R.layout.drawer_layout_map);

        toolbar = findViewById(R.id.mapActivityActionBar);
        drawerLayoutMap = findViewById(R.id.drawerLayoutMap);
        account = findViewById(R.id.account);
        relativeLayoutMap = findViewById(R.id.relativelayoutMap);
        listNavigationDevices = findViewById(R.id.navigationList);
        listNavigationMenu = findViewById(R.id.navigationMenu);

        //ButterKnife.bind(this);
        setupActionBar();
        drawerToggle.syncState();


        markers = new HashMap<>();
        locations = new HashMap<>();
        markerDrawables = new HashMap<>();
        selectedDeviceItemId = ID_ITEM_ALL_DEVICES;

        //load/initialize the osmdroid configuration, this can be done

        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the map has a writable location for the map cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's tile servers will get you banned based on this string
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    MapActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_WRITE
            );
        }

        Log.i(TAG, "CREATE map : session : "+session);

        //inflate and create the map (already done upper ;-) )
        //setContentView(R.layout.activity_map);

        map = (MapView) findViewById(R.id.map);
        map.setMaxZoomLevel(20.0);

        setupMapTileProviders();

        selectedLayer = prefs.getString("map_selected_layer", "OpenStreetMap Mapnik");
        if (!layersMap.containsKey(selectedLayer)) {
            // selected layer was removed
            selectedLayer = "OpenStreetMap Mapnik";
            prefs.edit().putString("map_selected_layer", "OpenStreetMap Mapnik").apply();
        }
        setTileSource(selectedLayer);

        IMapController mapController = map.getController();
        mapController.setZoom(2.0);

        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.ALWAYS);

        this.mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(ctx), map);
        //this.mLocationOverlay.enableFollowLocation();
        //this.mLocationOverlay.setEnableAutoStop(true);
        map.getOverlays().add(this.mLocationOverlay);

        CopyrightOverlay copyrightOverlay = new CopyrightOverlay(map.getContext());
        copyrightOverlay.setTextColor(Color.BLACK);
        map.getOverlays().add(copyrightOverlay);

        setupMapButtons();

        final DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        mScaleBarOverlay = new ScaleBarOverlay(map);
        mScaleBarOverlay.setCentred(true);
        //play around with these values to get the location on screen in the right place for your application
        mScaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10);
        map.getOverlays().add(this.mScaleBarOverlay);

    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_WRITE:
                if (grantResults.length > 0) {
                    Log.d(TAG, "[permission STORAGE result] "+grantResults[0]);
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        declareMapsForgeProvider();
                    }
                    else {

                    }
                }
                break;
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.syncState();
    }

    private void setupMapTileProviders() {
        layersMap = new HashMap<>();
        layersMap.put("OpenStreetMap Mapnik", TileSourceFactory.MAPNIK);
        layersMap.put("Hike bike map", TileSourceFactory.HIKEBIKEMAP);
        layersMap.put("OpenTopoMap", TileSourceFactory.OpenTopo);
        layersMap.put(
                "OpenCycleMap",
                new XYTileSource(
                        "OpenCycleMap", 1, 22, 256,
                        ".png",
                        new String[]{
                                "https://a.tile.thunderforest.com/cycle/",
                                "https://b.tile.thunderforest.com/cycle/",
                                "https://c.tile.thunderforest.com/cycle/"
                        },
                        "OpenCycleMap (https://www.opencyclemap.org)"
                )
        );
        layersMap.put(
                "ESRI Aerial",
                new OnlineTileSourceBase(
                        "ARCGisOnline", 1, 19, 256,
                        "",
                        new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"},
                        "Esri ArcgisOnline") {
                    @Override
                    public String getTileURLString(long tileIndex) {
                        String mImageFilenameEnding = "";

                        return getBaseUrl() + MapTileIndex.getZoom(tileIndex) + "/"
                                + MapTileIndex.getY(tileIndex) + "/" + MapTileIndex.getX(tileIndex)
                                + mImageFilenameEnding;
                    }
                }
        );
        layersMap.put(
                "ESRI Topo with relief",
                new OnlineTileSourceBase(
                        "ARCGisOnlineTopo", 1, 19, 256,
                        "",
                        new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/"},
                        "Esri ArcgisOnline") {
                    @Override
                    public String getTileURLString(long tileIndex) {
                        String mImageFilenameEnding = "";

                        return getBaseUrl() + MapTileIndex.getZoom(tileIndex) + "/"
                                + MapTileIndex.getY(tileIndex) + "/" + MapTileIndex.getX(tileIndex)
                                + mImageFilenameEnding;
                    }
                }
        );

        // MAPSFORGE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            declareMapsForgeProvider();
        }

    }

    private void declareMapsForgeProvider() {
        Log.i(TAG, "[DECLARE MAPSFORGE]");
        MapsForgeTileSource.createInstance(this.getApplication());
        Set<File> mapfiles = findMapFiles();
        //do a simple scan of local storage for .map files.
        File[] maps = new File[mapfiles.size()];
        maps = mapfiles.toArray(maps);
        if (maps == null || maps.length == 0) {
        }
        else {
            layersMap.put("MapsForge", null);
        }
    }

    private MapsForgeTileProvider getMapsForgeTileProvider() {
        MapsForgeTileProvider mapsForgeTileProvider;
        Set<File> mapfiles = findMapFiles();
        //do a simple scan of local storage for .map files.
        File[] maps = new File[mapfiles.size()];
        maps = mapfiles.toArray(maps);
        if (maps == null || maps.length == 0) {
            mapsForgeTileProvider = null;
        }
        else {
            XmlRenderTheme theme = null;
            try {
                theme = new AssetsRenderTheme(map.getContext().getApplicationContext(), "renderthemes/", "rendertheme-v4.xml");
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            MapsForgeTileSource fromFiles = null;

            fromFiles = MapsForgeTileSource.createFromFiles(maps, theme, "rendertheme-v4");
            mapsForgeTileProvider = new MapsForgeTileProvider(
                    new SimpleRegisterReceiver(map.getContext()),
                    fromFiles, null);
        }
        return mapsForgeTileProvider;
    }

    private void setupActionBar() {
        Log.i(TAG, "[setupactionbar]");
        setSupportActionBar(toolbar);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayoutMap, toolbar, R.string.action_drawer_open, R.string.action_drawer_close);
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerLayoutMap.addDrawerListener(drawerToggle);
        setTitle(getString(R.string.simple_map_title, session.getName()));
        drawerLayoutMap.findViewById(R.id.drawer_top_layout_map).setBackgroundColor(ThemeUtils.primaryColor(this));
        ImageView logoView = drawerLayoutMap.findViewById(R.id.drawer_logo_map);
        logoView.setColorFilter(ThemeUtils.primaryColor(this), PorterDuff.Mode.OVERLAY);

        if (toolbar != null) {
            int color = ThemeUtils.primaryColor(this);
            toolbar.setBackgroundColor(color);
        }

        Window window = getWindow();
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int colorDark = ThemeUtils.primaryDarkColor(this);
                window.setStatusBarColor(colorDark);
            }
        }
    }

    public void onResume(){
        Log.i(TAG, "[onResume begin]");
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up

        // i don't know why but map.onResume() always enables myLocation...
        if (prefs.getBoolean("map_myposition", true)) {
            mLocationOverlay.enableMyLocation();
        }
        else {
            mLocationOverlay.disableMyLocation();
        }
        //this.mLocationOverlay.enableMyLocation();
        //this.mLocationOverlay.enableFollowLocation();
        /*Location currentLocation = mLocationOverlay.getLastFix();
        if (currentLocation != null) {
            GeoPoint myPosition = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
            map.getController().animateTo(myPosition);
        }
        */
        setupNavigationMenu();
        startRefresh();
        Log.i(TAG, "[onResume end]");
    }

    public void onPause(){
        Log.i(TAG, "[onPause begin]");
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up

        stopRefresh();
        Log.i(TAG, "[onPause end]");
    }

    private void setupNavigationDeviceList() {
        ArrayList<NavigationAdapter.NavigationItem> itemsNavigationDevice = new ArrayList<>();

        NavigationAdapter.NavigationItem itemAll = new NavigationAdapter.NavigationItem(ID_ITEM_ALL_DEVICES, getString(R.string.item_all_devices_label), markers.keySet().size(), R.drawable.ic_allgrey_24dp);
        itemsNavigationDevice.add(itemAll);
        List<String> devNames = new ArrayList<>();
        devNames.addAll(markers.keySet());
        Collections.sort(devNames, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                return s1.compareToIgnoreCase(s2);
            }
        });
        for (String devName : devNames) {
            String label = devName;
            if (isToday(locations.get(devName).getTimestamp()*1000)) {
                label += " (" + sdfHour.format(locations.get(devName).getTimestamp() * 1000) + ")";
            }
            else {
                label += "\n(" + sdfCompleteSimple.format(locations.get(devName).getTimestamp() * 1000) + ")";
            }
            NavigationAdapter.NavigationItem item = new NavigationAdapter.NavigationItem(devName, label, null, R.drawable.ic_phone_android_grey_24dp);
            itemsNavigationDevice.add(item);
        }
        bringMarkersToFrontByTimestamp();

        adapterDevices = new NavigationAdapter(new NavigationAdapter.ClickListener() {
            @Override
            public void onItemClick(NavigationAdapter.NavigationItem item) {
                selectItem(item, true);
            }

            private void selectItem(NavigationAdapter.NavigationItem item, boolean closeNavigation) {
                adapterDevices.setSelectedItem(item.id);
                Log.i(TAG, "[select item] "+item.id);
                selectedDeviceItemId = item.id;
                if (!selectedDeviceItemId.equals(ID_ITEM_ALL_DEVICES)) {
                    Marker m = markers.get(selectedDeviceItemId);
                    bringMarkerToFront(m);
                }

                // update views
                if (closeNavigation) {
                    drawerLayoutMap.closeDrawers();
                }

                // zoom anyway, whatever the autozoom value is
                zoomOnAllMarkers();

            }

            @Override
            public void onIconClick(NavigationAdapter.NavigationItem item) {
                onItemClick(item);
            }
        });

        adapterDevices.setItems(itemsNavigationDevice);
        if (markers.containsKey(selectedDeviceItemId)) {
            adapterDevices.setSelectedItem(selectedDeviceItemId);
        }
        else {
            adapterDevices.setSelectedItem(ID_ITEM_ALL_DEVICES);
            selectedDeviceItemId = ID_ITEM_ALL_DEVICES;
        }
        listNavigationDevices.setAdapter(adapterDevices);
    }

    private void setupNavigationMenu() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int freq = prefs.getInt("map_freq", 15);
        //final NavigationAdapter.NavigationItem itemTrashbin = new NavigationAdapter.NavigationItem("trashbin", getString(R.string.action_trashbin), null, R.drawable.ic_delete_grey600_24dp);
        final NavigationAdapter.NavigationItem itemFreq = new NavigationAdapter.NavigationItem("freq", getString(R.string.action_frequency), freq, R.drawable.ic_timer_grey_24dp);
        //final NavigationAdapter.NavigationItem itemSettings = new NavigationAdapter.NavigationItem("settings", getString(R.string.action_settings), null, R.drawable.ic_settings_grey600_24dp);
        //final NavigationAdapter.NavigationItem itemAbout = new NavigationAdapter.NavigationItem("about", getString(R.string.simple_about), null, R.drawable.ic_info_outline_grey600_24dp);
        final NavigationAdapter.NavigationItem itemPin = new NavigationAdapter.NavigationItem("pin", getString(R.string.action_pin_to_homescreen), null, R.drawable.ic_add_menu_grey_24dp);

        ArrayList<NavigationAdapter.NavigationItem> itemsMenu = new ArrayList<>();
        itemsMenu.add(itemFreq);
        //itemsMenu.add(itemSettings);
        //itemsMenu.add(itemAbout);

        // If the platform supports pinned shortcuts, show menu item
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
                itemsMenu.add(itemPin);
            }
        }

        NavigationAdapter adapterMenu = new NavigationAdapter(new NavigationAdapter.ClickListener() {
            @Override
            public void onItemClick(NavigationAdapter.NavigationItem item) {
                /*if (item == itemSettings) {
                    Intent settingsIntent = new Intent(getApplicationContext(), PreferencesActivity.class);
                    startActivityForResult(settingsIntent, server_settings);
                }
                else if (item == itemAbout) {
                    Intent aboutIntent = new Intent(getApplicationContext(), AboutActivity.class);
                    startActivityForResult(aboutIntent, about);
                }
                else*/ if (item == itemFreq) {
                    int currentFreq = prefs.getInt("map_freq", 15);

                    final EditText frequencyEdit = new EditText(map.getContext());
                    frequencyEdit.setText(String.valueOf(currentFreq));
                    frequencyEdit.setRawInputType(InputType.TYPE_CLASS_NUMBER);
                    frequencyEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
                    AlertDialog.Builder fromUrlBuilder = new AlertDialog.Builder(new ContextThemeWrapper(map.getContext(), R.style.AppThemeDialog));
                    fromUrlBuilder.setMessage(getString(R.string.map_choose_frequency_dialog_message));
                    fromUrlBuilder.setTitle(getString(R.string.map_choose_frequency_dialog_title));

                    fromUrlBuilder.setView(frequencyEdit);

                    fromUrlBuilder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            setFrequency(frequencyEdit.getText().toString());
                            Log.i(TAG, "[CHANGE FREQ] "+frequencyEdit.getText().toString());
                            // restore keyboard auto hide behaviour
                            InputMethodManager inputMethodManager = (InputMethodManager) frequencyEdit.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                        }
                    });

                    fromUrlBuilder.setNegativeButton(getString(R.string.simple_cancel), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // restore keyboard auto hide behaviour
                            InputMethodManager inputMethodManager = (InputMethodManager) frequencyEdit.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                        }
                    });

                    // create the alert dialog
                    Dialog fromUrlDialog = fromUrlBuilder.create();
                    fromUrlDialog.show();
                    frequencyEdit.setSelectAllOnFocus(true);
                    frequencyEdit.requestFocus();
                    // show keyboard
                    InputMethodManager inputMethodManager = (InputMethodManager) frequencyEdit.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                } else if (item == itemPin) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {

                        if (ShortcutManagerCompat.isRequestPinShortcutSupported(getApplicationContext())) {
                            long sessionId = getIntent().getLongExtra(PARAM_SESSIONID, 0);

                            // Main app intent
                            Intent mainIntent = new Intent(getApplicationContext(), LogjobsListViewActivity.class);
                            mainIntent.setAction(Intent.ACTION_VIEW);

                            // Map intent
                            Intent mapIntent = new Intent(getApplicationContext(), MapActivity.class);
                            mapIntent.setAction(Intent.ACTION_VIEW);
                            // Add session id
                            mapIntent.putExtra(PARAM_SESSIONID, sessionId);

                            // Build shortcut
                            ShortcutInfoCompat pinShortcutInfo = new ShortcutInfoCompat.Builder(MapActivity.this, "map" + sessionId)
                                    .setShortLabel(session.getName())
                                    .setLongLabel(getString(R.string.homescreen_map_shortcut_long_title, session.getName()))
                                    .setIcon(IconCompat.createWithResource(MapActivity.this, R.drawable.ic_map_grey_24dp))
                                    .setIntents(new Intent[]{mainIntent, mapIntent})
                                    .build();

                            // Request to launcher to pin shortcut
                            ShortcutManagerCompat.requestPinShortcut(getApplicationContext(), pinShortcutInfo, null);
                        }

                    }
                }
            }

            @Override
            public void onIconClick(NavigationAdapter.NavigationItem item) {
                onItemClick(item);
            }
        });

        adapterMenu.setItems(itemsMenu);
        listNavigationMenu.setAdapter(adapterMenu);
    }

    private void setFrequency(String f) {
        try {
            int freq = Integer.valueOf(f);
            if (freq > 0) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit().putInt("map_freq", freq).apply();
                stopRefresh();
                startRefresh();
                // to update freq displayed value
                setupNavigationMenu();
            }
        }
        catch (Exception e) {

        }
    }

    private void bringMarkerToFront(Marker m) {
        map.getOverlays().remove(m);
        map.getOverlays().add(m);
    }

    private void bringMarkersToFrontByTimestamp() {
        List<String> devNames = new ArrayList<>();
        devNames.addAll(markers.keySet());
        Collections.sort(devNames, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                if (locations.get(s1).getTimestamp() == locations.get(s2).getTimestamp()) {
                    return 0;
                }
                boolean yep = (locations.get(s1).getTimestamp() - locations.get(s2).getTimestamp()) > 0;
                return yep ? 1 : -1;
            }
        });
        for (String devName : devNames) {
            bringMarkerToFront(markers.get(devName));
        }
    }

    private void zoomOnAllMarkers() {
        if (markers.keySet().size() == 0) {
            return;
        }
        boolean selectMode = false;
        List<GeoPoint> points = new ArrayList<>();
        for (String devName : markers.keySet()) {
            Marker m = markers.get(devName);
            if (devName.equals(selectedDeviceItemId)) {
                if (selectMode) {
                }
                else {
                    selectMode = true;
                    points.clear();
                }
                // anyway we want this point
                points.add(new GeoPoint(m.getPosition().getLatitude(), m.getPosition().getLongitude()));
            }
            else {
                if (selectMode) {
                }
                else {
                    points.add(new GeoPoint(m.getPosition().getLatitude(), m.getPosition().getLongitude()));
                }
            }
        }
        if (points.size() == 1) {
            GeoPoint p = new GeoPoint(points.get(0).getLatitude(), points.get(0).getLongitude());
            //map.getController().setZoom(18.0);
            //map.getController().setCenter(p);
            //map.invalidate();
            if (map.getZoomLevelDouble() > 17.0) {
                map.getController().animateTo(p);
            }
            else {
                map.getController().animateTo(p, 17.0, (long) 1000);
            }

            Log.i(TAG, "[set center] "+p+" map center "+map.getMapCenter());
        }
        else {
            BoundingBox bb = new BoundingBox(
                    points.get(0).getLatitude(), points.get(0).getLongitude(),
                    points.get(0).getLatitude(), points.get(0).getLongitude()
            );
            for (GeoPoint point : points) {
                if (point.getLatitude() < bb.getLatSouth()) {
                    bb.set(bb.getLatNorth(), bb.getLonEast(), point.getLatitude(), bb.getLonWest());
                }
                if (point.getLatitude() > bb.getLatNorth()) {
                    bb.set(point.getLatitude(), bb.getLonEast(), bb.getLatSouth(), bb.getLonWest());
                }
                if (point.getLongitude() > bb.getLonEast()) {
                    bb.set(bb.getLatNorth(), point.getLongitude(), bb.getLatSouth(), bb.getLonWest());
                }
                if (point.getLongitude() < bb.getLonWest()) {
                    bb.set(bb.getLatNorth(), bb.getLonEast(), bb.getLatSouth(), point.getLongitude());
                }
            }
            //map.postInvalidate();
            map.zoomToBoundingBox(bb, true, 40);
            //map.getController().setCenter(new GeoPoint(bb.getCenterLatitude(), bb.getCenterLongitude()));
            //map.postInvalidate();
            Log.i(TAG, "[zoomToBounds] "+bb+" map center "+map.getMapCenter());

        }
    }

    private Timer timer;
    private TimerTask timerTask;

    public void startRefresh() {
        if(timer != null) {
            return;
        }
        timerTask = new TimerTask() {

            @Override
            public void run() {
                // launch task of server sync with callback
                Log.i(TAG, "[Task run]");
                db.getPhonetrackServerSyncHelper().getSessionLastPositions(session, syncCallBack);
            }
        };
        int currentFreq = prefs.getInt("map_freq", 15);
        if (currentFreq == 0) {
            currentFreq = 15;
            prefs.edit().putInt("map_freq", 15).apply();
        }
        timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, 0, currentFreq*1000);
    }

    public void stopRefresh() {
        if (timer != null) {
            timer.cancel();
            timer = null;
            timerTask = null;
        }
    }

    private IGetLastPosCallback syncCallBack = new IGetLastPosCallback() {
        @Override
        public void onFinish(Map<String, ColoredLocation> newLocations, String message) {
            for (String devName : newLocations.keySet()) {
                Log.i(TAG, "Results : "+devName+" | "+newLocations.get(devName));
                ColoredLocation loc = newLocations.get(devName);
                CustomLocationMarkerDrawable markerDrawable;
                // marker already exists, check if color needs to be updated
                if (markers.containsKey(devName)) {
                    markerDrawable = markerDrawables.get(devName);

                    String colorStr = loc.getColor();
                    if (colorStr != null) {
                        int newColor = Color.parseColor(colorStr);
                        int currentColor = markerDrawable.getColor();
                        Double currentAccuracy = markerDrawable.getAccuracy();
                        if (newColor != currentColor || currentAccuracy != loc.getAccuracy()) {
                            int textColor;
                            if (ThemeUtils.isBrightColor(newColor)) {
                                textColor = android.R.color.black;
                            }
                            else {
                                textColor = android.R.color.white;
                            }
                            markerDrawable.update(newColor, textColor, loc.getAccuracy());
                        }
                    }
                }
                // create the marker
                else {
                    Marker m = new Marker(map);
                    int color;
                    String colorStr = loc.getColor();
                    if (colorStr != null) {
                        color = Color.parseColor(colorStr);
                    }
                    else {
                        color = ThemeUtils.primaryColor(ctx);
                    }
                    int textColor;
                    if (ThemeUtils.isBrightColor(color)) {
                        textColor = android.R.color.black;
                    }
                    else {
                        textColor = android.R.color.white;
                    }
                    markerDrawable = new CustomLocationMarkerDrawable(R.mipmap.ic_marker, devName.substring(0, 1), color, textColor, loc.getAccuracy());
                    m.setIcon(markerDrawable);

                    map.getOverlays().add(m);
                    markers.put(devName, m);
                    markerDrawables.put(devName, markerDrawable);
                }

                // always update location data
                locations.put(devName, loc);
                Marker m = markers.get(devName);
                String text = devName;
                text += "\n"+sdfComplete.format(new Date(loc.getTimestamp()*1000));
                if (loc.getAltitude() != null) {
                    text += "\n"+getString(R.string.popup_altitude_value, loc.getAltitude());
                }
                if (loc.getAccuracy() != null) {
                    text += "\n"+getString(R.string.popup_accuracy_value, loc.getAccuracy());
                }
                if (loc.getSpeed() != null) {
                    text += "\n"+getString(R.string.popup_speed_value, loc.getSpeed()*3.6);
                }
                if (loc.getBearing() != null) {
                    text += "\n"+getString(R.string.popup_bearing_value, loc.getBearing());
                }
                if (loc.getSatellites() != null) {
                    text += "\n"+getString(R.string.popup_satellites, loc.getSatellites());
                }
                if (loc.getBattery() != null) {
                    text += "\n"+getString(R.string.popup_battery_value, loc.getBattery());
                }
                if (loc.getUserAgent() != null) {
                    text += "\n"+getString(R.string.popup_user_agent)+" : "+loc.getUserAgent();
                }
                m.setTitle(text);
                m.setPosition(new GeoPoint(loc.getLat(), loc.getLon()));
            }
            // delete removed
            List<String> devsToDel = new ArrayList<>();
            for (String markerDevName : markers.keySet()) {
                if (!newLocations.containsKey(markerDevName)) {
                    devsToDel.add(markerDevName);
                }
            }
            for (String devToDel : devsToDel) {
                map.getOverlays().remove(markers.get(devToDel));
                markers.remove(devToDel);
                locations.remove(devToDel);
                markerDrawables.remove(devToDel);
            }

            map.invalidate();
            // update device list
            setupNavigationDeviceList();
            if (prefs.getBoolean("map_autozoom", true)) {
                zoomOnAllMarkers();
            }
        }
    };

    private void setupMapButtons() {
        btDisplayMyLoc = (ImageButton) findViewById(R.id.ic_center_map);
        btFollowMe = (ImageButton) findViewById(R.id.ic_follow_me);
        btZoom = (ImageButton) findViewById(R.id.ic_zoom_all);
        btZoomAuto = (ImageButton) findViewById(R.id.ic_zoom_auto);
        btLayers = (ImageButton) findViewById(R.id.ic_map_layers);

        btDisplayMyLoc.setColorFilter(Color.WHITE);
        btFollowMe.setColorFilter(Color.WHITE);
        btZoom.setColorFilter(Color.WHITE);
        btZoomAuto.setColorFilter(Color.WHITE);
        btLayers.setColorFilter(Color.WHITE);

        if (prefs.getBoolean("map_myposition", true)) {
            btDisplayMyLoc.setBackground(toggleCircle);
            mLocationOverlay.enableMyLocation();
        }
        else {
            mLocationOverlay.disableMyLocation();
            prefs.edit().putBoolean("map_followme", false).apply();
        }

        btDisplayMyLoc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "centerMap clicked ");
                if (mLocationOverlay.isMyLocationEnabled()) {
                    mLocationOverlay.disableMyLocation();
                    mLocationOverlay.disableFollowLocation();
                    btDisplayMyLoc.setBackgroundResource(0);
                    btFollowMe.setBackgroundResource(0);
                    prefs.edit().putBoolean("map_myposition", false).apply();
                    prefs.edit().putBoolean("map_followme", false).apply();
                }
                else {
                    mLocationOverlay.enableMyLocation();
                    btDisplayMyLoc.setBackground(toggleCircle);
                    prefs.edit().putBoolean("map_myposition", true).apply();
                }
            }
        });

        if (prefs.getBoolean("map_followme", false)) {
            // disable auto zoom (which shouldn't be enabled but who knows these days)
            btZoomAuto.setBackgroundResource(0);
            prefs.edit().putBoolean("map_autozoom", false).apply();
            // enable follow me
            mLocationOverlay.enableMyLocation();
            mLocationOverlay.enableFollowLocation();
            btFollowMe.setBackground(toggleCircle);
            btDisplayMyLoc.setBackground(toggleCircle);
        }
        else {
            mLocationOverlay.disableFollowLocation();
        }

        btFollowMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "btFollowMe clicked ");
                if (mLocationOverlay.isFollowLocationEnabled()) {
                    mLocationOverlay.disableFollowLocation();
                    btFollowMe.setBackgroundResource(0);
                    prefs.edit().putBoolean("map_followme", false).apply();
                }
                else {
                    // disable autozoom
                    btZoomAuto.setBackgroundResource(0);
                    prefs.edit().putBoolean("map_autozoom", false).apply();
                    // enable follow me
                    mLocationOverlay.enableMyLocation();
                    mLocationOverlay.enableFollowLocation();
                    btFollowMe.setBackground(toggleCircle);
                    btDisplayMyLoc.setBackground(toggleCircle);
                    prefs.edit().putBoolean("map_myposition", true).apply();
                    prefs.edit().putBoolean("map_followme", true).apply();
                }
            }
        });



        btZoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "btZoom clicked ");
                zoomOnAllMarkers();
            }
        });

        if (prefs.getBoolean("map_autozoom", true)) {
            btZoomAuto.setBackground(toggleCircle);
            // disable follow me
            mLocationOverlay.disableFollowLocation();
            btFollowMe.setBackgroundResource(0);
            prefs.edit().putBoolean("map_followme", false).apply();
        }

        btZoomAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "btAUTOZoom clicked ");
                if (!prefs.getBoolean("map_autozoom", true)) {
                    // disable follow me
                    mLocationOverlay.disableFollowLocation();
                    btFollowMe.setBackgroundResource(0);
                    prefs.edit().putBoolean("map_followme", false).apply();
                    // enable auto zoom
                    btZoomAuto.setBackground(toggleCircle);
                    prefs.edit().putBoolean("map_autozoom", true).apply();
                    zoomOnAllMarkers();
                }
                else {
                    btZoomAuto.setBackgroundResource(0);
                    prefs.edit().putBoolean("map_autozoom", false).apply();
                }
            }
        });

        btLayers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder selectBuilder = new AlertDialog.Builder(new ContextThemeWrapper(map.getContext(), R.style.AppThemeDialog));
                selectBuilder.setTitle(getString(R.string.map_choose_layer));

                final CharSequence[] layers = layersMap.keySet().toArray(new CharSequence[layersMap.keySet().size()]);
                List<String> layerNamesList = new ArrayList<>();
                for (int i=0; i<layers.length; i++) {
                    layerNamesList.add(layers[i].toString());
                }
                int checked = layerNamesList.indexOf(selectedLayer);
                selectBuilder.setSingleChoiceItems(layers, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedLayer = layers[which].toString();
                        setTileSource(selectedLayer);
                        prefs.edit().putString("map_selected_layer", selectedLayer).apply();
                        dialog.dismiss();
                    }
                });
                selectBuilder.setNegativeButton(getString(R.string.simple_cancel), null);
                AlertDialog selectDialog = selectBuilder.create();
                selectDialog.show();
            }
        });
    }

    private void setTileSource(String layerKey) {
        // unfortunately i didn't find a way to keep existing tile providers
        // it seems they are destoryed/detached when an other one is selected
        // so here, we create a new one each time
        if (layerKey.equals("MapsForge")) {
            map.setTileProvider(getMapsForgeTileProvider());
        }
        else {
            defaultTileProvider = new MapTileProviderBasic(getApplicationContext());
            defaultTileProvider.setTileSource(layersMap.get(layerKey));
            map.setTileProvider(defaultTileProvider);
        }
    }

    protected static Set<File> findMapFiles() {
        Set<File> maps = new HashSet<>();
        List<StorageUtils.StorageInfo> storageList = StorageUtils.getStorageList();
        for (int i = 0; i < storageList.size(); i++) {
            File f = new File(storageList.get(i).path + File.separator + "osmdroid" + File.separator);
            if (f.exists()) {
                maps.addAll(scan(f));
            }
        }
        return maps;
    }

    static private Collection<? extends File> scan(File f) {
        List<File> ret = new ArrayList<>();
        File[] files = f.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.getName().toLowerCase().endsWith(".map"))
                    return true;
                return false;
            }
        });
        if (files != null) {
            Collections.addAll(ret, files);
        }
        return ret;
    }

    /**
     * Marker drawable icon with accuracy
     */
    private class CustomLocationMarkerDrawable extends Drawable  {
        // Cached values
        final String mLetter;
        final int mDrawableId;
        private Double mAccuracy;
        private int mColor;

        // Main part of the icon, which is only regenerated on colour change
        private Bitmap mBitmap;

        private final Paint mPaint;
        private final Paint mAccuracyPaint;
        private final Paint mAccuracyBorderPaint;

        /**
         * Constructor
         * @param drawableId Drawable identifier for the icon resource
         * @param text Letter to place on the marker
         * @param markerColor Primary color
         * @param textColorId Text color
         * @param accuracy Location accuracy
         */
        public CustomLocationMarkerDrawable(int drawableId, String text, int markerColor, int textColorId, Double accuracy) {
            // Cache values
            mLetter = text;
            mDrawableId = drawableId;

            // Create paints
            mPaint = new Paint();
            mAccuracyPaint = new Paint();
            mAccuracyBorderPaint = new Paint();

            // Initialize icon and accuracy paints
            update(markerColor, textColorId, accuracy);
        }

        /**
         * Update for changed color or accuracy
         * @param markerColor Primary color
         * @param textColorId Text color
         * @param accuracy Location accuracy
         */
        public void update(int markerColor, int textColorId, Double accuracy) {
            mBitmap = BitmapFactory.decodeResource(ctx.getResources(), mDrawableId).copy(Bitmap.Config.ARGB_8888, true);
            mBitmap = Bitmap.createScaledBitmap(mBitmap, 70, 70, true);

            // Cache for use updating accuracy circle
            mColor = markerColor;

            Canvas canvas = new Canvas(mBitmap);
            Paint paintCol = new Paint();

            ColorFilter filter = new PorterDuffColorFilter(
                    markerColor,
                    PorterDuff.Mode.SRC_IN
            );
            paintCol.setColorFilter(filter);

            canvas.drawBitmap(mBitmap, 0, 0, paintCol);

            Paint paint = new Paint();

            paint.setStyle(Paint.Style.FILL);
            //paint.setColor(Color.BLACK);
            paint.setColor(ContextCompat.getColor(ctx, textColorId));
            paint.setTextSize(35);
            paint.setAntiAlias(true);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            float textWidth = paint.measureText(mLetter);

            canvas.drawText(mLetter, mBitmap.getWidth()/2 - textWidth/2, mBitmap.getHeight()/2, paint);

            // Store accuracy
            mAccuracy = accuracy;

            // Create accuracy paints for updated color
            mAccuracyPaint.setAntiAlias(false);
            mAccuracyPaint.setStyle(Paint.Style.FILL);
            mAccuracyPaint.setColor(mColor);
            mAccuracyPaint.setAlpha(45);

            mAccuracyBorderPaint.setAntiAlias(true);
            mAccuracyBorderPaint.setStyle(Paint.Style.STROKE);
            mAccuracyBorderPaint.setColor(mColor);
            mAccuracyBorderPaint.setAlpha(180);
        }

        public Double getAccuracy() {
            return mAccuracy;
        }

        public int getColor() {
            return mColor;
        }

        @Override
        public void draw(Canvas canvas) {
            final Rect bounds = getBounds();

            // Draw accuracy, if we have one
            if (mAccuracy != null) {
                final float accuracyRadius = map.getProjection().metersToPixels(mAccuracy.floatValue());

                // Avoid drawing if it's going to be very small
                if (accuracyRadius > 15) {
                    canvas.drawCircle(bounds.centerX(), bounds.centerY()+getIntrinsicHeight()/2, accuracyRadius, mAccuracyPaint);
                    canvas.drawCircle(bounds.centerX(), bounds.centerY()+getIntrinsicHeight()/2, accuracyRadius, mAccuracyBorderPaint);
                }
            }

            // Draw main icon
            canvas.drawBitmap(mBitmap, bounds.centerX() - mBitmap.getWidth()/2, bounds.centerY() + mBitmap.getHeight()/2 - mBitmap.getHeight(), mPaint);

            // Debug marker
            // canvas.drawCircle(bounds.centerX(), bounds.centerY() + getIntrinsicHeight()/2, 5, mPaint);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void setAlpha(int arg0) {
        }

        @Override
        public void setColorFilter(ColorFilter arg0) {
        }

        @Override
        public int getIntrinsicWidth() {
            return mBitmap.getWidth();
        }

        @Override
        public int getIntrinsicHeight() {
            return mBitmap.getHeight();
        }
    }
}
