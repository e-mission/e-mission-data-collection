//
//  BEMTrackingConfigManager.h
//  emission
//
//  Created by Kalyanaraman Shankari on 3/25/16.
//
//

#import <Foundation/Foundation.h>
#import "LocationTrackingConfig.h"
#import "ConsentConfig.h"

@interface BEMTrackingConfigManager : NSObject

+ (LocationTrackingConfig*) instance;
+ (void) updateTrackingConfig:(LocationTrackingConfig*) newTrackingConfig;

+ (NSDictionary*) getDeploymentConfig;
+ (BOOL) upgradeDeploymentConfig:(NSDictionary*)newDeploymentConfig;

+ (ConsentConfig*) getConsentConfig;
+ (ConsentConfig*) getPriorConsent;
+ (BOOL) isConsented:(NSString*)reqConsent;
+ (void) setConsented:(ConsentConfig*) newConfig;

@end
