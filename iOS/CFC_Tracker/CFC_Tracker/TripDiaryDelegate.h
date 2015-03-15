//
//  TripDiaryDelegate.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/6/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "TripDiaryStateMachine.h"

@interface TripDiaryDelegate : NSObject <CLLocationManagerDelegate>

- (id)initWithMachine:(TripDiaryStateMachine*) machine;

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

@end
