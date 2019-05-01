package net.eneiluj.nextcloud.phonetrack.model;

import androidx.annotation.Nullable;

public class ColoredLocation extends BasicLocation {

    private String color;

    public ColoredLocation(double lat, double lon, long timestamp,
                           @Nullable Double bearing, @Nullable Double altitude, @Nullable Double speed,
                           @Nullable Double accuracy, @Nullable Long satellites, @Nullable Double battery,
                           @Nullable String userAgent, @Nullable String color) {

        super(lat, lon, timestamp, bearing, altitude, speed, accuracy, satellites, battery, userAgent);
        this.color = color;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}