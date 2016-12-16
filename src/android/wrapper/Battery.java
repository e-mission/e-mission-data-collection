package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

import android.content.Intent;
import android.os.BatteryManager;

/**
 * Created by shankari on 3/27/16.
 */
public class Battery {
    public Battery(Intent batteryChangedIntent) {
        int android_level = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int android_scale = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        battery_level_pct = ((float)android_level/(float)android_scale) * 100;
        battery_status = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        android_health = getHealthString(batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_HEALTH,
                BatteryManager.BATTERY_HEALTH_UNKNOWN));
        android_plugged = getPluggedString(batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED,
                0));
        android_technology = batteryChangedIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        android_temperature = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        android_voltage = batteryChangedIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        ts = ((double)System.currentTimeMillis())/1000;
    }

    private String getHealthString(int healthConstant) {
        switch(healthConstant) {
            case BatteryManager.BATTERY_HEALTH_COLD: return "COLD";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "DEAD";
            case BatteryManager.BATTERY_HEALTH_GOOD: return "GOOD";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "OVER_VOLTAGE";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "OVERHEAT";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "UNSPECIFIED_FAILURE";
        }
        return "UNKNOWN";
    }

    private String getPluggedString(int healthConstant) {
        switch(healthConstant) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "AC";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "WIRELESS";
        }
        return "UNKNOWN";
    }

    public float getBatteryLevelPct() {
        return battery_level_pct;
    }

    private float battery_level_pct;
    private int battery_status;
    private String android_health;
    private String android_plugged;
    private String android_technology;
    private int android_temperature;
    private int android_voltage;
    private double ts;
}
