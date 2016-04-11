package edu.berkeley.eecs.emission.cordova.tracker.sensors;

import android.content.Context;

import edu.berkeley.eecs.emission.R;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;

/**
 * Created by shankari on 7/8/15.
 */
public class PollSensorManager {

    /*
     * We don't have a lot of examples of sensors that are not controlled by the sensor manager
     * and need to be polled. Even the accelerometer can be polled at a set frequency.
     * Maybe the battery can be an example?
     */
    public static PollSensor getSensor(String name) {
        if (name.equals("battery")) {
            return new BatteryPollSensor();
        }
        return null;
    }

    /**
     * Note that some entries might be null if the corresponding names in the list don't have
     * any matches.
     */
    public static PollSensor[] getSensorList(Context ctxt) {
        String[] sensorNameList = null;
                /*
                UserCacheFactory.getUserCache(ctxt).getDocument(R.string.key_usercache_sensor_config,
                        String[].class);
                */
        if (sensorNameList == null) {
            // We return null if the document does not exist.
            return new PollSensor[0];
        }
        PollSensor[] sensorList = new PollSensor[sensorNameList.length];

        for (int i = 0; i < sensorNameList.length; i++) {
            sensorList[i] = getSensor(sensorNameList[i]);
        }
        return sensorList;
    }

    public static void getAndSaveAllValues(Context ctxt) {
        // TODO: Probably shouldn't parse the sensor list every time.
        // Convert this into a singleton instead?
        PollSensor[] sensorList = getSensorList(ctxt);
        for (int i = 0; i < sensorList.length; i++) {
            PollSensor currSensor = sensorList[i];
            if (sensorList[i] != null) {
                sensorList[i].getAndSaveValue(ctxt);
            }
        }
    }
}
