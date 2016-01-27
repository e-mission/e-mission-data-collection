#import "BEMDataCollection.h"
#import "Location/LocationTrackingConfig.h"

@implementation BEMDataCollection

- (void)startupInit:(CDVInvokedUrlCommand*)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
            @"startupInit called, is NOP on iOS", ] showUI:FALSE];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", e];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (void)launchInit:(CDVInvokedUrlCommand*)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
            @"launchInit called, is NOP on iOS", ] showUI:FALSE];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", e];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (void)getSettings:(CDVInvokedUrlCommand*)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        ConnectionSettings* instance = [[ConnectionSettings sharedInstance] getConnectURL];
        LocationTrackingConfig* cfg = [LocationTrackingConfig instance];
        NSMutableDictionary* retDict = @{@"isDutyCycling": [cfg isDutyCycling],
                                         @"accuracy": [self getAccuracyAsString[cfg accuracy]], // from TripDiaryDelegate.m
                                         @"geofenceRadius": [cfg geofenceRadius],
                                         @"accuracyThreshold": 200,
                                         @"filter": "distance",
                                         @"filterValue": [cfg filterDistance],
                                         @"tripEndStationaryMins": [cfg tripEndStationaryMins]};
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK
                                   messageAsDictionary:retDict];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", e];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (NSString*)getAccuracyAsString:(double)accuracyLevel
{
    if (accuracyLevel == kCLLocationAccuracyBest) {
        return @"BEST";
    } else if (accuracyLevel == kCLLocationAccuracyHundredMeters) {
        return @"HUNDRED_METERS";
    } else {
        return @"UNKNOWN";
    }
}

/*
 * Note that it is possible that some of this can happen on startup init
 * instead of every time the application is launched. But I am not sure which
 * ones, and so far, we have always done everything when the application is
 * launched. I am apprehensive that moving to startup init will break things in
 * unexpected ways, specially while we are making a bunch of other changes
 * anyway. So the current plan is that the code will be retained in here, this
 * will be called from the delegate's didFinishLaunchingWithOptions method, and
 * once we know that everything works, I can slowly move changes to
 * startupInit, one by one.
 */

@end
