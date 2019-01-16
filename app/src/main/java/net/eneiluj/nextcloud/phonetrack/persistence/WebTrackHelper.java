package net.eneiluj.nextcloud.phonetrack.persistence;

import android.content.Context;
import android.content.SharedPreferences;
//import android.preference.PreferenceManager;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import net.eneiluj.nextcloud.phonetrack.BuildConfig;
import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.service.WebTrackService;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;

import at.bitfire.cert4android.CustomCertManager;

/**
 * Web server communication
 *
 */

public class WebTrackHelper {
    private static final String TAG = WebTrackService.class.getSimpleName();

    private static final String CLIENT_SCRIPT = "client/index.php";
    private static final String PARAM_ACTION = "action";

    // addpos
    public static final String PARAM_TIME = "timestamp";
    public static final String PARAM_LAT = "lat";
    public static final String PARAM_LON = "lon";
    public static final String PARAM_ALT = "alt";
    public static final String PARAM_SPEED = "speed";
    public static final String PARAM_BEARING = "bearing";
    public static final String PARAM_ACCURACY = "acc";
    public static final String PARAM_BATTERY = "bat";
    public static final String PARAM_SATELLITES = "sat";
    public static final String PARAM_USERAGENT = "useragent";
    private static final String application_json = "application/json";

    private final String webUserAgent;
    private final Context context;

    private static boolean tlsSocketInitialized = false;
    // Socket timeout in milliseconds
    static final int SOCKET_TIMEOUT = 30 * 1000;

    private CustomCertManager certManager;


    /**
     * Constructor
     * @param ctx Context
     */
    public WebTrackHelper(Context ctx, CustomCertManager certManager) {
        context = ctx;
        this.certManager = certManager;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        webUserAgent = context.getString(R.string.app_name) + "/" + BuildConfig.VERSION_NAME + "; " + System.getProperty("http.agent");
    }

    @SuppressWarnings("StringConcatenationInLoop")
    private String postMultiple(URL url, JSONObject params) throws IOException {

        if (LoggerService.DEBUG) { Log.d(TAG, "[postMultiple: " + url + " : " + params + "]"); }
        String response;

        HttpURLConnection connection = null;
        InputStream in = null;
        //OutputStream out = null;
        try {
            boolean redirect;
            int redirectTries = 5;
            do {
                redirect = false;
                //connection = (HttpURLConnection) url.openConnection();
                connection = SupportUtil.getHttpURLConnection(certManager, url.toString());
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("User-Agent", webUserAgent);
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(SOCKET_TIMEOUT);
                connection.setReadTimeout(SOCKET_TIMEOUT);
                connection.setUseCaches(true);

                byte[] paramData = null;
                if (params != null) {
                    paramData = params.toString().getBytes();
                    Log.d(getClass().getSimpleName(), "Params: " + params);
                    connection.setFixedLengthStreamingMode(paramData.length);
                    connection.setRequestProperty("Content-Type", application_json);
                    connection.setDoOutput(true);
                    OutputStream os = connection.getOutputStream();
                    os.write(paramData);
                    os.flush();
                    os.close();
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307) {
                    URL base = connection.getURL();
                    String location = connection.getHeaderField("Location");
                    if (LoggerService.DEBUG) { Log.d(TAG, "[postMultiple redirect: " + location + "]"); }
                    if (location == null || redirectTries == 0) {
                        throw new IOException(context.getString(R.string.e_illegal_redirect, responseCode));
                    }
                    redirect = true;
                    redirectTries--;
                    url = new URL(base, location);
                    String h1 = base.getHost();
                    String h2 = url.getHost();
                    if (h1 != null && !h1.equalsIgnoreCase(h2)) {
                        throw new IOException(context.getString(R.string.e_illegal_redirect, responseCode));
                    }
                    try {
                        //out.close();
                        connection.getInputStream().close();
                        connection.disconnect();
                    } catch (final IOException e) {
                        if (LoggerService.DEBUG) { Log.d(TAG, "[connection cleanup failed (ignored)]"); }
                    }
                }
                else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new IOException(context.getString(R.string.e_auth_failure, responseCode));
                }
                else if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException(context.getString(R.string.e_http_code, responseCode));
                }
            } while (redirect);

            in = new BufferedInputStream(connection.getInputStream());

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                sb.append(inputLine);
            }
            response = sb.toString();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (final IOException e) {
                if (LoggerService.DEBUG) { Log.d(TAG, "[connection cleanup failed (ignored)]"); }
            }
        }
        if (LoggerService.DEBUG) { Log.d(TAG, "[postMultiple response: " + response + "]"); }
        return response;
    }

    /**
     * Send post request
     * @param params Request parameters
     * @return Server response
     * @throws IOException Connection error
     */
    @SuppressWarnings("StringConcatenationInLoop")
    private String postWithParams(URL url, Map<String, String> params) throws IOException {

        if (LoggerService.DEBUG) { Log.d(TAG, "[postWithParams: " + url + " : " + params + "]"); }
        String response;

        String dataString = "";
        for (Map.Entry<String, String> p : params.entrySet()) {
            String key = p.getKey();
            String value = p.getValue();
            if (dataString.length() > 0) {
                dataString += "&";
            }
            dataString += URLEncoder.encode(key, "UTF-8") + "=";
            dataString += URLEncoder.encode(value, "UTF-8");
        }
        byte[] data = dataString.getBytes();

        HttpURLConnection connection = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            boolean redirect;
            int redirectTries = 5;
            do {
                redirect = false;
                //connection = (HttpURLConnection) url.openConnection();
                connection = SupportUtil.getHttpURLConnection(certManager, url.toString());
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("Content-Length", Integer.toString(data.length));
                connection.setRequestProperty("User-Agent", webUserAgent);
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(SOCKET_TIMEOUT);
                connection.setReadTimeout(SOCKET_TIMEOUT);
                connection.setUseCaches(true);

                out = new BufferedOutputStream(connection.getOutputStream());
                out.write(data);
                out.flush();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                        || responseCode == 307) {
                    URL base = connection.getURL();
                    String location = connection.getHeaderField("Location");
                    if (LoggerService.DEBUG) { Log.d(TAG, "[postWithParams redirect: " + location + "]"); }
                    if (location == null || redirectTries == 0) {
                        throw new IOException(context.getString(R.string.e_illegal_redirect, responseCode));
                    }
                    redirect = true;
                    redirectTries--;
                    url = new URL(base, location);
                    String h1 = base.getHost();
                    String h2 = url.getHost();
                    if (h1 != null && !h1.equalsIgnoreCase(h2)) {
                        throw new IOException(context.getString(R.string.e_illegal_redirect, responseCode));
                    }
                    try {
                        out.close();
                        connection.getInputStream().close();
                        connection.disconnect();
                    } catch (final IOException e) {
                        if (LoggerService.DEBUG) { Log.d(TAG, "[connection cleanup failed (ignored)]"); }
                    }
                }
                else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new IOException(context.getString(R.string.e_auth_failure, responseCode));
                }
                else if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException(context.getString(R.string.e_http_code, responseCode));
                }
            } while (redirect);

            in = new BufferedInputStream(connection.getInputStream());

            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String inputLine;
            while ((inputLine = br.readLine()) != null) {
                sb.append(inputLine);
            }
            response = sb.toString();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (final IOException e) {
                if (LoggerService.DEBUG) { Log.d(TAG, "[connection cleanup failed (ignored)]"); }
            }
        }
        if (LoggerService.DEBUG) { Log.d(TAG, "[postWithParams response: " + response + "]"); }
        return response;
    }

    /**
     * Upload position to server
     * @param params Map of parameters (position properties)
     * @throws IOException Connection error
     */
    public void postPositionToPhoneTrack(URL url, Map<String, String> params) throws IOException {
        if (LoggerService.DEBUG) { Log.d(TAG, "[postPositionToPhoneTrack]"); }
        String response = postWithParams(url, params);
        int done = 0;
        try {
            JSONObject json = new JSONObject(response);
            done = json.getInt("done");
        } catch (JSONException e) {
            if (LoggerService.DEBUG) { Log.d(TAG, "[postPositionToPhoneTrack json failed: " + e + "]"); }
        }
        if (done != 1) {
            throw new IOException(context.getString(R.string.e_server_response));
        }
    }

    /**
     * post multiple positions in one request, build the JSON parameters
     * @param url
     * @param params
     * @throws IOException
     */
    public void postMultiplePositionsToPhoneTrack(URL url, JSONObject params) throws IOException {
        if (LoggerService.DEBUG) { Log.d(TAG, "[postMultiplePositionsToPhoneTrack]"); }
        String response = postMultiple(url, params);
        int done = 0;
        try {
            JSONObject json = new JSONObject(response);
            done = json.getInt("done");
        } catch (JSONException e) {
            if (LoggerService.DEBUG) { Log.d(TAG, "[postMultiplePositionsToPhoneTrack json failed: " + e + "]"); }
        }
        if (done != 1) {
            throw new IOException(context.getString(R.string.e_server_response));
        }
    }

    /**
     * Upload position to server
     * @param params Map of parameters (position properties)
     * @throws IOException Connection error
     */
    public void sendGETPositionToCustom(String urlStr, Map<String, String> params) throws IOException {
        String urlWithValues = urlStr.replace("%LAT", params.get(PARAM_LAT))
                .replace("%LON", params.get(PARAM_LON))
                .replace("%TIMESTAMP", params.get(PARAM_TIME))
                .replace("%ALT", params.get(PARAM_ALT))
                .replace("%ACC", params.get(PARAM_ACCURACY))
                .replace("%SPD", params.get(PARAM_SPEED))
                .replace("%DIR", params.get(PARAM_BEARING))
                .replace("%SAT", params.get(PARAM_SATELLITES))
                .replace("%BATT", params.get(PARAM_BATTERY))
                .replace("%UA", params.get(PARAM_USERAGENT));

        URL url = new URL(urlWithValues);
        // TODO do the GET request
        StringBuilder result = new StringBuilder();
        //HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        HttpURLConnection conn = SupportUtil.getHttpURLConnection(certManager, url.toString());
        if (LoggerService.DEBUG) { Log.d(TAG, "[getWithParams: " + url+"]"); }
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(SOCKET_TIMEOUT);
        conn.setReadTimeout(SOCKET_TIMEOUT);
        conn.setRequestMethod("GET");
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();
        if (LoggerService.DEBUG) { Log.d(TAG, "[GET request response: " + result + "]"); }
    }

    public void sendPOSTPositionToCustom(String urlStr, Map<String, String> params) throws IOException {
        if (LoggerService.DEBUG) { Log.d(TAG, "[SENDPOS  "+params+"]"); }
        String urlWithValues = urlStr.replace("%LAT", params.get(PARAM_LAT))
                .replace("%LON", params.get(PARAM_LON))
                .replace("%TIMESTAMP", params.get(PARAM_TIME))
                .replace("%ALT", params.get(PARAM_ALT))
                .replace("%ACC", params.get(PARAM_ACCURACY))
                .replace("%SPD", params.get(PARAM_SPEED))
                .replace("%DIR", params.get(PARAM_BEARING))
                .replace("%SAT", params.get(PARAM_SATELLITES))
                .replace("%BATT", params.get(PARAM_BATTERY))
                .replace("%UA", params.get(PARAM_USERAGENT));

        String[] urlSplit;
        String[] paramSplit;
        String baseUrl;
        Map<String, String> paramsToSend = new HashMap<>();
        if (urlWithValues.contains("?")) {
            urlSplit = urlWithValues.split("\\?");
            if (urlSplit.length == 2) {
                baseUrl = urlSplit[0];
                paramSplit = urlSplit[1].split("\\&");
                for (String aParamSplit : paramSplit) {
                    if (aParamSplit.contains("=")) {
                        String[] oneParamSplit = aParamSplit.split("=");
                        if (oneParamSplit.length == 2) {
                            paramsToSend.put(oneParamSplit[0], oneParamSplit[1]);
                        }
                    }
                }
                postWithParams(new URL(baseUrl), paramsToSend);
            }
            else {
                if (LoggerService.DEBUG) { Log.d(TAG, "[POST URL ERROR "+urlSplit+"]"); }
                throw new IOException(context.getString(R.string.malformed_post_url));
            }
        }
        else {
            if (LoggerService.DEBUG) { Log.d(TAG, "[POST URL ERROR]"); }
            throw new IOException(context.getString(R.string.malformed_post_url));
        }


    }

    public URL getUrlFromPhoneTrackLogjob(DBLogjob lj) throws MalformedURLException {
        return new URL(
                lj.getUrl().replaceAll("/+$", "") +
                        "/index.php/apps/phonetrack/logPost/" + lj.getToken() + "/" + lj.getDeviceName()
        );
    }

    public URL getUrlMultipleFromPhoneTrackLogjob(DBLogjob lj) throws MalformedURLException {
        return new URL(
                lj.getUrl().replaceAll("/+$", "") +
                        "/index.php/apps/phonetrack/logPostMultiple/" + lj.getToken() + "/" + lj.getDeviceName()
        );
    }
}
