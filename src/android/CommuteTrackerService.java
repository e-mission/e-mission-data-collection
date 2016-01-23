package edu.berkeley.eecs.cfc_tracker;

import java.io.File;
import java.util.Properties;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.IBinder;

import com.google.android.gms.location.LocationListener;

import edu.berkeley.eecs.cfc_tracker.log.Log;

public class CommuteTrackerService extends Service implements
	Runnable, SensorEventListener {
	private static final String TAG = "CommuteTrackerService";
	public static int SLEEP_TIME = 60 * 1000; // 60 seconds = 1 minute
	Thread pollThread = new Thread(this);
	boolean running = false;
	
	/** The file that stores location and accelerometer data */
	File privateFileDir;

	// Value in milliseconds
	public static long MIN_TIME_BW_UPDATES = 60 * 1000L;
	// Value is in meters
	public static float MIN_DISTANCE_CHANGE_FOR_UPDATES = 50;
	// value in microsecs
	private static int ACCEL_READ_DURATION = 10 * 1000; // = 10 secs
	
    double latitude, longitude;
    
    Sensor accelerometer;
	SensorManager sm;
	
	double accelerameter_x = 0;
	double accelerameter_y = 0;
	double accelerameter_z = 0;
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void onCreate() {
		Log.i(this, TAG, "onCreate called");
	}

	/*
	 * Quick note on the multi-threading and synchronization here, to write it down for the record.
	 * First, we cannot poll in the onStartCommand function because it is run in the main thread of the
	 * application, and putting a long running loop in here will cause the application to be non-responsive
	 * (I have tested this). So, we launch a poll thread in the start command.
	 * 
	 * Second, all the Thread.stop() methods are currently deprecated because they don't shutdown the thread
	 * cleanly. So we are going to use a boolean variable (running) to control it.
	 * 
	 * But we using this boolean variable without any synchronization! How is this OK!??
	 * 
	 * The answer is two-fold:
	 * a) The thread only ever reads the variable. It is only written by the main thread in onStartCommand() and onDestroy.
	 * b) In both write locations, the variable is written without any checks. So there are no dependencies to worry about.
	 * c) It is fine for the thread to read a slightly stale value - it will just read to us reading one additional set of
	 *    sensor values.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(this, TAG, "CommuteTrackerService.onStartCommand invoked with flags = "+flags+
				" startId = "+startId);
		if (!running) {
			// pollThread.start();
			sm = (SensorManager) getSystemService(SENSOR_SERVICE);
	        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			// locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
			// setLocation();
	        /*
	        locationHandler = new LocationHandler(this);
	        locationHandler.startTracking(mLocationListener);
	        
	        activityHandler = new ActivityRecognitionHandler(this);
	        activityHandler.startMonitoring();
	        */
	        
			running = true;
			privateFileDir = getFilesDir();
			Log.i(this, TAG, "Writing sensor data to directory "+privateFileDir
					+" whose existence state is "+privateFileDir.exists());
		}
		return START_STICKY;
	}
	
	public void run() {
		// We currently keep polling the sensors until the service is stopped
		System.out.println("Starting the run");
		while(running) {
			try {
				System.out.println("Polling sensors");
				System.out.println("Starting reading accelerometer data");
		        sm.registerListener((SensorEventListener) this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL); 
		        Thread.sleep(ACCEL_READ_DURATION);
		        System.out.println("Stopped reading accelerometer data");
				sm.unregisterListener((SensorEventListener)this);
				// We should only sleep for SLEEP_TIME - ACCEL_READ_DURATION.
				// The second SLEEP_TIME seems unnecessary
				Thread.sleep(SLEEP_TIME - ACCEL_READ_DURATION);
				// Thread.sleep(SLEEP_TIME);
			}
			catch (InterruptedException e) {
				System.out.println("Polling thread in CFC tracker interrupted while sleeping, restarting polling");
			}
		}
	}
	
	public void onSensorChanged(SensorEvent event){
		accelerameter_x = event.values[0];
		accelerameter_y = event.values[1];
		accelerameter_z = event.values[2]; 
		Properties accelData = new Properties();
		accelData.put(Constants.ACCELERATOR_X, String.valueOf(event.values[0]));
		accelData.put(Constants.ACCELERATOR_Y, String.valueOf(event.values[1]));
		accelData.put(Constants.ACCELERATOR_Z, String.valueOf(event.values[2]));
		
		/** Writes accelerometer data to local file. */
		// DataUtils.saveData(accelData, privateFileDir);
		Log.i(this, TAG, "X: " + accelerameter_x + "Y: "+ accelerameter_y + "Z: " + accelerameter_z);
	}	
	
	/** Define a LocationListener to listen to updates from locationManager. */
	LocationListener mLocationListener = new LocationListener() {		
		Location prevLoc = null;
		private static final double THRESHOLD = 100;
		private static final int NOT_MOVED_THRESHOLD = 5;
		private int notMovedCount = 0;
		
        @Override
        public void onLocationChanged(Location newLoc) {
        	Log.d(CommuteTrackerService.this, TAG, "CommuteTrackerService: Received location update to "+newLoc);
        	Properties locData = new Properties();
        	locData.put(Constants.LATITUDE, String.valueOf(newLoc.getLatitude()));
            locData.put(Constants.LONGITUDE, String.valueOf(newLoc.getLongitude()));
            
            /*
            if (prevLoc != null) {
            	float distance = prevLoc.distanceTo(newLoc);
            	if(distance < THRESHOLD) {
            		notMovedCount++;
            		Log.d(TAG, "distance "+distance+" is below threshold "+THRESHOLD+
            				" for the past "+notMovedCount+" times");
            		if (notMovedCount > NOT_MOVED_THRESHOLD) {
            			Log.d(TAG, notMovedCount+" > "+NOT_MOVED_THRESHOLD+", registering geofence");
            			new GeofenceHandler(CommuteTrackerService.this).registerGeofence(
            					newLoc.getLatitude(), newLoc.getLongitude());
            			stopSelf();
            			notMovedCount = 0;
            			prevLoc = null;
            		}
            	}
            }
            */
            // In any case, we set the previous location to this one
            prevLoc = newLoc;
        }
    };
    
    private void stopService() {
    	Log.i(this, TAG, "User initiated stop");
    	/*
    	 * I tried putting calls to the handler in here, but that didn't work.
    	 * This log statement never actually showed up - it is unclear if the method is called.
    	 */
    	stopSelf();
    }
	
	@Override
	public void onDestroy() {
		running = false;
		Log.i(this, TAG, "CommuteTrackerService.onDestroy invoked");
		sm.unregisterListener((SensorEventListener)this);
		// locationManager.removeUpdates(mLocationListener);
		/*
		locationHandler.stopTracking(mLocationListener);
		activityHandler.stopMonitoring();
		*/
		// Generate the final trip
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			Log.d(this, TAG, "Accelerometer accuracy changed to "+accuracy);
			// Unclear what we should do here, punt for now
		}
	}
}
