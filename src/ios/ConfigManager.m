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

static LocationTrackingConfig *_instance;
static ConsentConfig *_consent_config;
static NSDictionary *_dynamic_instance;
static bool _is_fleet = false;

+ (LocationTrackingConfig*) instance {
    if (_instance == NULL) {
        @try {
        _instance = [self readFromCache];
        } @catch (NSException *exception) {
            _instance = [LocationTrackingConfig new];
        }
        
        if (_instance == NULL) {
            // This is still NULL, which means that there is no document in the usercache.
            // Let us add a dummy one based on the default settings
            // we don't want to save it to the database because then it will look like a user override
            _instance = [LocationTrackingConfig new];
        }
    }
    return _instance;
}

+ (ConsentConfig*) consent_config {
    if (_consent_config == NULL) {
        // TODO: should we put this in a try/catch block like the instance singleton
        _consent_config = (ConsentConfig*)[[BuiltinUserCache database] getDocument:CONSENT_CONFIG_KEY wrapperClass:[ConsentConfig class]];
    }
    return _consent_config;
}

+ (NSDictionary*) dynamic_instance {
    if (_dynamic_instance == NULL) {
        // TODO: should we put this in a try/catch block like the instance singleton
        // TODO: clean this up. Potentially write a wrapper for this instead of using
        // a dictionary
        _dynamic_instance = [[BuiltinUserCache database] getDocument:@"config/app_ui_config" withMetadata:false];
        if (_dynamic_instance != NULL && [[_dynamic_instance allKeys] containsObject:@"tracking"]) {
            NSDictionary* trackingObj = [_dynamic_instance valueForKey:@"tracking"];
            if ([[trackingObj allKeys] containsObject:@"bluetooth_only"]) {
                _is_fleet = [trackingObj valueForKey:@"bluetooth_only"];
            }
        }
    }
    return _dynamic_instance;
}


+ (LocationTrackingConfig*) readFromCache {
    return (LocationTrackingConfig*)[[BuiltinUserCache database] getDocument:SENSOR_CONFIG_KEY wrapperClass:[LocationTrackingConfig class]];
}

+ (void) updateConfig:(LocationTrackingConfig*) newConfig {
    [[BuiltinUserCache database] putReadWriteDocument:SENSOR_CONFIG_KEY value:newConfig];
    _instance = newConfig;
}

+ (ConsentConfig*) getPriorConsent {
    ConsentConfig* currConfig = (ConsentConfig*)[[BuiltinUserCache database] getDocument:CONSENT_CONFIG_KEY wrapperClass:[ConsentConfig class]];
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"in getPriorConsent, currConfig = %@", currConfig]];
    return currConfig;
}

+ (BOOL) isConsented:(NSString*)reqConsent {
    @try {
    ConsentConfig* currConfig = [self consent_config];
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

+ (BOOL) isFleet {
    // this will populate _is_fleet properly
    NSDictionary* dynamicConfig = [self dynamic_instance];
    return _is_fleet;
}

@end
