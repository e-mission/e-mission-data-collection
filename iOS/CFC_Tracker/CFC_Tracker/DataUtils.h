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

+ (double)dateToTs:(NSDate*)date;
+ (NSDate*)dateFromTs:(double)ts;

+ (NSDictionary*)wrapperToDict:(NSObject*)obj;
+ (NSString*)wrapperToString:(NSObject*)obj;

+ (void)dictToWrapper:(NSDictionary*)dict wrapper:(NSObject*)obj;
+ (NSObject*)stringToWrapper:(NSString*)str wrapperClass:(Class)cls;

+ (NSMutableDictionary*)loadFromJSONString:(NSString *)jsonString;
+ (NSString*)saveToJSONString:(NSDictionary*) jsonDict;

+ (NSDate*) getMidnight;

+ (void) addPoint:(CLLocation*) currLoc;
+ (NSArray*) getLastPoints:(int) nPoints;

+ (void) addModeChange:(EMActivity*) activity;

+ (void) clearOngoingDb;
+ (void) clearStoredDb;

+ (BOOL)hasTripEnded:(int)tripEndThresholdMins;
+ (void) endTrip;
+ (void) convertOngoingToStored;
+ (void) pushAndClearData:(void (^)(BOOL))completionHandler;

+ (NSArray*) getTripsToPush;
+ (void) deletePushedTrips:(NSArray*) tripsToPush;
+ (void) deleteAllStoredTrips;

+ (NSMutableDictionary*) getJSONPlace:(CLLocation*) loc;
+ (NSMutableDictionary*) getTrackPoint:(CLLocation*) loc;
@end
