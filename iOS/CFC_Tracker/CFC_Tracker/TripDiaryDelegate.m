//
//  TripDiaryDelegate.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/6/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "TripDiaryDelegate.h"
#import "ConnectionSettings.h"
#import <Parse/Parse.h>
#import "BuiltinUserCache.h"
#import "TripDiaryStateMachine.h"
#import "TripDiaryActions.h"
#import "LocalNotificationManager.h"
#import "SimpleLocation.h"
#import "LocationTrackingConfig.h"

#define ACCURACY_THRESHOLD 200

@interface TripDiaryDelegate() {
    TripDiaryStateMachine* _tdsm;
    GeofenceStatusCallback currCallback;
}
@end

@implementation TripDiaryDelegate

- (id)initWithMachine:(TripDiaryStateMachine*) machine {
    _tdsm = machine;
    return [super init];
}

- (void)locationManager:(CLLocationManager *)manager
     didUpdateLocations:(NSArray *)locations {
    NSLog(@"Recieved %ld location updates ", (unsigned long)locations.count);
    
    if (locations.count == 0) {
        NSLog(@"locations.count = %lu in didUpdateLocations, early return", (unsigned long)locations.count);
        return;
    }
    
    NSAssert(locations.count > 0, @"locations.count = %lu in didUpdateLocations after early return check!", (unsigned long)locations.count);
    CLLocation *lastLocation = locations[locations.count - 1];
    NSLog(@"lastLocation is %f, %f", lastLocation.coordinate.longitude, lastLocation.coordinate.latitude);
    
    if (_tdsm.currState == kStartState) {
        // Find the last location
//        [TripDiaryActions stopTracking:CFCTransitionInitialize withLocationMgr:manager];
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Re-entering call to create geofence at location %@", lastLocation]];
        [TripDiaryActions createGeofenceHere:manager inState:_tdsm.currState];
    }
    
    if (_tdsm.currState != kOngoingTripState) {
        for (CLLocation* currLoc in locations) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Recieved location %@, ongoing trip = false", currLoc]
                                               showUI:TRUE];
        }
    }
    
    if (_tdsm.currState == kOngoingTripState) {
        /*
         * This can be called with either coarse or fine location tracking.
         * If we are supposed to be in the "ongoing trip" state, but this is
         * called with only coarse location tracking, then the app must have
         * been killed and restarted. Let's restart fine location tracking.
         *
         * Unfortunately, there is no way to check whether only coarse or
         * both coarse and fine location tracking is turned on, so we can't do this.
         * Hopefully, if fine location tracking was turned on, and the app was killed
         * and restarted then fine location tracking will still be turned on.
         */
        
        for (CLLocation* currLoc in locations) {
            NSLog(@"Adding point with timestamp %ld", (long)[currLoc.timestamp timeIntervalSince1970]);
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Recieved location %@, ongoing trip = true", currLoc]];
            SimpleLocation* currSimpleLoc = [[SimpleLocation alloc] initWithCLLocation:currLoc];
            [[BuiltinUserCache database] putSensorData:@"key.usercache.location" value:currSimpleLoc];
            
            if (![LocationTrackingConfig instance].isDutyCycling) {
                // We are not going to do any special filtering on the client side, so let's just return here
                return;
            }
            
            // Else, we want to only consider valid points while deciding whether a trip has ended or not
            // Let's get the last 10 points, just like on android

            NSArray* last10Points = [[BuiltinUserCache database]
                                      getLastSensorData:@"key.usercache.location" nEntries:10
                                        wrapperClass:[SimpleLocation class]];
            BOOL validPoint = FALSE;
            
            if (currLoc.horizontalAccuracy < ACCURACY_THRESHOLD) {
                if (last10Points.count == 0) {
                    validPoint = TRUE;
                } else {
                    assert(last10Points.count > 0);
                    SimpleLocation* lastPoint = last10Points[last10Points.count - 1];
                    if ([currSimpleLoc distanceFromLocation:lastPoint] != 0) {
                        validPoint = TRUE;
                    } else {
                        NSLog(@"Duplicate point, %@, skipping", currLoc);
                    }
                }
            } else {
                NSLog(@"Found bad quality point %@, skipping ", currLoc);
            }
            NSLog(@"Current point status = %@", @(validPoint));
            if (validPoint) {
                [[BuiltinUserCache database] putSensorData:@"key.usercache.filtered_location" value:currSimpleLoc];
            }
        }
    }
}

- (void)locationManager:(CLLocationManager *)manager
       didFailWithError:(NSError *)error {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Location tracking failed with error %@", error]];
}

- (void)logPastHourCollectionCount {
    NSDate* dateNow = [NSDate date];
    NSCalendar *gregorian = [[NSCalendar alloc]
                             initWithCalendarIdentifier:NSGregorianCalendar];
    NSDateComponents* dayHourMinuteComponents = [gregorian components:(NSDayCalendarUnit|NSHourCalendarUnit|NSMinuteCalendarUnit) fromDate:dateNow];
    
    if ([dayHourMinuteComponents minute] == 0) {
        /*
         * If this turns out to be a performance hassle, replace by
         */
        NSDate* hourAgo = [dateNow dateByAddingTimeInterval:-(60 * 60)];
        TimeQuery* tq = [TimeQuery new];
        tq.timeKey = @"metadata.usercache.write_ts";
        tq.startDate = hourAgo;
        tq.endDate = dateNow;

        NSArray* pastHourTrips = [[BuiltinUserCache database]
                                  getSensorDataForInterval:@"key.usercache.location" tq:tq wrapperClass:[SimpleLocation class]];

        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Recived = %ld updates in the %ld hour of %ld day",
                                                   (long)pastHourTrips.count, (long)[dayHourMinuteComponents hour],
                                                   (long)[dayHourMinuteComponents day]]];
        
    }
}

- (void)locationManager:(CLLocationManager *)manager
          didExitRegion:(CLRegion *)region {
    if([region.identifier compare:kCurrGeofenceID] != NSOrderedSame) {
        NSLog(@"exited region %@ that does not match current geofence %@", region.identifier, kCurrGeofenceID);
    }
    // Since we are going to keep the geofence around during ongoing tracking to ensure that
    // we are re-initalized, we will keep getting exit messages. We need to ignore if we are not
    // in the "waiting_for_trip_start" state.
    if (_tdsm.currState == kWaitingForTripStartState) {
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionExitedGeofence];
    } else {
        NSLog(@"Received geofence exit in state %@, ignoring",
              [TripDiaryStateMachine getStateName:_tdsm.currState]);
    }
}

- (void)locationManager:(CLLocationManager *)manager
                didStartMonitoringForRegion:(CLRegion *)region {
    [LocalNotificationManager addNotification:
        [NSString stringWithFormat:@"started monitoring for region %@", region.identifier]];
    // Needed in order to avoid getting a failure in the geofence creation by querying it too quickly
    // http://www.cocoanetics.com/2014/05/radar-monitoring-clregion-immediately-after-removing-one-fails/
    // Not documented anywhere other than a blog post!!
    [NSThread sleepForTimeInterval:0.5];
    [manager requestStateForRegion:region];
}

- (void)locationManager:(CLLocationManager *)manager
      didDetermineState:(CLRegionState)state forRegion:(CLRegion *)region {
    
    NSString* stateStr = [TripDiaryDelegate geofenceStateToString:state];
    if (currCallback != NULL) {
        currCallback(stateStr);
    }
    
    /*
     * So we have created a geofence, and since we in a state where we are yet to start a trip,
     * we expect that we are inside the geofence. But we aren't!
     * So we must be in motion, and moving so fast that we went outside the geofence in the time that it
     * took for the OS to initialize the geofence. Let's officially acknowledge that and transition to
     * having started the trip. This is also the safer option since if we made a mistake here
     * (say because of low accuracy), then we will quickly end the trip, and the low distance trip
     * will be filtered on the server side. But if we made a mistake in staying in the same state, we
     * will never leave the geofence, since we are already outside, and we won't track any trips at all.
     */
    
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Current state of region %@ is %d (%@)", region.identifier, (int)state, stateStr]];
    NSString* currTransition = nil;
    if (state == CLRegionStateInside) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Received INSIDE geofence state when currState = %@", [TripDiaryStateMachine getStateName:_tdsm.currState]]];
        if (_tdsm.currState == kStartState) {
            currTransition = CFCTransitionInitComplete;
        } else if (_tdsm.currState == kWaitingForTripStartState) {
            // This is a duplicate or called by a status checking function
            currTransition = CFCTransitionNOP;
        } else if (_tdsm.currState == kOngoingTripState) {
            currTransition = CFCTransitionEndTripTracking;
        }
    } else if (state == CLRegionStateOutside) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Received OUTSIDE geofence state when currState = %@", [TripDiaryStateMachine getStateName:_tdsm.currState]]];
        if (_tdsm.currState == kStartState) {
            currTransition = CFCTransitionExitedGeofence;
        } else if (_tdsm.currState == kWaitingForTripStartState) {
            // TODO: Figure out whether we should return a NOP or an exit here. Note that we should never create a geofence
            // when we are already in the waiting for geofence state.
            currTransition = CFCTransitionNOP;
        } else if (_tdsm.currState == kOngoingTripState) {
            currTransition = CFCTransitionTripRestarted;
        }
    } else {
        // state == CLRegionStateUnknown
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Received UNKNOWN geofence state when currState = %@, unclear what to do", [TripDiaryStateMachine getStateName:_tdsm.currState]]];
        // It looks like geofence creation is not complete.
        // TODO: Figure out how to deal with this error condition
    }
    
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                        object:currTransition];

    currCallback = NULL;
}

- (void)locationManager:(CLLocationManager *)manager
            didChangeAuthorizationStatus:(CLAuthorizationStatus)status {
    NSLog(@"New authorization status = %d, always = %d", status, kCLAuthorizationStatusAuthorizedAlways);
    if (_tdsm.currState == kStartState) {
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionInitialize];
    }
}



+(NSString*)geofenceStateToString:(CLRegionState)state {
    if (state == CLRegionStateInside) {
        return @"inside";
    } else if (state == CLRegionStateOutside) {
        return @"outside";
    } else {
        return @"unknown";
    }
}

- (void)locationManager:(CLLocationManager *)manager
               didVisit:(CLVisit *)visit
{
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Received visit notification = %@",
                                               visit] showUI:true];
}

- (void)locationManagerDidPauseLocationUpdates:(CLLocationManager *)manager
{
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Location updates PAUSED"] showUI:true];
}

- (void)locationManagerDidResumeLocationUpdates:(CLLocationManager *)manager
{
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Location updates RESUMED"] showUI:true];
}

- (void)locationManager:(CLLocationManager *)manager
                monitoringDidFailForRegion:(CLRegion *)region
                withError:(NSError *)error {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Monitoring failed for region %@ with error %@", region, error]];
    NSLog(@"Number of existing geofences = %lu", (unsigned long)manager.monitoredRegions.count);
}

- (void)locationManager:(CLLocationManager *)manager didUpdateHeading:(CLHeading *)newHeading {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Got new heading %@", newHeading]];
}

@end
