package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

import com.google.android.gms.location.LocationRequest;
import edu.berkeley.eecs.emission.cordova.tracker.Constants;

/**
 * Created by shankari on 10/20/15.
 */

public class LocationTrackingConfig {

    private static final int FIVE_MINUTES_IN_SEC = 5 * 60;

    private boolean is_duty_cycling = true;
    private boolean simulate_user_interaction = false;
    private int accuracy = LocationRequest.PRIORITY_HIGH_ACCURACY;
    private int accuracy_threshold = 200;
    private int filter_distance = -1; // unused
    private int filter_time = Constants.THIRTY_SECONDS;
    private int geofence_radius = Constants.TRIP_EDGE_THRESHOLD;
    private int trip_end_stationary_mins = 5;
    private int android_geofence_responsiveness = 5 * Constants.MILLISECONDS;
    private boolean is_fleet = false;

    // ios fields not set
    private boolean ios_use_visit_notifications_for_detection;
    private boolean ios_use_remote_push_for_sync;

    public boolean isDutyCycling() {
        return this.is_duty_cycling;
    }

    public boolean isSimulateUserInteraction() {
        return this.simulate_user_interaction;
    }

    public int getAccuracy() {
        return this.accuracy;
    }

    public int getAccuracyThreshold() {
        return this.accuracy_threshold;
    }

    public int getFilterDistance() {
        return this.filter_distance;
    }

    public int getFilterTime() {
        return this.filter_time;
    }

    public int getGeofenceRadius() {
        return this.geofence_radius;
    }

    public int getTripEndStationaryMins() {
        return this.trip_end_stationary_mins;
    }

    public int getResponsiveness() {
        return this.android_geofence_responsiveness;
    }

    public boolean isFleet() {
        return this.is_fleet;
    }

    // We don't need any "set" fields because the entire document will be set as a whole
    // using the javascript interface
}
