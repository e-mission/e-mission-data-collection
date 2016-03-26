//
//  ConfigManager.h
//  emission
//
//  Created by Kalyanaraman Shankari on 3/25/16.
//
//

#import <Foundation/Foundation.h>
#import "LocationTrackingConfig.h"

@interface ConfigManager : NSObject

+ (LocationTrackingConfig*) instance;
+ (void) updateConfig:(LocationTrackingConfig*) newConfig;
@end
