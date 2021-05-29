#import "TripDiarySettingsCheck.h"
#import "LocalNotificationManager.h"
#import "BEMAppDelegate.h"

#import <CoreMotion/CoreMotion.h>

@implementation TripDiarySettingsCheck

+(void)checkSettingsAndPermission {
    if (![CLLocationManager locationServicesEnabled]) {
        NSString* errorDescription = NSLocalizedStringFromTable(@"location-turned-off-problem", @"DCLocalizable", nil);
        [LocalNotificationManager showNotificationAfterSecs:errorDescription withUserInfo:NULL secsLater:60];
    }
    if ([CLLocationManager authorizationStatus] != kCLAuthorizationStatusAuthorizedAlways) {
        NSString* errorDescription = NSLocalizedStringFromTable(@"location-permission-problem", @"DCLocalizable", nil);
        [LocalNotificationManager showNotificationAfterSecs:errorDescription withUserInfo:NULL secsLater:60];
    }
    if ([CMMotionActivityManager isActivityAvailable] == YES) {
        [LocalNotificationManager addNotification:@"Motion activity available, checking auth status"];
        if ([CMMotionActivityManager authorizationStatus] == CMAuthorizationStatusDenied) {
            NSString* errorDescription = NSLocalizedStringFromTable(@"activity-permission-problem", @"DCLocalizable", nil);
            [LocalNotificationManager showNotificationAfterSecs:errorDescription withUserInfo:NULL secsLater:60];
        }
    }
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

@end
