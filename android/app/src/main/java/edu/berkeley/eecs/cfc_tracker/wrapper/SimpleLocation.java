package edu.berkeley.eecs.cfc_tracker.wrapper;

import android.location.Location;

import java.text.SimpleDateFormat;

/**
 * Created by shankari on 8/30/15.
 */
public class SimpleLocation {
    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public long getTs() {
        return ts;
    }

    private double latitude;
    private double longitude;
    private double altitude;

    private long ts;
    private String fmt_time;
    private long elapsedRealtimeNanos;

    private float sensed_speed;
    private float accuracy;
    private float bearing;

    private String provider;

    /*
     * No-arg constructor to use with gson.
     * If this works, consider switching to a custom serializer instead.
     */
    public SimpleLocation() {}

    public SimpleLocation(Location loc) {
        latitude = loc.getLatitude();
        longitude = loc.getLongitude();
        altitude = loc.getAltitude();

        ts = loc.getTime();
        fmt_time = SimpleDateFormat.getDateTimeInstance().format(ts);
        elapsedRealtimeNanos = loc.getElapsedRealtimeNanos();

        sensed_speed = loc.getSpeed();
        accuracy = loc.getAccuracy();
        bearing = loc.getBearing();
    }

    public float distanceTo(SimpleLocation dest) {
        float[] results = new float[1];
        Location.distanceBetween(latitude, longitude, dest.getLatitude(), dest.getLongitude(), results);
        return results[0];
    }
}
