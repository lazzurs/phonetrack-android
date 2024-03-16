package net.eneiluj.nextcloud.phonetrack.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.nextcloud.android.sso.QueryParam;
import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.api.Response;
import com.nextcloud.android.sso.exceptions.TokenMismatchException;

import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.persistence.WebTrackHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.bitfire.cert4android.CustomCertManager;

@WorkerThread
public class PhoneTrackClient {
    private static final String TAG = PhoneTrackClient.class.getSimpleName();

    /**
     * This entity class is used to return relevant data of the HTTP reponse.
     */
    public static class ResponseData {
        private final String content;
        private final String etag;
        private final long lastModified;

        public ResponseData(String content, String etag, long lastModified) {
            this.content = content;
            this.etag = etag;
            this.lastModified = lastModified;
        }

        public String getContent() {
            return content;
        }

        public String getETag() {
            return etag;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    public static final String JSON_ID = "id";
    public static final String JSON_TITLE = "title";
    public static final String JSON_ETAG = "etag";
    private static final String application_json = "application/json";
    private String url;
    private String username;
    private String password;
    private NextcloudAPI nextcloudAPI;
    private Context context;

    public PhoneTrackClient(String url, String username, String password, @Nullable NextcloudAPI nextcloudAPI, Context context) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.nextcloudAPI = nextcloudAPI;
        this.context = context;
    }

    public ServerResponse.MapsAddPointResponse mapsAddPoint(CustomCertManager ccm, Map<String, String> params) throws JSONException, IOException, TokenMismatchException {
        String target = "/index.php/apps/maps/api/1.0/devices";
        String userAgent = Build.MODEL
                .replaceAll(" ", "")
                .replaceAll("/", "");
        userAgent += " (PhoneTrack/Android)";
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to add point to Maps");
            List<QueryParam> mapsParams = new ArrayList<>();
            mapsParams.add(new QueryParam("lat", params.get(WebTrackHelper.PARAM_LAT)));
            mapsParams.add(new QueryParam("lng", params.get(WebTrackHelper.PARAM_LON)));
            mapsParams.add(new QueryParam("timestamp", params.get(WebTrackHelper.PARAM_TIME)));
            mapsParams.add(new QueryParam("user_agent", userAgent));
            mapsParams.add(new QueryParam("altitude", params.get(WebTrackHelper.PARAM_ALT)));
            mapsParams.add(new QueryParam("battery", params.get(WebTrackHelper.PARAM_BATTERY)));
            mapsParams.add(new QueryParam("accuracy", params.get(WebTrackHelper.PARAM_ACCURACY)));
            return new ServerResponse.MapsAddPointResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_POST, mapsParams));
        } else {
            Map<String, String> mapsParams = new HashMap<>();
            mapsParams.put("lat", params.get(WebTrackHelper.PARAM_LAT));
            mapsParams.put("lng", params.get(WebTrackHelper.PARAM_LON));
            mapsParams.put("timestamp", params.get(WebTrackHelper.PARAM_TIME));
            mapsParams.put("user_agent", userAgent);
            mapsParams.put("altitude", params.get(WebTrackHelper.PARAM_ALT));
            mapsParams.put("battery", params.get(WebTrackHelper.PARAM_BATTERY));
            mapsParams.put("accuracy", params.get(WebTrackHelper.PARAM_ACCURACY));
            return new ServerResponse.MapsAddPointResponse(requestServer(ccm, target, METHOD_POST, new JSONObject(mapsParams), null, true, false));
        }
    }

    public ServerResponse.SessionsResponse getSessions(CustomCertManager ccm, long lastModified, String lastETag) throws JSONException, IOException, TokenMismatchException {
        String target = "/index.php/apps/phonetrack/" + "api/getsessions";
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to get sessions");
            //return new ServerResponse.SessionsResponse(new ResponseData("[]", lastETag, lastModified));
            return new ServerResponse.SessionsResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null));
        }
        else {
            return new ServerResponse.SessionsResponse(requestServer(ccm, target, METHOD_GET, null, lastETag, true, false));
        }
    }

    public ServerResponse.CapabilitiesResponse getColor(CustomCertManager ccm) throws JSONException, IOException, TokenMismatchException {
        String target = "/ocs/v2.php/cloud/capabilities";
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to get color");
            //return new ServerResponse.SessionsResponse(new ResponseData("[]", lastETag, lastModified));
            return new ServerResponse.CapabilitiesResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null));
        }
        else {
            return new ServerResponse.CapabilitiesResponse(requestServer(ccm, target, METHOD_GET, null, null, true, true));
        }
    }

    public ServerResponse.AvatarResponse getAvatar(CustomCertManager ccm, @Nullable String otherUserName) throws JSONException, IOException, TokenMismatchException {
        String targetUserName = username;
        if (otherUserName != null) {
            targetUserName = otherUserName;
        }
        String target = "/index.php/avatar/" + targetUserName + "/45";
        Log.d(getClass().getSimpleName(), "avatar target "+target);
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to get avatar");
            return new ServerResponse.AvatarResponse(imageRequestServerWithSSO(nextcloudAPI, target, METHOD_GET, null));
        }
        else {
            return new ServerResponse.AvatarResponse(imageRequestServer(ccm, target, METHOD_GET, null, null, true, false));
        }
    }

    public ServerResponse.ShareDeviceResponse shareDevice(CustomCertManager ccm, String token, String deviceName) throws JSONException, IOException, TokenMismatchException {
        String target = "/index.php/apps/phonetrack/" + "api/sharedevice/" + URLEncoder.encode(token, "utf-8") + "/" + URLEncoder.encode(deviceName, "utf-8");
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to get share device");
            return new ServerResponse.ShareDeviceResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null));
        }
        else {
            return new ServerResponse.ShareDeviceResponse(requestServer(ccm, target, METHOD_GET, null, null, true, false));
        }
    }

    public ServerResponse.CreateSessionResponse createSession(CustomCertManager ccm, String sessionName) throws JSONException, IOException, TokenMismatchException {
        String target = "/index.php/apps/phonetrack/" + "api/createsession/" + URLEncoder.encode(sessionName, "utf-8");
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to create session");
            return new ServerResponse.CreateSessionResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null));
        }
        else {
            return new ServerResponse.CreateSessionResponse(requestServer(ccm, target, METHOD_GET, null, null, true, false));
        }
    }

    public ServerResponse.GetSessionLastPositionsResponse getSessionLastPositions(CustomCertManager ccm, DBSession session) throws JSONException, IOException, TokenMismatchException {
        String target = "/index.php/apps/phonetrack/" + "api/getuserlastpositions/" + URLEncoder.encode(session.getToken(), "utf-8");
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to get session last positions");
            return new ServerResponse.GetSessionLastPositionsResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null));
        }
        else {
            return new ServerResponse.GetSessionLastPositionsResponse(requestServer(ccm, target, METHOD_GET, null, null, true, false));
        }
    }

    public ServerResponse.GetSessionPositionsResponse getSessionPositions(CustomCertManager ccm, DBSession session,
                                                                          @Nullable Long limit, @Nullable Long tsmin) throws JSONException, IOException, TokenMismatchException {
        String target = "/index.php/apps/phonetrack/" + "api/getuserpositions/" + URLEncoder.encode(session.getToken(), "utf-8");
        if (limit != null || tsmin != null) {
            target += "?";
            if (limit != null) {
                target += "limit=" + limit;
                if (tsmin != null) {
                    target += "&";
                }
            }
            if (tsmin != null) {
                target += "tsmin=" + tsmin;
            }
        }
        if (nextcloudAPI != null) {
            Log.d(getClass().getSimpleName(), "using SSO to get session last positions");
            return new ServerResponse.GetSessionPositionsResponse(requestServerWithSSO(nextcloudAPI, target, METHOD_GET, null));
        }
        else {
            return new ServerResponse.GetSessionPositionsResponse(requestServer(ccm, target, METHOD_GET, null, null, true, false));
        }
    }

    private ResponseData requestServerWithSSO(NextcloudAPI nextcloudAPI, String target, String method, Collection<QueryParam> params) throws TokenMismatchException{
        StringBuffer result = new StringBuffer();

        NextcloudRequest nextcloudRequest;
        if (params == null) {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target).build();
        }
        else {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target)
                    .setParameter(params)
                    .build();
        }

        try {
            Log.d(getClass().getSimpleName(), "BEGGGGGGGGGGG ");
            Response response = nextcloudAPI.performNetworkRequestV2(nextcloudRequest);
            InputStream inputStream = response.getBody();

            BufferedReader rd = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            Log.d(getClass().getSimpleName(), "RESSSS " + result.toString());
            inputStream.close();
        } catch (TokenMismatchException e) {
            Log.d(getClass().getSimpleName(), "Mismatcho SSO server request error "+e.toString());
            /*try {
                SingleAccountHelper.reauthenticateCurrentAccount(:smile:);
            } catch (NextcloudFilesAppAccountNotFoundException | NoCurrentAccountSelectedException | NextcloudFilesAppNotSupportedException ee) {
                UiExceptionManager.showDialogForException(new SettingsActivity(), ee);
            } catch (NextcloudFilesAppAccountPermissionNotGrantedException ee) {
                // Unable to reauthenticate account just like that..
                // TODO Show login screen here
                LoginDialogFragment loginDialogFragment = new LoginDialogFragment();
                loginDialogFragment.show(new SettingsActivity().getSupportFragmentManager(), "NoticeDialogFragment");
            }*/
            throw e;

        } catch (Exception e) {
            // TODO handle errors
            Log.d(getClass().getSimpleName(), "SSO server request error "+e.toString());
        }

        return new ResponseData(result.toString(), "", 0);
    }
    /**
     * Request-Method for POST, PUT with or without JSON-Object-Parameter
     *
     * @param target Filepath to the wanted function
     * @param method GET, POST, DELETE or PUT
     * @param params JSON Object which shall be transferred to the server.
     * @return Body of answer
     * @throws MalformedURLException
     * @throws IOException
     */
    private ResponseData requestServer(CustomCertManager ccm, String target, String method, JSONObject params, String lastETag, boolean needLogin, boolean isOCSRequest)
            throws IOException {
        StringBuffer result = new StringBuffer();
        // setup connection
        String targetURL = url + target.replaceAll("^/", "");
        HttpURLConnection con = SupportUtil.getHttpURLConnection(ccm, targetURL);
        con.setRequestMethod(method);
        if (needLogin) {
            con.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP));
        }
        // https://github.com/square/retrofit/issues/805#issuecomment-93426183
        con.setRequestProperty( "Connection", "Close");
        con.setRequestProperty("User-Agent", "phonetrack-android/" + SupportUtil.getAppVersionName(context));
        if (lastETag != null && METHOD_GET.equals(method)) {
            con.setRequestProperty("If-None-Match", lastETag);
        }
        if (isOCSRequest) {
            con.setRequestProperty("OCS-APIRequest", "true");
        }
        con.setConnectTimeout(10 * 1000); // 10 seconds
        Log.d(getClass().getSimpleName(), method + " " + targetURL);
        // send request data (optional)
        byte[] paramData = null;
        if (params != null) {
            paramData = params.toString().getBytes();
            Log.d(getClass().getSimpleName(), "Params: " + params);
            con.setFixedLengthStreamingMode(paramData.length);
            con.setRequestProperty("Content-Type", application_json);
            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            os.write(paramData);
            os.flush();
            os.close();
        }
        // read response data
        int responseCode = con.getResponseCode();
        Log.d(getClass().getSimpleName(), "HTTP response code: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            throw new ServerResponse.NotModifiedException();
        }

        Log.i(TAG, "METHOD : "+method);
        BufferedReader rd = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        // create response object
        String etag = con.getHeaderField("ETag");
        long lastModified = con.getHeaderFieldDate("Last-Modified", 0) / 1000;
        Log.i(getClass().getSimpleName(), "Result length:  " + result.length() + (paramData == null ? "" : "; Request length: " + paramData.length));
        Log.d(getClass().getSimpleName(), "ETag: " + etag + "; Last-Modified: " + lastModified + " (" + con.getHeaderField("Last-Modified") + ")");
        // return these header fields since they should only be saved after successful processing the result!
        return new ResponseData(result.toString(), etag, lastModified);
    }

    private ResponseData imageRequestServerWithSSO(NextcloudAPI nextcloudAPI, String target, String method, Collection<QueryParam> params) throws TokenMismatchException{
        StringBuffer result = new StringBuffer();
        String strBase64 = "";

        NextcloudRequest nextcloudRequest;
        if (params == null) {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target).build();
        }
        else {
            nextcloudRequest = new NextcloudRequest.Builder()
                    .setMethod(method)
                    .setUrl(target)
                    .setParameter(params)
                    .build();
        }

        try {
            Response response = nextcloudAPI.performNetworkRequestV2(nextcloudRequest);
            InputStream inputStream = response.getBody();

            Bitmap selectedImage = BitmapFactory.decodeStream(inputStream);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            selectedImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            strBase64 = Base64.encodeToString(byteArray, 0);


            inputStream.close();
        } catch (TokenMismatchException e) {
            Log.d(getClass().getSimpleName(), "Mismatcho SSO server request error "+e.toString());
            throw e;

        } catch (Exception e) {
            // TODO handle errors
            Log.d(getClass().getSimpleName(), "SSO server request error "+e.toString());
        }

        return new ResponseData(strBase64, "", 0);
    }

    private ResponseData imageRequestServer(CustomCertManager ccm, String target,
                                                                       String method, JSONObject params, String lastETag, boolean needLogin, boolean isOCSRequest)
            throws IOException {
        StringBuffer result = new StringBuffer();
        String strBase64 = "";
        // setup connection
        String targetURL = url + target.replaceAll("^/", "");
        HttpURLConnection con = SupportUtil.getHttpURLConnection(ccm, targetURL);
        con.setRequestMethod(method);
        if (needLogin) {
            con.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString((username + ":" + password).getBytes(), Base64.NO_WRAP));
        }
        // https://github.com/square/retrofit/issues/805#issuecomment-93426183
        con.setRequestProperty( "Connection", "Close");
        con.setRequestProperty("User-Agent", "phonetrack-android/" + SupportUtil.getAppVersionName(context));
        if (lastETag != null && METHOD_GET.equals(method)) {
            con.setRequestProperty("If-None-Match", lastETag);
        }
        if (isOCSRequest) {
            con.setRequestProperty("OCS-APIRequest", "true");
        }
        con.setConnectTimeout(10 * 1000); // 10 seconds
        Log.d(getClass().getSimpleName(), method + " " + targetURL);
        // send request data (optional)
        byte[] paramData = null;
        if (params != null) {
            paramData = params.toString().getBytes();
            Log.d(getClass().getSimpleName(), "Params: " + params);
            con.setFixedLengthStreamingMode(paramData.length);
            con.setRequestProperty("Content-Type", application_json);
            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            os.write(paramData);
            os.flush();
            os.close();
        }
        // read response data
        int responseCode = con.getResponseCode();
        Log.d(getClass().getSimpleName(), "HTTP response code: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            throw new ServerResponse.NotModifiedException();
        }

        Log.i(TAG, "METHOD : "+method);

        Bitmap selectedImage = BitmapFactory.decodeStream(con.getInputStream());
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        selectedImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        strBase64 = Base64.encodeToString(byteArray, 0);

        return new ResponseData(strBase64, "", 0);
    }
}
