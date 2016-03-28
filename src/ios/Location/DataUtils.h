//
//  DataUtils.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/9/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

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

+ (NSArray*) getLastPoints:(int) nPoints;
+ (BOOL)hasTripEnded:(int)tripEndThresholdMins;
+ (void) saveBatteryAndSimulateUser;
// + (void) pushAndClearData:(void (^)(BOOL))completionHandler;

@end
