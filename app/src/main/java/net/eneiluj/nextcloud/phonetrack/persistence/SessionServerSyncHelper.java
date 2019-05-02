package net.eneiluj.nextcloud.phonetrack.persistence;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
//import android.preference.PreferenceManager;
import androidx.preference.PreferenceManager;

import android.os.RemoteException;
import android.util.Log;

import com.google.gson.GsonBuilder;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException;
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException;
import com.nextcloud.android.sso.exceptions.TokenMismatchException;
import com.nextcloud.android.sso.helper.SingleAccountHelper;
import com.nextcloud.android.sso.model.SingleSignOnAccount;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import at.bitfire.cert4android.CustomCertManager;
import at.bitfire.cert4android.CustomCertService;
import at.bitfire.cert4android.ICustomCertService;
import at.bitfire.cert4android.IOnCertificateDecision;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.android.activity.SettingsActivity;
import net.eneiluj.nextcloud.phonetrack.model.ColoredLocation;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;
import net.eneiluj.nextcloud.phonetrack.util.IGetLastPosCallback;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrackClient;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrackClientUtil.LoginStatus;
import net.eneiluj.nextcloud.phonetrack.util.ServerResponse;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;

/**
 * Helps to synchronize the Database to the Server.
 */
public class SessionServerSyncHelper {

    private static final String TAG = SessionServerSyncHelper.class.getSimpleName();

    public static final String BROADCAST_SESSIONS_SYNC_FAILED = "net.eneiluj.nextcloud.phonetrack.broadcast.sessions_sync_failed";
    public static final String BROADCAST_SESSIONS_SYNCED = "net.eneiluj.nextcloud.phonetrack.broadcast.sessions_synced";
    public static final String BROADCAST_SSO_TOKEN_MISMATCH = "net.eneiluj.nextcloud.phonetrack.broadcast.token_mismatch";

    private static SessionServerSyncHelper instance;

    /**
     * Get (or create) instance from SessionServerSyncHelper.
     * This has to be a singleton in order to realize correct registering and unregistering of
     * the BroadcastReceiver, which listens on changes of network connectivity.
     *
     * @param dbHelper PhoneTrackSQLiteOpenHelper
     * @return SessionServerSyncHelper
     */
    public static synchronized SessionServerSyncHelper getInstance(PhoneTrackSQLiteOpenHelper dbHelper) {
        if (instance == null) {
            instance = new SessionServerSyncHelper(dbHelper);
        }
        return instance;
    }

    private final PhoneTrackSQLiteOpenHelper dbHelper;
    private final Context appContext;

    private CustomCertManager customCertManager;
    private ICustomCertService iCustomCertService;

    // Track network connection changes using a BroadcastReceiver
    private boolean networkConnected = false;

    private boolean cert4androidReady = false;
    private final ServiceConnection certService = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            iCustomCertService = ICustomCertService.Stub.asInterface(iBinder);
            cert4androidReady = true;
            if (isSyncPossible()) {
                scheduleSync(false);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            cert4androidReady = false;
            iCustomCertService = null;
        }
    };

    // current state of the synchronization
    private boolean syncActive = false;
    private boolean syncScheduled = false;

    // list of callbacks for both parts of synchronziation
    private List<ICallback> callbacksPush = new ArrayList<>();
    private List<ICallback> callbacksPull = new ArrayList<>();

    private ConnectionStateMonitor connectionMonitor;

    private SessionServerSyncHelper(PhoneTrackSQLiteOpenHelper db) {
        this.dbHelper = db;
        this.appContext = db.getContext().getApplicationContext();
        new Thread() {
            @Override
            public void run() {
                customCertManager = SupportUtil.getCertManager(appContext);
            }
        }.start();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // track network connectivity changes
            connectionMonitor = new ConnectionStateMonitor();
            connectionMonitor.enable(appContext);
        }
        updateNetworkStatus();
        // bind to certifciate service to block sync attempts if service is not ready
        appContext.bindService(new Intent(appContext, CustomCertService.class), certService, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void finalize() throws Throwable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectionMonitor.disable(appContext);
        }
        appContext.unbindService(certService);
        if (customCertManager != null) {
            customCertManager.close();
        }
        super.finalize();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private class ConnectionStateMonitor extends ConnectivityManager.NetworkCallback {

        final NetworkRequest networkRequest;

        public ConnectionStateMonitor() {
            networkRequest = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        }

        public void enable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.registerNetworkCallback(networkRequest , this);
        }

        // Likewise, you can have a disable method that simply calls ConnectivityManager#unregisterCallback(networkRequest) too.

        public void disable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(this);
        }

        @Override
        public void onAvailable(Network network) {
            if (LoggerService.DEBUG) { Log.d(TAG, "NETWORK AVAILABLE : SYNC SESSIONS from synchelper"); }
            updateNetworkStatus();
            if (isSyncPossible()) {
                scheduleSync(false);
            }
        }
    }

    public static boolean isConfigured(Context context) {
        boolean useSSO = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SettingsActivity.SETTINGS_USE_SSO, false);
        boolean classicURLConfigured = !PreferenceManager.getDefaultSharedPreferences(context).getString(
                SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS
        ).isEmpty();
        return useSSO || classicURLConfigured;
    }

    /**
     * Synchronization is only possible, if there is an active network connection and
     * Cert4Android service is available.
     * SessionServerSyncHelper observes changes in the network connection.
     * The current state can be retrieved with this method.
     *
     * @return true if sync is possible, otherwise false.
     */
    public boolean isSyncPossible() {
        updateNetworkStatus();
        //Log.d(TAG, networkConnected+ " " +isConfigured(appContext) +" "+ cert4androidReady);
        return networkConnected && isConfigured(appContext) && cert4androidReady;
    }

    public CustomCertManager getCustomCertManager() {
        return customCertManager;
    }

    public void checkCertificate(byte[] cert, IOnCertificateDecision callback) throws RemoteException {
        iCustomCertService.checkTrusted(cert, true, false, callback);
    }

    /**
     * Adds a callback method to the SessionServerSyncHelper for the synchronization part push local changes to the server.
     * All callbacks will be executed once the synchronization operations are done.
     * After execution the callback will be deleted, so it has to be added again if it shall be
     * executed the next time all synchronize operations are finished.
     *
     * @param callback Implementation of ICallback, contains one method that shall be executed.
     */
    public void addCallbackPush(ICallback callback) {
        callbacksPush.add(callback);
    }

    /**
     * Adds a callback method to the SessionServerSyncHelper for the synchronization part pull remote changes from the server.
     * All callbacks will be executed once the synchronization operations are done.
     * After execution the callback will be deleted, so it has to be added again if it shall be
     * executed the next time all synchronize operations are finished.
     *
     * @param callback Implementation of ICallback, contains one method that shall be executed.
     */
    public void addCallbackPull(ICallback callback) {
        callbacksPull.add(callback);
    }


    /**
     * Schedules a synchronization and start it directly, if the network is connected and no
     * synchronization is currently running.
     *
     * @param onlyLocalChanges Whether to only push local changes to the server or to also load the whole list of sessions from the server.
     */
    public void scheduleSync(boolean onlyLocalChanges) {
        Log.d(getClass().getSimpleName(), "Sync requested (" + (onlyLocalChanges ? "onlyLocalChanges" : "full") + "; " + (syncActive ? "sync active" : "sync NOT active") + ") ...");
        Log.d(getClass().getSimpleName(), "(network:" + networkConnected + "; conf:" + isConfigured(appContext) + "; cert4android:" + cert4androidReady + ")");
        if (isSyncPossible() && (!syncActive || onlyLocalChanges)) {
            Log.d(getClass().getSimpleName(), "... starting now");
            SyncTask syncTask = new SyncTask(onlyLocalChanges);
            syncTask.addCallbacks(callbacksPush);
            callbacksPush = new ArrayList<>();
            if (!onlyLocalChanges) {
                syncTask.addCallbacks(callbacksPull);
                callbacksPull = new ArrayList<>();
            }
            syncTask.execute();
        } else if (!onlyLocalChanges) {
            Log.d(getClass().getSimpleName(), "... scheduled");
            syncScheduled = true;
            for (ICallback callback : callbacksPush) {
                callback.onScheduled();
            }
        } else {
            Log.d(getClass().getSimpleName(), "... do nothing");
            for (ICallback callback : callbacksPush) {
                callback.onScheduled();
            }
        }
    }

    private void updateNetworkStatus() {
        ConnectivityManager connMgr = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeInfo = connMgr.getActiveNetworkInfo();
        if (activeInfo != null && activeInfo.isConnected()) {
            Log.d(SessionServerSyncHelper.class.getSimpleName(), "Network connection established.");
            networkConnected = true;
        } else {
            networkConnected = false;
            Log.d(SessionServerSyncHelper.class.getSimpleName(), "No network connection.");
        }
    }

    /**
     * SyncTask is an AsyncTask which performs the synchronization in a background thread.
     * Synchronization consists of two parts: pushLocalChanges and pullRemoteChanges.
     */
    private class SyncTask extends AsyncTask<Void, Void, LoginStatus> {
        private final boolean onlyLocalChanges;
        private final List<ICallback> callbacks = new ArrayList<>();
        private PhoneTrackClient client;
        private List<Throwable> exceptions = new ArrayList<>();

        public SyncTask(boolean onlyLocalChanges) {
            this.onlyLocalChanges = onlyLocalChanges;
        }

        public void addCallbacks(List<ICallback> callbacks) {
            this.callbacks.addAll(callbacks);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (!onlyLocalChanges && syncScheduled) {
                syncScheduled = false;
            }
            syncActive = true;
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createPhoneTrackClient(); // recreate PhoneTrackClients on every sync in case the connection settings was changed
            Log.i(getClass().getSimpleName(), "STARTING SYNCHRONIZATION");
            //dbHelper.debugPrintFullDB();
            LoginStatus status = LoginStatus.OK;
            // TODO avoid doing getsessions everytime
            //pushLocalChanges();
            //if (!onlyLocalChanges) {
            if (client != null) {
                status = pullRemoteChanges();
            }
            else {
                status = LoginStatus.AUTH_FAILED;
            }
            //}
            //dbHelper.debugPrintFullDB();
            Log.i(getClass().getSimpleName(), "SYNCHRONIZATION FINISHED");
            return status;
        }

        /**
         * Pull remote Changes: update or create each remote session and remove remotely deleted sessions.
         */
        private LoginStatus pullRemoteChanges() {
            // TODO add/remove sessions
            Log.d(getClass().getSimpleName(), "pullRemoteChanges()");
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext);
            String lastETag = preferences.getString(SettingsActivity.SETTINGS_KEY_ETAG, null);
            long lastModified = preferences.getLong(SettingsActivity.SETTINGS_KEY_LAST_MODIFIED, 0);
            LoginStatus status;
            try {
                Map<String, DBSession> localTokenToSession = dbHelper.getTokenMap();
                ServerResponse.SessionsResponse response = client.getSessions(customCertManager, lastModified, lastETag);
                List<DBSession> remoteSessions = response.getSessions(dbHelper);
                Set<String> remoteTokens = new HashSet<>();
                // pull remote changes: update or create each remote logjob
                for (DBSession remoteSession : remoteSessions) {
                    //Log.v(getClass().getSimpleName(), "   Process Remote Session: " + remoteSession);
                    remoteTokens.add(remoteSession.getToken());
                    if (localTokenToSession.containsKey(remoteSession.getToken())) {

                        DBSession localSession = localTokenToSession.get(remoteSession.getToken());
                        // TODO
                        if (!localSession.getName().equals(remoteSession.getName())
                                || !localSession.getNextURL().equals(remoteSession.getNextURL())
                                || !localSession.getToken().equals(remoteSession.getToken())
                                || localSession.getPublicToken() == null
                                || !localSession.getPublicToken().equals(remoteSession.getPublicToken())
                                || localSession.isFromShare() != remoteSession.isFromShare()
                                || localSession.isPublic() != remoteSession.isPublic()
                        ) {
                            Log.v(getClass().getSimpleName(), "session "+localSession.getName()+" found locally -> needs update");
                            dbHelper.updateSession(localSession.getId(), remoteSession);
                        }
                        else {
                            Log.v(getClass().getSimpleName(), "session "+localSession.getName()+" found locally -> does not need update");
                        }
                    } else {
                        Log.v(getClass().getSimpleName(), "create session");
                        dbHelper.addSession(remoteSession);
                    }
                }
                Log.d(getClass().getSimpleName(), "Remove remotely deleted Sessions");
                // remove remotely deleted sessions
                for (String localToken : localTokenToSession.keySet()) {
                    if (!remoteTokens.contains(localToken)) {
                        DBSession s = localTokenToSession.get(localToken);
                        Log.v(getClass().getSimpleName(), "   ... remove " + s.getName());
                        dbHelper.deleteSession(s.getId());
                    }
                }
                status = LoginStatus.OK;

                // update ETag and Last-Modified in order to reduce size of next response
                SharedPreferences.Editor editor = preferences.edit();
                String etag = response.getETag();
                if (etag != null && !etag.isEmpty()) {
                    editor.putString(SettingsActivity.SETTINGS_KEY_ETAG, etag);
                } else {
                    editor.remove(SettingsActivity.SETTINGS_KEY_ETAG);
                }
                long modified = response.getLastModified();
                if (modified != 0) {
                    editor.putLong(SettingsActivity.SETTINGS_KEY_LAST_MODIFIED, modified);
                } else {
                    editor.remove(SettingsActivity.SETTINGS_KEY_LAST_MODIFIED);
                }
                editor.apply();
            } catch (ServerResponse.NotModifiedException e) {
                Log.d(getClass().getSimpleName(), "No changes, nothing to do.");
                status = LoginStatus.OK;
            } catch (IOException e) {
                Log.e(getClass().getSimpleName(), "Exception", e);
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            } catch (JSONException e) {
                Log.e(getClass().getSimpleName(), "Exception", e);
                exceptions.add(e);
                status = LoginStatus.JSON_FAILED;
            } catch (TokenMismatchException e) {
                Log.e(getClass().getSimpleName(), "Catch MISMATCHTOKEN", e);
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            }

            return status;
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            super.onPostExecute(status);
            if (status != LoginStatus.OK) {
                String errorString = appContext.getString(
                        R.string.error_sync,
                        appContext.getString(status.str)
                );
                errorString += "\n\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
                // broadcast the error
                // if the log job list is not visible, no toast
                Intent intent = new Intent(BROADCAST_SESSIONS_SYNC_FAILED);
                intent.putExtra(LoggerService.BROADCAST_ERROR_MESSAGE, errorString);
                appContext.sendBroadcast(intent);
                if (status == LoginStatus.SSO_TOKEN_MISMATCH) {
                    Intent intent2 = new Intent(BROADCAST_SSO_TOKEN_MISMATCH);
                    appContext.sendBroadcast(intent2);
                }
            }
            else {
                Intent intent = new Intent(BROADCAST_SESSIONS_SYNCED);
                appContext.sendBroadcast(intent);
            }
            syncActive = false;
            // notify callbacks
            for (ICallback callback : callbacks) {
                callback.onFinish();
            }
            dbHelper.notifySessionsChanged();
            // start next sync if scheduled meanwhile
            if (syncScheduled) {
                scheduleSync(false);
            }
        }
    }

    private NextcloudAPI.ApiConnectedListener apiCallback = new NextcloudAPI.ApiConnectedListener() {
        @Override
        public void onConnected() {
            // ignore this one..
            Log.d(getClass().getSimpleName(), "API connected!!!!");
        }

        @Override
        public void onError(Exception ex) {
            // TODO handle error in your app
        }
    };

    private PhoneTrackClient createPhoneTrackClient() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(appContext.getApplicationContext());
        String url = "";
        String username = "";
        String password = "";
        boolean useSSO = preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false);
        if (useSSO) {
            try {
                SingleSignOnAccount ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(appContext.getApplicationContext());
                NextcloudAPI nextcloudAPI = new NextcloudAPI(appContext.getApplicationContext(), ssoAccount, new GsonBuilder().create(), apiCallback);
                return new PhoneTrackClient(url, username, password, nextcloudAPI);
            }
            catch (NextcloudFilesAppAccountNotFoundException e) {
                return null;
            }
            catch (NoCurrentAccountSelectedException e) {
                return null;
            }
        }
        else {
            url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS);
            username = preferences.getString(SettingsActivity.SETTINGS_USERNAME, SettingsActivity.DEFAULT_SETTINGS);
            password = preferences.getString(SettingsActivity.SETTINGS_PASSWORD, SettingsActivity.DEFAULT_SETTINGS);
            return new PhoneTrackClient(url, username, password, null);
        }
    }

    public boolean shareDevice(String token, String deviceName, ICallback callback) {
        if (isSyncPossible()) {
            ShareDeviceTask shareDeviceTask = new ShareDeviceTask(token, deviceName, callback);
            shareDeviceTask.execute();
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create public share with name restriction on device
     * or just get the share token if it already exists
     *
     */
    private class ShareDeviceTask extends AsyncTask<Void, Void, LoginStatus> {
        private PhoneTrackClient client;
        private String token;
        private String deviceName;
        private String publicUrl = null;
        private ICallback callback;
        private List<Throwable> exceptions = new ArrayList<>();

        public ShareDeviceTask(String token, String deviceName, ICallback callback) {
            this.token = token;
            this.deviceName = deviceName;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createPhoneTrackClient();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            if (LoggerService.DEBUG) { Log.i(getClass().getSimpleName(), "STARTING share device"); }
            LoginStatus status = LoginStatus.OK;
            String sharetoken;
            try {
                ServerResponse.ShareDeviceResponse response = client.shareDevice(customCertManager, token, deviceName);
                sharetoken = response.getPublicToken();
                if (LoggerService.DEBUG) {
                    Log.i(getClass().getSimpleName(), "HERE IS THE TOKEN BIIIITCH "+sharetoken);
                }
                if (prefs.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false)) {
                    publicUrl = prefs.getString(SettingsActivity.SETTINGS_SSO_URL, SettingsActivity.DEFAULT_SETTINGS)
                            .replaceAll("/+$", "")
                            + "/index.php/apps/phonetrack/publicSessionWatch/" + sharetoken;
                }
                else {
                    publicUrl = prefs.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS)
                            .replaceAll("/+$", "")
                            + "/index.php/apps/phonetrack/publicSessionWatch/" + sharetoken;
                }
            } catch (IOException e) {
                if (LoggerService.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            } catch (JSONException e) {
                if (LoggerService.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.JSON_FAILED;
            } catch (TokenMismatchException e) {
                Log.e(getClass().getSimpleName(), "Catch MISMATCHTOKEN", e);
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            }
            if (LoggerService.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED share device");
            }
            return status;
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            super.onPostExecute(status);
            String errorString = "";
            if (status != LoginStatus.OK) {
                errorString = appContext.getString(
                        R.string.error_sync,
                        appContext.getString(status.str)
                );
                errorString += "\n\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
            }
            callback.onFinish(publicUrl, errorString);
        }
    }

    public boolean getSessionLastPositions(DBSession session, IGetLastPosCallback callback) {
        if (isSyncPossible()) {
            GetSessionlastPositionsTask getSessionlastPositionsTask = new GetSessionlastPositionsTask(session, callback);
            getSessionlastPositionsTask.execute();
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create public share with name restriction on device
     * or just get the share token if it already exists
     *
     */
    private class GetSessionlastPositionsTask extends AsyncTask<Void, Void, LoginStatus> {
        private PhoneTrackClient client;
        private DBSession session;
        private IGetLastPosCallback callback;
        private List<Throwable> exceptions = new ArrayList<>();
        private Map<String, ColoredLocation> locations;

        public GetSessionlastPositionsTask(DBSession session, IGetLastPosCallback callback) {
            this.session = session;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createPhoneTrackClient();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            if (LoggerService.DEBUG) { Log.i(getClass().getSimpleName(), "STARTING share device"); }
            LoginStatus status = LoginStatus.OK;
            locations = new HashMap<>();
            try {
                ServerResponse.GetSessionLastPositionsResponse response = client.getSessionLastPositions(customCertManager, session);
                locations = response.getPositions(session);
                if (LoggerService.DEBUG) {
                    Log.i(getClass().getSimpleName(), "HERE ARE THE positions BIIIITCH "+locations.keySet().size());
                }

            } catch (IOException e) {
                if (LoggerService.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            } catch (JSONException e) {
                if (LoggerService.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.JSON_FAILED;
            } catch (TokenMismatchException e) {
                Log.e(getClass().getSimpleName(), "Catch MISMATCHTOKEN", e);
                status = LoginStatus.SSO_TOKEN_MISMATCH;
            }
            if (LoggerService.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED share device");
            }
            return status;
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            super.onPostExecute(status);
            String errorString = "";
            if (status != LoginStatus.OK) {
                errorString = appContext.getString(
                        R.string.error_sync,
                        appContext.getString(status.str)
                );
                errorString += "\n\n";
                for (Throwable e : exceptions) {
                    errorString += e.getClass().getName() + ": " + e.getMessage();
                }
            }
            callback.onFinish(locations, errorString);
        }
    }

    public boolean createSession(String sessionName, ICallback callback) {
        if (isSyncPossible()) {
            CreateSessionTask createSessionTask = new CreateSessionTask(sessionName, callback);
            createSessionTask.execute();
            return true;
        }
        return false;
    }

    /**
     * task to ask server to create a session
     *
     */
    private class CreateSessionTask extends AsyncTask<Void, Void, LoginStatus> {
        private PhoneTrackClient client;
        private String sessionName;
        private String sessionId = null;
        private ICallback callback;
        private List<Throwable> exceptions = new ArrayList<>();

        public CreateSessionTask(String sessionName, ICallback callback) {
            this.sessionName = sessionName;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected LoginStatus doInBackground(Void... voids) {
            client = createPhoneTrackClient();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            if (LoggerService.DEBUG) { Log.i(getClass().getSimpleName(), "STARTING share device"); }
            LoginStatus status = LoginStatus.OK;
            try {
                ServerResponse.CreateSessionResponse response = client.createSession(customCertManager, sessionName);
                sessionId = response.getSessionId();
                if (LoggerService.DEBUG) {
                    Log.i(getClass().getSimpleName(), "HERE IS THE ID BIIIITCH "+sessionId);
                }
            } catch (IOException e) {
                if (LoggerService.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.CONNECTION_FAILED;
            } catch (JSONException e) {
                if (LoggerService.DEBUG) {
                    Log.e(getClass().getSimpleName(), "Exception", e);
                }
                exceptions.add(e);
                status = LoginStatus.JSON_FAILED;
            } catch (Exception e) {
                exceptions.add(new Exception(appContext.getString(R.string.error_create_session_exists)));
            }
            if (LoggerService.DEBUG) {
                Log.i(getClass().getSimpleName(), "FINISHED create session task");
            }
            return status;
        }

        @Override
        protected void onPostExecute(LoginStatus status) {
            super.onPostExecute(status);
            String errorString = "";
            if (status != LoginStatus.OK) {
                errorString = appContext.getString(
                        R.string.error_sync,
                        appContext.getString(status.str)
                );
                errorString += "\n\n";
            }
            for (Throwable e : exceptions) {
                errorString += e.getClass().getName() + ": " + e.getMessage();
            }
            callback.onFinish(sessionId, errorString);
        }
    }
}
