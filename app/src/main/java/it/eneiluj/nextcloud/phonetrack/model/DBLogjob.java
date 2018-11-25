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
    private DBStatus status;
    private String excerpt = "";

    public DBLogjob(long id, String title, String nextURL, String token, String deviceName, DBStatus status) {
        this.id = id;
        this.title = title;
        this.nextURL = nextURL;
        this.token = token;
        this.deviceName = deviceName;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public DBStatus getStatus() {
        return status;
    }

    public void setStatus(DBStatus status) {
        this.status = status;
    }

    @Override
    public boolean isSection() {
        return false;
    }

    @Override
    public String toString() {
        return "#DBLogjob" + getId() + "/" + this.title + ", " +
                this.nextURL + ", " + this.token + ", " +
                this.deviceName + ", " + getStatus();
    }
}
