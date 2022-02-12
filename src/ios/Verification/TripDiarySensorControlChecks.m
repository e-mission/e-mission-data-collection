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
    return [CMMotionActivityManager isActivityAvailable] == YES;
}

+(BOOL)checkMotionActivityPermissions {
        CMAuthorizationStatus currAuthStatus = [CMMotionActivityManager authorizationStatus];
    return currAuthStatus == CMAuthorizationStatusAuthorized;
}

+(BOOL)checkNotificationsEnabled {
    UIUserNotificationSettings* requestedSettings = [self REQUESTED_NOTIFICATION_TYPES];
    UIUserNotificationSettings* currSettings = [[UIApplication sharedApplication] currentUserNotificationSettings];
    return requestedSettings.types == currSettings.types;
}

+(UIUserNotificationSettings*) REQUESTED_NOTIFICATION_TYPES {
    return [UIUserNotificationSettings
            settingsForTypes:UIUserNotificationTypeAlert|UIUserNotificationTypeBadge
            categories:nil];
}


/*
+(void)checkSettingsAndPermission {
    [TripDiarySettingsCheck checkLocationSettingsAndPermission:TRUE];
    [TripDiarySettingsCheck checkMotionSettingsAndPermission:TRUE];
}

+(void)checkMotionSettingsAndPermission:(BOOL)inBackground {
    if ([CMMotionActivityManager isActivityAvailable] == YES) {
        [LocalNotificationManager addNotification:@"Motion activity available, checking auth status"];
        CMAuthorizationStatus currAuthStatus = [CMMotionActivityManager authorizationStatus];
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Auth status = %ld", currAuthStatus]];

        if (currAuthStatus == CMAuthorizationStatusRestricted) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Activity detection not enabled, prompting user to change Settings"]];
            if (inBackground) {
                NSString* errorDescription = NSLocalizedStringFromTable(@"activity-turned-off-problem", @"DCLocalizable", nil);
                [LocalNotificationManager showNotificationAfterSecs:errorDescription withUserInfo:NULL secsLater:60];
            }
            [TripDiarySettingsCheck showLaunchSettingsAlert:@"activity-detection-unsupported" withMessage:@"activity-turned-off-problem" button:@"fix-service-action-button"];
        }
        if (currAuthStatus == CMAuthorizationStatusNotDetermined) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Activity status not determined, initializing to get regular prompt"]];
            [BEMActivitySync initWithConsent];
        }
        if ([CMMotionActivityManager authorizationStatus] == CMAuthorizationStatusDenied) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Activity status denied, opening app settings to enable"]];
            NSString* errorDescription = NSLocalizedStringFromTable(@"activity-permission-problem", @"DCLocalizable", nil);
            if (inBackground) {
            [LocalNotificationManager showNotificationAfterSecs:errorDescription withUserInfo:NULL secsLater:60];
        }
            [TripDiarySettingsCheck showLaunchSettingsAlert:@"permission-problem-title" withMessage:@"activity-permission-problem" button:@"fix-permission-action-button"];
        }
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Activity detection unsupported, all trips will be UNKNOWN"]];
        NSString* title = NSLocalizedStringFromTable(@"activity-detection-unsupported", @"DCLocalizable", nil);
        NSString* message = NSLocalizedStringFromTable(@"travel-mode-unknown", @"DCLocalizable", nil);

        UIAlertController* alert = [UIAlertController alertControllerWithTitle:title
                                   message:message
                                   preferredStyle:UIAlertControllerStyleAlert];

        UIAlertAction* defaultAction = [UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault
            handler:^(UIAlertAction * action) {
        }];
        [alert addAction:defaultAction];
        [TripDiarySettingsCheck showSettingsAlert:alert];
    }
}

+(void)showLaunchSettingsAlert:(NSString*)titleTag withMessage:(NSString*)messageTag button:(NSString*)buttonTag {
    NSString* title = NSLocalizedStringFromTable(titleTag, @"DCLocalizable", nil);
    NSString* message = NSLocalizedStringFromTable(messageTag, @"DCLocalizable", nil);
    NSString* errorAction = NSLocalizedStringFromTable(buttonTag, @"DCLocalizable", nil);

    UIAlertController* alert = [UIAlertController alertControllerWithTitle:title
                               message:message
                               preferredStyle:UIAlertControllerStyleAlert];

    UIAlertAction* defaultAction = [UIAlertAction actionWithTitle:errorAction style:UIAlertActionStyleDefault
        handler:^(UIAlertAction * action) {
        [TripDiarySettingsCheck openAppSettings];
    }];
    [alert addAction:defaultAction];
    [TripDiarySettingsCheck showSettingsAlert:alert];
}

+(void) openAppSettings {
    [[UIApplication sharedApplication] openURL:[NSURL URLWithString:UIApplicationOpenSettingsURLString] options:@{} completionHandler:^(BOOL success) {
    if (success) {
        NSLog(@"Opened url");
    } else {
        NSLog(@"Failed open");
    }}];
}

+(void) showSettingsAlert:(UIAlertController*)alert {
    CDVAppDelegate *ad = [[UIApplication sharedApplication] delegate];
    CDVViewController *vc = ad.viewController;
    [vc presentViewController:alert animated:YES completion:nil];
}
*/

@end
