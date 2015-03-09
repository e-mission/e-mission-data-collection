//
//  TripDiaryActions.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/7/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreMotion/CoreMotion.h>
#import <CoreLocation/CoreLocation.h>
#import "TripDiaryStateMachine.h"

#define kCurrGeofenceID @"STATIONARY_GEOFENCE_LOCATION"
#define kHundred_Meters 100 // in meters
#define kTripEndStationaryMins 10 // in minutes

@interface TripDiaryActions : NSObject

+ (void) resetFSM:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr
  withActivityMgr:(CMMotionActivityManager*)activityMgr;

+ (void) oneTimeInitTracking:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr
     withActivityMgr:(CMMotionActivityManager*)activityMgr;

+ (void) startTracking:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr
       withActivityMgr:(CMMotionActivityManager*)activityMgr;

+ (void) stopTracking:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr
      withActivityMgr:(CMMotionActivityManager*)activityMgr;

+ (void)createGeofenceHere:(CLLocationManager *)manager inState:(TripDiaryStates)currState;

+ (void)deleteGeofence:(CLLocationManager*)manager;

+ (BOOL)hasTripEnded;

@end
