//
//  MotionActivity.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 11/2/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "MotionActivity.h"
#import "DataUtils.h"

@implementation MotionActivity

- (instancetype)initWithCMMotionActivity:(CMMotionActivity *)activity {
    self = [super init];
    self.stationary = activity.stationary;
    self.walking = activity.walking;
    self.running = activity.running;
    self.automotive = activity.automotive;
    self.cycling = activity.cycling;
    self.unknown = activity.unknown;
    
    self.ts = [DataUtils dateToTs:activity.startDate];
    self.confidence = [self getConfidenceString:activity.confidence];
    return self;
}

- (NSString*) getConfidenceString:(CMMotionActivityConfidence)confidenceEnum {
    if (confidenceEnum == CMMotionActivityConfidenceHigh) {
        return @"high";
    } else if (confidenceEnum == CMMotionActivityConfidenceMedium) {
        return @"medium";
    } else {
        assert(confidenceEnum == CMMotionActivityConfidenceLow);
        return @"low";
    }
}

@end
