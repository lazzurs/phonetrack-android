package net.eneiluj.nextcloud.phonetrack.model;

import java.io.Serializable;

/**
 * DBLogjob represents a single logjob from the local SQLite database with all attributes.
 */
public class DBLogjob implements Item, Serializable {

    private long id;
    private String title = "";
    private String nextURL;
    private String token;
    private String deviceName;
    private int minTime;
    private int minDistance;
    private int minAccuracy;
    private Boolean enabled;
    private DBStatus status;
    private int nbSync;

    public DBLogjob(long id, String title, String nextURL, String token, String deviceName, int minTime, int minDistance, int minAccuracy, Boolean enabled, int nbSync) {
        this.id = id;
        this.title = title;
        this.nextURL = nextURL;
        this.token = token;
        this.deviceName = deviceName;
        this.minAccuracy = minAccuracy;
        this.minDistance = minDistance;
        this.minTime = minTime;
        this.enabled = enabled;
        this.nbSync = nbSync;
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

    public void setNbSync(int nbSync) {
        this.nbSync = nbSync;
    }

    public void setNextURL(String nextURL) {
        this.nextURL = nextURL;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public void setAttrFromUrl(String url) {
        String[] spl = url.split("/app/phonetrack/");
        System.out.println(spl.length);
        if (spl.length == 2) {
            String nextURL = spl[0];
            if (nextURL.contains("index.php")) {
                nextURL = nextURL.replace("index.php", "");
            }

            String right = spl[1];
            String[] spl2 = right.split("/");
            if (spl2.length > 2) {
                String token = spl2[1];
                String[] spl3 = spl2[2].split("\\?");
                if (spl3.length > 1) {
                    String devname = spl3[0];
                    this.title = "From logging URL";
                    this.deviceName = devname;
                    this.token = token;
                    this.nextURL = nextURL;
                }
            }
        }
    }

    public String getToken() {
        return token;
    }

    public int getNbSync() {
        return nbSync;
    }

    public String getNextURL() {
        return nextURL;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public int getMinTime() {
        return minTime;
    }
    public int getMinDistance() {
        return minDistance;
    }
    public int getMinAccuracy() {
        return minAccuracy;
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
        return "#DBLogjob" + getId() + "/" + this.title + ", " + this.enabled + ", " +
                this.nextURL + ", " + this.token + ", " +
                this.deviceName;
    }
}
