package edu.berkeley.eecs.emission.cordova.tracker;

import android.content.Context;

import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import org.apache.cordova.ConfigXmlParser;

import java.util.HashMap;

import edu.berkeley.eecs.emission.R;
import org.json.JSONException;
import org.json.JSONObject;

import edu.berkeley.eecs.emission.cordova.tracker.wrapper.ConsentConfig;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.Log;
import edu.berkeley.eecs.emission.cordova.unifiedlogger.NotificationHelper;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;

/**
 * Created by shankari on 3/25/16.
 */
public class ConfigManager {
    private static String TAG = "ConfigManager";
    private static LocationTrackingConfig cachedConfig;
    private static ConsentConfig cachedConsent;
    private static JSONObject cachedDynamicConfig;
    private static boolean cachedIsFleet = false;

    public static LocationTrackingConfig getConfig(Context context) {
        if (cachedConfig == null) {
            try {
            cachedConfig = readFromCache(context);
            if (cachedConfig == null) {
                // This is still NULL, which means that there is no document in the usercache.
                // Let us set it to the default settings
                // we don't want to save it to the database because then it will look like a user override
                cachedConfig = new LocationTrackingConfig();
            }
            } catch(JsonParseException e) {
                Log.e(context, TAG, "Found error " + e + "parsing sensing config json, resetting to defaults");
                NotificationHelper.createNotification(context, Constants.TRACKING_ERROR_ID,
                        null, context.getString(R.string.error_reading_stored_config));
                cachedConfig = new LocationTrackingConfig();
                updateConfig(context, cachedConfig);
            }
        }
        return cachedConfig;
    }

    private static LocationTrackingConfig readFromCache(Context context) {
        return UserCacheFactory.getUserCache(context)
                .getDocument(R.string.key_usercache_sensor_config, LocationTrackingConfig.class);
    }

    protected static void updateConfig(Context context, LocationTrackingConfig newConfig) {
        UserCacheFactory.getUserCache(context)
                .putReadWriteDocument(R.string.key_usercache_sensor_config, newConfig);
        cachedConfig = newConfig;
    }

    public static String getReqConsent(Context ctxt) {
        ConfigXmlParser parser = new ConfigXmlParser();
        parser.parse(ctxt);
        String reqConsent = parser.getPreferences().getString("emSensorDataCollectionProtocolApprovalDate", null);
        return reqConsent;
    }

    public static ConsentConfig getConsent(Context context) throws JsonParseException {
        if (cachedConsent == null) {
            cachedConsent = UserCacheFactory.getUserCache(context)
                .getDocument(R.string.key_usercache_consent_config, ConsentConfig.class);
            Log.i(context, TAG, "Read cached consent "+cachedConsent+" from database");
        }
        return cachedConsent;
    }

    public static boolean isConsented(Context context, String reqConsent) {
        try {
            ConsentConfig currConfig = getConsent(context);
            return currConfig != null && reqConsent.equals(currConfig.getApproval_date());
        } catch(JsonParseException e) {
            Log.e(context, TAG, "Found error " + e + "parsing consent json, re-prompting user");
            return false;
        }
    }

    public static void setConsented(Context context, ConsentConfig newConsent) {
        UserCacheFactory.getUserCache(context)
                .putReadWriteDocument(R.string.key_usercache_consent_config, newConsent);
    }

    public static JSONObject getDynamicConfig(Context context) throws JSONException {
        if (cachedDynamicConfig == null) {
            cachedDynamicConfig = (JSONObject) UserCacheFactory.getUserCache(context).getDocument("config/app_ui_config", false);
            cachedIsFleet = (cachedDynamicConfig != null &&
                   cachedDynamicConfig.has("tracking") &&
                   cachedDynamicConfig.getJSONObject("tracking").getBoolean("bluetooth_only"));
        }
        return cachedDynamicConfig;
    }

    public static boolean isFleet(Context ctxt) {
        // this will populate the cached _is_fleet properly
        try {
            JSONObject dynamicConfig = getDynamicConfig(ctxt);
        } catch (JSONException e) {
            Log.e(ctxt, TAG, "ERROR "+e+" while reading dynamic config, returning default "+cachedIsFleet);
        }
        Log.d(ctxt, TAG, "found cached dynamicConfig, returning isFleet = "+cachedIsFleet);
        return cachedIsFleet;
    }
}
