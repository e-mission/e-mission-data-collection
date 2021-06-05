#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import <UIKit/UIKit.h>
#import <CoreMotion/CoreMotion.h>

@interface TripDiarySettingsCheck: NSObject

+(void)checkSettingsAndPermission;
+(void)checkLocationSettingsAndPermission:(BOOL)inBackground;
+(void)checkMotionSettingsAndPermission:(BOOL)inBackground;
+(void)promptForPermission:(CLLocationManager*)locMgr;
+(void)openAppSettings;
+(void)showSettingsAlert:(UIAlertController*)alert;

@end
