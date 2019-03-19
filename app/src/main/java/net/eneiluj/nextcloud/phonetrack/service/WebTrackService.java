package net.eneiluj.nextcloud.phonetrack.service;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.eneiluj.nextcloud.phonetrack.BuildConfig;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.LogjobsListViewActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLocation;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.persistence.WebTrackHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import at.bitfire.cert4android.CustomCertManager;

import static android.app.PendingIntent.FLAG_ONE_SHOT;

/**
 * Service synchronizing local database positions with remote server.
 *
 */

public class WebTrackService extends IntentService {

    private static final String TAG = WebTrackService.class.getSimpleName();
    public static final String BROADCAST_SYNC_FAILED = "net.eneiluj.nextcloud.phonetrack.broadcast.sync_failed";
    public static final String BROADCAST_SYNC_STARTED = "net.eneiluj.nextcloud.phonetrack.broadcast.sync_started";
    public static final String BROADCAST_SYNC_DONE = "net.eneiluj.nextcloud.phonetrack.broadcast.sync_done";

    private PhoneTrackSQLiteOpenHelper db;
    private WebTrackHelper web;
    private static PendingIntent pi = null;

    final private static int FIVE_MINUTES = 1000 * 60 * 5;

    public WebTrackService() {
        super("WebTrackService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (LoggerService.DEBUG) { Log.d(TAG, "[websync create]"); }

        db = PhoneTrackSQLiteOpenHelper.getInstance(this);
        CustomCertManager certManager = db.getPhonetrackServerSyncHelper().getCustomCertManager();
        web = new WebTrackHelper(this, certManager);
    }

    /**
     * Handle synchronization intent
     * @param intent Intent
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        if (LoggerService.DEBUG) { Log.d(TAG, "[websync start]"); }

        long logjobId = intent.getLongExtra(LogjobsListViewActivity.UPDATED_LOGJOB_ID, 0);

        if (pi != null) {
            // cancel pending alarm
            if (LoggerService.DEBUG) { Log.d(TAG, "[websync cancel alarm]"); }
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.cancel(pi);
            }
            pi = null;
        }

        doSync(logjobId);

    }

    /**
     * Send all positions in database
     */
    private void doSync(long ljIdToSync) {
        boolean anyError = false;

        // get the logjobs
        List<DBLogjob> logjobs;
        if (ljIdToSync == 0) {
            // iterate over positions in db
            logjobs = db.getLogjobs();
        }
        // if only one logjob is asked, just get this one
        else {
            logjobs = new ArrayList<>();
            logjobs.add(db.getLogjob(ljIdToSync));
        }

        if (logjobs.size() > 0) {
            // start loading animation in logjob list
            Intent intent = new Intent(BROADCAST_SYNC_STARTED);
            sendBroadcast(intent);
        }

        for (DBLogjob logjob : logjobs) {
            long ljId = logjob.getId();
            try {
                // PhoneTrack logjob
                if (!logjob.getDeviceName().isEmpty() && !logjob.getToken().isEmpty()) {
                    URL url = web.getUrlFromPhoneTrackLogjob(logjob);
                    List<DBLocation> locations = db.getLocationOfLogjob(ljId);
                    // send one by one
                    if (locations.size() <= 5) {
                        for (DBLocation loc : locations) {
                            long locId = loc.getId();
                            Map<String, String> params = dbLocationToMap(loc);
                            web.postPositionToPhoneTrack(url, params);
                            db.deleteLocation(locId);
                            db.incNbSync(logjob);
                            db.setLastSyncTimestamp(ljId, System.currentTimeMillis() / 1000);
                            Intent intent = new Intent(BROADCAST_SYNC_DONE);
                            intent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, ljId);
                            sendBroadcast(intent);
                        }
                        if (locations.size() > 0) {
                            db.resetLastSyncError(ljId);
                        }
                    }
                    // send multiple locations per request
                    else {
                        url = web.getUrlMultipleFromPhoneTrackLogjob(logjob);
                        List<DBLocation> tmpLocs = new ArrayList<>();
                        int n = 0;
                        for (DBLocation loc : locations) {
                            tmpLocs.add(loc);
                            n++;
                            if (n%200 == 0) {
                                JSONObject params = dbLocationsToJSON(tmpLocs);
                                web.postMultiplePositionsToPhoneTrack(url, params);
                                for (DBLocation locToDel : tmpLocs) {
                                    long locId = locToDel.getId();
                                    db.deleteLocation(locId);
                                    db.incNbSync(logjob);
                                }
                                tmpLocs = new ArrayList<>();
                                // update nbsync in logjob list
                                Intent intent = new Intent(BROADCAST_SYNC_DONE);
                                intent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, ljId);
                                sendBroadcast(intent);
                            }
                        }
                        // last bunch
                        if (tmpLocs.size() > 0) {
                            JSONObject params = dbLocationsToJSON(tmpLocs);
                            web.postMultiplePositionsToPhoneTrack(url, params);
                            for (DBLocation locToDel : tmpLocs) {
                                long locId = locToDel.getId();
                                db.deleteLocation(locId);
                                db.incNbSync(logjob);
                            }
                        }
                        db.setLastSyncTimestamp(ljId, System.currentTimeMillis() / 1000);
                        db.resetLastSyncError(ljId);
                        Intent intent = new Intent(BROADCAST_SYNC_DONE);
                        intent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, ljId);
                        sendBroadcast(intent);
                    }
                }
                // custom logjob
                else {
                    String destUrl = logjob.getUrl();
                    List<DBLocation> locations = db.getLocationOfLogjob(ljId);
                    for (DBLocation loc : locations) {
                        long locId = loc.getId();
                        Map<String, String> params = dbLocationToMap(loc);
                        if (logjob.getPost()) {
                            web.sendPOSTPositionToCustom(destUrl, params);
                        }
                        else {
                            web.sendGETPositionToCustom(destUrl, params);
                        }

                        db.deleteLocation(locId);
                        db.incNbSync(logjob);
                        db.setLastSyncTimestamp(ljId, System.currentTimeMillis() / 1000);
                        Intent intent = new Intent(BROADCAST_SYNC_DONE);
                        intent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, ljId);
                        sendBroadcast(intent);
                    }
                    if (locations.size() > 0) {
                        db.resetLastSyncError(ljId);
                    }
                }
            } catch (IOException e) {
                // handle web errors
                if (LoggerService.DEBUG) {
                    Log.d(TAG, "[websync io exception: " + e + "]");
                }
                anyError = true;
                handleError(e, ljId);
            } catch (JSONException e2) {
                anyError = true;
                handleError(e2, ljId);
            }
        }
        // retry only if there was any error and tracking is on
        if (anyError && LoggerService.isRunning()) {
            if (LoggerService.DEBUG) { Log.d(TAG, "[websync set alarm]"); }
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent syncIntent = new Intent(getApplicationContext(), WebTrackService.class);
            pi = PendingIntent.getService(this, 0, syncIntent, FLAG_ONE_SHOT);
            if (am != null) {
                am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + FIVE_MINUTES, pi);
            }
        }
        // notify loggerservice to update notification content
        if (LoggerService.isRunning()) {
            Intent intent = new Intent(this, LoggerService.class);
            intent.putExtra(LoggerService.UPDATE_NOTIFICATION, true);
            startService(intent);
        }
        // stop loading animation in logjob list
        Intent intent = new Intent(BROADCAST_SYNC_DONE);
        sendBroadcast(intent);
    }

    /**
     * Actions performed in case of synchronization error.
     * Send broadcast to main activity, schedule retry if tracking is on.
     *
     * @param e Exception
     */
    private void handleError(Exception e, long ljId) {
        String message;
        if (e instanceof UnknownHostException) {
            message = getString(R.string.e_unknown_host, e.getMessage());
        } else if (e instanceof MalformedURLException || e instanceof URISyntaxException) {
            message = getString(R.string.e_bad_url, e.getMessage());
        } else if (e instanceof ConnectException || e instanceof NoRouteToHostException) {
            message = getString(R.string.e_connect, e.getMessage());
        } else {
            message = e.getMessage();
        }
        if (LoggerService.DEBUG) { Log.d(TAG, "[websync retry: " + message + "]"); }

        db.setLastSyncError(ljId, System.currentTimeMillis()/1000, message);
        //db.setError(message);
        Intent intent = new Intent(BROADCAST_SYNC_FAILED);
        intent.putExtra(LoggerService.BROADCAST_EXTRA_PARAM, ljId);
        intent.putExtra(LoggerService.BROADCAST_ERROR_MESSAGE, message);
        sendBroadcast(intent);
    }

    /**
     * Convert cursor to map of request parameters
     *
     * @return Map of parameters
     */
    private Map<String, String> dbLocationToMap(DBLocation loc) {
        if (LoggerService.DEBUG) { Log.d(TAG, "[DBLOC to map "+loc+"]"); }

        Map<String, String> params = new HashMap<>();
        params.put(WebTrackHelper.PARAM_TIME, String.valueOf(loc.getTimestamp()));
        params.put(WebTrackHelper.PARAM_LAT, String.valueOf(loc.getLat()));
        params.put(WebTrackHelper.PARAM_LON, String.valueOf(loc.getLon()));
        params.put(WebTrackHelper.PARAM_ALT, (loc.getAltitude() != null) ? String.valueOf(loc.getAltitude()) : "");
        params.put(WebTrackHelper.PARAM_ACCURACY, (loc.getAccuracy() != null) ? String.valueOf(loc.getAccuracy()): "");
        params.put(WebTrackHelper.PARAM_SPEED, (loc.getSpeed() != null) ? String.valueOf(loc.getSpeed()) : "");
        params.put(WebTrackHelper.PARAM_BEARING, (loc.getBearing() != null) ? String.valueOf(loc.getBearing()) : "");
        params.put(WebTrackHelper.PARAM_SATELLITES, (loc.getSatellites() != null) ? String.valueOf(loc.getSatellites()) : "");
        params.put(WebTrackHelper.PARAM_BATTERY, String.valueOf(loc.getBattery()));
        params.put(WebTrackHelper.PARAM_USERAGENT, (loc.getUserAgent() != null) ? loc.getUserAgent() : "");
        return params;
    }

    private JSONObject dbLocationsToJSON(List<DBLocation> locations) throws JSONException {
        if (LoggerService.DEBUG) { Log.d(TAG, "[DBLOC to JSONObject]"); }

        JSONObject result = new JSONObject();
        JSONArray points = new JSONArray();

        for (DBLocation loc : locations) {
            JSONArray point = new JSONArray();
            point.put(loc.getLat());
            point.put(loc.getLon());
            point.put(loc.getTimestamp());
            point.put(loc.getAltitude());
            point.put(loc.getAccuracy());
            point.put(loc.getBattery());
            point.put(loc.getSatellites());
            point.put(loc.getUserAgent());
            point.put(loc.getSpeed());
            point.put(loc.getBearing());

            points.put(point);
        }

        result.put("points", points);
        return result;
    }

    /**
     * Cleanup
     */
    @Override
    public void onDestroy() {
        if (LoggerService.DEBUG) { Log.d(TAG, "[websync stop]"); }
        super.onDestroy();
    }

}
