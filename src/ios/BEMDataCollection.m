#import "BEMDataCollection.h"
#import "LocalNotificationManager.h"
#import "Location/LocationTrackingConfig.h"
#import <CoreLocation/CoreLocation.h>

@implementation BEMDataCollection

- (void)startupInit:(CDVInvokedUrlCommand*)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
            @"startupInit called, is NOP on iOS"] showUI:FALSE];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", exception];
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
            @"launchInit called, is NOP on iOS"] showUI:FALSE];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (void)getConfig:(CDVInvokedUrlCommand *)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        LocationTrackingConfig* cfg = [LocationTrackingConfig instance];
        NSDictionary* retDict = @{@"isDutyCycling": @([cfg isDutyCycling]),
                                         @"accuracy": [self getAccuracyAsString:[cfg accuracy]], // from TripDiaryDelegate.m
                                         @"geofenceRadius": @([cfg geofenceRadius]),
                                         @"accuracyThreshold": @(200),
                                         @"filter": @"distance",
                                         @"filterValue": @([cfg filterDistance]),
                                         @"tripEndStationaryMins": @([cfg tripEndStationaryMins])};
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK
                                   messageAsDictionary:retDict];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", exception];
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

@end
