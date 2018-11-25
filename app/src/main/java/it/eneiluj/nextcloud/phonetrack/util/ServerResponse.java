package it.eneiluj.nextcloud.phonetrack.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import it.eneiluj.nextcloud.phonetrack.model.CloudSession;

/**
 * Provides entity classes for handling server responses with a single note ({@link SessionResponse}) or a list of phonetrack ({@link SessionsResponse}).
 */
public class ServerResponse {

    public static class NotModifiedException extends IOException {
    }

    public static class SessionResponse extends ServerResponse {
        public SessionResponse(NotesClient.ResponseData response) {
            super(response);
        }

        public CloudSession getSession() throws JSONException {
            return getSessionFromJSON(new JSONObject(getContent()));
        }
    }

    public static class SessionsResponse extends ServerResponse {
        public SessionsResponse(NotesClient.ResponseData response) {
            super(response);
        }

        public List<CloudSession> getSessions() throws JSONException {
            List<CloudSession> sessionsList = new ArrayList<>();
            JSONObject topObj = new JSONObject(getContent());
            JSONArray sessions = new JSONArray(topObj.get("sessions"));
            for (int i = 0; i < sessions.length(); i++) {
                JSONArray json = sessions.getJSONArray(i);
                sessionsList.add(getSessionFromJSON(json));
            }
            return sessionsList;
        }
    }


    private final NotesClient.ResponseData response;

    public ServerResponse(NotesClient.ResponseData response) {
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

    protected CloudSession getSessionFromJSON(JSONArray json) throws JSONException {
        long id = 0;
        String name = "";
        String token = "";
        if (json.length() > 1) {
            name = json.get(0);
            token = json.get(1);
        }
        /*if (!json.isNull(NotesClient.JSON_ID)) {
            id = json.getLong(NotesClient.JSON_ID);
        }
        if (!json.isNull(NotesClient.JSON_TITLE)) {
            title = json.getString(NotesClient.JSON_TITLE);
        }
        if (!json.isNull(NotesClient.JSON_CONTENT)) {
            content = json.getString(NotesClient.JSON_CONTENT);
        }
        if (!json.isNull(NotesClient.JSON_MODIFIED)) {
            modified = GregorianCalendar.getInstance();
            modified.setTimeInMillis(json.getLong(NotesClient.JSON_MODIFIED) * 1000);
        }
        if (!json.isNull(NotesClient.JSON_FAVORITE)) {
            favorite = json.getBoolean(NotesClient.JSON_FAVORITE);
        }
        if (!json.isNull(NotesClient.JSON_CATEGORY)) {
            category = json.getString(NotesClient.JSON_CATEGORY);
        }
        if (!json.isNull(NotesClient.JSON_ETAG)) {
            etag = json.getString(NotesClient.JSON_ETAG);
        }
        return new CloudSession(id, modified, title, content, favorite, category, etag);
        */
        return new CloudSession(name, token);
    }
}
