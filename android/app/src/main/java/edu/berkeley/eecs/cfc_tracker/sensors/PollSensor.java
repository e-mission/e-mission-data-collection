package edu.berkeley.eecs.cfc_tracker.sensors;

import android.content.Context;

/**
 * Created by shankari on 7/8/15.
 */
public interface PollSensor {
    public abstract void getAndSaveValue(Context ctxt);
}
