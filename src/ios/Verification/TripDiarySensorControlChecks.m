#import "TripDiarySensorControlChecks.h"
#import "LocalNotificationManager.h"
#import "BEMAppDelegate.h"
#import "BEMActivitySync.h"

#import <CoreMotion/CoreMotion.h>

@implementation TripDiarySensorControlChecks

+(BOOL)checkLocationSettings {
    return [CLLocationManager locationServicesEnabled];
}

// TODO: Decide whether we want to have a separate check for precise

+(BOOL)checkLocationPermissions {
    BOOL alwaysPerm = [CLLocationManager authorizationStatus] == kCLAuthorizationStatusAuthorizedAlways;
    BOOL precisePerm = TRUE;
            CLLocationManager* currLocMgr = [TripDiaryStateMachine instance].locMgr;
            if (@available(iOS 14.0, *)) {
                CLAccuracyAuthorization preciseOrNot = [currLocMgr accuracyAuthorization];
        precisePerm = preciseOrNot == CLAccuracyAuthorizationFullAccuracy;
            } else {
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"No precise location check needed for iOS < 14"]];
            }
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Returning combination of always = %@ and precise %@", @(alwaysPerm), @(precisePerm)]];
    return alwaysPerm && precisePerm;
}

+(BOOL)checkMotionActivitySettings {
#if TARGET_OS_SIMULATOR
    return TRUE;
#else
    return [CMMotionActivityManager isActivityAvailable] == YES;
#endif
}

+(BOOL)checkMotionActivityPermissions {
#if TARGET_OS_SIMULATOR
    return TRUE;
#else
        CMAuthorizationStatus currAuthStatus = [CMMotionActivityManager authorizationStatus];
    return currAuthStatus == CMAuthorizationStatusAuthorized;
#endif
}

+(BOOL)checkNotificationsEnabled {
    UIUserNotificationSettings* requestedSettings = [self REQUESTED_NOTIFICATION_TYPES];
    UIUserNotificationSettings* currSettings = [[UIApplication sharedApplication] currentUserNotificationSettings];
    return (currSettings.types & requestedSettings.types) == requestedSettings.types;
}

+(UIUserNotificationSettings*) REQUESTED_NOTIFICATION_TYPES {
    return [UIUserNotificationSettings
            settingsForTypes:UIUserNotificationTypeAlert|UIUserNotificationTypeBadge
            categories:nil];
}

@end
