//
//  ConfigManager.h
//  emission
//
//  Created by Kalyanaraman Shankari on 3/25/16.
//
//

#import <Foundation/Foundation.h>
#import "LocationTrackingConfig.h"
#import "ConsentConfig.h"

@interface ConfigManager : NSObject

+ (LocationTrackingConfig*) instance;
+ (void) updateConfig:(LocationTrackingConfig*) newConfig;
+ (ConsentConfig*) getPriorConsent;
+ (BOOL) isConsented:(NSString*)reqConsent;
+ (void) setConsented:(ConsentConfig*) newConfig;
@end
