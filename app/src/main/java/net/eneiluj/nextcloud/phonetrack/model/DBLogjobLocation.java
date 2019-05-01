package net.eneiluj.nextcloud.phonetrack.model;

import androidx.annotation.Nullable;

public class DBLogjobLocation extends BasicLocation {

    private long id;
    private long logjobId;
    private boolean synced;
    private boolean currentRun;

    public DBLogjobLocation(long id, long logjobId, double lat, double lon, long timestamp,
                            @Nullable Double bearing, @Nullable Double altitude, @Nullable Double speed,
                            @Nullable Double accuracy, @Nullable Long satellites, @Nullable Double battery,
                            @Nullable String userAgent, boolean synced, boolean currentRun) {

        super(lat, lon, timestamp, bearing, altitude, speed, accuracy, satellites, battery, userAgent);
        this.id = id;
        this.logjobId = logjobId;
        this.synced = synced;
        this.currentRun = currentRun;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getLogjobId() {
        return logjobId;
    }

    public void setLogjobId(long logjobId) {
        this.logjobId = logjobId;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    public boolean isCurrentRun() {
        return currentRun;
    }

    public void setCurrentRun(boolean currentRun) {
        this.currentRun = currentRun;
    }

    @Override
    public String toString() {
        return "#DBLogjobLocation " + getId() + "/" + this.logjobId + " " + super.toString();
    }
}