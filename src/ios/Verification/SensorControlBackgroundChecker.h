#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import <UIKit/UIKit.h>
#import <CoreMotion/CoreMotion.h>

@interface SensorControlBackgroundChecker: NSObject

+(void)restartFSMIfStartState;
+(void)checkAppState;

@end
