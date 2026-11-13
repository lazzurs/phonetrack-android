package net.eneiluj.nextcloud.phonetrack.persistence;

import androidx.annotation.Nullable;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Decides whether an upload may follow an HTTP redirect.
 *
 * The upload code follows redirects by hand (it must re-send the body), so it has to
 * enforce what {@link HttpURLConnection} would otherwise enforce itself: never leave
 * https for http, and never send the body or the Authorization header to another
 * host or port.
 */
public final class RedirectPolicy {
    /** Redirects followed for one upload before giving up. */
    public static final int MAX_REDIRECTS = 5;

    private RedirectPolicy() {
    }

    public static boolean isRedirect(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                || responseCode == 307
                || responseCode == 308;
    }

    /**
     * Resolve the Location header against the current URL.
     *
     * @return the URL to retry, or null if there is no usable Location or the
     * redirect is not allowed
     */
    @Nullable
    public static URL resolve(URL current, @Nullable String location) {
        if (location == null) {
            return null;
        }
        URL target;
        try {
            target = new URL(current, location);
        } catch (MalformedURLException e) {
            return null;
        }
        return isAllowed(current, target) ? target : null;
    }

    public static boolean isAllowed(URL from, URL to) {
        String fromProtocol = from.getProtocol();
        String toProtocol = to.getProtocol();
        boolean fromHttps = "https".equalsIgnoreCase(fromProtocol);
        boolean toHttps = "https".equalsIgnoreCase(toProtocol);
        if (!toHttps && !"http".equalsIgnoreCase(toProtocol)) {
            return false;
        }
        // never downgrade
        if (fromHttps && !toHttps) {
            return false;
        }
        String fromHost = from.getHost();
        if (fromHost == null || fromHost.isEmpty() || !fromHost.equalsIgnoreCase(to.getHost())) {
            return false;
        }
        if (fromProtocol.equalsIgnoreCase(toProtocol)) {
            return effectivePort(from) == effectivePort(to);
        }
        // http -> https upgrade on the same host: the port necessarily changes
        return true;
    }

    private static int effectivePort(URL url) {
        return url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
    }
}
