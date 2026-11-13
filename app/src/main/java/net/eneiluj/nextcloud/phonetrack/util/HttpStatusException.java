package net.eneiluj.nextcloud.phonetrack.util;

import java.io.IOException;

/** The server answered an upload with an HTTP error status. */
public class HttpStatusException extends IOException {

    private final int statusCode;

    public HttpStatusException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
