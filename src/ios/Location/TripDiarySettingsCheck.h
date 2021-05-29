#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import <UIKit/UIKit.h>
#import <CoreMotion/CoreMotion.h>

@interface TripDiarySettingsCheck: NSObject

+(void)checkSettingsAndPermission;
+(void)openAppSettings;
+(void)showSettingsAlert:(UIAlertController*)alert;

@end
