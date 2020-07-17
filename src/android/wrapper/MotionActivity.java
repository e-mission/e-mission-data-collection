package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

import com.google.android.gms.location.DetectedActivity;


public class MotionActivity {
    private double ts;
    private int confidence;
    private int type;

    public MotionActivity(DetectedActivity act) {
        confidence = act.getConfidence();
        type = act.getType();
        ts = ((double)System.currentTimeMillis())/1000;
    }
}
