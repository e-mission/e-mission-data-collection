//
//  DataUtils.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/9/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface DataUtils : NSObject

+ (void) endTrip;
+ (NSArray*) getTripsToPush;
+ (void) deletePushedTrips:(NSArray*) tripsToPush;
+ (void) deleteAllStoredTrips;

@end
