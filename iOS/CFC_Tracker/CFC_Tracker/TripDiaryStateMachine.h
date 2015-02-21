//
//  TripDiaryStateMachine.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 1/31/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>
#import <UIKit/UIKit.h>

typedef enum : NSUInteger {
    kTransitionInitialize,
    kTransitionExitedGeofence,
    kTransitionStoppedMoving,
    kTransitionStopTracking
} TripDiaryStateTransitions;

typedef enum : NSUInteger {
    kStartState,
    kWaitingForTripStartState,
    kOngoingTripState
} TripDiaryStates;

typedef void(^GeofenceStatusCallback)(NSString* geofenceStatus);

@interface TripDiaryStateMachine : NSObject <CLLocationManagerDelegate>

@property TripDiaryStates currState;

+ (NSString*)getTransitionName:(TripDiaryStateTransitions) transition;
+ (NSString*)getStateName:(TripDiaryStates) state;

- (void)checkGeofenceState:(GeofenceStatusCallback) resultField;

- (void)locationManager:(CLLocationManager *)manager
     didUpdateLocations:(NSArray *)locations;

- (void)locationManager:(CLLocationManager *)manager
     didExitRegion:(CLRegion *)region;

- (void)locationManager:(CLLocationManager *)manager
     didStartMonitoringForRegion:(CLRegion *)region;

- (void)locationManager:(CLLocationManager *)manager
      didDetermineState:(CLRegionState)state forRegion:(CLRegion *)region;

- (void)locationManager:(CLLocationManager *)manager
    didChangeAuthorizationStatus:(CLAuthorizationStatus)status;

- (void)locationManager:(CLLocationManager *)manager
               didVisit:(CLVisit *)visit;

- (void)locationManagerDidPauseLocationUpdates:(CLLocationManager *)manager;

- (void)locationManagerDidResumeLocationUpdates:(CLLocationManager *)manager;

- (void)locationManager:(CLLocationManager *)manager
        monitoringDidFailForRegion:(CLRegion *)region
             withError:(NSError *)error;

- (void)locationManager:(CLLocationManager *)manager
       didUpdateHeading:(CLHeading *)newHeading;

-(void)handleTransition:(TripDiaryStateTransitions) transition;

@end
