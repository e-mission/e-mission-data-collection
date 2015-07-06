package edu.berkeley.eecs.cfc_tracker.test;

import android.content.Context;
import android.location.Location;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import com.google.android.gms.location.DetectedActivity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONObject;

/**
 * Created by shankari on 6/28/15.
 */
public class WrapperTest extends AndroidTestCase {
    private Context testCtxt;

    // We want to store a long-term log for
    public WrapperTest() {
        super();
    }

    protected void setUp() throws Exception {
		/*
		 * Don't need to populate with test data here, can do that in the test cases.
		 */
        super.setUp();
        RenamingDelegatingContext context =
                new RenamingDelegatingContext(getContext(), "test_");
        testCtxt = context;
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testLocationSerDeser() throws Exception {
        Location testLoc = new Location("TEST");
        testLoc.setAccuracy(25.25f);
        testLoc.setAltitude(26.26);
        testLoc.setBearing(27);
        testLoc.setElapsedRealtimeNanos(28l);
        testLoc.setLatitude(29.29);
        testLoc.setLongitude(30.30);
        testLoc.setSpeed(31.31f);
        testLoc.setTime(32l);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String locJson = gson.toJson(testLoc);
        System.out.println("Serialized JSON = " + locJson);
        JSONObject parsedJSON = new JSONObject(locJson);
        assertEquals(parsedJSON.getDouble("mAccuracy"), 25.25);
        assertEquals(parsedJSON.getLong("mElapsedRealtimeNanos"), 28);
        assertEquals(parsedJSON.getDouble("mLatitude"), 29.29);
        assertEquals(parsedJSON.getDouble("mLongitude"), 30.30);
        Location parsedLoc = new Gson().fromJson(locJson, Location.class);
        assertEquals(parsedLoc.getAccuracy(), 25.25f);
        assertEquals(parsedLoc.getElapsedRealtimeNanos(), 28l);
        assertEquals(parsedLoc.getLatitude(), 29.29);
        assertEquals(parsedLoc.getLongitude(), 30.30);
    }

    public void testDetectedActivitySerDeser() throws Exception {
        DetectedActivity testDetectedBicycle = new DetectedActivity(DetectedActivity.ON_BICYCLE, 80);
        DetectedActivity testDetectedWalking = new DetectedActivity(DetectedActivity.WALKING, 90);
        DetectedActivity testDetectedInVehicle = new DetectedActivity(DetectedActivity.IN_VEHICLE, 70);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String bicycleJson = gson.toJson(testDetectedBicycle);
        System.out.println("Serialized JSON for bicycle = " + gson.toJson(testDetectedBicycle));
        System.out.println("Serialized JSON for walking = " + gson.toJson(testDetectedWalking));
        System.out.println("Serialized JSON for vehicle = " + gson.toJson(testDetectedInVehicle));

        DetectedActivity parsedBicycle = new Gson().fromJson(bicycleJson, DetectedActivity.class);
        assertEquals(parsedBicycle.getType(), DetectedActivity.ON_BICYCLE);
        assertEquals(parsedBicycle.getConfidence(), 80);
    }
}
