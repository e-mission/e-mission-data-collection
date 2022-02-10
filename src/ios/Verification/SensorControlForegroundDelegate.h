#import <Cordova/CDV.h>
#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import <UIKit/UIKit.h>
#import <CoreMotion/CoreMotion.h>

@interface SensorControlForegroundDelegate: NSObject

+(void)checkLocationSettings:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command;
+(void)checkLocationPermissions:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command;
+(void)checkMotionActivitySettings:(id<CDVCommandDelegate>)delegate
forCommand:(CDVInvokedUrlCommand*)command;
+(void)checkMotionActivityPermissions:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command;
+(void)checkNotificationsEnabled:(id<CDVCommandDelegate>)delegate forCommand:(CDVInvokedUrlCommand*)command;
@end
