//
//  DataUtils.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/9/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import "EMActivity.h"

@interface DataUtils : NSObject
+ (NSString*)dateToString:(NSDate*)date;
+ (NSDate*)dateFromString:(NSString*)string;
+ (NSDate*) getMidnight;

+ (void) addPoint:(CLLocation*) currLoc;
+ (NSArray*) getLastPoints:(int) nPoints;

+ (void) addModeChange:(EMActivity*) activity;

+ (void) clearOngoingDb;
+ (void) clearStoredDb;

+ (void) endTrip;
+ (void) convertOngoingToStored;
+ (NSArray*) getTripsToPush;
+ (void) deletePushedTrips:(NSArray*) tripsToPush;
+ (void) deleteAllStoredTrips;

+ (NSMutableDictionary*) getJSONPlace:(CLLocation*) loc;
+ (NSMutableDictionary*) getTrackPoint:(CLLocation*) loc;
@end
