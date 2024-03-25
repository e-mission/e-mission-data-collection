package edu.berkeley.eecs.emission.cordova.tracker.bluetooth;

// Android imports
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

// Altbeacon imports
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;

// Other plugin imports
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;


public class BluetoothService extends Service {
    private static String TAG = "BluetoothService";
    private BeaconManager beaconManager;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(this, TAG, "onStartCommand called!!!!");

        // Define the beacon manager
        beaconManager = BeaconManager.getInstanceForApplication(this);
        
        // Start scanning for BLE beacons
        startBeaconScan();

        // Start sticky
        return 1;
    }

    // @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startBeaconScan() {
        // Code to start scanning for BLE beacons using AltBeacon library
        Log.d(this, TAG, "startBeaconScan called!!!!");

        beaconManager.addMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                Log.d(BluetoothService.this, TAG, "Beacon entered the region. Beacon UID: " + region.getUniqueId());
            }
    
            @Override
            public void didExitRegion(Region region) {
                Log.d(BluetoothService.this, TAG, "Beacon has exited the region. Beacon UID: " + region.getUniqueId());
            }
    
            @Override
            public void didDetermineStateForRegion(int state, Region region) {
                // state = 0, OUTSIDE
                // state = 1, INSIDE
                if (state == 0) {
                    Log.d(BluetoothService.this, TAG, "Beacon is not inside the region! Beacon UID: " + region.getUniqueId());
                } else {
                    Log.d(BluetoothService.this, TAG, "Beacon is inside the region! Beacon UID: " + region.getUniqueId());
                }
            }
        });
    
        beaconManager.startMonitoring(new Region("426C7565-4368-6172-6D42-6561636F6E73", null, null, null));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBeaconScan();
    }

    private void stopBeaconScan() {
        Log.d(this, TAG, "Stopping monitoring for the beacon.");
        beaconManager.stopMonitoring(new Region("426C7565-4368-6172-6D42-6561636F6E73", null, null, null));
    }
}
