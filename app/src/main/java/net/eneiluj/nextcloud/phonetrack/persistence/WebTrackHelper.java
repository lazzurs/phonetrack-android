package net.eneiluj.nextcloud.phonetrack.persistence;

import android.content.Context;
import androidx.annotation.Nullable;

import android.os.Build;
import android.util.Base64;
import android.util.Log;

import com.nextcloud.android.sso.exceptions.TokenMismatchException;

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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.service.WebTrackService;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrackClient;
import net.eneiluj.nextcloud.phonetrack.util.ServerResponse;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;

import at.bitfire.cert4android.CustomCertManager;

/**
 * Web server communication
 *
 */

public class WebTrackHelper {
    private static final String TAG = WebTrackService.class.getSimpleName();

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

    // Socket timeout in milliseconds
    static final int SOCKET_TIMEOUT = 30 * 1000;

    private final CustomCertManager certManager;


    /**
     * Constructor
     * @param ctx Context
     */
    public WebTrackHelper(Context ctx, CustomCertManager certManager) {
        context = ctx;
        this.certManager = certManager;

        webUserAgent = context.getString(R.string.app_name) + "/" + SupportUtil.getAppVersionName(context) + "; " + System.getProperty("http.agent");
    }

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

                if (params != null) {
                    byte[] paramData = params.toString().getBytes();
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
    private String postWithParams(URL url, Map<String, String> params, @Nullable String login, @Nullable String password) throws IOException {

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
                // basic auth if login/password given
                if (login != null && password != null) {
                    connection.setRequestProperty(
                            "Authorization",
                            "Basic " + Base64.encodeToString((login + ":" + password).getBytes(), Base64.NO_WRAP));
                }

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

    private String postJSON(URL url, JSONObject jsonParams, @Nullable String login, @Nullable String password) throws IOException {

        if (LoggerService.DEBUG) { Log.d(TAG, "[postJSON: " + url + " : " + jsonParams + "]"); }
        String response;

        byte[] jsonBytes = jsonParams.toString().getBytes(StandardCharsets.UTF_8);

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
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                //connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Length", Integer.toString(jsonBytes.length));
                connection.setRequestProperty("User-Agent", webUserAgent);
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(SOCKET_TIMEOUT);
                connection.setReadTimeout(SOCKET_TIMEOUT);
                connection.setUseCaches(true);
                // basic auth if login/password given
                if (login != null && password != null) {
                    connection.setRequestProperty(
                            "Authorization",
                            "Basic " + Base64.encodeToString((login + ":" + password).getBytes(), Base64.NO_WRAP));
                }

                out = new BufferedOutputStream(connection.getOutputStream());
                out.write(jsonBytes);
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

    public void postPositionToMaps(PhoneTrackClient client, Map<String, String> params) throws IOException {
        if (LoggerService.DEBUG) { Log.d(TAG, "[postPositionToMaps]"); }
        //String response = postWithParams(url, params);
        int deviceId = 0;
        try {
            ServerResponse.MapsAddPointResponse response = client.mapsAddPoint(certManager, params);
            deviceId = response.getDeviceId();
        } catch (JSONException e) {
            if (LoggerService.DEBUG) { Log.d(TAG, "[postPositionToMaps json failed, JSONException: " + e + "]"); }
        } catch (TokenMismatchException e) {
            if (LoggerService.DEBUG) { Log.d(TAG, "[postPositionToMaps json failed, TokenMismatchException: " + e + "]"); }
        } catch (Exception e) {
            if (LoggerService.DEBUG) { Log.d(TAG, "[postPositionToMaps json failed, Exception: " + e + "]"); }
        }
        if (deviceId == 0) {
            throw new IOException(context.getString(R.string.e_server_response));
        }
    }

    /**
     * Upload position to server
     * @param params Map of parameters (position properties)
     * @throws IOException Connection error
     */
    public void postPositionToPhoneTrack(URL url, Map<String, String> params) throws IOException {
        if (LoggerService.DEBUG) { Log.d(TAG, "[postPositionToPhoneTrack]"); }
        String response = postWithParams(url, params, null, null);
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
    public void sendGETPositionToCustom(String urlStr, Map<String, String> params, @Nullable String login, @Nullable String password) throws IOException {
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
        // use basic auth if login/password are set
        if (login != null && password != null) {
            conn.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString((login + ":" + password).getBytes(), Base64.NO_WRAP));
        }
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();
        if (LoggerService.DEBUG) { Log.d(TAG, "[GET request response: " + result + "]"); }
    }

    public void sendPOSTPositionToCustom(String urlStr, Map<String, String> params,
                                         @Nullable String login, @Nullable String password,
                                         boolean sendJsonPayload
    ) throws IOException, JSONException {
        if (LoggerService.DEBUG) { Log.d(TAG, "[SENDPOS  "+params+"]"); }
        if (sendJsonPayload) {
            // build JSON object
            JSONObject jsonParams = new JSONObject();
            jsonParams.put("_type", "location");
            jsonParams.put("acc", params.get(PARAM_ACCURACY));
            jsonParams.put("alt", params.get(PARAM_ALT));
            jsonParams.put("batt", params.get(PARAM_BATTERY));
            jsonParams.put("lat", params.get(PARAM_LAT));
            jsonParams.put("lon", params.get(PARAM_LON));
            jsonParams.put("tst", Integer.valueOf(params.get(PARAM_TIME)));
            String paramSpeed = params.get(PARAM_SPEED);
            if (paramSpeed != null && !paramSpeed.equals("") && !paramSpeed.equals("0")) {
                double speed = Double.parseDouble(paramSpeed);
                double kphD = speed * 3.6;
                int kph = (int) kphD;
                jsonParams.put("vel", kph);
            }
            String tid = Build.MODEL
                    .replaceAll(" ", "")
                    .replaceAll("/", "");
            tid += " (PhoneTrack/Android)";
            jsonParams.put("tid", tid);
            // send it
            postJSON(new URL(urlStr), jsonParams, login, password);
        }
        else {
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
                    postWithParams(new URL(baseUrl), paramsToSend, login, password);
                } else {
                    if (LoggerService.DEBUG) {
                        Log.d(TAG, "[POST URL ERROR " + urlWithValues + "]");
                    }
                    throw new IOException(context.getString(R.string.malformed_post_url));
                }
            } else {
                if (LoggerService.DEBUG) {
                    Log.d(TAG, "[POST URL ERROR]");
                }
                throw new IOException(context.getString(R.string.malformed_post_url));
            }
        }

    }

    public URL getUrlFromPhoneTrackLogjob(DBLogjob lj) throws MalformedURLException {
        String cleanDeviceName = lj.getDeviceName().replaceAll("/", "-");
        String encodedDeviceName = cleanDeviceName;
        try {
            encodedDeviceName = URLEncoder.encode(cleanDeviceName, "UTF-8").replaceAll("\\+", "%20");
        } catch (Exception e) {
            Log.e(TAG, "[Encode error] Unknown exception: " + e);
        }
        return new URL(
                lj.getUrl().replaceAll("/+$", "") +
                        "/index.php/apps/phonetrack/logPost/" + lj.getToken() + "/" + encodedDeviceName
        );
    }

    public URL getUrlMultipleFromPhoneTrackLogjob(DBLogjob lj) throws MalformedURLException {
        return new URL(
                lj.getUrl().replaceAll("/+$", "") +
                        "/index.php/apps/phonetrack/logPostMultiple/" + lj.getToken() + "/" + lj.getDeviceName().replaceAll("/", "-")
        );
    }
}
