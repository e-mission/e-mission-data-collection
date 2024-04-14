package edu.berkeley.eecs.emission.cordova.tracker.bluetooth;

// Android imports
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

// Altbeacon imports
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Beacon;

// Other plugin imports
import edu.berkeley.eecs.emission.R;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.tracker.ExplicitIntent;
import edu.berkeley.eecs.emission.cordova.tracker.verification.SensorControlChecks;


public class BluetoothMonitoringService extends Service {
    private static String TAG = "BluetoothMonitoringService";
    private BeaconManager beaconManager;
    private String uuid = "bf3df3b1-6e46-35fa-86e5-927c95dd096c";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(this, TAG, "onStartCommand called!!!!");

        // Instantiate variable
        beaconManager = BeaconManager.getInstanceForApplication(this);
        // This line will ensure that we always get a first callback if beacon is in region when we start monitoring
        // https://github.com/AltBeacon/android-beacon-library/issues/708#issuecomment-399513853
        beaconManager.setRegionStatePersistenceEnabled(false);

        // Start monitoring for BLE beacons
        startMonitoring();

        // Start sticky
        return 1;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startMonitoring() {
        Log.d(this, TAG, "Start monitoring has been called!");

        // Code to start monitoring for BLE beacons using AltBeacon library
        beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                Log.d(BluetoothMonitoringService.this, TAG, "I just saw a beacon for the first time!");
            }
    
            @Override
            public void didExitRegion(Region region) {
                Log.d(BluetoothMonitoringService.this, TAG, "I no longer see an beacon");
            }
    
            @Override
            public void didDetermineStateForRegion(int state, Region region) {
                Log.d(BluetoothMonitoringService.this, TAG, "I have just switched from seeing/not seeing beacons: "+state);
            }
        });
    
        // Define our region and start monitoring
        Region region = new Region(uuid, null, null, null);
        beaconManager.startMonitoring(region);
        Log.d(this, TAG, "Starting to monitor for our region: " + region.toString());
    }

    @Override
    public void onDestroy() {
        stopMonitoring();
        super.onDestroy();
    }

    private void stopMonitoring() {
        Log.d(this, TAG, "Stopping monitoring for the beacon.");
        beaconManager.stopMonitoring(new Region(uuid, null, null, null));
        beaconManager.removeAllMonitorNotifiers();
    }
}
