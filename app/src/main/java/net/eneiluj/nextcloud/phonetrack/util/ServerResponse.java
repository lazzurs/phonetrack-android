package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.content.SharedPreferences;
//import android.preference.PreferenceManager;
import android.support.v7.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;
import net.eneiluj.nextcloud.phonetrack.model.DBLocation;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

/**
 * Provides entity classes for handling server responses with a single logjob ({@link SessionResponse}) or a list of phonetrack ({@link SessionsResponse}).
 */
public class ServerResponse {

    public static class NotModifiedException extends IOException {
    }

    public static class SessionResponse extends ServerResponse {
        public SessionResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public DBSession getSession(PhoneTrackSQLiteOpenHelper dbHelper) throws JSONException {
            return getSessionFromJSON(new JSONArray(getContent()), dbHelper);
        }
    }

    public static class SessionsResponse extends ServerResponse {
        public SessionsResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public List<DBSession> getSessions(PhoneTrackSQLiteOpenHelper dbHelper) throws JSONException {
            List<DBSession> sessionsList = new ArrayList<>();
            //JSONObject topObj = new JSONObject(getTitle());
            JSONArray sessions = new JSONArray(getContent());
            for (int i = 0; i < sessions.length(); i++) {
                JSONArray json = sessions.getJSONArray(i);
                // if session is not shared
                //if (json.length() > 4) {
                sessionsList.add(getSessionFromJSON(json, dbHelper));
                //}
            }
            return sessionsList;
        }
    }

    public static class ShareDeviceResponse extends ServerResponse {
        public ShareDeviceResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public String getPublicToken() throws JSONException {
            return getPublicTokenFromJSON(new JSONObject(getContent()));
        }
    }

    public static class GetSessionLastPositionsResponse extends ServerResponse {
        public GetSessionLastPositionsResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public Map<String, DBLocation> getPositions(DBSession session) throws JSONException {
            return getPositionsFromJSON(new JSONObject(getContent()), session);
        }
    }

    private final PhoneTrackClient.ResponseData response;

    public ServerResponse(PhoneTrackClient.ResponseData response) {
        this.response = response;
    }

    protected String getContent() {
        return response.getContent();
    }

    public String getETag() {
        return response.getETag();
    }

    public long getLastModified() {
        return response.getLastModified();
    }

    protected String getPublicTokenFromJSON(JSONObject json) throws JSONException {
        int done = 0;
        String publictoken;
        if (json.has("code") && json.has("sharetoken")) {
            done = json.getInt("code");
            publictoken = json.getString("sharetoken");
            if (done == 1) {
                return publictoken;
            }
        }
        return null;
    }

    protected Map<String, DBLocation> getPositionsFromJSON(JSONObject json, DBSession session) throws JSONException {
        Map<String, DBLocation> locations = new HashMap<>();
        if (json.has(session.getPublicToken())) {
            JSONObject jsonLocs = json.getJSONObject(session.getPublicToken());
            Iterator<String> keys = jsonLocs.keys();
            while(keys.hasNext()) {
                String devName = keys.next();
                JSONObject oneLoc = jsonLocs.getJSONObject(devName);
                locations.put(devName,
                        new DBLocation(
                                0, 0,
                                oneLoc.getDouble("lat"),
                                oneLoc.getDouble("lon"),
                                oneLoc.getLong("timestamp"),
                                oneLoc.isNull("bearing") ? null : oneLoc.getDouble("bearing"),
                                oneLoc.isNull("altitude") ? null : oneLoc.getDouble("altitude"),
                                oneLoc.isNull("speed") ? null : oneLoc.getDouble("speed"),
                                oneLoc.isNull("accuracy") ? null : oneLoc.getDouble("accuracy"),
                                oneLoc.isNull("satellites") ? null : oneLoc.getLong("satellites"),
                                oneLoc.isNull("batterylevel") ? null : oneLoc.getDouble("batterylevel"),
                                oneLoc.isNull("useragent") ? null : oneLoc.getString("useragent")
                        )
                );
            }
        }
        return locations;
    }

    protected DBSession getSessionFromJSON(JSONArray json, PhoneTrackSQLiteOpenHelper dbHelper) throws JSONException {
        String name = "";
        String token = "";
        String publicToken = "";
        boolean isPublic = true;
        boolean isFromShare = false;
        if (json.length() > 1) {
            name = json.getString(0);
            token = json.getString(1);
            publicToken = json.getString(2);
            isPublic = (json.getInt(4) != 0);
            isFromShare = (json.length() <= 6);
        }

        Context appContext = dbHelper.getContext().getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext.getApplicationContext());
        String url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS);
        return new DBSession(0, token, name, url, publicToken, isFromShare, isPublic);
    }
}
