//
//  Battery.h
//  emission
//
//  Created by Kalyanaraman Shankari on 3/27/16.
//
//

#import <Foundation/Foundation.h>

@interface Battery : NSObject

// This returns a value between 0 and 1. Will be converted to a value between 0 and 100
// on the server side.
@property float battery_level_ratio;
@property UIDeviceBatteryState battery_status;
@property double ts;

@end
