package it.eneiluj.nextcloud.phonetrack.model;

import java.io.Serializable;
import java.util.Calendar;

import it.eneiluj.nextcloud.phonetrack.util.NoteUtil;

/**
 * DBSession represents a single session from the local SQLite database with all attributes.
 * It extends CloudSession with attributes required for local data management.
 */
public class DBSession extends CloudSession implements Item, Serializable {

    private long id;
    private String name = "";
    private String token = "";
    private DBStatus status;

    public DBSession(long id, String name, String token, DBStatus status) {
        super(name, token);
        this.id = id;
        this.status = status;
    }

    public long getId() {
        return id;
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
        return "#" + this.id + "/" + super.toString() + " " + getStatus();
    }
}
