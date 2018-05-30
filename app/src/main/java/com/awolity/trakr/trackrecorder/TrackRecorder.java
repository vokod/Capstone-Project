package com.awolity.trakr.trackrecorder;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;

import com.awolity.trakr.R;
import com.awolity.trakr.data.entity.TrackEntity;
import com.awolity.trakr.data.entity.TrackpointEntity;
import com.awolity.trakr.di.TrakrApplication;
import com.awolity.trakr.location.LocationManager;
import com.awolity.trakr.repository.Repository;
import com.awolity.trakr.utils.MyLog;
import com.awolity.trakr.utils.StringUtils;
import com.google.android.gms.location.LocationRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

import javax.inject.Inject;

public class TrackRecorder implements LocationManager.LocationManagerCallback {

    public static final String TRACKID_BROADCAST_NAME = "com.awolity.trakr.trackrecorder.TrackRecorder.trackIdBroadcast";
    public static final String EXTRA_TRACK_ID = "extra_track_id";
    private static final String TAG = TrackRecorder.class.getSimpleName();
    private TrackEntity track;
    private long trackId;
    private LocationManager locationManager;
    private Handler handler;
    private final Runnable uiUpdater;
    private TrackRecorderStatus status;

    @SuppressWarnings("WeakerAccess")
    @Inject
    Repository repository;

    @SuppressWarnings("WeakerAccess")
    @Inject
    Context context;

    @SuppressWarnings("WeakerAccess")
    @Inject
    Executor discIoExecutor;

    TrackRecorder() {
        // MyLog.d(TAG, "TrackRecorder");
        status = new TrackRecorderStatus();
        TrakrApplication.getInstance().getAppComponent().inject(this);
        getPreferencesToStatus(context);
        locationManager = new LocationManager(
                status.getTrackingInterval(),
                status.getTrackingInterval() / 2,
                status.getTrackingAccuracy());

        uiUpdater = new Runnable() {
            @Override
            public void run() {
                updateNotification(context, track);
                handler.postDelayed(uiUpdater, 1000);
            }
        };
    }

    private void getPreferencesToStatus(Context context) {
        status.setupPreferences(context);
    }

    void startRecording() {
        MyLog.d(TAG, "startRecording");

        handler = new Handler();

        discIoExecutor.execute(new Runnable() {
            @Override
            public void run() {
                track = getBaseTrack();
                trackId = repository.saveTrackSync(track);
                track.setTrackId(trackId);

                sendTrackIdBroadcast(context, trackId);

                if (locationManager.isLocationEnabled()) {
                    locationManager.isLocationSettingsGood(new LocationManager.LocationSettingsCallback() {
                        @Override
                        public void onLocationSettingsDetermined(boolean isSettingsGood) {
                            if (isSettingsGood) {
                                // here starts the whole recording
                                locationManager.start(TrackRecorder.this);
                                uiUpdater.run();
                            } else {
                                status.setEverythingGoodForRecording(false);
                            }
                        }
                    });
                } else {
                    status.setEverythingGoodForRecording(false);
                }
                if (!status.isEverythingGoodForRecording()) {
                    // TODO: throw something
                }
            }
        });
    }

    private TrackEntity getBaseTrack() {
        TrackEntity track = new TrackEntity();
        track.setStartTime(System.currentTimeMillis());
        track.setTitle(TrackEntity.getDefaultName(track.getStartTime()));
        track.setMetadata(buildMetadataString());
        return track;
    }

    void stopRecording() {
        locationManager.stop();
        handler.removeCallbacks(uiUpdater);
    }

    @Override
    public void onLocationChanged(Location location) {
        MyLog.d(TAG, "onLocationChanged");

        status.setActualTrackpoint(createTrackPoint(location));

        // if accuracy is below the required level, drop the point
        if (!status.isAccurateEnough()) {
            MyLog.d(TAG, "onLocationChanged - accuracy is below expected - DROPPING");
            return;
        }

        // filtering is only possible if there is a previous data point
        if (status.isThereAPreviousTrackpoint()) {
             MyLog.d(TAG, "onLocationChanged - there IS a previous trackpoint ");

            if (status.isDistanceFarEnoghFromPreviousTrackpoint()) {
                MyLog.d(TAG, "onLocationChanged - the new location is away from previous, SAVING");

                saveTrackAndPointToDb();
                updateNotification(context, track);
            } else {
                MyLog.d(TAG, "onLocationChanged - the new location is exactly the previous, DROPPING");
            }

        } else {
             MyLog.d(TAG, "onLocationChanged - there is NO previous trackpoint ");
            if (status.getActualTrackpoint().getAltitude() != 0) {
                MyLog.d(TAG, "onLocationChanged - there is no previous trackpoint and this one has valid altitude. SAVING");
                saveTrackAndPointToDb();
                updateNotification(context, track);
            } else {
                MyLog.d(TAG, "onLocationChanged - there is no previous trackpoint and this one's altitude is 0. DROPPING");
            }
        }
    }

    private void saveTrackAndPointToDb(){
        saveTrackpointToDb(status.getActualTrackpoint());
        updateTrackData();
        updateTrackInDb(track);
    }

    private void updateTrackData() {

        track.increaseNumOfTrackpoints();

        if (status.isThereAPreviousTrackpoint()) {
            track.increaseElapsedTime(status.getActualTrackpoint().getTime());
            track.increaseDistance(status.getActualTrackpoint().getDistance());
            track.calculateAscentDescent(status.getActualTrackpoint(), status.getPreviousTrackpoint());
            track.calculateAvgSpeed();
            track.checkSetExtremeValues(status.getActualTrackpoint());
        }
    }

    private TrackpointEntity createTrackPoint(Location location) {
        TrackpointEntity tp = TrackpointEntity.fromLocation(location);
        tp.setTrackId(trackId);
        return tp;
    }

    private void saveTrackpointToDb(TrackpointEntity trackpointEntity) {
        // MyLog.d(TAG, "saveTrackpointToDb");
        repository.saveTrackpoint(trackpointEntity);
    }

    private void updateTrackInDb(TrackEntity track) {
        // MyLog.d(TAG, "updateTrackInDb - trackId:" + track.getTrackId());
        repository.updateTrack(track);
    }

    private String buildMetadataString() {
        String priority;

        switch (locationManager.getLocationRequestPriority()) {
            case LocationRequest.PRIORITY_HIGH_ACCURACY:
                priority = "High accuracy";
                break;
            case LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY:
                priority = "Balanced power accuracy";
                break;
            case LocationRequest.PRIORITY_LOW_POWER:
                priority = "Low power mode";
                break;
            case LocationRequest.PRIORITY_NO_POWER:
                priority = "Passive mode";
                break;
            default:
                priority = "unknown";
        }

        String metadataString = "Tracking interval: "
                + locationManager.getLocationRequestInterval() / 1000
                + "s. "
                + "Minimal distance between two points: "
                + status.getTrackingDistance()
                + "m. "
                + "Geolocation accuracy: "
                + priority
                + ". "
                + "Altitude filter parameter: "
                + status.getAltitudeFilterParameter()
                + ". "
                + "Minimum distance between two points: "
                + status.getAccuracyFilterParameter()
                + ". ";
        return metadataString;
    }

    public long getTrackId() {
        return trackId;
    }

    private static void updateNotification(Context context, TrackEntity track) {
        // MyLog.d(TAG, "updateNotification");
        List<String> lines = new ArrayList<>(6);
        // TODO: extract
        lines.add(context.getString(R.string.record_track_notification_title));
        lines.add("Duration: " + StringUtils.getElapsedTimeAsString(System.currentTimeMillis() - track.getStartTime()) + "s");
        lines.add("Distance:" + StringUtils.getDistanceAsThreeCharactersString(track.getDistance()) + "m");
        lines.add("Ascent: " + String.format(Locale.getDefault(), "%.0f", track.getAscent()) + "m");
        lines.add("Descent: " + String.format(Locale.getDefault(), "%.0f", track.getDescent()) + "m");
        lines.add("Avg. speed: " + String.format(Locale.getDefault(), "%.1f", track.getAvgSpeed()) + "km/h");

        TrakrNotification.updateNotification(context, lines);
    }

    private static void sendTrackIdBroadcast(Context context, long trackId) {
        // MyLog.d(TAG, "sendTrackIdBroadcast - trackId:" + trackId);
        Intent intent = new Intent(TRACKID_BROADCAST_NAME);
        intent.putExtra(EXTRA_TRACK_ID, trackId);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }


}