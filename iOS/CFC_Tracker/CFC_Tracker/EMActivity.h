//
//  EMActivity.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/30/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreMotion/CoreMotion.h>

typedef enum : NSUInteger {
    kTransportActivity,
    kCyclingActivity,
    kWalkingActivity,
    kUnknownActivity
} TripActivityStates;

/*
 * Our version of CMMotionActivity. Created because of difficulties with mocking CMMotionActivity
 * for the purposes of testing. This also allows us to deserialize this from the database.
 */

@interface EMActivity : NSObject

@property TripActivityStates mode;
@property CMMotionActivityConfidence confidence;
@property NSDate* startDate;
- (NSString*)getActivityName;

+ (NSString*)getActivityName:(TripActivityStates) mode;
+ (TripActivityStates)getRelevantActivity:(CMMotionActivity*) activity;

@end
