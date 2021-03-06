package com.awolity.trakr.view.main;

import android.app.Dialog;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.awolity.trakr.BuildConfig;
import com.awolity.trakr.R;
import com.awolity.trakr.view.model.MapPoint;
import com.awolity.trakr.view.detail.TrackDetailActivity;
import com.awolity.trakr.view.list.TrackListActivity;
import com.awolity.trakr.view.main.bottom.BottomSheetBaseFragment;
import com.awolity.trakr.view.main.bottom.BottomSheetChartsFragment;
import com.awolity.trakr.view.main.bottom.BottomSheetFragmentPagerAdapter;
import com.awolity.trakr.view.main.bottom.BottomSheetPointFragment;
import com.awolity.trakr.view.main.bottom.BottomSheetTrackFragment;
import com.awolity.trakr.view.explore.ExploreActivity;
import com.awolity.trakr.view.settings.SettingsActivity;
import com.awolity.trakr.utils.Constants;
import com.awolity.trakr.utils.Utility;
import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.List;

public class MainActivity extends AppCompatActivity
        implements OnMapReadyCallback,
        GoogleMap.OnCameraMoveListener,
        TrackRecorderServiceManager.TrackRecorderServiceManagerListener {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int REQUEST_CODE_CHANGE_LOCATION_SETTINGS = 333;
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final float ZOOM_LEVEL_INITIAL = 15;

    private FirebaseAnalytics firebaseAnalytics;

    private GoogleMap googleMap;
    private BottomSheetPointFragment pointFragment;
    private BottomSheetTrackFragment trackFragment;
    private BottomSheetChartsFragment chartsFragment;
    private FloatingActionButton fab;

    private MainActivityViewModel mainActivityViewModel;
    private TrackRecorderServiceManager serviceManager;
    private MainActivityStatus status;
    private long trackId = Constants.NO_LAST_RECORDED_TRACK;

    private PolylineManager polylineManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // MyLog.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
        status = new MainActivityStatus();
        fab = findViewById(R.id.fab);

        if (savedInstanceState != null) {
            status.setCameraPosition(savedInstanceState.getParcelable(KEY_CAMERA_POSITION));
        }

        setupBottomSheet(savedInstanceState);
        MainActivityUtils.checkLocationPermission(this, PERMISSION_REQUEST_CODE);

        setupFab();
        setupMapFragment();
        setupViewModel();
    }

    @SuppressWarnings("ConstantConditions")
    private void setupBottomSheet(Bundle savedInstanceState) {
        // MyLog.d(TAG, "setupBottomSheet");
        LinearLayout llBottomSheet = findViewById(R.id.ll_bottom_sheet);
        final BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(llBottomSheet);
        BottomSheetFragmentPagerAdapter adapter =
                new BottomSheetFragmentPagerAdapter(getSupportFragmentManager());

        if (savedInstanceState == null) {
            pointFragment = BottomSheetPointFragment.newInstance(getString(R.string.bottom_sheet_label_point));
            trackFragment = BottomSheetTrackFragment.newInstance(getString(R.string.bottom_sheet_label_track));
            chartsFragment = BottomSheetChartsFragment.newInstance(getString(R.string.bottom_sheet_label_charts));
        } else {
            pointFragment = (BottomSheetPointFragment) getSupportFragmentManager()
                    .getFragment(savedInstanceState, BottomSheetPointFragment.class.getName());
            if (pointFragment == null) {
                pointFragment = BottomSheetPointFragment.newInstance(getString(R.string.bottom_sheet_label_point));
            }
            pointFragment.setTitle(getString(R.string.bottom_sheet_label_point));

            trackFragment = (BottomSheetTrackFragment) getSupportFragmentManager()
                    .getFragment(savedInstanceState, BottomSheetTrackFragment.class.getName());
            if (trackFragment == null) {
                trackFragment = BottomSheetTrackFragment.newInstance(getString(R.string.bottom_sheet_label_track));
            }
            trackFragment.setTitle(getString(R.string.bottom_sheet_label_track));

            chartsFragment = (BottomSheetChartsFragment) getSupportFragmentManager()
                    .getFragment(savedInstanceState, BottomSheetChartsFragment.class.getName());
            if (chartsFragment == null) {
                chartsFragment = BottomSheetChartsFragment.newInstance(getString(R.string.bottom_sheet_label_charts));
            }
            chartsFragment.setTitle(getString(R.string.bottom_sheet_label_charts));
        }
        adapter.setFragments(new BottomSheetBaseFragment[]{pointFragment, trackFragment, chartsFragment});
        ViewPager viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(2);

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.getTabAt(0).setIcon(R.drawable.ic_point_selected);
        tabLayout.getTabAt(1).setIcon(R.drawable.ic_track);
        tabLayout.getTabAt(2).setIcon(R.drawable.ic_charts);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                switch (tab.getPosition()) {
                    case 0:
                        tab.setIcon(getResources().getDrawable(R.drawable.ic_point_selected));
                        tab.select();
                        break;
                    case 1:
                        tab.setIcon(getResources().getDrawable(R.drawable.ic_track_selected));
                        break;
                    case 2:
                        tab.setIcon(getResources().getDrawable(R.drawable.ic_charts_selected));
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        tab.setIcon(getResources().getDrawable(R.drawable.ic_point));
                        break;
                    case 1:
                        tab.setIcon(getResources().getDrawable(R.drawable.ic_track));
                        break;
                    case 2:
                        tab.setIcon(getResources().getDrawable(R.drawable.ic_charts));
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        BottomSheetBehavior.BottomSheetCallback bottomSheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    isBottomSheetUp = true;
                    if (googleMap != null) {
                        MainActivityUtils.scrollMapUp(MainActivity.this, googleMap);
                    }
                } else if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    isBottomSheetUp = false;
                    if (googleMap != null) {
                        MainActivityUtils.scrollMapDown(MainActivity.this, googleMap);
                    }
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        };
        bottomSheetBehavior.setBottomSheetCallback(bottomSheetCallback);
    }

    private boolean isBottomSheetUp = false;

    private void setupFab() {
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> {
            if (!MainActivityUtils.isLocationPermissionEnabled(MainActivity.this)) {
                MainActivityUtils.checkLocationPermission(MainActivity.this,
                        PERMISSION_REQUEST_CODE);
                return;
            }

            if (status.isRecording()) {
                showStopDiag();
            } else {
                mainActivityViewModel.isLocationSettingsGood(
                        (isSettingsGood, e) -> {
                            if (isSettingsGood) {
                                serviceManager.startService();
                                MainActivityUtils.logStartRecordingEvent(firebaseAnalytics);
                            } else {
                                isAirplaneErrorShown = false;
                                showLocationSettingsDialog(e);
                            }
                        });
            }
        });
    }


    private void setupMapFragment() {
        // MyLog.d(TAG, "setupMapFragment");
        if (MainActivityUtils.checkPlayServices(this)) {
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.mapFragment);
            //noinspection ConstantConditions
            mapFragment.getMapAsync(this);
        } else {
            FrameLayout mapOverlay = findViewById(R.id.map_overlay);
            mapOverlay.setVisibility(View.INVISIBLE);
        }
    }

    private void setupViewModel() {
        mainActivityViewModel = ViewModelProviders.of(this).get(MainActivityViewModel.class);
    }

    private void showLocationSettingsDialog(Exception e) {
        try {
            ResolvableApiException resolvable = (ResolvableApiException) e;
            resolvable.startResolutionForResult(MainActivity.this,
                    REQUEST_CODE_CHANGE_LOCATION_SETTINGS);
        } catch (IntentSender.SendIntentException sendEx) {
            // Ignore the error.
        } catch (ClassCastException castEx) {
            if (!isAirplaneErrorShown) {
                showAirplaneModeErrorDialog();
            }
        }
    }

    private void showAirplaneModeErrorDialog() {
        isAirplaneErrorShown = true;
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        // set title
        alertDialogBuilder.setTitle(R.string.activity_main_dialog_airplane_title);
        // set dialog message
        alertDialogBuilder
                .setMessage(R.string.activity_main_dialog_airplane_message)
                .setCancelable(true)
                .setIcon(getDrawable(R.drawable.ic_warning))
                .setPositiveButton(getString(android.R.string.ok), (dialog, which) -> determineLocationSettings());
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void setupTrackRecorderService() {
        // MyLog.d(TAG, "setupTrackRecorderService");
        serviceManager = new TrackRecorderServiceManager(this);
        if (TrackRecorderServiceManager.isServiceRunning(this)) {
            // MyLog.d(TAG, "setupTrackRecorderService - service is running");
            status.setContinueRecording();
            long trackId = mainActivityViewModel.getLastRecordedTrackId();
            if (trackId != Constants.NO_LAST_RECORDED_TRACK) {
                polylineManager = new PolylineManager(this);
                setupTrackViewModel(trackId);
                this.trackId = trackId;
                trackFragment.startTrackDataUpdate(trackId);
                chartsFragment.startTrackDataUpdate(trackId);
                status.setRecording(true);
                MainActivityUtils.startFabAnimation(fab);
            }
        } else {
            mainActivityViewModel.clearLastRecordedTrackId();
        }
    }

    private void startLocationUpdates() {
        // MyLog.d(TAG, "startLocationUpdates");
        if (Utility.isLocationEnabled(this)) {
            mainActivityViewModel.getLocation().observe(MainActivity.this, location -> {
                if (location != null) {
                    updateMap(location);
                }
            });
        }
    }

    private void updateMap(Location location) {
        // MyLog.d(TAG, "updateMap");
        if (!status.isThereACameraPosition()) {
            // it is first start, so centered
            if (status.isRecording()) {
                centerTrackOnMap();
            } else {
                updateCamera(CameraPosition.fromLatLngZoom(new LatLng(location.getLatitude(),
                        location.getLongitude()), ZOOM_LEVEL_INITIAL));
            }
        }
    }

    private void stopLocationUpdates() {
        mainActivityViewModel.stopLocation();
    }

    private void updateCamera(CameraPosition cameraPosition) {
        if (!cameraPosition.equals(status.getCameraPosition())) {
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            status.setCameraPosition(googleMap.getCameraPosition());
        }
    }

    private void updateCamera(final LatLngBounds bounds) {
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            status.setCameraPosition(googleMap.getCameraPosition());
        }, 500);
    }

    private void setupTrackViewModel(final long trackId) {
        // MyLog.d(TAG, "setupTrackViewModel");
        mainActivityViewModel = ViewModelProviders.of(this).get(MainActivityViewModel.class);
        mainActivityViewModel.reset();
        mainActivityViewModel.init(trackId);
        if (status.isContinueRecording()) {
            mainActivityViewModel.getMapPoints().observe(this, mapPointListobserver);
        }
        mainActivityViewModel.getActualMapPoint().observe(this, actualMapPointObserver);
    }

    private final Observer<List<MapPoint>> mapPointListobserver
            = new Observer<List<MapPoint>>() {
        @Override
        public void onChanged(@Nullable List<MapPoint> mapPoints) {
            if (mapPoints != null
                    && mapPoints.size() != 0
                    && polylineManager != null) {
                numOfTrackpoints = mapPoints.size();
                polylineManager.drawPolyline(googleMap,
                        MainActivityUtils.transformTrackpointsToLatLngs(mapPoints));
            }
            mainActivityViewModel.getMapPoints().removeObserver(this);
        }
    };

    private int numOfTrackpoints = 0;

    private final Observer<MapPoint> actualMapPointObserver
            = new Observer<MapPoint>() {
        @Override
        public void onChanged(@Nullable MapPoint mapPoint) {
            if (mapPoint != null) {
                if (polylineManager != null) {
                    polylineManager.continuePolyline(googleMap,
                            new LatLng(mapPoint.getLatitude(),
                                    mapPoint.getLongitude()));
                    numOfTrackpoints++;
                }
            }
        }
    };

    private void centerTrackOnMap() {
        mainActivityViewModel.getTrackData().observe(this, track -> {
            // if we already have a cameraposition, than it centering is unnecessary
            if (status.getCameraPosition() != null) {
                return;
            }
            if (track != null) {
                if (track.getNorthestPoint() != 0 || track.getSouthestPoint() != 0
                        || track.getWesternPoint() != 0 || track.getEasternPoint() != 0) {
                    LatLngBounds bounds = new LatLngBounds(
                            new LatLng(track.getSouthestPoint(), track.getWesternPoint()),
                            new LatLng(track.getNorthestPoint(), track.getEasternPoint()));
                    if (googleMap != null) {
                        updateCamera(bounds);
                        status.setCameraPosition(googleMap.getCameraPosition());
                    }
                }
            }
        });
    }

    private void determineLocationSettings() {
        mainActivityViewModel.isLocationSettingsGood((isSettingsGood, e) -> {
            if (isSettingsGood) {
                setupTrackRecorderService();
            } else {
                showLocationSettingsDialog(e);
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // MyLog.d(TAG, "onNewIntent");
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();
        // MyLog.d(TAG, "onResume");
        startLocationUpdates();
        if (status.isRecording() && !status.isThereACameraPosition()) {
            centerTrackOnMap();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // MyLog.d(TAG, "onPause");
        stopLocationUpdates();
    }

    private boolean isAirplaneErrorShown = false;

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // MyLog.d(TAG, "onWindowFocusChanged: " + hasFocus);
        if (hasFocus) {
            determineLocationSettings();
        }
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        this.googleMap = googleMap;
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        try {
            googleMap.setMyLocationEnabled(true);
        } catch (SecurityException e) {
            if (!BuildConfig.DEBUG) Crashlytics.logException(e);
        }

        if (status.isThereACameraPosition()) {
            updateCamera(status.getCameraPosition());
        }

        googleMap.setOnMyLocationButtonClickListener(() -> {
            if (isBottomSheetUp) {
                Location myLocation = mainActivityViewModel.getLocation().getValue();
                if (myLocation != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLng(
                            new LatLng(myLocation.getLatitude(), myLocation.getLongitude())),
                            new GoogleMap.CancelableCallback() {
                                @Override
                                public void onFinish() {
                                    MainActivityUtils.scrollMapUp(MainActivity.this, googleMap);
                                }

                                @Override
                                public void onCancel() {

                                }
                            });
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay!
                if (googleMap != null) {
                    try {
                        googleMap.setMyLocationEnabled(true);
                    } catch (SecurityException e) {
                        if (!BuildConfig.DEBUG) Crashlytics.logException(e);
                        // MyLog.e(TAG, e.getLocalizedMessage());
                    }
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_list_tracks) {
            startActivity(TrackListActivity.getStarterIntent(this));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(SettingsActivity.getStarterIntent(this));
        } else if (id == R.id.action_map_tracks) {
            startActivity(new Intent(this, ExploreActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (googleMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, googleMap.getCameraPosition());

            getSupportFragmentManager().putFragment(
                    outState, BottomSheetPointFragment.class.getName(), pointFragment);
            getSupportFragmentManager().putFragment(
                    outState, BottomSheetTrackFragment.class.getName(), trackFragment);
            getSupportFragmentManager().putFragment(
                    outState, BottomSheetChartsFragment.class.getName(), chartsFragment);

            super.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onCameraMove() {
        status.setCameraPosition(googleMap.getCameraPosition());
    }

    @Override
    public void onServiceStarted(long trackId) {
        // MyLog.d(TAG, "onServiceStarted");
        MainActivityUtils.startFabAnimation(fab);
        this.trackId = trackId;
        setupTrackViewModel(trackId);
        trackFragment.startTrackDataUpdate(trackId);
        chartsFragment.startTrackDataUpdate(trackId);
        status.setRecording(true);
        polylineManager = new PolylineManager(this);
    }

    @Override
    public void onServiceStopped() {
        // MyLog.d(TAG, "onServiceStopped");
        MainActivityUtils.stopFabAnimation(fab);
        trackFragment.stopTrackDataUpdate();
        chartsFragment.stopTrackDataUpdate();
        status.setRecording(false);
        polylineManager.clearPolyline(googleMap);
        polylineManager = null;
        mainActivityViewModel.clearLastRecordedTrackId();
        mainActivityViewModel.finishRecording();
        if (numOfTrackpoints > 1) {
            Intent intent = TrackDetailActivity.getStarterIntent(
                    MainActivity.this, trackId);
            startActivity(intent);
        }
        trackId = Constants.NO_LAST_RECORDED_TRACK;
        numOfTrackpoints = 0;
    }

    private void showStopDiag() {
        final View dialogView = View.inflate(this, R.layout.activity_main_dialog_stop_recording,
                null);
        final Dialog dialog = new Dialog(this, R.style.AppTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);

        Button continueBtn = dialog.findViewById(R.id.btn_continue);
        Button stopBtn = dialog.findViewById(R.id.btn_stop);

        continueBtn.setOnClickListener(v -> MainActivityUtils.revealShow(fab, dialogView, false, dialog));

        stopBtn.setOnClickListener(v -> {
            MainActivityUtils.revealShow(fab, dialogView, false, dialog);
            serviceManager.stopService();
            MainActivityUtils.logStopRecordingEvent(firebaseAnalytics);
        });

        dialog.setOnShowListener(dialogInterface -> MainActivityUtils.revealShow(fab, dialogView, true, null));

        dialog.setOnKeyListener((dialogInterface, i, keyEvent) -> {
            if (i == KeyEvent.KEYCODE_BACK) {
                MainActivityUtils.revealShow(fab, dialogView, false, dialog);
                return true;
            }
            return false;
        });
        //noinspection ConstantConditions
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.show();
    }
}
