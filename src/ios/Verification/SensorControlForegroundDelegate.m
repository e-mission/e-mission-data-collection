#import "SensorControlForegroundDelegate.h"
#import "TripDiarySensorControlChecks.h"
#import "SensorControlBackgroundChecker.h"
#import "LocalNotificationManager.h"
#import "BEMAppDelegate.h"
#import "BEMActivitySync.h"

#import <CoreMotion/CoreMotion.h>

@interface SensorControlForegroundDelegate() {
    id<CDVCommandDelegate> commandDelegate;
    CDVInvokedUrlCommand* command;
}
@end

@implementation SensorControlForegroundDelegate

/*
 * BEGIN: "check" implementations
 */

// typedef BOOL (*CheckFnType)(void);

-(void) sendCheckResult:(BOOL)result
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

-(id)initWithDelegate:(id<CDVCommandDelegate>)delegate
           forCommand:(CDVInvokedUrlCommand *)command
{
    self->command = command;
    self->commandDelegate = delegate;
    return [super init];
}


-(void)checkLocationSettings
{
    BOOL result = [TripDiarySensorControlChecks checkLocationSettings];
    [self sendCheckResult:result
                 errorKey:@"location_not_enabled"];
}

-(void)checkLocationPermissions
{
    BOOL result = [TripDiarySensorControlChecks checkLocationPermissions];
    [self sendCheckResult:result
                 errorKey:@"location_permission_off"];
}

-(void)checkMotionActivitySettings
{
    BOOL result = [TripDiarySensorControlChecks checkMotionActivitySettings];
    [self sendCheckResult:result
                 errorKey:@"activity_settings_off"];
}


-(void)checkMotionActivityPermissions
{
    BOOL result = [TripDiarySensorControlChecks checkMotionActivityPermissions];
    [self sendCheckResult:result
                 errorKey:@"activity_permission_off"];
}

-(void)checkNotificationsEnabled
{
    BOOL result = [TripDiarySensorControlChecks checkNotificationsEnabled];
    [self sendCheckResult:result
                 errorKey:@"notifications_blocked"];
}

/*
 * END: "check" implementations
 */

/*
 * BEGIN: "fix" implementations
 */

/*
 * In iOS, we cannot open overall settings, only the app settings.
 * Should we open it anyway? Or just tell the user what to do?
 * If we open it, we don't appear to get any callbacks when the location services are enabled.
 * there are callbacks only for the auth/permission changes.
 * https://developer.apple.com/documentation/corelocation/cllocationmanagerdelegate?language=objc
 * So let's just prompt the user. Which means that we can reuse the check with a different error message
 */

-(void) checkAndPromptLocationSettings
{
    BOOL result = [TripDiarySensorControlChecks checkLocationSettings];
    [self sendCheckResult:result
                 errorKey:@"location-turned-off-problem"];
}
@end
