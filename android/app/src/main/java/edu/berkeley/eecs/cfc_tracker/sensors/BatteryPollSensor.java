package edu.berkeley.eecs.cfc_tracker.sensors;

import android.content.Context;

import edu.berkeley.eecs.cfc_tracker.usercache.UserCache;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCacheFactory;

/**
 * Created by shankari on 7/8/15.
 */
public class BatteryPollSensor implements PollSensor {
    public void getAndSaveValue(Context ctxt) {
        float currLevel = BatteryUtils.getBatteryLevel(ctxt);
        UserCacheFactory.getUserCache(ctxt).putMessage("background/battery", currLevel);
    }
}
