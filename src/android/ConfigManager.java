package edu.berkeley.eecs.emission.cordova.tracker;

import android.content.Context;

import java.util.HashMap;

import edu.berkeley.eecs.emission.R;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.ConsentConfig;
import edu.berkeley.eecs.emission.cordova.tracker.wrapper.LocationTrackingConfig;
import edu.berkeley.eecs.emission.cordova.usercache.UserCacheFactory;

/**
 * Created by shankari on 3/25/16.
 */
public class ConfigManager {
    private static LocationTrackingConfig cachedConfig;

    public static LocationTrackingConfig getConfig(Context context) {
        if (cachedConfig == null) {
            cachedConfig = readFromCache(context);
            if (cachedConfig == null) {
                // This is still NULL, which means that there is no document in the usercache.
                // Let us set it to the default settings
                // we don't want to save it to the database because then it will look like a user override
                cachedConfig = new LocationTrackingConfig();
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

    public static boolean isConsented(Context context, String reqConsent) {
        ConsentConfig currConfig = UserCacheFactory.getUserCache(context)
                .getDocument(R.string.key_usercache_consent_config, ConsentConfig.class);
        return reqConsent.equals(currConfig.getApproval_date());
    }

    public static void setConsented(Context context, ConsentConfig newConsent) {
        UserCacheFactory.getUserCache(context)
                .putReadWriteDocument(R.string.key_usercache_consent_config, newConsent);
    }
}
