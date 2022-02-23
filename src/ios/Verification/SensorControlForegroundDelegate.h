#import <Cordova/CDV.h>
#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import <UIKit/UIKit.h>
#import <CoreMotion/CoreMotion.h>
#import "TripDiaryDelegate.h"
#import "AppDelegate.h"

@interface SensorControlForegroundDelegate: NSObject

- (id)initWithDelegate:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command;;
- (void)checkLocationSettings;
- (void)checkLocationPermissions;
- (void)checkMotionActivitySettings;
- (void)checkMotionActivityPermissions;
- (void)checkNotificationsEnabled;

- (void) checkAndPromptLocationSettings;
- (void) checkAndPromptLocationPermissions;
- (void) didChangeAuthorizationStatus:(CLAuthorizationStatus)status;

- (void) checkAndPromptFitnessPermissions;
- (void) didRecieveFitnessPermission:(BOOL)isPermitted;

- (void) checkAndPromptNotificationPermission;
- (void) didRegisterUserNotificationSettings:(UIUserNotificationSettings*)isPermitted;
@end

@interface TripDiaryDelegate (TripDiaryDelegatePermissions)
- (void)registerForegroundDelegate:(SensorControlForegroundDelegate*) foregroundDelegate;
- (void)locationManager:(CLLocationManager *)manager
    didChangeAuthorizationStatus:(CLAuthorizationStatus)status;

@end

@interface MotionActivityPermissionDelegate: NSObject
+ (void)registerForegroundDelegate:(SensorControlForegroundDelegate*) foregroundDelegate;
+ (void)readAndPromptForPermission;
@end

@interface AppDelegate (AppDelegate)
+ (void)registerForegroundDelegate:(SensorControlForegroundDelegate*) foregroundDelegate;
+ (void)readAndPromptForPermission;
@end

