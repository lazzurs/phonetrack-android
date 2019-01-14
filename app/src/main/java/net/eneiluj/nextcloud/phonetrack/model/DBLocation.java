package net.eneiluj.nextcloud.phonetrack.model;

/**
 * DBLocation represents a location from the local SQLite database with all attributes.
 * key_id, key_logjobid, key_lat, key_lon, 4 key_time, 5 key_bearing,
 * 6 key_altitude, 7 key_speed, 8 key_accuracy, 9 key_satellites, 10 key_battery
 */
public class DBLocation {

    private long id;
    private long logjobId;
    private double lat;
    private double lon;
    private long timestamp;
    private double bearing;
    private double altitude;
    private double speed;
    private double accuracy;
    private double satellites;
    private double battery;

    public DBLocation(long id, long logjobId, double lat, double lon, long timestamp, double bearing,
                      double altitude, double speed, double accuracy, long satellites, double battery) {
        this.id = id;
        this.logjobId = logjobId;
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
        this.bearing = bearing;
        this.altitude = altitude;
        this.speed = speed;
        this.accuracy = accuracy;
        this.satellites = satellites;
        this.battery = battery;
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

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getBearing() {
        return bearing;
    }

    public void setBearing(double bearing) {
        this.bearing = bearing;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public double getSatellites() {
        return satellites;
    }

    public void setSatellites(double satellites) {
        this.satellites = satellites;
    }

    public double getBattery() {
        return battery;
    }

    public void setBattery(double battery) {
        this.battery = battery;
    }

    @Override
    public String toString() {
        return "#DBLocation" + getId() + "/" + this.logjobId + ", " + this.lat + ", " +
                this.lon + ", " + this.timestamp + ", acc " + this.accuracy + ", speed : "+ this.speed +
                ", sat : "+ this.satellites + ", bea : " + this.bearing + ", alt : " +this.altitude +
                ", bat : " + this.battery;
    }
}
