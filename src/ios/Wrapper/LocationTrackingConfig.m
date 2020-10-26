//
//  LocationTrackingConfig.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 11/6/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "LocationTrackingConfig.h"
#import "BEMBuiltinUserCache.h"
#import <CoreLocation/CoreLocation.h>

#define kOneMeter 1 // in meters
#define kFiveMeters 5 // in meters
#define kFifty_Meters 50 // in meters
#define kHundred_Meters 100 // in meters
#define kTripEndStationaryMins 10 // in minutes

@implementation LocationTrackingConfig

-(id)init {
    self.is_duty_cycling = YES;
    self.simulate_user_interaction = NO;
    self.accuracy = kCLLocationAccuracyBest;
    self.accuracy_threshold = 200;
    self.filter_distance = kOneMeter;
    self.filter_time = -1; // unused
    self.geofence_radius = kHundred_Meters;
    self.trip_end_stationary_mins = kTripEndStationaryMins;
    self.ios_use_visit_notifications_for_detection = YES;
    self.ios_use_remote_push_for_sync = YES;
    self.android_geofence_responsiveness = -1; // unused
    return self;
}

@end
