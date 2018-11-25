package it.eneiluj.nextcloud.phonetrack.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import it.eneiluj.nextcloud.phonetrack.util.NoteUtil;

/**
 * CloudSession represents a remote note from an OwnCloud server.
 * It can be directly generated from the JSON answer from the server.
 */
public class CloudSession implements Serializable {
    private String name = "";
    private String token = "";

    public CloudSession(String name, String token) {
        this.name = name;
        this.token = token;
    }

    @Override
    public String toString() {
        return "Session(" + this.name + ", " + this.token + ")";
    }
}