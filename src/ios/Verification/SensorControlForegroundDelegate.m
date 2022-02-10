#import "SensorControlForegroundDelegate.h"
#import "TripDiarySensorControlChecks.h"
#import "LocalNotificationManager.h"
#import "BEMAppDelegate.h"
#import "BEMActivitySync.h"

#import <CoreMotion/CoreMotion.h>

@implementation SensorControlForegroundDelegate
// typedef BOOL (*CheckFnType)(void);

+(void) sendCheckResult:(BOOL)result
             forDelegate:(id<CDVCommandDelegate>) commandDelegate
              forCommand:(CDVInvokedUrlCommand*)command
                errorKey:(NSString*)localizableErrorKey
{
    NSString* callbackId = [command callbackId];
    @try {
        if (result) {
            CDVPluginResult* result = [CDVPluginResult
                                       resultWithStatus:CDVCommandStatus_OK];
            [commandDelegate sendPluginResult:result callbackId:callbackId];
        } else {
            NSString* msg = NSLocalizedStringFromTable(localizableErrorKey, @"DCLocalizable", nil);
            CDVPluginResult* result = [CDVPluginResult
                                       resultWithStatus:CDVCommandStatus_ERROR
                                       messageAsString:msg];
            [commandDelegate sendPluginResult:result callbackId:callbackId];
        }
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

+(void)checkLocationSettings:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command
{
    BOOL result = [TripDiarySensorControlChecks checkLocationSettings];
    [self sendCheckResult:result
              forDelegate:delegate forCommand:command
                 errorKey:@"location_not_enabled"];
}

+(void)checkLocationPermissions:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command
{
    BOOL result = [TripDiarySensorControlChecks checkLocationPermissions];
    [self sendCheckResult:result
              forDelegate:delegate forCommand:command
                 errorKey:@"location_permission_off"];
}

+(void)checkMotionActivitySettings:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command
{
    BOOL result = [TripDiarySensorControlChecks checkMotionActivitySettings];
    [self sendCheckResult:result
              forDelegate:delegate forCommand:command
                 errorKey:@"activity_settings_off"];
}


+(void)checkMotionActivityPermissions:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command
{
    BOOL result = [TripDiarySensorControlChecks checkMotionActivityPermissions];
    [self sendCheckResult:result
              forDelegate:delegate forCommand:command
                 errorKey:@"activity_permission_off"];
}

+(void)checkNotificationsEnabled:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command
{
    BOOL result = [TripDiarySensorControlChecks checkNotificationsEnabled];
    [self sendCheckResult:result
              forDelegate:delegate forCommand:command
                 errorKey:@"notifications_blocked"];
}


@end
