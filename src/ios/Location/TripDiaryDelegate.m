//
//  TripDiaryDelegate.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/6/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "TripDiaryDelegate.h"
#import "BEMBuiltinUserCache.h"
#import "TripDiaryStateMachine.h"
#import "TripDiaryActions.h"
#import "LocalNotificationManager.h"
#import "SimpleLocation.h"
#import "LocationTrackingConfig.h"
#import "ConfigManager.h"
#import "BEMAppDelegate.h"
#import "SensorControlBackgroundChecker.h"

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
    
    if (_tdsm.currState != kOngoingTripState) {
        for (CLLocation* currLoc in locations) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"In state %@, Recieved location %@",
                                                       [TripDiaryStateMachine getStateName:_tdsm.currState], currLoc]
                                               showUI:TRUE];
            // Let's just see what a geofence check returns. Note that the state check currently generates transitions
            // based on the current state.
            // TODO: Refactor for simplicity - the delegate should just generate INSIDE and OUTSIDE notifications, and
            // the state machine should deal with the transitions
            CLRegion* currGeofence = [TripDiaryActions getGeofence:manager];
            if (currGeofence != nil) {
                // Can be null if we are in tracking_stopped state
                [manager requestStateForRegion:currGeofence];
            }
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
            
            if (![ConfigManager instance].is_duty_cycling) {
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


- (void)locationManager:(CLLocationManager *)manager
          didExitRegion:(CLRegion *)region {
    if([region.identifier compare:kCurrGeofenceID] != NSOrderedSame) {
        [LocalNotificationManager addNotification:
            [NSString stringWithFormat:@"exited region %@ that does not match current geofence %@",
                                        region.identifier, kCurrGeofenceID]
                            showUI:TRUE];

    }
    // Since we are going to keep the geofence around during ongoing tracking to ensure that
    // we are re-initalized, we will keep getting exit messages. We need to ignore if we are not
    // in the "waiting_for_trip_start" state.
    if (_tdsm.currState == kWaitingForTripStartState) {
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionExitedGeofence];
    } else {
        [LocalNotificationManager addNotification:
         [NSString stringWithFormat:@"Received geofence exit in state %@, ignoring",
                [TripDiaryStateMachine getStateName:_tdsm.currState]]
                                           showUI:TRUE];

    }
}

- (void)locationManager:(CLLocationManager *)manager
                didStartMonitoringForRegion:(CLRegion *)region {
    [LocalNotificationManager addNotification:
            [NSString stringWithFormat:@"started monitoring for region %@", region.identifier]
                                       showUI:TRUE];
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
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"In didChangeAuthorizationStatus, new authorization status = %d, always = %d", status, kCLAuthorizationStatusAuthorizedAlways]];

    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Calling TripDiarySettingsCheck from didChangeAuthorizationStatus to verify location service status and permission"]];

    // This is currently a separate check here instead of being folded in with checkLocationSettingsAndPermission
    // because the pre-iOS13 option to prompt the user requires a reference to the location manager
    // and the background call to checkLocationSettingsAndPermission from the remote push code
    // doesn't have that reference. Can simplify this after we stop supporting iOS13.
    if ([CLLocationManager authorizationStatus] == kCLAuthorizationStatusNotDetermined) {
        // STATUS SCREEN: handle with checkAndFix
        // [TripDiarySettingsCheck promptForPermission:manager];
    } else {
        // STATUS SCREEN: handle with checkAndFix
        // [TripDiarySettingsCheck checkLocationSettingsAndPermission:FALSE];
    }

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
    [AppDelegate checkNativeConsent];
    // According to the design pattern that I have followed here, I should post a notification from here
    // which will be handled by the state machine. However, as we have seen in the past, this does not really work
    // completely, because if a trip has ended, we want to create a geofence, and when we start monitoring the geofence,
    // it issues a callback, and the callback is not delivered while the application is in the background.
    // So what we want to do here is to do as much as we can in the same thread, and then wait for the geofence
    // monitoring to restart.
    // The methods of your delegate object are called from the thread in which you started the corresponding
    // location services. That thread must itself have an active run loop,
    // like the one found in your application’s main thread.
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"departure date is %@, isDistantDate? %@, after distantDate? %@ ", visit.departureDate, @([visit.departureDate isEqualToDate:[NSDate distantFuture]]),
                                               @([visit.departureDate compare:[NSDate distantFuture]])] showUI:FALSE];
    if ([visit.departureDate isEqualToDate:[NSDate distantFuture]]) {
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionVisitStarted];
    } else {
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionVisitEnded];
    }
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
