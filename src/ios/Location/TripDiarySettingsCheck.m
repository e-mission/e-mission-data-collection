#import "TripDiarySettingsCheck.h"
#import "LocalNotificationManager.h"

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
@end
