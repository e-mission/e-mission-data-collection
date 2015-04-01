//
//  EMActivity.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/30/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "EMActivity.h"

@interface EMActivity()
@end

@implementation EMActivity

-(id)init {
    self.mode = kUnknownActivity;
    self.confidence = CMMotionActivityConfidenceLow;
    self.startDate = [NSDate date];
    return [super init];
}

- (NSString*)getActivityName {
    return [EMActivity getActivityName:self.mode];
}

+ (NSString*)getActivityName:(TripActivityStates) mode {
    if(mode == kWalkingActivity) {
        return @"walking";
    } else if (mode == kCyclingActivity) {
        return @"cycling";
    } else if (mode == kTransportActivity) {
        return @"transport";
    } else {
        return @"unknown";
    }
}

+ (TripActivityStates)getRelevantActivity:(CMMotionActivity*) activity {
    if (activity.automotive == YES) {
        return kTransportActivity;
    } else if (activity.cycling == YES) {
        return kCyclingActivity;
    } else if (activity.walking == YES) {
        return kWalkingActivity;
    } else {
        return kUnknownActivity;
    }
}



@end
