//
//  TripDiaryActions.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/7/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "TripDiaryActions.h"
#import "TripDiaryStateMachine.h"
#import "OngoingTripsDatabase.h"
#import "LocalNotificationManager.h"
#import "DataUtils.h"

@implementation TripDiaryActions

+ (void) resetFSM:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr
                              withActivityMgr:(CMMotionActivityManager*)activityMgr {
    if ([transition isEqualToString:CFCTransitionForceStopTracking]) {
        [self stopTrackingLocation:locMgr];
        [self stopTrackingActivity:activityMgr];
        [self deleteGeofence:locMgr];
        [self stopTrackingSignificantLocationChanges:locMgr];
        [self stopTrackingVisits:locMgr];
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionTrackingStopped];
    }
}

+ (void) oneTimeInitTracking:(NSString *)transition withLocationMgr:(CLLocationManager *)locMgr
             withActivityMgr:(CMMotionActivityManager *)activityMgr {
    if ([transition isEqualToString:CFCTransitionInitialize]) {
        [self startTrackingSignificantLocationChanges:locMgr];
        [self startTrackingVisits:locMgr];
    }
}

+ (void) startTracking:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr
       withActivityMgr:(CMMotionActivityManager*)activityMgr {
    [self startTrackingLocation:locMgr];
    [self startTrackingActivity:activityMgr];
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                        object:CFCTransitionTripStarted];
}

+ (void) stopTracking:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr
                                             withActivityMgr:(CMMotionActivityManager*)activityMgr {
    [self stopTrackingLocation:locMgr];
    [self stopTrackingActivity:activityMgr];
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                        object:CFCTransitionTripEnded];
}

+ (void)printGeofences:(CLLocationManager*)manager {
    CLCircularRegion* currRegion = NULL;
    
    NSEnumerator *enumerator = [manager.monitoredRegions objectEnumerator];
    while ((currRegion = [enumerator nextObject])) {
        NSLog(@"Found geofence with id %@, coordinates %f %f",
              currRegion.identifier,
              currRegion.center.longitude,
              currRegion.center.latitude);
    }
}

+ (void)createGeofenceHere:(CLLocationManager *)manager inState:(TripDiaryStates)currState {

    /*
     * There are two main use cases for which we would need to create a geofence.
     * a) in case we are ending a trip, in which case the current location is fairly accurate, and can be used directly
     * b) in case we are initializing the FSM
     * - at app init
     * - while resuming from a force stop
     * at that point, the cached location is likely to be inaccurate. Let's turn on location tracking and retrieve it. We
     * also check to see if the existing cached location is fairly recent, but that makes the logic more complicated, so
     * let's punt for now ( )
     */
    
    NSLog(@"At method CREATION");
    CLLocation* currLoc = manager.location;
    if (currLoc == nil || (currState == kStartState && abs(currLoc.timestamp.timeIntervalSinceNow) > kTripEndStationaryMins * 60)) {
        [self startTrackingLocation:manager];
        [self stopTrackingLocation:manager];
        // TODO: Figure out if we will get an update even if we stop tracking right here
    } else {
        CLLocation* currLoc = manager.location;
        // We shouldn't need to check the timestamp on the location here.
        // If we are restarting, then the location will be nil
        // If not, we just finished
        CLCircularRegion *geofenceRegion = [[CLCircularRegion alloc] initWithCenter:currLoc.coordinate
                                                                             radius:kHundred_Meters
                                                                         identifier:kCurrGeofenceID];
    
        geofenceRegion.notifyOnEntry = YES;
        geofenceRegion.notifyOnExit = YES;
        NSLog(@"BEFORE creating region");
        [self printGeofences:manager];
    
        [manager startMonitoringForRegion:geofenceRegion];
        NSLog(@"AFTER creating region");
        [self printGeofences:manager];
    }
}

+ (void)deleteGeofence:(CLLocationManager*)manager {
    /*
     * TODO: Determine whether we need to get the existing region from the region list,
     * or whether it is sufficient to create a new region with the same identifier
     */
    NSEnumerator *enumerator = [manager.monitoredRegions objectEnumerator];
    CLCircularRegion* currRegion;
    CLCircularRegion* selRegion = NULL;
    while ((currRegion = [enumerator nextObject])) {
        NSLog(@"Considering region with id = %@, location %f, %f",
              currRegion.identifier,
              currRegion.center.longitude,
              currRegion.center.latitude);
        if ([currRegion.identifier compare:kCurrGeofenceID] == NSOrderedSame) {
            NSLog(@"Deleting it!!");
            selRegion = currRegion;
        }
    }
    
    if (selRegion != NULL) {
        [manager stopMonitoringForRegion:selRegion];
    } else {
        NSLog(@"No geofence found to delete, skipping...");
    }
}

/*
 * Configuring the iOS location manager. It looks like there are multiple ways to reduce the power consumption
 * of continuous location tracking in iOS, but it is hard to combine their use.
 * - We can lower the desired accuracy so that the phone can use modes other than GPS to determine the location. However, if we want deferred updates, we have to use the "best" accuracy which forces the use of GPS.
 * - We can defer updates so that the app is not woken up constantly to provide information that is just logged immediately. However, we then HAVE to us the most accurate mode, viz. GPS.
 * - We can use a distance filter so that we get woken up rarely. However, then it is hard/impossible to detect the end of a trip because we currently detect the end of a trip by using the fact that we have been within a particular radius for a particular amount of time. But with a distance filter, we won't get updates if we are within the radius, so we won't be woken up and so we won't detect the end of the trip. We can work around this for android by adding a time based alarm, but that is not an option on iOS.
 * So it looks like we have to either use the highest accuracy or be woken up constantly, both of which suck for power consumption. An adaptive, as opposed to fixed, distance filter might help with this because then we could use it in conjunction with a lower accuracy mode.
 * deferred updates don't appear to work, at least on the iPhone4. We are now switching to a distance filter, along with detecting the end of the trip using push notifications. The idea is that although we won't get any more updates after the trip ends due to the distance filter, when we receive the push notification, we can check to see the time of the last update. If it was more than (say) 10 minutes ago, then we can detect that the trip has ended.
 */

+ (void)startTrackingLocation:(CLLocationManager*) manager {
    // Switch to a more fine grained tracking during the trip
    manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters;
    /* If we use a distance filter, we can lower the power consumption because we will get updates less
     * frequently. HOWEVER, we won't be able to detect a trip end because of the above.
     * Going with the push notification route...
     */
    manager.distanceFilter = kHundred_Meters;
    [manager startUpdatingLocation];
}

+ (void)stopTrackingLocation:(CLLocationManager*) manager {
    [manager stopUpdatingLocation];
}

+ (void)startTrackingSignificantLocationChanges:(CLLocationManager*) manager {
    [manager startMonitoringSignificantLocationChanges];
}

+ (void)stopTrackingSignificantLocationChanges:(CLLocationManager*) manager {
    [manager stopMonitoringSignificantLocationChanges];
}

+ (void)startTrackingActivity:(CMMotionActivityManager*) manager {
    NSOperationQueue* mq = [NSOperationQueue mainQueue];
    [manager startActivityUpdatesToQueue:mq
                             withHandler:^(CMMotionActivity *activity) {
                                 NSString *activityName = [self getActivityName:activity];
                                 NSLog(@"Got activity change %@ starting at %@ with confidence %d", activityName, activity.startDate, (int)activity.confidence);
                             }];
}

+ (void)stopTrackingActivity:(CMMotionActivityManager*) manager {
    [manager stopActivityUpdates];
}

+(void)startTrackingVisits:(CLLocationManager*) manager {
    if ([CLLocationManager instancesRespondToSelector:@selector(startMonitoringVisits)]) {
        [manager startMonitoringVisits];
    }
}

+(void)stopTrackingVisits:(CLLocationManager*) manager {
    if ([CLLocationManager instancesRespondToSelector:@selector(stopMonitoringVisits)]) {
        [manager stopMonitoringVisits];
    }
}

+(NSString*)getActivityName:(CMMotionActivity*) activity {
    if(activity.walking) {
        return @"walking";
    } else if (activity.cycling) {
        return @"cycling";
    } else if (activity.automotive) {
        return @"transport";
    } else {
        return @"unknown";
    }
}

/*
 * This is a little tricky to implement with a distance filter, because we will stop getting updates when the trip ends.
 * The current plan is to receive a silent push notification every 30 minutes. At this point, we check when the last update
 * occurred. If the update was greater than the time for detecting the end of a trip (10 minutes), then the trip has ended,
 * otherwise, it has not.
 * TODO: Add a "machine" or "state" parameter if we want to check the current state.
 * Also, it seems like this would be a good fit for DataUtils instead of being here...
 */

+ (BOOL) hasTripEnded {

    NSArray* last3Points = [[OngoingTripsDatabase database] getLastPoints:3];
    if (last3Points.count == 0) {
        /*
         * There are no points in the database. This means that no trip has been started in the past 30 minutes.
         * This should never happen because we only invoke this when we are in the ongoing trip state, so let's generate a
         * notification here.
         */
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"last3Points.count = %lu while checking for trip end",
                                                   (unsigned long)last3Points.count]];
        // Let's also return "NO", since it is the safer option
        return NO;
    } else {
        NSDate* lastDate = ((CLLocation*)last3Points.firstObject).timestamp;
        NSDate* firstDate = ((CLLocation*)last3Points.lastObject).timestamp;
        NSLog(@"firstDate = %@, lastDate = %@", firstDate, lastDate);
        
        if (abs(lastDate.timeIntervalSinceNow) > kTripEndStationaryMins * 60) {
            NSLog(@"interval to the last date = %f, returning YES", [firstDate timeIntervalSinceDate:lastDate]);
            return YES;
        } else {
            NSLog(@"interval to the last date = %f, returning NO", [firstDate timeIntervalSinceDate:lastDate]);
            return NO;
        }
    }
}

+ (void) pushTripToServer {
    [DataUtils endTrip];
    NSArray* tripsToPush = [DataUtils getTripsToPush];
    // Push the trips here
    [DataUtils deletePushedTrips:tripsToPush];
}


@end
