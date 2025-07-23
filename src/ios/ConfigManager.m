//
//  ConfigManager.m
//  emission
//
//  Created by Kalyanaraman Shankari on 3/25/16.
//
//

#import "ConfigManager.h"
#import "BEMBuiltinUserCache.h"
#import "ConsentConfig.h"
#import "LocalNotificationManager.h"

#define SENSOR_CONFIG_KEY @"key.usercache.sensor_config"
#define CONSENT_CONFIG_KEY @"key.usercache.consent_config"

@implementation ConfigManager

static LocationTrackingConfig *cachedConfig;
static ConsentConfig *cachedConsentConfig;
static NSDictionary *cachedDeploymentConfig;

// corresponds to getConfig in ConfigManager.java
+ (LocationTrackingConfig*) instance {
    if (cachedConfig == NULL) {
        cachedConfig = [self readFromCache];
        if (cachedConfig == NULL) {
            // This is still NULL, which means that there is no document in the usercache.
            // Let us add a dummy one based on the default settings
            // we don't want to save it to the database because then it will look like a user override
            cachedConfig = [self getConfigDefault];
        }
    }
    return cachedConfig;
}

+ (LocationTrackingConfig*) readFromCache {
    @try {
        LocationTrackingConfig* cachedConfig = (LocationTrackingConfig*)[[BuiltinUserCache database] getDocument:SENSOR_CONFIG_KEY wrapperClass:[LocationTrackingConfig class]];
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"in readFromCache, cached tracking config = %@", cachedConfig]];
        return cachedConfig;
    } @catch (NSException *exception) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Exception while reading sensor config, returning NULL: %@", exception]];
        return NULL;
    }
}

+ (void) updateConfig:(LocationTrackingConfig*) newConfig {
    [[BuiltinUserCache database] putReadWriteDocument:SENSOR_CONFIG_KEY value:newConfig];
    cachedConfig = newConfig;
}

+ (NSDictionary*) getDeploymentConfig {
    if (cachedDeploymentConfig == NULL) {
        @try {
            cachedDeploymentConfig = [[BuiltinUserCache database] getDocument:@"config/app_ui_config" withMetadata:false];
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"in getDeploymentConfig, deployment config = %@", cachedDeploymentConfig]];
        } @catch (NSException *exception) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Exception while reading deployment config, returning NULL: %@", exception]];
        }
    }
    return cachedDeploymentConfig;
}

+ (LocationTrackingConfig*) getConfigDefault {
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
