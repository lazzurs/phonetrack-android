package net.eneiluj.nextcloud.phonetrack.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.util.HttpStatusException;

import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

/** Turns the reason an upload failed into a short, translated sentence for the user. */
public final class UploadErrors {

    /**
     * Uploads of a log job that must fail in a row before the logging notification says so:
     * the first failure is often a transient one that the next retry fixes.
     */
    public static final int NOTIFY_AFTER_FAILURES = 3;

    private UploadErrors() {
    }

    /** Never null, never a class name. */
    @NonNull
    public static String describe(Context context, @Nullable Throwable error) {
        // the cause chain is short; the limit only guards against a loop in it
        Throwable e = error;
        for (int depth = 0; e != null && depth < 8; depth++, e = e.getCause()) {
            String known = describeOne(context, e);
            if (known != null) {
                return known;
            }
        }
        String message = error == null ? null : error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return context.getString(R.string.upload_error_unknown);
        }
        if (message.equals(context.getString(R.string.e_server_response))) {
            return context.getString(R.string.upload_error_server_response);
        }
        return message;
    }

    @Nullable
    private static String describeOne(Context context, Throwable e) {
        if (e instanceof HttpStatusException) {
            int code = ((HttpStatusException) e).getStatusCode();
            if (code == 401 || code == 403) {
                return context.getString(R.string.upload_error_login_refused, code);
            }
            if (code == 404) {
                return context.getString(R.string.upload_error_not_found, code);
            }
            if (code >= 500) {
                return context.getString(R.string.upload_error_server, code);
            }
            return context.getString(R.string.upload_error_http, code);
        }
        if (e instanceof SSLHandshakeException || e instanceof SSLPeerUnverifiedException
                || e instanceof CertificateException) {
            return context.getString(R.string.upload_error_certificate);
        }
        if (e instanceof SocketTimeoutException) {
            return context.getString(R.string.upload_error_timeout);
        }
        if (e instanceof UnknownHostException) {
            String host = e.getMessage();
            return host == null || host.trim().isEmpty()
                    ? context.getString(R.string.upload_error_unknown_host_no_name)
                    : context.getString(R.string.upload_error_unknown_host, host);
        }
        if (e instanceof MalformedURLException || e instanceof URISyntaxException) {
            return context.getString(R.string.upload_error_bad_url);
        }
        if (e instanceof ConnectException || e instanceof NoRouteToHostException) {
            return context.getString(R.string.upload_error_connect);
        }
        return null;
    }
}
