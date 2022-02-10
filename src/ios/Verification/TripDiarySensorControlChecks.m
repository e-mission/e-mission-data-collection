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

+(void)checkLocationSettingsAndPermission:(BOOL)inBackground {
    if (![CLLocationManager locationServicesEnabled]) {
        // first, check to see if location services are enabled
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"in checkLocationSettingsAndPermissions, locationService is not enabled"]];

        NSString* errorDescription = NSLocalizedStringFromTable(@"location-turned-off-problem", @"DCLocalizable", nil);
        if (inBackground) {
        [LocalNotificationManager showNotificationAfterSecs:errorDescription withUserInfo:NULL secsLater:60];
    }
    } else {
        // next, check to see if it is "always"
    if ([CLLocationManager authorizationStatus] != kCLAuthorizationStatusAuthorizedAlways) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"in checkLocationSettingsAndPermissions, locationService is enabled, but the permission is %d", [CLLocationManager authorizationStatus]]];

        NSString* errorDescription = NSLocalizedStringFromTable(@"location-permission-problem", @"DCLocalizable", nil);
            if (inBackground) {
        [LocalNotificationManager showNotificationAfterSecs:errorDescription withUserInfo:NULL secsLater:60];
    }
            [TripDiarySettingsCheck showLaunchSettingsAlert:@"permission-problem-title" withMessage:@"location-permission-problem" button:@"fix-permission-action-button"];
        } else {
            // finally, check to see if it is "precise"
            // we currently check these in a cascade, since generating multiple alerts results in
            // "Attempt to present <UIAlertController: 0x7fd6c1018400> on <MainViewController: 0x7fd6e7c0a2a0> (from <MainViewController: 0x7fd6e7c0a2a0>) which is already presenting <UIAlertController: 0x7fd6e000ac00>."
            CLLocationManager* currLocMgr = [TripDiaryStateMachine instance].locMgr;
            if (@available(iOS 14.0, *)) {
                CLAccuracyAuthorization preciseOrNot = [currLocMgr accuracyAuthorization];
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"in checkLocationSettingsAndPermissions, locationService is enabled, permission is 'always', accuracy status is %ld", preciseOrNot]];
                if (preciseOrNot != CLAccuracyAuthorizationFullAccuracy) {
                    [TripDiarySettingsCheck showLaunchSettingsAlert:@"permission-problem-title" withMessage:@"precise-location-problem" button:@"fix-permission-action-button"];
                }
            } else {
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"No precise location check needed for iOS < 14"]];
            }
        }
    }
}

+(void)promptForPermission:(CLLocationManager*)locMgr {
    if (IsAtLeastiOSVersion(@"13.0")) {
        NSLog(@"iOS 13+ detected, launching UI settings to easily enable always");
        [TripDiarySettingsCheck openAppSettings];
    }
    else if ([CLLocationManager instancesRespondToSelector:@selector(requestAlwaysAuthorization)]) {
        NSLog(@"Current location authorization = %d, always = %d, requesting always",
              [CLLocationManager authorizationStatus], kCLAuthorizationStatusAuthorizedAlways);
        [locMgr requestAlwaysAuthorization];
    } else {
        // TODO: should we remove this? Not sure when it will ever be called, given that
        // requestAlwaysAuthorization is available in iOS8+
        [LocalNotificationManager addNotification:@"Don't need to request authorization, system will automatically prompt for it"];
    }
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
