package edu.berkeley.eecs.emission.cordova.tracker.verification;

import android.Manifest;

public class SensorControlConstants {

    public static String LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;
    public static String BACKGROUND_LOC_PERMISSION = Manifest.permission.ACCESS_BACKGROUND_LOCATION;
    public static String MOTION_ACTIVITY_PERMISSION = Manifest.permission.ACTIVITY_RECOGNITION;
    public static String REQUEST_BATTERY_PERMISSION = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;

    public static final int ENABLE_LOCATION_SETTINGS = 362253738;
    public static final int ENABLE_LOCATION_SETTINGS_MANUAL = 362253736;
    public static final int ENABLE_LOCATION_PERMISSION = 362253737;
    public static final int ENABLE_BACKGROUND_LOC_PERMISSION = 362253739;
    public static final int ENABLE_BOTH_PERMISSION = 362253740;
    public static final int ENABLE_MOTION_ACTIVITY_PERMISSION = 362253741;
    public static final int ENABLE_NOTIFICATIONS = 362253742;
    public static final int REMOVE_UNUSED_APP_RESTRICTIONS = 362253743;
    public static final int OPEN_APP_STATUS_PAGE = 362253744;
    public static final int OPEN_BATTERY_OPTIMIZATION_PAGE = 362253745;
    public static final int IGNORE_BATTERY_OPTIMIZATIONS = 362253746;
    public static final int LOCATION_INTERMEDIARY = 362253747;

    public static final String ENABLE_LOCATION_PERMISSION_ACTION = "ENABLE_LOCATION_PERMISSION";
    public static final String ENABLE_BACKGROUND_LOC_PERMISSION_ACTION = "ENABLE_BACKGROUND_LOC_PERMISSION";
    public static final String ENABLE_MOTION_ACTIVITY_PERMISSION_ACTION = "ENABLE_MOTION_ACTIVITY_PERMISSION";
    public static final String OPEN_APP_STATUS_PAGE_ACTION ="OPEN_APP_STATUS_PAGE";

}
