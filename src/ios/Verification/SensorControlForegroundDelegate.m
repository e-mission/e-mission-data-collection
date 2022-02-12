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

-(void) checkAndPromptLocationPermissions
{
    NSString* callbackId = [command callbackId];
    @try {
        BOOL result = [TripDiarySensorControlChecks checkLocationPermissions];

        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"in checkLocationSettingsAndPermissions, locationService is enabled, but the permission is %d", [CLLocationManager authorizationStatus]]];

        if (result) {
            CDVPluginResult* result = [CDVPluginResult
                                       resultWithStatus:CDVCommandStatus_OK];
            [commandDelegate sendPluginResult:result callbackId:callbackId];
        } else {
            [self promptForPermission:[TripDiaryStateMachine instance].locMgr];
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

- (void) didChangeAuthorizationStatus:(CLAuthorizationStatus)status
{
    NSString* callbackId = [command callbackId];
    @try {
        if (status == kCLAuthorizationStatusAuthorizedAlways) {
            CDVPluginResult* result = [CDVPluginResult
                                       resultWithStatus:CDVCommandStatus_OK];
            [commandDelegate sendPluginResult:result callbackId:callbackId];
        } else {
            NSString* msg = NSLocalizedStringFromTable(@"location_permission_off_app_open",         @"DCLocalizable", nil);
            CDVPluginResult* result = [CDVPluginResult
                                       resultWithStatus:CDVCommandStatus_ERROR
                                       messageAsString:msg];
            [commandDelegate sendPluginResult:result callbackId:callbackId];
        }
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While handling auth callback, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [commandDelegate sendPluginResult:result callbackId:callbackId];
    }

}

-(void)promptForPermission:(CLLocationManager*)locMgr {
    if (IsAtLeastiOSVersion(@"13.0")) {
        NSLog(@"iOS 13+ detected, launching UI settings to easily enable always");
        // we want to leave the registration in the prompt for permission, since we don't want to register callbacks when we open the app settings for other reasons
        [[TripDiaryStateMachine delegate] registerForegroundDelegate:self];
        [self openAppSettings];
    }
    else {
        if ([CLLocationManager authorizationStatus] == kCLAuthorizationStatusNotDetermined) {
            if ([CLLocationManager instancesRespondToSelector:@selector(requestAlwaysAuthorization)]) {
                NSLog(@"Current location authorization = %d, always = %d, requesting always",
                      [CLLocationManager authorizationStatus], kCLAuthorizationStatusAuthorizedAlways);
                [locMgr requestAlwaysAuthorization];
            } else {
                // TODO: should we remove this? Not sure when it will ever be called, given that
                // requestAlwaysAuthorization is available in iOS8+
                [LocalNotificationManager addNotification:@"Don't need to request authorization, system will automatically prompt for it"];
            }
        } else {
            // we want to leave the registration in the prompt for permission, since we don't want to register callbacks when we open the app settings for other reasons
            [[TripDiaryStateMachine delegate] registerForegroundDelegate:self];
            [self openAppSettings];
        }
    }
}

-(void) openAppSettings {
    [[UIApplication sharedApplication] openURL:[NSURL URLWithString:UIApplicationOpenSettingsURLString] options:@{} completionHandler:^(BOOL success) {
    if (success) {
        NSLog(@"Opened url");
    } else {
        NSLog(@"Failed open");
    }}];
}

@end

@implementation TripDiaryDelegate (TripDiaryDelegatePermissions)

NSMutableArray* foregroundDelegateList;

/*
 * This is a bit tricky since this function is called whenever the authorization is changed
 * Design decisions are at:
 * https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-1035972636
 * https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-1035976420
 * https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-1035984060
 */

- (void)registerForegroundDelegate:(SensorControlForegroundDelegate*) foregroundDelegate
{
    if (foregroundDelegateList == nil) {
        foregroundDelegateList = [NSMutableArray new];
    }
    [foregroundDelegateList addObject:foregroundDelegate];
}

- (void)locationManager:(CLLocationManager *)manager
            didChangeAuthorizationStatus:(CLAuthorizationStatus)status {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"In checker's didChangeAuthorizationStatus, new authorization status = %d, always = %d", status, kCLAuthorizationStatusAuthorizedAlways]];

    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Calling TripDiarySettingsCheck from didChangeAuthorizationStatus to verify location service status and permission"]];
    if (foregroundDelegateList.count > 0) {
        for (id currDelegate in foregroundDelegateList) {
            [currDelegate didChangeAuthorizationStatus:(CLAuthorizationStatus)status];
        }
        [foregroundDelegateList removeAllObjects];
    } else {
        [SensorControlBackgroundChecker checkAppState];
    }
}

@end
