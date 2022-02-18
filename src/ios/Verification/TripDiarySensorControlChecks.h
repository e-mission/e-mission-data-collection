#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import <UIKit/UIKit.h>
#import <CoreMotion/CoreMotion.h>
#import <UIKit/UIKit.h>

@interface TripDiarySensorControlChecks: NSObject

+(BOOL)checkLocationSettings;
+(BOOL)checkLocationPermissions;
+(BOOL)checkMotionActivitySettings;
+(BOOL)checkMotionActivityPermissions;
+(BOOL)checkNotificationsEnabled;

+(UIUserNotificationSettings*) REQUESTED_NOTIFICATION_TYPES;
@end
