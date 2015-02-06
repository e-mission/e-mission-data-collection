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

@interface TripDiaryStateMachine : NSObject <CLLocationManagerDelegate>

+ (NSString*)getTransitionName:(TripDiaryStateTransitions) transition;

- (void)checkGeofenceState:(UILabel*) resultField;

- (void)locationManager:(CLLocationManager *)manager
     didUpdateLocations:(NSArray *)locations;

- (void)locationManager:(CLLocationManager *)manager
     didExitRegion:(CLRegion *)region;

- (void)locationManager:(CLLocationManager *)manager
     didStartMonitoringForRegion:(CLRegion *)region;

- (void)locationManager:(CLLocationManager *)manager
      didDetermineState:(CLRegionState)state forRegion:(CLRegion *)region;

-(void)handleTransition:(TripDiaryStateTransitions) transition;

@end
