package net.eneiluj.nextcloud.phonetrack.model;

import androidx.annotation.Nullable;

public class DBColoredLocation extends DBLocation {

    private String color;

    public DBColoredLocation(long id, long logjobId, double lat, double lon, long timestamp,
                             @Nullable Double bearing, @Nullable Double altitude, @Nullable Double speed,
                             @Nullable Double accuracy, @Nullable Long satellites, @Nullable Double battery,
                             @Nullable String userAgent, @Nullable String color) {

        super(id,logjobId, lat, lon, timestamp, bearing, altitude, speed, accuracy, satellites, battery, userAgent);
        this.color = color;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}