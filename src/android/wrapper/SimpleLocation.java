package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

import android.location.Location;

import java.text.SimpleDateFormat;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

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

    public double getTs() {
        return ts;
    }

    private double latitude;
    private double longitude;
    private double altitude;

    private double ts;
    private String fmt_time;
    private long elapsedRealtimeNanos;

    private float sensed_speed;
    private float accuracy;
    private float bearing;

    private String provider;
    private final String filter = "time";

    /*
     * No-arg constructor to use with gson.
     * If this works, consider switching to a custom serializer instead.
     */
    public SimpleLocation() {}

    public SimpleLocation(Location loc) {
        latitude = loc.getLatitude();
        longitude = loc.getLongitude();
        altitude = loc.getAltitude();

        ts = ((double)loc.getTime())/1000;
        // NOTE: There is no ISO format datetime shortcut on java.
        // This will probably return values that are not in the ISO format.
        // but that's OK because we will fix it on the server
        fmt_time = SimpleDateFormat.getDateTimeInstance().format(loc.getTime());
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

    public static float distanceTo(Location loc, JSONObject destGeoJSON) throws JSONException {
        float[] results = new float[1];
        JSONArray destCoordinates = destGeoJSON.getJSONArray("coordinates");
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
            destCoordinates.getDouble(1), destCoordinates.getDouble(0), results);
        return results[0];
    }
}
