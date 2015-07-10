package edu.berkeley.eecs.cfc_tracker.test.usercache;

import android.content.Context;
import android.location.Location;
import android.test.AndroidTestCase;
import android.test.RenamingDelegatingContext;

import com.google.android.gms.location.DetectedActivity;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

import edu.berkeley.eecs.cfc_tracker.R;
import edu.berkeley.eecs.cfc_tracker.usercache.BuiltinUserCache;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCache;
import edu.berkeley.eecs.cfc_tracker.usercache.UserCacheFactory;
import edu.berkeley.eecs.cfc_tracker.wrapper.Metadata;

/**
 * Created by shankari on 7/6/15.
 */
public class BuiltinUserCacheTest extends AndroidTestCase {
    BuiltinUserCache dbHelper;
    Context cachedContext;

    private static final String METADATA_TAG = "metadata";
    private static final String DATA_TAG = "data";

    public BuiltinUserCacheTest() {
        super();
    }

    protected void setUp() throws Exception {
        super.setUp();
        RenamingDelegatingContext context =
                new RenamingDelegatingContext(getContext(), "test_");
        cachedContext = context;
        dbHelper = new BuiltinUserCache(context);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private Location getDummyLocation(double lat, double lng, float accuracy) {
        Location loc = new Location("TEST");
        loc.setLatitude(lat);
        loc.setLongitude(lng);
        loc.setAccuracy(accuracy);
        return loc;
    }

    private DetectedActivity getDetectedActivity(int activity, int confidence) {
        return new DetectedActivity(activity, confidence);
    }

    public void testGetPutMessage() throws Exception {
        UserCache uc = UserCacheFactory.getUserCache(cachedContext);
        uc.putMessage(R.string.key_usercache_location, getDummyLocation(25.25, 35.35, 5.5f));
        uc.putMessage(R.string.key_usercache_location, getDummyLocation(27.27, 37.37, 7.7f));

        uc.putMessage(R.string.key_usercache_activity, getDetectedActivity(DetectedActivity.ON_BICYCLE, 80));
        uc.putMessage(R.string.key_usercache_activity, getDetectedActivity(DetectedActivity.ON_FOOT, 90));

        BuiltinUserCache biuc = new BuiltinUserCache(cachedContext);
        String toSend = biuc.sync_phone_to_server().toString();
        System.out.println("String to send = " + toSend);

        JSONArray el = new JSONArray(toSend);
        assertEquals(el.length(), 4);
        assertEquals(new Gson().fromJson(el.getJSONObject(0).getJSONObject(METADATA_TAG).toString(), Metadata.class).getKey(),
                cachedContext.getString(R.string.key_usercache_location));
        assertEquals(new Gson().fromJson(el.getJSONObject(2).getJSONObject(METADATA_TAG).toString(), Metadata.class).getKey(),
                cachedContext.getString(R.string.key_usercache_activity));
        assertEquals(el.getJSONObject(0).getJSONObject(DATA_TAG).getDouble("mLatitude"), 25.25);
        assertEquals(el.getJSONObject(2).getJSONObject(DATA_TAG).getInt("zzaxw"), 1);
    }

    private Metadata getDummyMetadata(String key, String type) {
        Metadata md = new Metadata();
        md.setWrite_ts(System.currentTimeMillis());
        md.setType(type);
        md.setKey(key);
        md.setPlugin("data");
        return md;
    }

    public void testPutGetDocument() throws Exception {
        JSONArray el = new JSONArray();
        JSONObject configProbesEntry = new JSONObject();
        configProbesEntry.put(METADATA_TAG, new JSONObject(new Gson().toJson(getDummyMetadata("config/sensors", "document"))));
        configProbesEntry.put(DATA_TAG, new JSONArray("['accelerometer', 'gyrometer', 'linear_accelerometer']"));

        JSONObject locationConfigEntry = new JSONObject();
        locationConfigEntry.put(METADATA_TAG, new JSONObject(new Gson().toJson(getDummyMetadata("config/location", "document"))));
        String locationConfigData =
                "{'accuracy': 'POWER_BALANCED_ACCURACY', 'filter': 'DISTANCE_FILTER', 'geofence_radius': 100}";
        locationConfigEntry.put(DATA_TAG, new JSONObject(locationConfigData));

        el.put(configProbesEntry);
        el.put(locationConfigEntry);

        String toReceive = el.toString();
        System.out.println("String to send = " + toReceive);

        JSONArray readEntryList = new JSONArray(toReceive);
        BuiltinUserCache biuc = new BuiltinUserCache(cachedContext);
        biuc.sync_server_to_phone(readEntryList);

        UserCache uc = UserCacheFactory.getUserCache(cachedContext);
        String[] sensorArray = uc.getDocument(R.string.key_usercache_config_sensors, String[].class);
        System.out.println("sensor array = " + Arrays.toString(sensorArray));
        assertEquals(sensorArray.length, 3);
        assertEquals(sensorArray[0], "accelerometer");
        assertEquals(sensorArray[1], "gyrometer");
        assertEquals(sensorArray[2], "linear_accelerometer");
    }

    public void testRawInputFromServer() throws Exception {
        String rawInput = "[{'data': ['accelerometer', 'gyrometer', 'linear_accelerometer'],"+
                "'metadata': {'type': 'document', 'key': 'config/sensors', 'write_ts': 1436253782105}},"+
                "{'data': {'filter': 'DISTANCE_FILTER', 'geofence_radius': 100, 'accuracy': 'POWER_BALANCED_ACCURACY'},"+
                "'metadata': {'type': 'document', 'key': 'config/location_config', 'write_ts': 1436253782106}}]";
        JSONArray el = new JSONArray(rawInput);
        System.out.println("parsed entries "+el);

        BuiltinUserCache biuc = new BuiltinUserCache(cachedContext);
        biuc.sync_server_to_phone(el);

        UserCache uc = UserCacheFactory.getUserCache(cachedContext);
        String[] sensorArray = uc.getDocument(R.string.key_usercache_config_sensors, String[].class);
        System.out.println("sensor array = " + Arrays.toString(sensorArray));
        assertEquals(sensorArray.length, 3);
        assertEquals(sensorArray[0], "accelerometer");
        assertEquals(sensorArray[1], "gyrometer");
        assertEquals(sensorArray[2], "linear_accelerometer");
    }

    public void testRawOutputToServer() throws Exception {
        String expectedOutputRegEx = new StringBuilder().append("\\[").
                append("\\{\"metadata\":\\{\"key\":\"background./location\",\"type\":\"message\",\"read_ts\":0,\"write_ts\":[0-9]*\\},").
                append("\"data\":\\{.*\"mAccuracy\":5.5,.*\"mLatitude\":45.64,.*\"mLongitude\":21.35.*\\]").toString();

        UserCache uc = UserCacheFactory.getUserCache(cachedContext);
        uc.putMessage(R.string.key_usercache_location, getDummyLocation(45.64, 21.35, 5.5f));

        BuiltinUserCache biuc = new BuiltinUserCache(cachedContext);
        JSONArray el = biuc.sync_phone_to_server();

        String toSend = el.toString();

        assertTrue(toSend.matches(expectedOutputRegEx));
    }
}
