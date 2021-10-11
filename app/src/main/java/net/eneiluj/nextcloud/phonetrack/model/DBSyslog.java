package net.eneiluj.nextcloud.phonetrack.model;

public class DBSyslog {
    private String message;
    private String tag;
    private String level;
    private long timestamp;

    public DBSyslog(String message, String tag, String level, long timestamp) {
        this.message = message;
        this.tag = tag;
        this.level = level;
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
