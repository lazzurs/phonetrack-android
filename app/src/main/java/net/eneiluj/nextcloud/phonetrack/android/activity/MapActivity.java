package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLocation;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;
import net.eneiluj.nextcloud.phonetrack.util.IGetLastPosCallback;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay.backgroundColor;

public class MapActivity extends Activity {
    MapView map = null;

    private static final String TAG = MapActivity.class.getSimpleName();

    public static final String PARAM_SESSIONID = "net.eneiluj.nextcloud.phonetrack.mapSessionId";

    private MyLocationNewOverlay mLocationOverlay;
    private CompassOverlay mCompassOverlay;
    private RotationGestureOverlay mRotationGestureOverlay;
    private ScaleBarOverlay mScaleBarOverlay;
    private Context ctx;

    private ImageButton btCenterMap;
    private ImageButton btFollowMe;

    private Map<String, DBLocation> locations;
    private Map<String, Marker> markers;

    private DBSession session;
    private PhoneTrackSQLiteOpenHelper db;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        setContentView(R.layout.activity_map);

        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);

        map.setMultiTouchControls(true);



        this.mLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(ctx), map);
        //this.mLocationOverlay.enableMyLocation();
        //this.mLocationOverlay.enableFollowLocation();
        this.mLocationOverlay.setEnableAutoStop(true);
        map.getOverlays().add(this.mLocationOverlay);

        btCenterMap = (ImageButton) findViewById(R.id.ic_center_map);

        btCenterMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "centerMap clicked ");
                if (mLocationOverlay.isMyLocationEnabled()) {
                    mLocationOverlay.disableMyLocation();
                }
                else {
                    mLocationOverlay.enableMyLocation();
                    Location currentLocation = mLocationOverlay.getLastFix();
                    if (currentLocation != null) {
                        GeoPoint myPosition = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
                        map.getController().animateTo(myPosition);
                    }
                }
            }
        });

        btFollowMe = (ImageButton) findViewById(R.id.ic_follow_me);
        //btFollowMe.setColorFilter(Color.argb(255, 128, 128, 128));

        btFollowMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "btFollowMe clicked ");
                mLocationOverlay.enableMyLocation();
                mLocationOverlay.enableFollowLocation();
            }
        });

        btFollowMe = (ImageButton) findViewById(R.id.ic_zoom_all);
        //btFollowMe.setColorFilter(Color.argb(255, 128, 128, 128));

        btFollowMe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "btZoom clicked ");
                //zoomOnAllMarkers();
            }
        });


        IMapController mapController = map.getController();
        mapController.setZoom(15.5);
        //Location startPoint = this.mLocationOverlay.get
        //System.out.println("STARTPOINT" + startPoint);
        //mapController.setCenter(new GeoPoint(startPoint.getLatitude(), startPoint.getLongitude()));

        /*mRotationGestureOverlay = new RotationGestureOverlay(map);
        mRotationGestureOverlay.setEnabled(true);
        map.getOverlays().add(this.mRotationGestureOverlay);
        */

        /*this.mCompassOverlay = new CompassOverlay(ctx, map);
        this.mCompassOverlay.enableCompass();
        map.getOverlays().add(this.mCompassOverlay);
        */

        final DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        mScaleBarOverlay = new ScaleBarOverlay(map);
        mScaleBarOverlay.setCentred(true);
        //play around with these values to get the location on screen in the right place for your application
        mScaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, 10);
        map.getOverlays().add(this.mScaleBarOverlay);


        //build a marker
        Marker m = new Marker(map);
        m.setTextLabelBackgroundColor(Color.GREEN);
        BitmapDrawable bmd = writeOnDrawable(R.mipmap.ic_marker, "A", R.color.bg_attention, android.R.color.black);
        m.setIcon(bmd);
        m.setTitle("hello world");
        m.setPosition(new GeoPoint(43.6617,3.8473));
        map.getOverlays().add(m);
    }

    public void onResume(){
        Log.i(TAG, "[onResume begin]");
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up

        this.mLocationOverlay.enableMyLocation();
        //this.mLocationOverlay.enableFollowLocation();
        Location currentLocation = mLocationOverlay.getLastFix();
        if (currentLocation != null) {
            GeoPoint myPosition = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
            map.getController().animateTo(myPosition);
        }

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
            }
        }
    };
}
