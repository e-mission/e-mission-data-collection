package edu.berkeley.eecs.cfc_tracker;

public final class Constants {
	public static String LONGITUDE = "longitude";
	public static String LATITUDE = "latitude";
	public static String ACCELERATOR_X = "ax";
	public static String ACCELERATOR_Y = "ay";
	public static String ACCELERATOR_Z = "az";
	public static String BATTERY_LEVEL = "battery_level";
	public static String ACTIVITY_TYPE = "activity_type";
	public static String ACTIVITY_CONFIDENCE = "activity_confidence";
	
	public static int TRIP_EDGE_THRESHOLD = 100; // meters

    public static final int MILLISECONDS = 1000; // used to make constants like 30 * MILLISECONDS
	public static final int THIRTY_SECONDS = 30 * MILLISECONDS; // 30 secs, keep it similar to GPS
    public static final long NANO2MS = 1000000;

    public static String[] sensors = {LONGITUDE,
									  LATITUDE,
									  ACCELERATOR_X,
									  ACCELERATOR_Y,
									  ACCELERATOR_Z,
									  BATTERY_LEVEL,
									  ACTIVITY_TYPE,
									  ACTIVITY_CONFIDENCE};
}
