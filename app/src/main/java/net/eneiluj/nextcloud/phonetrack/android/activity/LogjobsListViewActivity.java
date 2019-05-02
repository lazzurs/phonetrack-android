package net.eneiluj.nextcloud.phonetrack.android.activity;

import android.Manifest;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.eneiluj.nextcloud.phonetrack.R;
import net.eneiluj.nextcloud.phonetrack.model.Category;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjob;
import net.eneiluj.nextcloud.phonetrack.model.DBLogjobLocation;
import net.eneiluj.nextcloud.phonetrack.model.DBSession;
import net.eneiluj.nextcloud.phonetrack.model.Item;
import net.eneiluj.nextcloud.phonetrack.model.ItemAdapter;
import net.eneiluj.nextcloud.phonetrack.model.NavigationAdapter;
import net.eneiluj.nextcloud.phonetrack.model.SyncError;
import net.eneiluj.nextcloud.phonetrack.persistence.LoadLogjobsListTask;
import net.eneiluj.nextcloud.phonetrack.persistence.PhoneTrackSQLiteOpenHelper;
import net.eneiluj.nextcloud.phonetrack.persistence.SessionServerSyncHelper;
import net.eneiluj.nextcloud.phonetrack.service.LoggerService;
import net.eneiluj.nextcloud.phonetrack.service.WebTrackService;
import net.eneiluj.nextcloud.phonetrack.util.ICallback;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrack;
import net.eneiluj.nextcloud.phonetrack.util.PhoneTrackClientUtil;
import net.eneiluj.nextcloud.phonetrack.util.SupportUtil;
import net.eneiluj.nextcloud.phonetrack.util.ThemeUtils;

public class LogjobsListViewActivity extends AppCompatActivity implements ItemAdapter.LogjobClickListener {

    private final static int PERMISSION_LOCATION = 1;
    private final static int PERMISSION_FOREGROUND = 2;

    private final static int PERMISSION_FOREGROUND_SERVICE = 1;

    private static final String TAG = LogjobsListViewActivity.class.getSimpleName();

    public final static String CREATED_LOGJOB = "net.eneiluj.nextcloud.phonetrack.created_logjob";
    public final static String CREDENTIALS_CHANGED = "net.eneiluj.nextcloud.phonetrack.CREDENTIALS_CHANGED";
    public static final String ADAPTER_KEY_ALL = "all";
    public static final String ADAPTER_KEY_ENABLED = "enabled";
    public static final String ADAPTER_KEY_PHONETRACK = "pt";
    public static final String ADAPTER_KEY_CUSTOM = "custom";
    public static final String CATEGORY_PHONETRACK = "pt";
    public static final String CATEGORY_CUSTOM = "cu";

    public final static String UPDATED_LOGJOBS = "net.eneiluj.nextcloud.phonetrack.UPDATED_LOGJOBS";
    public final static String UPDATED_LOGJOB_ID = "net.eneiluj.nextcloud.phonetrack.UPDATED_LOGJOB_ID";

    private static final String SAVED_STATE_NAVIGATION_SELECTION = "navigationSelection";
    private static final String SAVED_STATE_NAVIGATION_ADAPTER_SLECTION = "navigationAdapterSelection";
    private static final String SAVED_STATE_NAVIGATION_OPEN = "navigationOpen";

    private final static int create_logjob_cmd = 0;
    private final static int show_single_logjob_cmd = 1;
    private final static int server_settings = 2;
    private final static int about = 3;
    private final static int map = 4;


    Toolbar toolbar;
    DrawerLayout drawerLayout;
    TextView account;
    SwipeRefreshLayout swipeRefreshLayout;
    com.github.clans.fab.FloatingActionButton fabCreatePhoneTrack;
    com.github.clans.fab.FloatingActionButton fabCreateCustom;
    com.github.clans.fab.FloatingActionButton fabCreateSession;
    com.github.clans.fab.FloatingActionMenu fabMenu;
    RecyclerView listNavigationCategories;
    RecyclerView listNavigationMenu;
    RecyclerView listView;
    Snackbar ssoSnackbar;

    private ActionBarDrawerToggle drawerToggle;
    private ItemAdapter adapter = null;
    private NavigationAdapter adapterCategories;
    private NavigationAdapter.NavigationItem itemAll, itemEnabled, itemPhonetrack, itemCustom, itemUncategorized;
    private Category navigationSelection = new Category(null, null);
    private String navigationOpen = "";
    private ActionMode mActionMode;
    private PhoneTrackSQLiteOpenHelper db = null;
    private SearchView searchView = null;
    private ICallback syncCallBack = new ICallback() {
        @Override
        public void onFinish() {
            adapter.clearSelection();
            if (mActionMode != null) {
                mActionMode.finish();
            }
            refreshLists();
            //swipeRefreshLayout.setRefreshing(false);
        }

        @Override
        public void onFinish(String result, String message) {
        }

        @Override
        public void onScheduled() {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // First Run Wizard
        /*if (!SessionServerSyncHelper.isConfigured(this)) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivityForResult(settingsIntent, server_settings);
        }*/
        ssoSnackbar = null;

        String categoryAdapterSelectedItem = ADAPTER_KEY_ALL;
        if (savedInstanceState != null) {
            navigationSelection = (Category) savedInstanceState.getSerializable(SAVED_STATE_NAVIGATION_SELECTION);
            navigationOpen = savedInstanceState.getString(SAVED_STATE_NAVIGATION_OPEN);
            categoryAdapterSelectedItem = savedInstanceState.getString(SAVED_STATE_NAVIGATION_ADAPTER_SLECTION);
        }

        setContentView(R.layout.drawer_layout);
        toolbar = findViewById(R.id.logjobsListActivityActionBar);
        drawerLayout = findViewById(R.id.drawerLayout);
        account = findViewById(R.id.account);
        swipeRefreshLayout = findViewById(R.id.swiperefreshlayout);
        fabCreatePhoneTrack = findViewById(R.id.fab_create_phonetrack);
        fabCreateCustom = findViewById(R.id.fab_create_custom);
        fabCreateSession = findViewById(R.id.fab_create_session);
        fabMenu = findViewById(R.id.floatingMenu);
        listNavigationCategories = findViewById(R.id.navigationList);
        listNavigationMenu = findViewById(R.id.navigationMenu);
        listView = findViewById(R.id.recycler_view);

        //ButterKnife.bind(this);

        db = PhoneTrackSQLiteOpenHelper.getInstance(this);

        setupActionBar();
        setupLogjobsList();
        setupNavigationList(categoryAdapterSelectedItem);
        setupNavigationMenu();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            if (LoggerService.DEBUG) { Log.d(TAG, "[request location permission]"); }
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_LOCATION
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE)
                    != PackageManager.PERMISSION_GRANTED) {

                if (LoggerService.DEBUG) { Log.d(TAG, "[request foreground permission]"); }
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.FOREGROUND_SERVICE},
                        PERMISSION_FOREGROUND
                );
            }
        }

        Map<String, Integer> enabled = db.getEnabledCount();
        int nbEnabledLogjobs = enabled.containsKey("1") ? enabled.get("1") : 0;
        if (nbEnabledLogjobs > 0) {
            // start loggerservice !
            Intent intent = new Intent(LogjobsListViewActivity.this, LoggerService.class);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                startService(intent);
            } else {
                startForegroundService(intent);
            }
        }
    }

    @Override
    protected void onResume() {
        if (LoggerService.DEBUG) { Log.d(TAG, "[onResume]"); }
        super.onResume();
        // refresh and sync every time the activity gets visible
        refreshLists();
        swipeRefreshLayout.setRefreshing(false);
        //db.getPhonetrackServerSyncHelper().addCallbackPull(syncCallBack);
        if (db.getPhonetrackServerSyncHelper().isSyncPossible()) {
            synchronize();
        }

        registerBroadcastReceiver();

        if (LoggerService.DEBUG) { Log.d(TAG, "[onResume END]"); }
    }

    /**
     * On pause
     */
    @Override
    protected void onPause() {
        if (LoggerService.DEBUG) { Log.d(TAG, "[onPause]"); }
        super.onPause();

        try {
            unregisterReceiver(mBroadcastReceiver);
        }
        // i don't understand why this is happening on 6.0 only
        // onPause is called twice when trying to launch preferences activity
        // anyway this solves it, at least the app does not crash anymore
        catch (RuntimeException e) {
            if (LoggerService.DEBUG) { Log.d(TAG, "RECEIVER PROBLEM, let's ignore it..."); }
        }
        if (LoggerService.DEBUG) { Log.d(TAG, "[onPause END]"); }
    }


    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.syncState();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(SAVED_STATE_NAVIGATION_SELECTION, navigationSelection);
        outState.putString(SAVED_STATE_NAVIGATION_ADAPTER_SLECTION, adapterCategories.getSelectedItem());
        outState.putString(SAVED_STATE_NAVIGATION_OPEN, navigationOpen);
    }

    private void setupActionBar() {
        setSupportActionBar(toolbar);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.action_drawer_open, R.string.action_drawer_close);
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerLayout.findViewById(R.id.drawer_top_layout).setBackgroundColor(ThemeUtils.primaryColor(this));
        ImageView logoView = drawerLayout.findViewById(R.id.drawer_logo);
        logoView.setColorFilter(ThemeUtils.primaryColor(this), PorterDuff.Mode.OVERLAY);

        toolbar.setBackgroundColor(ThemeUtils.primaryColor(this));

        Window window = getWindow();
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int color = ThemeUtils.primaryDarkColor(this);
                window.setStatusBarColor(color);
            }
        }
    }

    private void setupLogjobsList() {
        initList();
        // Pull to Refresh
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (db.getPhonetrackServerSyncHelper().isSyncPossible()) {
                    synchronize();
                } else {
                    //swipeRefreshLayout.setRefreshing(false);
                    // don't bother user if no conf
                    if (SessionServerSyncHelper.isConfigured(getApplicationContext())) {
                        Toast.makeText(getApplicationContext(), getString(R.string.error_sync, getString(PhoneTrackClientUtil.LoginStatus.NO_NETWORK.str)), Toast.LENGTH_LONG).show();
                    }
                }
                if (db.getLocationNotSyncedCount() > 0) {
                    Intent syncIntent = new Intent(LogjobsListViewActivity.this, WebTrackService.class);
                    startService(syncIntent);
                    showToast(getString(R.string.uploading_started));
                }
                else {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        });

        fabMenu.setOnMenuToggleListener(new com.github.clans.fab.FloatingActionMenu.OnMenuToggleListener() {
            @Override
            public void onMenuToggle(boolean opened) {

                int drawableId;
                if (opened) {
                    if (SessionServerSyncHelper.isConfigured(getApplicationContext())) {
                        fabCreateSession.setVisibility(View.VISIBLE);
                    }
                    else {
                        fabCreateSession.setVisibility(View.GONE);
                    }
                } else {

                }

            }
        });

        fabCreateSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                fabMenu.close(true);
                EditText sessionNameEdit = new EditText(view.getContext());
                AlertDialog.Builder sessionBuilder = new AlertDialog.Builder(new ContextThemeWrapper(view.getContext(), R.style.AppThemeDialog));
                sessionBuilder.setMessage(getString(R.string.dialog_msg_create_session));
                sessionBuilder.setTitle(getString(R.string.dialog_title_create_session));

                sessionBuilder.setView(sessionNameEdit);

                sessionBuilder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String sessionName = sessionNameEdit.getText().toString();
                        if (!sessionName.isEmpty()) {
                            if (!db.getPhonetrackServerSyncHelper().createSession(sessionName, createSessionCallBack)) {
                                showToast(getString(R.string.error_create_session_network), Toast.LENGTH_LONG);
                            }
                        }
                        // restore keyboard auto hide behaviour
                        InputMethodManager inputMethodManager = (InputMethodManager) sessionNameEdit.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                    }
                });
                sessionBuilder.setNegativeButton(getString(R.string.simple_cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // restore keyboard auto hide behaviour
                        InputMethodManager inputMethodManager = (InputMethodManager) sessionNameEdit.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
                    }
                });
                AlertDialog sessionDialog = sessionBuilder.create();
                sessionDialog.show();
                sessionNameEdit.setSelectAllOnFocus(true);
                sessionNameEdit.requestFocus();
                // show keyboard
                InputMethodManager inputMethodManager = (InputMethodManager) sessionNameEdit.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        });

        fabCreateCustom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent createIntent = new Intent(getApplicationContext(), EditCustomLogjobActivity.class);
                startActivityForResult(createIntent, create_logjob_cmd);
                fabMenu.close(false);
            }
        });
        fabCreatePhoneTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent createIntent = new Intent(getApplicationContext(), EditPhoneTrackLogjobActivity.class);
                startActivityForResult(createIntent, create_logjob_cmd);
                fabMenu.close(false);
            }
        });

        boolean darkTheme = PhoneTrack.getAppTheme(this);
        // if dark theme and main color is black, make fab button lighter/gray
        if (darkTheme && ThemeUtils.primaryColor(this) == Color.BLACK) {
            fabMenu.setMenuButtonColorNormal(Color.DKGRAY);
            fabCreateCustom.setColorNormal(Color.DKGRAY);
            fabCreateSession.setColorNormal(Color.DKGRAY);
            fabCreatePhoneTrack.setColorNormal(Color.DKGRAY);
        }
        else {
            fabMenu.setMenuButtonColorNormal(ThemeUtils.primaryColor(this));
                fabCreateCustom.setColorNormal(ThemeUtils.primaryColor(this));
            fabCreateSession.setColorNormal(ThemeUtils.primaryColor(this));
            fabCreatePhoneTrack.setColorNormal(ThemeUtils.primaryColor(this));
        }
        fabMenu.setMenuButtonColorPressed(ThemeUtils.primaryColor(this));

        fabCreateCustom.setColorPressed(ThemeUtils.primaryColor(this));
        fabCreateSession.setColorPressed(ThemeUtils.primaryColor(this));
        fabCreatePhoneTrack.setColorPressed(ThemeUtils.primaryColor(this));
    }

    private void setupNavigationList(final String selectedItem) {
        itemAll = new NavigationAdapter.NavigationItem(ADAPTER_KEY_ALL, getString(R.string.label_all_logjobs), null, R.drawable.ic_allgrey_24dp);
        itemEnabled = new NavigationAdapter.NavigationItem(ADAPTER_KEY_ENABLED, getString(R.string.label_enabled), null, R.drawable.ic_check_box_grey_24dp);
        itemPhonetrack = new NavigationAdapter.NavigationItem(ADAPTER_KEY_PHONETRACK, getString(R.string.label_phonetrack_lj), null, R.drawable.ic_phonetrack_grey_24dp);
        itemCustom = new NavigationAdapter.NavigationItem(ADAPTER_KEY_CUSTOM, getString(R.string.label_custom_lj), null, R.drawable.ic_link_menu_grey_24dp);
        adapterCategories = new NavigationAdapter(new NavigationAdapter.ClickListener() {
            @Override
            public void onItemClick(NavigationAdapter.NavigationItem item) {
                selectItem(item, true);
            }

            private void selectItem(NavigationAdapter.NavigationItem item, boolean closeNavigation) {
                adapterCategories.setSelectedItem(item.id);

                // update current selection
                if (itemAll == item) {
                    navigationSelection = new Category(null, null);
                } else if (itemEnabled == item) {
                    navigationSelection = new Category(null, true);
                } else if (itemUncategorized == item) {
                    navigationSelection = new Category("", null);
                } else if (itemPhonetrack == item) {
                    navigationSelection = new Category(CATEGORY_PHONETRACK, null);
                } else if (itemCustom == item) {
                    navigationSelection = new Category(CATEGORY_CUSTOM, null);
                } else {
                    navigationSelection = new Category(item.label, null);
                }

                // auto-close sub-folder in Navigation if selection is outside of that folder
                if (navigationOpen != null) {
                    int slashIndex = navigationSelection.category == null ? -1 : navigationSelection.category.indexOf('/');
                    String rootCategory = slashIndex < 0 ? navigationSelection.category : navigationSelection.category.substring(0, slashIndex);
                    if (!navigationOpen.equals(rootCategory)) {
                        navigationOpen = null;
                    }
                }

                // update views
                if (closeNavigation) {
                    drawerLayout.closeDrawers();
                }
                refreshLists(true);
            }

            @Override
            public void onIconClick(NavigationAdapter.NavigationItem item) {
                if (item.icon == NavigationAdapter.ICON_MULTIPLE && !item.label.equals(navigationOpen)) {
                    navigationOpen = item.label;
                    selectItem(item, false);
                } else if (item.icon == NavigationAdapter.ICON_MULTIPLE || item.icon == NavigationAdapter.ICON_MULTIPLE_OPEN && item.label.equals(navigationOpen)) {
                    navigationOpen = null;
                    refreshLists();
                } else {
                    onItemClick(item);
                }
            }
        });
        adapterCategories.setSelectedItem(selectedItem);
        listNavigationCategories.setAdapter(adapterCategories);
    }


    private class LoadCategoryListTask extends AsyncTask<Void, Void, List<NavigationAdapter.NavigationItem>> {
        @Override
        protected List<NavigationAdapter.NavigationItem> doInBackground(Void... voids) {
            /*List<NavigationAdapter.NavigationItem> categories = db.getCategories();
            if (!categories.isEmpty() && categories.get(0).label.isEmpty()) {
                itemUncategorized = categories.get(0);
                itemUncategorized.label = getString(R.string.action_uncategorized);
                itemUncategorized.icon = NavigationAdapter.ICON_NOFOLDER;
            } else {
                itemUncategorized = null;
            }*/
            itemUncategorized = null;

            int nbPT = 0;
            int nbCU = 0;
            List<DBLogjob> ljs = db.getLogjobs();
            for (DBLogjob lj : ljs) {
                if (lj.getToken().isEmpty() && lj.getDeviceName().isEmpty()) {
                    nbCU++;
                }
                else {
                    nbPT++;
                }
            }

            Map<String, Integer> favorites = db.getEnabledCount();
            int numFavorites = favorites.containsKey("1") ? favorites.get("1") : 0;
            int numNonFavorites = favorites.containsKey("0") ? favorites.get("0") : 0;
            itemEnabled.count = numFavorites;
            itemAll.count = numFavorites + numNonFavorites;
            itemPhonetrack.count = nbPT;
            itemCustom.count = nbCU;

            ArrayList<NavigationAdapter.NavigationItem> items = new ArrayList<>();
            items.add(itemAll);
            items.add(itemEnabled);
            items.add(itemPhonetrack);
            items.add(itemCustom);
            NavigationAdapter.NavigationItem lastPrimaryCategory = null, lastSecondaryCategory = null;
            /*for (NavigationAdapter.NavigationItem item : categories) {
                int slashIndex = item.label.indexOf('/');
                String currentPrimaryCategory = slashIndex < 0 ? item.label : item.label.substring(0, slashIndex);
                String currentSecondaryCategory = null;
                boolean isCategoryOpen = currentPrimaryCategory.equals(navigationOpen);

                if (isCategoryOpen && !currentPrimaryCategory.equals(item.label)) {
                    String currentCategorySuffix = item.label.substring(navigationOpen.length() + 1);
                    int subSlashIndex = currentCategorySuffix.indexOf('/');
                    currentSecondaryCategory = subSlashIndex < 0 ? currentCategorySuffix : currentCategorySuffix.substring(0, subSlashIndex);
                }

                boolean belongsToLastPrimaryCategory = lastPrimaryCategory != null && currentPrimaryCategory.equals(lastPrimaryCategory.label);
                boolean belongsToLastSecondaryCategory = belongsToLastPrimaryCategory && lastSecondaryCategory != null && lastSecondaryCategory.label.equals(currentPrimaryCategory + "/" + currentSecondaryCategory);

                if (isCategoryOpen && !belongsToLastPrimaryCategory && currentSecondaryCategory != null) {
                    lastPrimaryCategory = new NavigationAdapter.NavigationItem("category:" + currentPrimaryCategory, currentPrimaryCategory, 0, NavigationAdapter.ICON_MULTIPLE_OPEN);
                    items.add(lastPrimaryCategory);
                    belongsToLastPrimaryCategory = true;
                }

                if (belongsToLastPrimaryCategory && belongsToLastSecondaryCategory) {
                    lastSecondaryCategory.count += item.count;
                    lastSecondaryCategory.icon = NavigationAdapter.ICON_SUB_MULTIPLE;
                } else if (belongsToLastPrimaryCategory) {
                    if (isCategoryOpen) {
                        item.label = currentPrimaryCategory + "/" + currentSecondaryCategory;
                        item.id = "category:" + item.label;
                        item.icon = NavigationAdapter.ICON_SUB_FOLDER;
                        items.add(item);
                        lastSecondaryCategory = item;
                    } else {
                        lastPrimaryCategory.count += item.count;
                        lastPrimaryCategory.icon = NavigationAdapter.ICON_MULTIPLE;
                        lastSecondaryCategory = null;
                    }
                } else {
                    if (isCategoryOpen) {
                        item.icon = NavigationAdapter.ICON_MULTIPLE_OPEN;
                    } else {
                        item.label = currentPrimaryCategory;
                        item.id = "category:" + item.label;
                    }
                    items.add(item);
                    lastPrimaryCategory = item;
                    lastSecondaryCategory = null;
                }
            }*/
            return items;
        }

        @Override
        protected void onPostExecute(List<NavigationAdapter.NavigationItem> items) {
            adapterCategories.setItems(items);
        }
    }


    private void setupNavigationMenu() {
        //final NavigationAdapter.NavigationItem itemTrashbin = new NavigationAdapter.NavigationItem("trashbin", getString(R.string.action_trashbin), null, R.drawable.ic_delete_grey600_24dp);
        final NavigationAdapter.NavigationItem itemMap = new NavigationAdapter.NavigationItem("map", getString(R.string.simple_map), null, R.drawable.ic_map_grey_24dp);
        final NavigationAdapter.NavigationItem itemSettings = new NavigationAdapter.NavigationItem("settings", getString(R.string.action_settings), null, R.drawable.ic_settings_grey600_24dp);
        final NavigationAdapter.NavigationItem itemAbout = new NavigationAdapter.NavigationItem("about", getString(R.string.simple_about), null, R.drawable.ic_info_outline_grey600_24dp);

        ArrayList<NavigationAdapter.NavigationItem> itemsMenu = new ArrayList<>();
        itemsMenu.add(itemMap);
        itemsMenu.add(itemSettings);
        itemsMenu.add(itemAbout);

        NavigationAdapter adapterMenu = new NavigationAdapter(new NavigationAdapter.ClickListener() {
            @Override
            public void onItemClick(NavigationAdapter.NavigationItem item) {
                if (item == itemSettings) {
                    Intent settingsIntent = new Intent(getApplicationContext(), PreferencesActivity.class);
                    startActivityForResult(settingsIntent, server_settings);
                }
                else if (item == itemAbout) {
                    Intent aboutIntent = new Intent(getApplicationContext(), AboutActivity.class);
                    startActivityForResult(aboutIntent, about);
                }
                else if (item == itemMap) {
                    List<DBSession> sessions = db.getSessions();
                    List<String> sessionNameList = new ArrayList<>();
                    final List<Long> sessionIdList = new ArrayList<>();
                    for (DBSession session : sessions) {
                        sessionNameList.add(session.getName());
                        sessionIdList.add(session.getId());
                    }
                    // manage session list DIALOG
                    AlertDialog.Builder selectBuilder = new AlertDialog.Builder(new ContextThemeWrapper(listView.getContext(), R.style.AppThemeDialog));
                    selectBuilder.setTitle(getString(R.string.map_choose_session_dialog_title));

                    if (sessionNameList.size() > 0) {
                        if (sessionNameList.size() == 1) {
                            long sid = sessionIdList.get(0);
                            Intent mapIntent = new Intent(getApplicationContext(), MapActivity.class);
                            mapIntent.putExtra(MapActivity.PARAM_SESSIONID, sid);
                            startActivityForResult(mapIntent, map);
                        }
                        else {
                            CharSequence[] entcs = sessionNameList.toArray(new CharSequence[sessionNameList.size()]);
                            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            long lastSelectedSessionId = preferences.getLong(SettingsActivity.SETTINGS_LAST_SELECTED_SESSION_ID, -1);

                            int selectedIndex = sessionIdList.indexOf(lastSelectedSessionId);
                            selectBuilder.setSingleChoiceItems(entcs, selectedIndex, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    long sid = sessionIdList.get(which);
                                    Intent mapIntent = new Intent(getApplicationContext(), MapActivity.class);
                                    mapIntent.putExtra(MapActivity.PARAM_SESSIONID, sid);
                                    startActivityForResult(mapIntent, map);
                                    dialog.dismiss();

                                    SharedPreferences.Editor editor = preferences.edit();
                                    editor.putLong(SettingsActivity.SETTINGS_LAST_SELECTED_SESSION_ID, sid);
                                    editor.apply();
                                }
                            });
                            selectBuilder.setNegativeButton(getString(R.string.simple_cancel), null);
                            selectBuilder.setPositiveButton(getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    ListView lw = ((AlertDialog)dialog).getListView();
                                    int w = lw.getCheckedItemPosition();

                                    long sid = sessionIdList.get(w);
                                    Intent mapIntent = new Intent(getApplicationContext(), MapActivity.class);
                                    mapIntent.putExtra(MapActivity.PARAM_SESSIONID, sid);
                                    startActivityForResult(mapIntent, map);
                                    dialog.dismiss();
                                }
                            });


                            AlertDialog selectDialog = selectBuilder.create();
                            selectDialog.show();
                        }
                    }
                    else {
                        showToast(getString(R.string.map_choose_session_dialog_impossible), Toast.LENGTH_LONG);
                    }
                }
            }

            @Override
            public void onIconClick(NavigationAdapter.NavigationItem item) {
                onItemClick(item);
            }
        });


        this.updateUsernameInDrawer();
        final LogjobsListViewActivity that = this;
        this.account.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsIntent = new Intent(that, SettingsActivity.class);
                startActivityForResult(settingsIntent, server_settings);
            }
        });

        adapterMenu.setItems(itemsMenu);
        listNavigationMenu.setAdapter(adapterMenu);
    }

    public void initList() {
        adapter = new ItemAdapter(this, db);
        listView.setAdapter(adapter);
        listView.setLayoutManager(new LinearLayoutManager(this));
        ItemTouchHelper touchHelper = new ItemTouchHelper(new SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            /**
             * Disable swipe on sections
             *
             * @param recyclerView RecyclerView
             * @param viewHolder   RecyclerView.ViewHoler
             * @return 0 if section, otherwise super()
             */
            @Override
            public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                if (viewHolder instanceof ItemAdapter.SectionViewHolder) return 0;
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            /**
             * Delete logjob if logjob is swiped to left or right
             *
             * @param viewHolder RecyclerView.ViewHoler
             * @param direction  int
             */
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                switch(direction) {
                    case ItemTouchHelper.LEFT: {
                        final DBLogjob dbLogjob = (DBLogjob) adapter.getItem(viewHolder.getAdapterPosition());
                        // get locations
                        final List<DBLogjobLocation> locations = db.getLocationsOfLogjob(dbLogjob.getId());
                        db.deleteLogjob(dbLogjob.getId());
                        adapter.remove(dbLogjob);
                        refreshLists();
                        Log.v(TAG, "Item deleted through swipe ----------------------------------------------");
                        Snackbar.make(swipeRefreshLayout, R.string.action_logjob_deleted, Snackbar.LENGTH_LONG)
                                .setAction(R.string.action_undo, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        db.addLogjob(dbLogjob);
                                        for (DBLogjobLocation dbloc : locations) {
                                            db.addLocation(dbloc);
                                        }
                                        refreshLists();
                                        Snackbar.make(swipeRefreshLayout, R.string.action_logjob_restored, Snackbar.LENGTH_SHORT)
                                                .show();
                                        notifyLoggerService(dbLogjob.getId());
                                    }
                                })
                                .show();
                        notifyLoggerService(dbLogjob.getId());
                        break;
                    }
                    case ItemTouchHelper.RIGHT: {
                        final DBLogjob dbLogjob = (DBLogjob) adapter.getItem(viewHolder.getAdapterPosition());
                        db.toggleEnabled(dbLogjob, syncCallBack);
                        refreshLists();
                        notifyLoggerService(dbLogjob.getId());
                        break;
                    }
                }
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                ItemAdapter.LogjobViewHolder logjobViewHolder = (ItemAdapter.LogjobViewHolder) viewHolder;
                // show swipe icon on the side
                logjobViewHolder.showSwipe(dX>0);
                // move only swipeable part of item (not leave-behind)
                getDefaultUIUtil().onDraw(c, recyclerView, logjobViewHolder.logjobSwipeable, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                getDefaultUIUtil().clearView(((ItemAdapter.LogjobViewHolder) viewHolder).logjobSwipeable);
            }
        });
        touchHelper.attachToRecyclerView(listView);
    }

    private void refreshLists() {
        refreshLists(false);
    }
    private void refreshLists(final boolean scrollToTop) {
        String subtitle;
        if (navigationSelection.favorite != null && navigationSelection.favorite) {
            subtitle = getString(R.string.app_name) + " - " + getString(R.string.label_enabled);
        } else if (navigationSelection.category == CATEGORY_PHONETRACK) {
            subtitle = getString(R.string.app_name);
        } else if (navigationSelection.category == CATEGORY_CUSTOM) {
            subtitle = getString(R.string.app_name) + " - " + getString(R.string.label_custom);
        } else {
            subtitle = getString(R.string.app_name) + " - " + getString(R.string.label_all_logjobs);
        }
        setTitle(subtitle);
        CharSequence query = null;
        if (searchView != null && !searchView.isIconified() && searchView.getQuery().length() != 0) {
            query = searchView.getQuery();
        }

        LoadLogjobsListTask.LogjobsLoadedListener callback = new LoadLogjobsListTask.LogjobsLoadedListener() {
            @Override
            public void onLogjobsLoaded(List<Item> ljItems, boolean showCategory) {
                adapter.setShowCategory(showCategory);
                adapter.setItemList(ljItems);
                if(scrollToTop) {
                    listView.scrollToPosition(0);
                }
            }
        };
        new LoadLogjobsListTask(getApplicationContext(), callback, navigationSelection, query).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        new LoadCategoryListTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public ItemAdapter getItemAdapter() {
        return adapter;
    }

    public SwipeRefreshLayout getSwipeRefreshLayout() {
        return swipeRefreshLayout;
    }

    /**
     * Adds the Menu Items to the Action Bar.
     *
     * @param menu Menu
     * @return boolean
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_list_view, menu);
        // Associate searchable configuration with the SearchView
        final MenuItem item = menu.findItem(R.id.search);
        searchView = (SearchView) item.getActionView();

        final LinearLayout searchEditFrame = searchView.findViewById(androidx.appcompat.R.id
                .search_edit_frame);

        searchEditFrame.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            int oldVisibility = -1;
            @Override
            public void onGlobalLayout() {
                int currentVisibility = searchEditFrame.getVisibility();

                if (currentVisibility != oldVisibility) {
                    if (currentVisibility == View.VISIBLE) {
                        fabMenu.setVisibility(View.INVISIBLE);
                    } else {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                fabMenu.setVisibility(View.VISIBLE);
                            }
                        }, 150);
                    }

                    oldVisibility = currentVisibility;
                }
            }

        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                refreshLists();
                return true;
            }
        });
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            searchView.setQuery(intent.getStringExtra(SearchManager.QUERY), true);
        }
        super.onNewIntent(intent);
    }

    /**
     * Handles the Results of started Sub Activities (Created Logjob, Edited Logjob)
     *
     * @param requestCode int to distinguish between the different Sub Activities
     * @param resultCode  int Return Code
     * @param data        Intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == create_logjob_cmd) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                //not need because of db.synchronisation in createActivity

                DBLogjob createdLogjob = (DBLogjob) data.getExtras().getSerializable(CREATED_LOGJOB);
                adapter.add(createdLogjob);
            }
            listView.scrollToPosition(0);
        } else if (requestCode == server_settings) {
            // Create new Instance with new URL and credentials
            db = PhoneTrackSQLiteOpenHelper.getInstance(this);
            if (db.getPhonetrackServerSyncHelper().isSyncPossible()) {
                this.updateUsernameInDrawer();
                adapter.removeAll();
                //synchronize();
            } else {
                if (SessionServerSyncHelper.isConfigured(getApplicationContext())) {
                    Toast.makeText(getApplicationContext(), getString(R.string.error_sync, getString(PhoneTrackClientUtil.LoginStatus.NO_NETWORK.str)), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void updateUsernameInDrawer() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String username;
        String url;
        if (preferences.getBoolean(SettingsActivity.SETTINGS_USE_SSO, false)) {
            username = preferences.getString(SettingsActivity.SETTINGS_SSO_USERNAME, SettingsActivity.DEFAULT_SETTINGS);
            url = preferences.getString(SettingsActivity.SETTINGS_SSO_URL, SettingsActivity.DEFAULT_SETTINGS).replace("https://", "").replace("http://", "");
        }
        else{
            username = preferences.getString(SettingsActivity.SETTINGS_USERNAME, SettingsActivity.DEFAULT_SETTINGS);
            url = preferences.getString(SettingsActivity.SETTINGS_URL, SettingsActivity.DEFAULT_SETTINGS).replace("https://", "").replace("http://", "");
        }
        if(!SettingsActivity.DEFAULT_SETTINGS.equals(username) && !SettingsActivity.DEFAULT_SETTINGS.equals(url)) {
            this.account.setText(username + "@" + url.substring(0, url.length() - 1));
        }
        else {
            this.account.setText(getString(R.string.drawer_connect_hint));
        }
    }

    @Override
    public void onLogjobClick(int position, View v) {
        if (mActionMode != null) {
            if (!adapter.select(position)) {
                v.setSelected(false);
                adapter.deselect(position);
            } else {
                v.setSelected(true);
            }
            int size = adapter.getSelected().size();
            mActionMode.setTitle(String.valueOf(getResources().getQuantityString(R.plurals.ab_selected, size, size)));
            int checkedItemCount = adapter.getSelected().size();
            boolean hasCheckedItems = checkedItemCount > 0;

            if (hasCheckedItems && mActionMode == null) {
                // TODO differ if one or more items are selected
                // if (checkedItemCount == 1) {
                // mActionMode = startActionMode(new
                // SingleSelectedActionModeCallback());
                // } else {
                // there are some selected items, start the actionMode
                mActionMode = startSupportActionMode(new MultiSelectedActionModeCallback());
                // }
            } else if (!hasCheckedItems && mActionMode != null) {
                // there no selected items, finish the actionMode
                mActionMode.finish();
            }
        } else {
            DBLogjob logjob = (DBLogjob) adapter.getItem(position);
            Intent intent;
            if (logjob.getToken().isEmpty() && logjob.getDeviceName().isEmpty()) {
                intent = new Intent(getApplicationContext(), EditCustomLogjobActivity.class);
            }
            else {
                intent = new Intent(getApplicationContext(), EditPhoneTrackLogjobActivity.class);
            }
            intent.putExtra(EditLogjobActivity.PARAM_LOGJOB_ID, logjob.getId());
            startActivityForResult(intent, show_single_logjob_cmd);

        }
    }

    @Override
    public void onLogjobEnabledClick(int position, View view) {
        DBLogjob logjob = (DBLogjob) adapter.getItem(position);
        if (logjob != null) {
            PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(view.getContext());
            db.toggleEnabled(logjob, syncCallBack);
            adapter.notifyItemChanged(position);
            refreshLists();

            notifyLoggerService(logjob.getId());
        }
    }

    @Override
    public void onLogjobMapButtonClick(long sessionId) {
        Intent mapIntent = new Intent(getApplicationContext(), MapActivity.class);
        mapIntent.putExtra(MapActivity.PARAM_SESSIONID, sessionId);
        startActivityForResult(mapIntent, map);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(SettingsActivity.SETTINGS_LAST_SELECTED_SESSION_ID, sessionId);
        editor.apply();
    }

    @Override
    public void onLogjobInfoButtonClick(int position, View view) {
        DBLogjob logjobItem = (DBLogjob) adapter.getItem(position);
        if (logjobItem != null) {
            DBLogjob logjob = db.getLogjob(logjobItem.getId());
            long ljId = logjob.getId();
            PhoneTrackSQLiteOpenHelper db = PhoneTrackSQLiteOpenHelper.getInstance(view.getContext());
            long tsNow = new Date().getTime() / 1000;
            long tsLastLoc = db.getLastLocTimestamp(ljId);
            long diffLastLoc = tsNow - tsLastLoc;
            long tsLastSync = db.getLastSyncTimestamp(ljId);
            long diffLastSync = tsNow - tsLastSync;
            SyncError lastSyncErr = db.getLastSyncError(ljId);
            long diffLastSyncErr = tsNow - lastSyncErr.getTimestamp();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

            if (LoggerService.DEBUG) { Log.d(TAG, "[LAST " + tsLastLoc + " "+tsLastSync+ "]"); }

            List<DBLogjobLocation> cRLocations = db.getCurrentRunLocationsOfLogjob(ljId);
            double totDistance = 0.0;
            long duration = 0;
            if (cRLocations.size() > 1) {
                // distance
                DBLogjobLocation loc;
                DBLogjobLocation prevLoc = cRLocations.get(0);
                int i = 1;
                while (i < cRLocations.size()) {
                    loc = cRLocations.get(i);
                    totDistance += SupportUtil.distance(
                            prevLoc.getLat(), loc.getLat(),
                            prevLoc.getLon(), loc.getLon(),
                            prevLoc.getAltitude(), loc.getAltitude()
                    );
                    prevLoc = loc;
                    i++;
                }
                // duration
                long tFirst = cRLocations.get(0).getTimestamp();
                long tLast = cRLocations.get(cRLocations.size()-1).getTimestamp();
                duration = tLast - tFirst;
            }

            String nbsyncText = view.getContext().getString(R.string.logjob_info_nbsync, logjob.getNbSync());
            String nbnotsyncText = view.getContext().getString(R.string.logjob_info_nbnotsync, db.getLogjobLocationNotSyncedCount(logjob.getId()));
            String lastLocText = "";
            String lastSyncText = "";
            String lastSyncErrText = "";

            View iView = LayoutInflater.from(this).inflate(R.layout.items_infodialog, null);
            TextView tv = iView.findViewById(R.id.infoNbsyncText);
            tv.setText(nbsyncText);
            TextView tv2 = iView.findViewById(R.id.infoNbnotsyncText);
            tv2.setText(nbnotsyncText);

            if (cRLocations.size() > 0) {
                String nbPointsText = view.getContext().getString(R.string.logjob_info_nbpoints, cRLocations.size());

                TextView tv3 = iView.findViewById(R.id.infoNbPointsText);
                tv3.setText(nbPointsText);
            }
            else {
                iView.findViewById(R.id.infoNbPointsLayout).setVisibility(View.GONE);
            }
            if (totDistance != 0.0) {
                String totDistanceText = view.getContext().getString(R.string.logjob_info_distance, totDistance);

                TextView tv3 = iView.findViewById(R.id.infoDistanceText);
                tv3.setText(totDistanceText);
            }
            else {
                iView.findViewById(R.id.infoDistanceLayout).setVisibility(View.GONE);
            }
            if (duration != 0) {
                String formattedDuration = SupportUtil.formatDuration(duration, view.getContext());
                String durationText = view.getContext().getString(R.string.logjob_info_duration, formattedDuration);

                TextView tv3 = iView.findViewById(R.id.infoDurationText);
                tv3.setText(durationText);
            }
            else {
                iView.findViewById(R.id.infoDurationLayout).setVisibility(View.GONE);
            }
            if (tsLastLoc != 0) {
                Date d = new Date(tsLastLoc*1000);
                String diffLastLocString = SupportUtil.formatDuration(diffLastLoc, view.getContext());
                lastLocText = view.getContext().getString(R.string.logjob_info_lastloc, diffLastLocString, sdf.format(d));

                TextView tv3 = iView.findViewById(R.id.infoLastLocText);
                tv3.setText(lastLocText);
            }
            else {
                iView.findViewById(R.id.infoLastLocLayout).setVisibility(View.GONE);
            }
            if (tsLastSync != 0) {
                Date d = new Date(tsLastSync*1000);
                String diffLastSyncString = SupportUtil.formatDuration(diffLastSync, view.getContext());
                lastSyncText = view.getContext().getString(R.string.logjob_info_lastsync, diffLastSyncString, sdf.format(d));

                TextView tv4 = iView.findViewById(R.id.infoLastSyncText);
                tv4.setText(lastSyncText);
            }
            else {
                iView.findViewById(R.id.infoLastSyncLayout).setVisibility(View.GONE);
            }

            if (lastSyncErr.getTimestamp() != 0) {
                Date d = new Date(lastSyncErr.getTimestamp()*1000);
                String diffLastLocString = SupportUtil.formatDuration(diffLastLoc, view.getContext());
                lastSyncErrText = view.getContext().getString(
                        R.string.logjob_info_lastsync_error,
                        diffLastLocString,
                        sdf.format(d),
                        lastSyncErr.getMessage());

                TextView tv5 = iView.findViewById(R.id.infoLastSyncErrText);
                tv5.setText(lastSyncErrText);
            }
            else {
                iView.findViewById(R.id.infoLastSyncErrLayout).setVisibility(View.GONE);
            }

            AlertDialog.Builder builder;
            builder = new AlertDialog.Builder(new ContextThemeWrapper(view.getContext(), R.style.AppThemeDialog));
            builder.setTitle(view.getContext().getString(R.string.logjob_info_dialog_title, logjob.getTitle()))
                    .setView(iView)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .setIcon(R.drawable.ic_info_outline_grey600_24dp)
                    .show();
        }
    }

    @Override
    public boolean onLogjobLongClick(int position, View v) {
        boolean selected = adapter.select(position);
        if (selected) {
            v.setSelected(true);
            mActionMode = startSupportActionMode(new MultiSelectedActionModeCallback());
            int checkedItemCount = adapter.getSelected().size();
            mActionMode.setTitle(getResources().getQuantityString(R.plurals.ab_selected, checkedItemCount, checkedItemCount));
        }
        return selected;
    }

    @Override
    public void onBackPressed() {
        if (searchView == null || searchView.isIconified()) {
            super.onBackPressed();
        } else {
            searchView.setIconified(true);
        }
    }

    private void synchronize() {
        if (LoggerService.DEBUG) { Log.d(TAG, "[call synchronize()]"); }
        db.getPhonetrackServerSyncHelper().addCallbackPull(syncCallBack);
        db.getPhonetrackServerSyncHelper().scheduleSync(false);
    }

    private void notifyLoggerService(long jobId) {
        Intent intent = new Intent(LogjobsListViewActivity.this, LoggerService.class);
        intent.putExtra(UPDATED_LOGJOBS, true);
        intent.putExtra(UPDATED_LOGJOB_ID, jobId);
        startService(intent);
    }

    /**
     * Handler for the MultiSelect Actions
     */
    private class MultiSelectedActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // inflate contextual menu
            mode.getMenuInflater().inflate(R.menu.menu_list_context_multiple, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        /**
         * @param mode ActionMode - used to close the Action Bar after all work is done.
         * @param item MenuItem - the item in the List that contains the Node
         * @return boolean
         */
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_delete:
                    List<Integer> selection = adapter.getSelected();
                    for (Integer i : selection) {
                        DBLogjob logjob = (DBLogjob) adapter.getItem(i);
                        db.deleteLogjob(logjob.getId());
                        // Not needed because of dbsync
                        //adapter.remove(logjob);
                        notifyLoggerService(logjob.getId());
                    }
                    mode.finish(); // Action picked, so close the CAB
                    //after delete selection has to be cleared
                    searchView.setIconified(true);
                    refreshLists();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            adapter.clearSelection();
            mActionMode = null;
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Display toast message
     * @param text Message
     */
    private void showToast(CharSequence text) {
        showToast(text, Toast.LENGTH_SHORT);
    }

    /**
     * Display toast message
     * @param text Message
     * @param duration Duration
     */
    private void showToast(CharSequence text, int duration) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    /**
     * Register broadcast receiver for synchronization
     * and tracking status updates
     */
    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(LoggerService.BROADCAST_LOCATION_STARTED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_STOPPED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_UPDATED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_GPS_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_NETWORK_DISABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_GPS_ENABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_NETWORK_ENABLED);
        filter.addAction(LoggerService.BROADCAST_LOCATION_PERMISSION_DENIED);
        filter.addAction(WebTrackService.BROADCAST_SYNC_STARTED);
        filter.addAction(WebTrackService.BROADCAST_SYNC_DONE);
        filter.addAction(WebTrackService.BROADCAST_SYNC_FAILED);
        filter.addAction(SessionServerSyncHelper.BROADCAST_SESSIONS_SYNC_FAILED);
        filter.addAction(SessionServerSyncHelper.BROADCAST_SESSIONS_SYNCED);
        filter.addAction(SessionServerSyncHelper.BROADCAST_SSO_TOKEN_MISMATCH);
        registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Broadcast receiver
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LoggerService.DEBUG) { Log.d(TAG, "[broadcast received " + intent + "]"); }
            if (intent == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case LoggerService.BROADCAST_LOCATION_UPDATED:
                    long ljId = intent.getLongExtra(LoggerService.BROADCAST_EXTRA_PARAM, 0);
                    if (LoggerService.DEBUG) { Log.d(TAG, "[broadcast loc updated " + ljId + "]"); }
                    // to update all items
                    //adapter.notifyDataSetChanged();
                    // but we update just the changed one
                    DBLogjob lj;
                    for (int i = 0; i < adapter.getItemCount(); i++) {
                        lj = (DBLogjob) adapter.getItem(i);
                        if (lj.getId() == ljId) {
                            adapter.notifyItemChanged(i);
                            break;
                        }
                    }
                    break;
                case WebTrackService.BROADCAST_SYNC_STARTED:
                    //swipeRefreshLayout.setRefreshing(true);
                    break;
                // when sync is finished (fail or success)
                case WebTrackService.BROADCAST_SYNC_DONE:
                    long ljId2 = intent.getLongExtra(LoggerService.BROADCAST_EXTRA_PARAM, 0);
                    if (ljId2 != 0) {
                        if (LoggerService.DEBUG) {
                            Log.d(TAG, "[broadcast loc synced " + ljId2 + "]");
                        }
                        // to update all items
                        //adapter.notifyDataSetChanged();
                        // but we update just the changed one
                        DBLogjob lj2;
                        for (int i = 0; i < adapter.getItemCount(); i++) {
                            lj2 = (DBLogjob) adapter.getItem(i);
                            if (lj2.getId() == ljId2) {
                                adapter.notifyItemChanged(i);
                                if (LoggerService.DEBUG) {
                                    Log.d(TAG, "[notifyItemChanged " + i + "]");
                                }
                                break;
                            }
                        }
                    }
                    // without parameter : end of sync service
                    else {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    break;
                case (WebTrackService.BROADCAST_SYNC_FAILED): {
                    long ljId3 = intent.getLongExtra(LoggerService.BROADCAST_EXTRA_PARAM, 0);
                    String errorMessage = intent.getStringExtra(LoggerService.BROADCAST_ERROR_MESSAGE);
                    showToast(getString(R.string.uploading_failed) + "\n" + errorMessage, Toast.LENGTH_LONG);
                    break;
                }
                case SessionServerSyncHelper.BROADCAST_SESSIONS_SYNC_FAILED:
                    String errorMessage = intent.getStringExtra(LoggerService.BROADCAST_ERROR_MESSAGE);
                    showToast(errorMessage, Toast.LENGTH_LONG);
                    break;
                case SessionServerSyncHelper.BROADCAST_SESSIONS_SYNCED:
                    showToast(getString(R.string.sessions_sync_success));
                    if (ssoSnackbar != null) {
                        ssoSnackbar.dismiss();
                        ssoSnackbar = null;
                    }
                    break;
                case SessionServerSyncHelper.BROADCAST_SSO_TOKEN_MISMATCH:
                    ssoSnackbar = Snackbar.make(swipeRefreshLayout, R.string.error_token_mismatch, Snackbar.LENGTH_INDEFINITE);
                    ssoSnackbar.show();
                    break;
                case LoggerService.BROADCAST_LOCATION_STARTED:
                    showToast(getString(R.string.tracking_started));
                    break;
                case LoggerService.BROADCAST_LOCATION_STOPPED:
                    showToast(getString(R.string.tracking_stopped));
                    break;
                case LoggerService.BROADCAST_LOCATION_GPS_DISABLED:
                    showToast(getString(R.string.gps_disabled_warning), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_NETWORK_DISABLED:
                    showToast(getString(R.string.net_disabled_warning), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_DISABLED:
                    showToast(getString(R.string.location_disabled), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_NETWORK_ENABLED:
                    showToast(getString(R.string.using_network), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_GPS_ENABLED:
                    showToast(getString(R.string.using_gps), Toast.LENGTH_LONG);
                    break;
                case LoggerService.BROADCAST_LOCATION_PERMISSION_DENIED:
                    showToast(getString(R.string.location_permission_denied), Toast.LENGTH_LONG);
                    ActivityCompat.requestPermissions(LogjobsListViewActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_LOCATION);
                    break;
            }
        }
    };

    private ICallback createSessionCallBack = new ICallback() {
        @Override
        public void onFinish() {
        }

        public void onFinish(String sessionId, String message) {
            if (sessionId != null) {
                Snackbar.make(swipeRefreshLayout, R.string.action_session_created, Snackbar.LENGTH_LONG).show();
                synchronize();
            }
            else {
                showToast(getString(R.string.error_create_session_helper, message), Toast.LENGTH_LONG);
            }
        }

        @Override
        public void onScheduled() {
        }
    };
}