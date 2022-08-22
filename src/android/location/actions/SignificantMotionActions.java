package edu.berkeley.eecs.emission.cordova.tracker.location.actions;
// Auto fixed by post-plugin hook 
import edu.berkeley.eecs.emission.R;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import edu.berkeley.eecs.emission.cordova.tracker.ConfigManager;
import edu.berkeley.eecs.emission.cordova.tracker.location.TripDiaryStateMachineForegroundService;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;

import edu.berkeley.eecs.emission.cordova.usercache.UserCache;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;


import android.hardware.SensorManager;
import android.hardware.Sensor;
import android.hardware.TriggerEventListener;
import android.hardware.TriggerEvent;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import edu.berkeley.eecs.emission.cordova.tracker.location.GeofenceExitIntentService;

/**
 * Created by shankari on 08/18/2022.
 */
public class SignificantMotionActions {
    private static final int SIG_MOTION_IN_NUMBERS = 744668466; // GEOFENCE

    private static final String TAG = "SignificantMotionAction";
    private static final String GEOFENCE_LOC_KEY = "CURR_GEOFENCE_LOCATION";

    private SensorManager mSensorManager;
    private Sensor mSigMotion;
    private TriggerEventListener mListener;

    private Context mCtxt;
    private UserCache uc;
    // Used only when the last location from the manager is null, or invalid and so we have
    // to read a new one. This is a private variable for synchronization
    private Location newLastLocation;

    class GenerateNotification extends TriggerEventListener {
        public void onTrigger(TriggerEvent event) {
            // Do Work.
            NotificationHelper.createNotification(mCtxt, SIG_MOTION_IN_NUMBERS,
                null, new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                    +" detected significant motion");
            Log.d(SignificantMotionActions.this.mCtxt, TAG, "Received sig motion sensor trigger, recreating request");

            if (SignificantMotionActions.this.mSigMotion != null) {
                SignificantMotionActions.this.mSensorManager.requestTriggerSensor(
                    SignificantMotionActions.this.mListener,
                    SignificantMotionActions.this.mSigMotion);
            }
        }
    }

    public SignificantMotionActions(Context ctxt) {
        this.mCtxt = ctxt;
        this.mSensorManager = (SensorManager)ctxt.getSystemService(ctxt.SENSOR_SERVICE);
        this.mSigMotion = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
        this.mListener = new GenerateNotification();
        Log.d(this.mCtxt, TAG, "initialized the motion action with context "+this.mCtxt
            +" sensor manager "+this.mSensorManager+" sig motion "+this.mSigMotion
            +" mListener "+this.mListener);
    }

    /*
     * Actually creates the geofence. We want to create the geofence at the last known location, so
     * we retrieve it from the location services. If this is not null, we call createGeofenceRequest to
     * create the geofence request and register it.
     *
     * see @GeofenceActions.createGeofenceRequest
     */
    public void create() {
        if (this.mSigMotion != null) {
            Log.d(this.mCtxt, TAG, "Created trigger on the sig motion sensor");
            this.mSensorManager.requestTriggerSensor(this.mListener, this.mSigMotion);
        } else {
            Log.d(this.mCtxt, TAG, "Skipped trigger creation on the sig motion sensor since it is null");
        }
    }

    public void remove() {
        if (this.mSigMotion != null) {
            Log.d(this.mCtxt, TAG, "Removed trigger on the sig motion sensor");
        this.mSensorManager.cancelTriggerSensor(this.mListener, this.mSigMotion);
        } else {
            Log.d(this.mCtxt, TAG, "Skipped trigger removal on the sig motion sensor since it is null");
        }
    }
}
