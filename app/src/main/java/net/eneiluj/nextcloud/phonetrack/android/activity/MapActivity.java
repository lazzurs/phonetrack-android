package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLocation;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.IGetLastPosCallback;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MapActivity extends AppCompatActivity {
    MapView map = null;

    private static final String TAG = MapActivity.class.getSimpleName();

    public static final String PARAM_SESSIONID = "net.eneiluj.nextcloud.phonetrack.mapSessionId";

    private MyLocationNewOverlay mLocationOverlay;
    private CompassOverlay mCompassOverlay;
    private RotationGestureOverlay mRotationGestureOverlay;
    private ScaleBarOverlay mScaleBarOverlay;
    private Context ctx;

    private ImageButton btDisplayMyLoc;
    private ImageButton btFollowMe;
    private ImageButton btZoom;
    private ImageButton btZoomAuto;

    //private Map<String, DBLocation> locations;
    private Map<String, Marker> markers;

    private DBSession session;
    private PhoneTrackSQLiteOpenHelper db;

    private boolean autoZoom;

    @BindView(R.id.mapActivityActionBar)
    Toolbar toolbar;
    @BindView(R.id.drawerLayoutMap)
    DrawerLayout drawerLayoutMap;
    @BindView(R.id.account)
    TextView account;
    @BindView(R.id.relativelayoutMap)
    RelativeLayout relativeLayoutMap;

    @BindView(R.id.navigationList)
    RecyclerView listNavigationCategories;
    @BindView(R.id.navigationMenu)
    RecyclerView listNavigationMenu;

    private ActionBarDrawerToggle drawerToggle;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.drawer_layout_map);
        ButterKnife.bind(this);
        setupActionBar();
        drawerToggle.syncState();

        markers = new HashMap<>();
        autoZoom = true;

        //load/initialize the osmdroid configuration, this can be done
        this.ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the map has a writable location for the map cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's tile servers will get you banned based on this string
        ActivityCompat.requestPermissions(MapActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PackageManager.PERMISSION_GRANTED);

        db = PhoneTrackSQLiteOpenHelper.getInstance(ctx);

        long sid = getIntent().getLongExtra(PARAM_SESSIONID, 0);
        session = db.getSession(sid);
        Log.i(TAG, "CREATE map : session : "+session);

        //inflate and create the map
        //setContentView(R.layout.activity_map);

        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMaxZoomLevel(20.0);

        map.setMultiTouchControls(true);



        this.mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(ctx), map);
        this.mLocationOverlay.enableMyLocation();
        //this.mLocationOverlay.enableFollowLocation();
        this.mLocationOverlay.setEnableAutoStop(true);
        map.getOverlays().add(this.mLocationOverlay);

        btDisplayMyLoc = (ImageButton) findViewById(R.id.ic_center_map);
        btDisplayMyLoc.setBackgroundResource(R.drawable.ic_plain_circle_grey_24dp);


        btDisplayMyLoc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "centerMap clicked ");
                if (mLocationOverlay.isMyLocationEnabled()) {
                    mLocationOverlay.disableMyLocation();
                    mLocationOverlay.disableFollowLocation();
                    btDisplayMyLoc.setBackgroundResource(0);
                    btFollowMe.setBackgroundResource(0);
                }
                else {
                    mLocationOverlay.enableMyLocation();
                    btDisplayMyLoc.setBackgroundResource(R.drawable.ic_plain_circle_grey_24dp);
                }
            }
        });

        btFollowMe = (ImageButton) findViewById(R.id.ic_follow_me);
        //btFollowMe.setColorFilter(Color.argb(255, 128, 128, 128));

        btFollowMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "btFollowMe clicked ");
                if (mLocationOverlay.isFollowLocationEnabled()) {
                    mLocationOverlay.disableFollowLocation();
                    btFollowMe.setBackgroundResource(0);
                }
                else {
                    mLocationOverlay.enableMyLocation();
                    mLocationOverlay.enableFollowLocation();
                    btFollowMe.setBackgroundResource(R.drawable.ic_plain_circle_grey_24dp);
                    btDisplayMyLoc.setBackgroundResource(R.drawable.ic_plain_circle_grey_24dp);
                }
            }
        });

        btZoom = (ImageButton) findViewById(R.id.ic_zoom_all);
        //btFollowMe.setColorFilter(Color.argb(255, 128, 128, 128));

        btZoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "btZoom clicked ");
                zoomOnAllMarkers();
            }
        });

        btZoomAuto = (ImageButton) findViewById(R.id.ic_zoom_auto);
        if (!autoZoom) {
            btZoomAuto.setBackgroundResource(0);
        }
        else {
            btZoomAuto.setBackgroundResource(R.drawable.ic_plain_circle_grey_24dp);
        }

        btZoomAuto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "btZoom clicked ");
                if (autoZoom) {
                    btZoomAuto.setBackgroundResource(0);
                    autoZoom = false;
                }
                else {
                    btZoomAuto.setBackgroundResource(R.drawable.ic_plain_circle_grey_24dp);
                    autoZoom = true;
                }
            }
        });


        IMapController mapController = map.getController();
        mapController.setZoom(2.0);

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
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.syncState();
    }

    private void setupActionBar() {
        Log.i(TAG, "[setupactionbar]");
        setSupportActionBar(toolbar);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayoutMap, toolbar, R.string.action_drawer_open, R.string.action_drawer_close);
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerLayoutMap.addDrawerListener(drawerToggle);
        setTitle("Map");
    }

    public void onResume(){
        Log.i(TAG, "[onResume begin]");
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up

        //this.mLocationOverlay.enableMyLocation();
        //this.mLocationOverlay.enableFollowLocation();
        /*Location currentLocation = mLocationOverlay.getLastFix();
        if (currentLocation != null) {
            GeoPoint myPosition = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
            map.getController().animateTo(myPosition);
        }
        */

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

    private void zoomOnAllMarkers() {
        if (markers.keySet().size() == 0) {
            return;
        }
        boolean selectMode = false;
        List<GeoPoint> points = new ArrayList<>();
        for (String devName : markers.keySet()) {
            Marker m = markers.get(devName);
            if (m.isInfoWindowShown()) {
                if (selectMode) {

                } else {
                    selectMode = true;
                    points.clear();
                }
                points.add(new GeoPoint(m.getPosition().getLatitude(), m.getPosition().getLongitude()));
            } else {
                if (selectMode) {
                } else {
                    points.add(new GeoPoint(m.getPosition().getLatitude(), m.getPosition().getLongitude()));
                }
            }
        }
        if (points.size() == 1) {
            map.getController().setCenter(
                    new GeoPoint(points.get(0).getLatitude(), points.get(0).getLongitude())
            );
            map.getController().setZoom(18.0);
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
            map.zoomToBoundingBox(bb, true, 40);
        }
    }

    public BitmapDrawable writeOnDrawable(int drawableId, String text, int markerColorId, int textColorId){

        Bitmap bm = BitmapFactory.decodeResource(ctx.getResources(), drawableId).copy(Bitmap.Config.ARGB_8888, true);
        bm = Bitmap.createScaledBitmap(bm, 70, 70, true);

        Canvas canvas = new Canvas(bm);
        Paint paintCol = new Paint();

        ColorFilter filter = new PorterDuffColorFilter(
                ContextCompat.getColor(this, markerColorId),
                PorterDuff.Mode.SRC_IN
        );
        paintCol.setColorFilter(filter);

        canvas.drawBitmap(bm, 0, 0, paintCol);

        Paint paint = new Paint();

        paint.setStyle(Paint.Style.FILL);
        //paint.setColor(Color.BLACK);
        paint.setColor(ContextCompat.getColor(ctx, textColorId));
        paint.setTextSize(35);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float textWidth = paint.measureText(text);

        canvas.drawText(text, bm.getWidth()/2 - textWidth/2, bm.getHeight()/2, paint);

        return new BitmapDrawable(ctx.getResources(), bm);
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
        timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, 0, 10000);
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
        public void onFinish(Map<String, DBLocation> locations, String message) {
            for (String devName : locations.keySet()) {
                Log.i(TAG, "Results : "+devName+" | "+locations.get(devName));
                DBLocation loc = locations.get(devName);
                if (markers.containsKey(devName)) {

                }
                else {
                    Marker m = new Marker(map);
                    BitmapDrawable bmd = writeOnDrawable(R.mipmap.ic_marker, devName.substring(0, 1), R.color.bg_attention, android.R.color.black);
                    m.setIcon(bmd);
                    m.setTitle(devName);
                    //m.setPosition(new GeoPoint(43.6617,3.8473));
                    map.getOverlays().add(m);
                    markers.put(devName, m);
                }
                markers.get(devName).setPosition(new GeoPoint(loc.getLat(), loc.getLon()));
            }
            if (autoZoom) {
                zoomOnAllMarkers();
            }
        }
    };
}
