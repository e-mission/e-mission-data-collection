//
//  TripDiaryActions.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/7/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "TripDiaryActions.h"
#import "TripDiaryStateMachine.h"
#import "LocalNotificationManager.h"
#import "DataUtils.h"
#import "CommunicationHelper.h"
#import "EMActivity.h"


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
        [LocalNotificationManager addNotification:
            [NSString stringWithFormat:@"Found geofence with id %@, coordinates %f %f",
              currRegion.identifier,
              currRegion.center.longitude,
              currRegion.center.latitude]];
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
    
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"In createGeofenceHere"]];
    CLLocation* currLoc = manager.location;
    if (currLoc == nil || (currState == kStartState && fabs(currLoc.timestamp.timeIntervalSinceNow) > kTripEndStationaryMins * 60)) {
        [LocalNotificationManager addNotification:[NSString
                                                   stringWithFormat:@"currLoc = %@, which is stale, restarting location updates", currLoc]];
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
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"BEFORE creating region"]];
        [self printGeofences:manager];
    
        [manager startMonitoringForRegion:geofenceRegion];
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"AFTER creating region"]];
        [self printGeofences:manager];
    }
}

+ (CLCircularRegion*)getGeofence:(CLLocationManager *)manager {
    /*
     * TODO: Determine whether we need to get the existing region from the region list,
     * or whether it is sufficient to create a new region with the same identifier
     */
    NSEnumerator *enumerator = [manager.monitoredRegions objectEnumerator];
    CLCircularRegion* currRegion;
    CLCircularRegion* selRegion = NULL;
    while ((currRegion = [enumerator nextObject])) {
        [LocalNotificationManager addNotification:
            [NSString stringWithFormat:@"Considering region with id = %@, location %f, %f",
              currRegion.identifier,
              currRegion.center.longitude,
              currRegion.center.latitude]];
        if ([currRegion.identifier compare:kCurrGeofenceID] == NSOrderedSame) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Deleting it!!"]];
            selRegion = currRegion;
        }
    }
    return selRegion;
}

+ (void)deleteGeofence:(CLLocationManager*)manager {
    CLCircularRegion* selRegion = [TripDiaryActions getGeofence:manager];
    if (selRegion != NULL) {
        [manager stopMonitoringForRegion:selRegion];
    } else {
        [LocalNotificationManager addNotification:
            [NSString stringWithFormat:@"No geofence found to delete, skipping..."]];
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
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"started fine location tracking"]];
    // Switch to a more fine grained tracking during the trip
    manager.desiredAccuracy = kCLLocationAccuracyHundredMeters;
    /* If we use a distance filter, we can lower the power consumption because we will get updates less
     * frequently. HOWEVER, we won't be able to detect a trip end because of the above.
     * Going with the push notification route...
     */
    manager.distanceFilter = kHundred_Meters;
    [manager startUpdatingLocation];
}

+ (void)stopTrackingLocation:(CLLocationManager*) manager {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"stopped fine location tracking"]];

    [manager stopUpdatingLocation];
}

+ (void)startTrackingSignificantLocationChanges:(CLLocationManager*) manager {
    [manager startMonitoringSignificantLocationChanges];
}

+ (void)stopTrackingSignificantLocationChanges:(CLLocationManager*) manager {
    [manager stopMonitoringSignificantLocationChanges];
}

/*
 Since we are not actually doing any real-time tweaking on the phone at this point, it is
 sufficient to read the activity list at the end of the trip. It looks like motion data is
 always being collected, even if we haven't registered for activity updates. I'm leaving this
 commented out in case we do want to get ongoing activity updates and do more processing on the
 phone going forward...
*/
 
+ (void)startTrackingActivity:(CMMotionActivityManager*) manager {
    NSOperationQueue* mq = [NSOperationQueue mainQueue];
    [manager startActivityUpdatesToQueue:mq
                             withHandler:^(CMMotionActivity *activity) {
                                 NSString *activityName = [EMActivity getActivityName:[EMActivity getRelevantActivity:activity]];
                                 [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                                            @"Got activity change %@ starting at %@ with confidence %d", activityName, activity.startDate, (int)activity.confidence]];
//                                 NSLog(@"Got activity change %@ starting at %@ with confidence %d", activityName, activity.startDate, (int)activity.confidence);
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

/*
 * This is a little tricky to implement with a distance filter, because we will stop getting updates when the trip ends.
 * The current plan is to receive a silent push notification every 30 minutes. At this point, we check when the last update
 * occurred. If the update was greater than the time for detecting the end of a trip (10 minutes), then the trip has ended,
 * otherwise, it has not.
 * TODO: Add a "machine" or "state" parameter if we want to check the current state.
 */

+ (BOOL) hasTripEnded {
    return [DataUtils hasTripEnded:kTripEndStationaryMins];
}

+ (void) pushTripToServer {
    [DataUtils pushAndClearData:^(BOOL status) {
        NSLog(@"pushAndClearData was successful in pushTripToServer");
    }];
    /*
    UIApplication *theApp = [UIApplication sharedApplication];
    [theApp.delegate application:theApp performFetchWithCompletionHandler:^(UIBackgroundFetchResult result) {
        NSLog(@"Completion handler result = %u", result);
    }];
     */
}


@end
