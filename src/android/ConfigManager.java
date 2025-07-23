package edu.berkeley.eecs.emission.cordova.tracker;

import android.content.Context;

import com.google.gson.Gson;
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
    private static JSONObject cachedDeploymentConfig;

    public static LocationTrackingConfig getConfig(Context context) {
        if (cachedConfig == null) {
            cachedConfig = readFromCache(context);
            if (cachedConfig == null) {
                // Still null, so there was no valid config in the usercache
                // Return the default config (with deployment-specific overrides if any)
                // Note: we do not store the default in the usercache, as it is not user-given
                cachedConfig = getConfigDefault(context);
            }
        }
        return cachedConfig;
    }

    private static LocationTrackingConfig readFromCache(Context context) {
        try {
            LocationTrackingConfig cfg = UserCacheFactory.getUserCache(context).getDocument(R.string.key_usercache_sensor_config, LocationTrackingConfig.class);
            Log.i(context, TAG, "in readFromCache, cached tracking config = " + cfg);
            return cfg;
        } catch (JsonParseException e) {
            Log.e(context, TAG, "Exception while reading sensor config json, returning null: " + e);
            return null;
        }
    }

    protected static void updateConfig(Context context, LocationTrackingConfig newConfig) {
        UserCacheFactory.getUserCache(context).putReadWriteDocument(R.string.key_usercache_sensor_config, newConfig);
        cachedConfig = newConfig;
    }

    public static JSONObject getDeploymentConfig(Context context) {
        if (cachedDeploymentConfig == null) {
            try {
                cachedDeploymentConfig = (JSONObject) UserCacheFactory.getUserCache(context).getDocument("config/app_ui_config", false);
                Log.i(context, TAG, "In getDeploymentConfig, deployment config = " + cachedDeploymentConfig);
            } catch (JSONException e) {
                Log.e(context, TAG, "Found error " + e + "parsing deployment config json, returning null");
            }
        }
        return cachedDeploymentConfig;
    }

    public static LocationTrackingConfig getConfigDefault(Context context) {
        JSONObject deploymentConfig = getDeploymentConfig(context);
        if (deploymentConfig != null && deploymentConfig.has("tracking")) {
            try {
                JSONObject tracking = deploymentConfig.getJSONObject("tracking");
                LocationTrackingConfig cfg = new Gson().fromJson(tracking.toString(), LocationTrackingConfig.class);
                Log.d(context, TAG, "Created default tracking config with deployment-specific values");
                return cfg;
            } catch (JSONException|JsonParseException e) {
                Log.e(context, TAG, "Exception while parsing tracking config from deployment config, will return built-in default config: " + e);
            }
        }
        return new LocationTrackingConfig();
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
}
