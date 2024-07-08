package edu.berkeley.eecs.emission.cordova.tracker.wrapper;

/**
 * Created by shankari on 3/30/24.
 */
public class BluetoothBLE {
    public String getEventType() {
        return eventType;
    }

    /* Not sure if we need to return the UUID, major and minor
       since we will only get callbacks for the registered UUID
       and we don't need to check the major or minor in native code
       only that it is **our** beacon. Let's hold off on them for now.
     **/

    /* We do need to use proximity in the FSM to avoid spurious exits (e.g. a
     * personal car parked next to a fleet car may still see a region enter,
     * but we don't want to track the trip because the proximity is "far".
     */
    public String getProximity() {
        return proximity;
    }

    public double getTs() {
        return ts;
    }

    // Similarly, we store the accuracy and the rssi for the record, but the
    // underlying API already converts it to a proximity value, so we use that
    // instead

    private String eventType;
    private String uuid;
    private int major;
    private int minor;
    private String proximity;
    private double accuracy;
    private int rssi;

    private double ts;
    // Should we put newState in here as well?
    // If so, we will need to change the location of the save

    private BluetoothBLE() {}

    public static BluetoothBLE initRegionEnter(String uuid, double ts) {
	BluetoothBLE enterEvent = new BluetoothBLE();
	enterEvent.eventType = "REGION_ENTER";
	enterEvent.uuid = uuid;
        enterEvent.ts = ts;
	return enterEvent;
    }

    public static BluetoothBLE initRegionExit(String uuid, double ts) {
	BluetoothBLE exitEvent = new BluetoothBLE();
	exitEvent.eventType = "REGION_EXIT";
	exitEvent.uuid = uuid;
        exitEvent.ts = ts;
	return exitEvent;
    }

    public static BluetoothBLE initRangeUpdate(String uuid, double ts,
        int major, int minor,
        String proximity, double accuracy, int rssi) {
	BluetoothBLE rangeEvent = new BluetoothBLE();
	rangeEvent.eventType = "RANGE_UPDATE";
	rangeEvent.uuid = uuid;
	rangeEvent.ts = ts;

	rangeEvent.major = major;
	rangeEvent.minor = minor;
	rangeEvent.proximity = proximity;
	rangeEvent.accuracy = accuracy;
	rangeEvent.rssi = rssi;
	return rangeEvent;
    }

    public static BluetoothBLE initFake(String eventType, String uuid, int major, int minor) {
        BluetoothBLE fakeEvent = new BluetoothBLE();
        fakeEvent.uuid = uuid;
        fakeEvent.eventType = eventType;
        fakeEvent.ts = System.currentTimeMillis() / 1000; // time is in seconds for us

        // we assume that we don't have major and minor entries for the
        // "monitor" responses
        if (eventType.equals("RANGE_UPDATE")) {
            fakeEvent.major = major;
            fakeEvent.minor = minor;
            fakeEvent.proximity = "ProximityNear";
            fakeEvent.accuracy = (int)(Math.random() * 100);
            fakeEvent.rssi = (int)(Math.random() * 10);
        }
        
        return fakeEvent;
    }
}
