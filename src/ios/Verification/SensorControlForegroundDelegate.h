#import <Cordova/CDV.h>
#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import <UIKit/UIKit.h>
#import <CoreMotion/CoreMotion.h>

@interface SensorControlForegroundDelegate: NSObject

- (id)initWithDelegate:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command;;
- (void)checkLocationSettings;
- (void)checkLocationPermissions;
- (void)checkMotionActivitySettings;
- (void)checkMotionActivityPermissions;
- (void)checkNotificationsEnabled;

- (void) checkAndPromptLocationSettings;
- (void) checkAndPromptLocationPermissions;
@end
