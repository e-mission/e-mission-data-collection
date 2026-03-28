//
//  BEMTrackingConfigManager.m
//  emission
//
//  Created by Kalyanaraman Shankari on 3/25/16.
//
//

#import "BEMTrackingConfigManager.h"
#import "BEMBuiltinUserCache.h"
#import "ConsentConfig.h"
#import "LocalNotificationManager.h"

#define SENSOR_CONFIG_KEY @"key.usercache.sensor_config"
#define CONSENT_CONFIG_KEY @"key.usercache.consent_config"
#define APP_CONFIG_KEY @"key.usercache.app_config"

static NSString* const CONFIG_APP_UI_CONFIG = @"config/app_ui_config";
static NSString* const CONFIG_PHONE_UI = @"CONFIG_PHONE_UI";

@implementation BEMTrackingConfigManager

static LocationTrackingConfig *cachedTrackingConfig;
static ConsentConfig *cachedConsentConfig;
static NSDictionary *cachedDeploymentConfig;

// corresponds to getTrackingConfig in TrackingConfigManager.java
+ (LocationTrackingConfig*) instance {
    if (cachedTrackingConfig == NULL) {
        cachedTrackingConfig = [self readFromCache];
        if (cachedTrackingConfig == NULL) {
            // This is still NULL, which means that there is no document in the usercache.
            // Let us add a dummy one based on the default settings
            // we don't want to save it to the database because then it will look like a user override
            cachedTrackingConfig = [self getTrackingConfigDefault];
        }
    }
    return cachedTrackingConfig;
}

+ (LocationTrackingConfig*) readFromCache {
    @try {
        LocationTrackingConfig* cfg = (LocationTrackingConfig*)[[BuiltinUserCache database] getDocument:SENSOR_CONFIG_KEY wrapperClass:[LocationTrackingConfig class]];
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"in readFromCache, cached tracking config = %@", cfg]];
        return cfg;
    } @catch (NSException *exception) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Exception while reading sensor config, returning NULL: %@", exception]];
        return NULL;
    }
}

+ (void) updateTrackingConfig:(LocationTrackingConfig*) newTrackingConfig {
    [[BuiltinUserCache database] putReadWriteDocument:SENSOR_CONFIG_KEY value:newTrackingConfig];
    cachedTrackingConfig = newTrackingConfig;
}

+ (BOOL) upgradeDeploymentConfig:(NSDictionary*)newDeploymentConfig {
    NSDictionary *existingDeploymentConfig = [self getDeploymentConfig];

    if (existingDeploymentConfig != NULL && newDeploymentConfig != NULL && [[existingDeploymentConfig allKeys] containsObject:@"version"] && [[newDeploymentConfig allKeys] containsObject:@"version"]) {
        int cachedVersion = [[existingDeploymentConfig valueForKey:@"version"] intValue];
        int newVersion = [[newDeploymentConfig valueForKey:@"version"] intValue];
        if (newVersion > cachedVersion) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Upgrading deployment config from version %d to version %d", cachedVersion, newVersion]];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Not upgrading deployment config; new version %d is not newer than cached version %d", newVersion, cachedVersion]];
            return NO;
        }
    } else {
        [LocalNotificationManager addNotification:@"Not upgrading deployment config because cached config or new config is null or does not have version key"];
        return NO;
    }
    [[BuiltinUserCache database] putReadWriteDocument:APP_CONFIG_KEY value:newDeploymentConfig];
    [[BuiltinUserCache database] putLocalStorage:CONFIG_PHONE_UI jsonValue:newDeploymentConfig];

    // clear cached values that could depend on deployment config changes
    cachedDeploymentConfig = NULL;
    cachedTrackingConfig = NULL;

    return YES;
}

+ (NSDictionary*) getDeploymentConfig {
    if (cachedDeploymentConfig == NULL) {
        @try {
            cachedDeploymentConfig = [[BuiltinUserCache database] getDocument:CONFIG_APP_UI_CONFIG withMetadata:false];
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"in getDeploymentConfig, deployment config = %@", cachedDeploymentConfig]];
        } @catch (NSException *exception) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Exception while reading deployment config, returning NULL: %@", exception]];
        }
    }
    return cachedDeploymentConfig;
}

+ (LocationTrackingConfig*) getTrackingConfigDefault {
    NSDictionary* deploymentConfig = [self getDeploymentConfig];
    if (deploymentConfig != NULL && [[deploymentConfig allKeys] containsObject:@"tracking"]) {
        @try {
            NSDictionary* tracking = [deploymentConfig valueForKey:@"tracking"];
            LocationTrackingConfig* cfg = [LocationTrackingConfig new];
            [cfg setValuesForKeysWithDictionary:tracking];
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Created default tracking config with deployment-specific values"]];
            return cfg;
        } @catch (NSException *exception) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Exception while reading deployment tracking config; will use built-in default: %@", exception]];
        }
    }
    return [LocationTrackingConfig new];
}

+ (ConsentConfig*) getConsentConfig {
    if (cachedConsentConfig == NULL) {
        // TODO: should we put this in a try/catch block like the instance singleton
        cachedConsentConfig = (ConsentConfig*)[[BuiltinUserCache database] getDocument:CONSENT_CONFIG_KEY wrapperClass:[ConsentConfig class]];
    }
    return cachedConsentConfig;
}

+ (ConsentConfig*) getPriorConsent {
    ConsentConfig* currConfig = (ConsentConfig*)[[BuiltinUserCache database] getDocument:CONSENT_CONFIG_KEY wrapperClass:[ConsentConfig class]];
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"in getPriorConsent, currConfig = %@", currConfig]];
    return currConfig;
}

+ (BOOL) isConsented:(NSString*)reqConsent {
    @try {
        ConsentConfig* currConfig = [self getConsentConfig];
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"checking consent with reqConsent = %@, cached currConfig.approval_date = %@",
                reqConsent, currConfig.approval_date]];

        if ([reqConsent isEqualToString:currConfig.approval_date]) {
                [LocalNotificationManager addNotification:@"isConsented = YES"];
            return YES;
        } else {
                [LocalNotificationManager addNotification:@"isConsented = NO"];
            return NO;
        }
    } @catch (NSException *exception) {
        [LocalNotificationManager addNotification:@"isConsented = exception"];
        return false;
    }
}

+ (void) setConsented:(ConsentConfig*)newConsent {
    [[BuiltinUserCache database] putReadWriteDocument:CONSENT_CONFIG_KEY value:newConsent];
}

@end
