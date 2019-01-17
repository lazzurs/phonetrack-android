package net.eneiluj.nextcloud.phonetrack.model;

/**
 * DBSession represents a single session from the local SQLite database with all attributes.
 */
public class DBSession {

    private long id;
    private String name;
    private String token;
    private String nextURL;
    private String publicToken;
    private boolean isFromShare;
    private boolean isPublic;

    public DBSession(long id, String token, String name, String nextURL, String publicToken,
                     boolean isFromShare, boolean isPublic) {
        this.id = id;
        this.token = token;
        this.name = name;
        this.nextURL = nextURL;
        this.publicToken = publicToken;
        this.isFromShare = isFromShare;
        this.isPublic = isPublic;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public boolean isFromShare() {
        return isFromShare;
    }

    public void setFromShare(boolean fromShare) {
        isFromShare = fromShare;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNextURL() {
        return nextURL;
    }

    public void setNextURL(String nextURL) {
        this.nextURL = nextURL;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public void setPublicToken(String publicToken) {
        this.publicToken = publicToken;
    }

    public long getId() {
        return id;
    }

    public String toString() {
        return "#" + this.id + "/" + this.name + ", " + this.token + ", " + this.nextURL +
                ", " + this.publicToken + ", "+ this.isFromShare;
    }
}
