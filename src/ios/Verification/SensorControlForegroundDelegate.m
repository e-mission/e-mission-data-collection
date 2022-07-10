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
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"In delegate didChangeAuthorizationStatus, newStatus = %d", status]];
    [[TripDiaryStateMachine instance].locMgr stopUpdatingLocation];
    NSString* callbackId = [command callbackId];
    @try {
        if (status == kCLAuthorizationStatusAuthorizedAlways) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"status == always, restarting FSM if start state"]];
            [SensorControlBackgroundChecker restartFSMIfStartState];
            CDVPluginResult* result = [CDVPluginResult
                                       resultWithStatus:CDVCommandStatus_OK];
            [commandDelegate sendPluginResult:result callbackId:callbackId];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"status %d != always %d, returning error", status, kCLAuthorizationStatusAuthorizedAlways]];
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

- (void)checkAndPromptFitnessPermissions {
    NSString* callbackId = [command callbackId];
#if TARGET_OS_SIMULATOR
    CDVPluginResult* result = [CDVPluginResult
                                resultWithStatus:CDVCommandStatus_OK];
    [commandDelegate sendPluginResult:result callbackId:callbackId];
#else
    @try {
        if ([CMMotionActivityManager isActivityAvailable] == YES) {
            [LocalNotificationManager addNotification:@"Motion activity available, checking auth status"];
            CMAuthorizationStatus currAuthStatus = [CMMotionActivityManager authorizationStatus];
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Auth status = %ld", currAuthStatus]];
        
            if (currAuthStatus == CMAuthorizationStatusAuthorized) {
                CDVPluginResult* result = [CDVPluginResult
                                           resultWithStatus:CDVCommandStatus_OK];
                [commandDelegate sendPluginResult:result callbackId:callbackId];
            }

            if (currAuthStatus == CMAuthorizationStatusNotDetermined) {
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Activity status not determined, initializing to get regular prompt"]];
                [MotionActivityPermissionDelegate registerForegroundDelegate:self];
                [MotionActivityPermissionDelegate readAndPromptForPermission];
            }
            
            if (currAuthStatus == CMAuthorizationStatusRestricted) {
                /*
                 It looked like this status is read when the app starts and cached after that. This is not resolvable from the code, so we just change the resulting message to highlight that the app needs to be restarted.
                     Gory details at: https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-1040948835
                 */
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Activity detection not enabled, prompting user to change Settings"]];
                NSString* msg = NSLocalizedStringFromTable(@"activity-turned-off-problem", @"DCLocalizable", nil);
                CDVPluginResult* result = [CDVPluginResult
                                           resultWithStatus:CDVCommandStatus_ERROR
                                           messageAsString:msg];
                [commandDelegate sendPluginResult:result callbackId:callbackId];
            }
        

            if ([CMMotionActivityManager authorizationStatus] == CMAuthorizationStatusDenied) {
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Activity status denied, opening app settings to enable"]];
                
                NSString* msg = NSLocalizedStringFromTable(@"activity-permission-problem", @"DCLocalizable", nil);
                CDVPluginResult* result = [CDVPluginResult
                                           resultWithStatus:CDVCommandStatus_ERROR
                                           messageAsString:msg];
                [commandDelegate sendPluginResult:result callbackId:callbackId];
                [self openAppSettings];
            }
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Activity detection unsupported, all trips will be UNKNOWN"]];
            NSString* msg = NSLocalizedStringFromTable(@"activity-detection-unsupported", @"DCLocalizable", nil);
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
#endif
}

-(void) didRecieveFitnessPermission:(BOOL)isPermitted
{
    [self sendCheckResult:isPermitted
                 errorKey:@"activity-permission-problem"];
}

- (void)checkAndPromptNotificationPermission {
    NSString* callbackId = [command callbackId];
    @try {
        if ([UIApplication instancesRespondToSelector:@selector(registerUserNotificationSettings:)]) {
            UIUserNotificationSettings* requestedSettings = [TripDiarySensorControlChecks REQUESTED_NOTIFICATION_TYPES];
            [AppDelegate registerForegroundDelegate:self];
            [[UIApplication sharedApplication] registerUserNotificationSettings:requestedSettings];
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

- (void) didRegisterUserNotificationSettings:(UIUserNotificationSettings*)newSettings {
    NSString* callbackId = [command callbackId];
    UIUserNotificationSettings* requestedSettings = [TripDiarySensorControlChecks REQUESTED_NOTIFICATION_TYPES];
    if (requestedSettings.types == newSettings.types) {
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK];
        [commandDelegate sendPluginResult:result callbackId:callbackId];
    } else {
        NSString* msg = NSLocalizedStringFromTable(@"notifications_blocked_app_open", @"DCLocalizable", nil);
        [self openAppSettings];
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
        [[TripDiaryStateMachine instance].locMgr startUpdatingLocation];
        [self openAppSettings];
    }
    else {
        if ([CLLocationManager authorizationStatus] == kCLAuthorizationStatusNotDetermined) {
            if ([CLLocationManager instancesRespondToSelector:@selector(requestAlwaysAuthorization)]) {
                NSLog(@"Current location authorization = %d, always = %d, requesting always",
                      [CLLocationManager authorizationStatus], kCLAuthorizationStatusAuthorizedAlways);
                [[TripDiaryStateMachine delegate] registerForegroundDelegate:self];
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

    if (foregroundDelegateList.count > 0) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"%lu foreground delegates found, calling didChangeAuthorizationStatus to return the new value %d", (unsigned long)foregroundDelegateList.count, status]];

        for (id currDelegate in foregroundDelegateList) {
            [currDelegate didChangeAuthorizationStatus:(CLAuthorizationStatus)status];
        }
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Notified all foreground delegates, removing all of them"]];
        [foregroundDelegateList removeAllObjects];
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"No foreground delegate found, calling SensorControlBackgroundChecker from didChangeAuthorizationStatus to verify location service status and permission"]];
        [SensorControlBackgroundChecker checkAppState];
    }
}
@end

@implementation MotionActivityPermissionDelegate
NSMutableArray* foregroundDelegateList;

/*
 * This is a bit tricky since this function is called whenever the authorization is changed
 * Design decisions are at:
 * https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-1035972636
 * https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-1035976420
 * https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-1035984060
 */

+(void)registerForegroundDelegate:(SensorControlForegroundDelegate*) foregroundDelegate
{
    if (foregroundDelegateList == nil) {
        foregroundDelegateList = [NSMutableArray new];
    }
    [foregroundDelegateList addObject:foregroundDelegate];
}

+(void)readAndPromptForPermission {
    CMMotionActivityManager* activityMgr = [[CMMotionActivityManager alloc] init];
    NSOperationQueue* mq = [NSOperationQueue mainQueue];
    NSDate* startDate = [NSDate new];
    NSTimeInterval dayAgoSecs = 24 * 60 * 60;
    NSDate* endDate = [NSDate dateWithTimeIntervalSinceNow:-(dayAgoSecs)];
    /* This queryActivity call is the one that prompt the user for permission */
    [activityMgr queryActivityStartingFromDate:startDate toDate:endDate toQueue:mq withHandler:^(NSArray *activities, NSError *error) {
        if (error == nil) {
            [LocalNotificationManager addNotification:@"activity recognition works fine"];
            if (foregroundDelegateList.count > 0) {
                for (id currDelegate in foregroundDelegateList) {
                    [currDelegate didRecieveFitnessPermission:TRUE];
                }
                [foregroundDelegateList removeAllObjects];
            } else {
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"no foreground delegate callbacks found for fitness sensors, ignoring success..."]];
            }
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Error %@ while reading activities, travel mode detection may be unavailable", error]];
            if (foregroundDelegateList.count > 0) {
                for (id currDelegate in foregroundDelegateList) {
                    [currDelegate didRecieveFitnessPermission:FALSE];
                }
                [foregroundDelegateList removeAllObjects];
            } else {
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"no foreground delegate callbacks found for fitness sensor error %@, ignoring...", error]];
            }
        }
    }];
}

@end

@implementation AppDelegate(CollectionPermission)
NSMutableArray* foregroundDelegateList;

/*
 * This is a bit tricky since this function is called whenever the authorization is changed
 * Design decisions are at:
 * https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-1035972636
 * https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-1035976420
 * https://github.com/e-mission/e-mission-docs/issues/680#issuecomment-1035984060
 */

+(void)registerForegroundDelegate:(SensorControlForegroundDelegate*) foregroundDelegate
{
    if (foregroundDelegateList == nil) {
        foregroundDelegateList = [NSMutableArray new];
    }
    [foregroundDelegateList addObject:foregroundDelegate];
}

-(void) application:(UIApplication*)application didRegisterUserNotificationSettings:(UIUserNotificationSettings *)notificationSettings
{
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Received callback from user notification settings "]
                                       showUI:FALSE];
    if (foregroundDelegateList.count > 0) {
        for (id currDelegate in foregroundDelegateList) {
            [currDelegate didRegisterUserNotificationSettings:notificationSettings];
        }
        [foregroundDelegateList removeAllObjects];
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"no foreground delegate callbacks found for notifications, ignoring success..."]];
    }
}
@end
