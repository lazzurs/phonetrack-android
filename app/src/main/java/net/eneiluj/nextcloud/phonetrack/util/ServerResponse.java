package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;
import net.eneiluj.nextcloud.phonetrack.model.BasicLocation;
import net.eneiluj.nextcloud.phonetrack.model.ColoredLocation;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

//import android.preference.PreferenceManager;

/**
 * Provides entity classes for handling server responses with a single logjob ({@link SessionResponse}) or a list of phonetrack ({@link SessionsResponse}).
 */
public class ServerResponse {
    private static final String TAG = ServerResponse.class.getSimpleName();

    public static class NotModifiedException extends IOException {
    }

    public static class MapsAddPointResponse extends ServerResponse {
        public MapsAddPointResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public int getDeviceId() throws JSONException, Exception {
            return getDeviceIdFromJSON(new JSONObject(getContent()));
        }
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

    public static class CapabilitiesResponse extends ServerResponse {
        public CapabilitiesResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public String getColor() throws IOException {
            return getColorFromContent(getContent());
        }
    }

    public static class AvatarResponse extends ServerResponse {
        public AvatarResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public String getAvatarString() throws IOException {
            return getContent();
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

    public static class CreateSessionResponse extends ServerResponse {
        public CreateSessionResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public String getSessionId() throws JSONException, Exception {
            return getSessionIdFromJSON(new JSONObject(getContent()));
        }
    }

    public static class GetSessionLastPositionsResponse extends ServerResponse {
        public GetSessionLastPositionsResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public Map<String, ColoredLocation> getPositions(DBSession session) throws JSONException {
            return getPositionsFromJSON(new JSONObject(getContent()), session);
        }
    }

    public static class GetSessionPositionsResponse extends ServerResponse {
        public GetSessionPositionsResponse(PhoneTrackClient.ResponseData response) {
            super(response);
        }

        public Map<String, List<BasicLocation>> getPositions(DBSession session) throws JSONException {
            return getMultiplePositionsFromJSON(new JSONObject(getContent()), session);
        }

        public Map<String, String> getColors(DBSession session) throws JSONException {
            return getMultipleColorsFromJSON(new JSONObject(getContent()), session);
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
        if (json.has("done") && json.has("code") && json.has("sharetoken")) {
            done = json.getInt("done");
            publictoken = json.getString("sharetoken");
            if (done == 1) {
                return publictoken;
            }
        }
        return null;
    }

    protected String getSessionIdFromJSON(JSONObject json) throws JSONException, Exception {
        int done = 0;
        String sessionId;
        if (json.has("done") && json.has("token")) {
            done = json.getInt("done");
            sessionId = json.getString("token");
            if (done == 1) {
                return sessionId;
            }
            else if (done == 2) {
                throw new Exception("Session already exists");
            }
        }
        return null;
    }

    protected int getDeviceIdFromJSON(JSONObject json) throws JSONException, Exception {
        int deviceId = 0;
        String sessionId;
        if (json.has("deviceId")) {
            deviceId = json.getInt("deviceId");
        }
        return deviceId;
    }

    protected String getColorFromContent(String content) throws IOException {
        //Log.i(TAG, content);
        String result = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();

            InputStream stream = new ByteArrayInputStream(content.getBytes());
            Document doc = db.parse(stream);
            doc.getDocumentElement().normalize();
            // Locate the Tag Name
            NodeList nodelist = doc.getElementsByTagName("color");
            if (nodelist.getLength() > 0) {
                result = nodelist.item(0).getTextContent();
                Log.i(TAG,"I GOT THE COLOR from server: "+result);
            }
        }
        catch (ParserConfigurationException e) {
        }
        catch (SAXException e) {
        }
        return result;
    }

    protected Map<String, ColoredLocation> getPositionsFromJSON(JSONObject json, DBSession session) throws JSONException {
        Map<String, ColoredLocation> locations = new HashMap<>();
        if (json.has(session.getToken())) {
            JSONObject jsonLocs = json.getJSONObject(session.getToken());
            Iterator<String> keys = jsonLocs.keys();
            while (keys.hasNext()) {
                String devName = keys.next();
                JSONObject oneLoc = jsonLocs.getJSONObject(devName);
                locations.put(devName,
                        new ColoredLocation(
                                oneLoc.getDouble("lat"),
                                oneLoc.getDouble("lon"),
                                oneLoc.getLong("timestamp"),
                                oneLoc.isNull("bearing") ? null : oneLoc.getDouble("bearing"),
                                oneLoc.isNull("altitude") ? null : oneLoc.getDouble("altitude"),
                                oneLoc.isNull("speed") ? null : oneLoc.getDouble("speed"),
                                oneLoc.isNull("accuracy") ? null : oneLoc.getDouble("accuracy"),
                                oneLoc.isNull("satellites") ? null : oneLoc.getLong("satellites"),
                                oneLoc.isNull("batterylevel") ? null : oneLoc.getDouble("batterylevel"),
                                oneLoc.isNull("useragent") ? null : oneLoc.getString("useragent"),
                                oneLoc.isNull("color") ? null : oneLoc.getString("color")
                        )
                );
            }
        }
        return locations;
    }

    protected Map<String, List<BasicLocation>> getMultiplePositionsFromJSON(JSONObject json, DBSession session) throws JSONException {
        Map<String, List<BasicLocation>> locations = new HashMap<>();
        if (json.has(session.getToken()) && json.get(session.getToken()) instanceof JSONObject) {
            JSONObject jsonSession = json.getJSONObject(session.getToken());
            Iterator<String> keys = jsonSession.keys();
            while (keys.hasNext()) {
                String devName = keys.next();
                JSONObject oneDev = jsonSession.getJSONObject(devName);
                List<BasicLocation> devLocations = new ArrayList<>();
                // loop on points
                JSONArray points = oneDev.getJSONArray("points");
                for (int i = 0; i < points.length(); i++) {
                    JSONObject point = points.getJSONObject(i);
                    devLocations.add(
                        new BasicLocation(
                                point.getDouble("lat"),
                                point.getDouble("lon"),
                                point.getLong("timestamp"),
                                point.isNull("bearing") ? null : point.getDouble("bearing"),
                                point.isNull("altitude") ? null : point.getDouble("altitude"),
                                point.isNull("speed") ? null : point.getDouble("speed"),
                                point.isNull("accuracy") ? null : point.getDouble("accuracy"),
                                point.isNull("satellites") ? null : point.getLong("satellites"),
                                point.isNull("batterylevel") ? null : point.getDouble("batterylevel"),
                                point.isNull("useragent") ? null : point.getString("useragent")
                        )
                    );
                    locations.put(devName, devLocations);
                }
            }
        }
        return locations;
    }

    protected Map<String, String> getMultipleColorsFromJSON(JSONObject json, DBSession session) throws JSONException {
        Map<String, String> colors = new HashMap<>();
        if (json.has(session.getToken()) && json.get(session.getToken()) instanceof JSONObject) {
            JSONObject jsonSession = json.getJSONObject(session.getToken());
            Iterator<String> keys = jsonSession.keys();
            while (keys.hasNext()) {
                String devName = keys.next();
                JSONObject oneDev = jsonSession.getJSONObject(devName);
                String color = oneDev.isNull("color") ? null : oneDev.getString("color");
                colors.put(devName, color);
            }
        }
        return colors;
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
        String url;
        if (preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false)) {
            url = preferences.getString(SettingsActivity.SETTINGS_SSO_URL, SettingsActivity.DEFAULT_SETTINGS);
        }
        else {
            url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS);
        }
        return new DBSession(0, token, name, url, publicToken, isFromShare, isPublic);
    }
}
