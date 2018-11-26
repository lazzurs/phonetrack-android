package it.eneiluj.nextcloud.phonetrack.model;

import java.io.Serializable;
import java.util.Calendar;

import it.eneiluj.nextcloud.phonetrack.util.NoteUtil;

/**
 * DBLogjob represents a single note from the local SQLite database with all attributes.
 */
public class DBLogjob implements Item, Serializable {

    private long id;
    private String title = "";
    private String nextURL;
    private String token;
    private String deviceName;
    private Boolean enabled;
    private DBStatus status;

    public DBLogjob(long id, String title, String nextURL, String token, String deviceName, Boolean enabled) {
        this.id = id;
        this.title = title;
        this.nextURL = nextURL;
        this.token = token;
        this.deviceName = deviceName;
        this.enabled = enabled;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getToken() {
        return token;
    }

    public String getNextURL() {
        return nextURL;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public Boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isSection() {
        return false;
    }

    @Override
    public String toString() {
        return "#DBLogjob" + getId() + "/" + this.title + ", " +
                this.nextURL + ", " + this.token + ", " +
                this.deviceName;
    }
}
