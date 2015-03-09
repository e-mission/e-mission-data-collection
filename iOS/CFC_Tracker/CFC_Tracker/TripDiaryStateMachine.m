//
//  TripDiaryStateMachine.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 1/31/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "TripDiaryStateMachine.h"
#import "TripDiaryActions.h"
#import "TripDiaryDelegate.h"

#import "LocalNotificationManager.h"

#import "OngoingTripsDatabase.h"
#import <CoreMotion/CoreMotion.h>


@interface TripDiaryStateMachine() {
    CLLocationManager *locMgr;
    CMMotionActivityManager *activityMgr;
    TripDiaryDelegate* _locDelegate;
}
@end


@implementation TripDiaryStateMachine

static NSString * const kCurrState = @"CURR_STATE";

// static int FOUR_MINUTES_IN_SECONDS = 4 * 60;

+ (NSString*)getStateName:(TripDiaryStates)state {
    if (state == kStartState) {
        return @"STATE_START";
    } else if (state == kWaitingForTripStartState) {
        return @"STATE_WAITING_FOR_TRIP_START";
    } else if (state == kOngoingTripState) {
        return @"STATE_ONGOING_TRIP";
    } else {
        return @"UNKNOWN";
    }
}

-(id)init{
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    self.currState = kStartState;
    // TODO: Think about whether we should do this everytime
    [defaults setObject:kStartState forKey:kCurrState];
    
    // Register for notifications related to the state machine
    [[NSNotificationCenter defaultCenter] addObserverForName:CFCTransitionNotificationName object:nil queue:nil usingBlock:^(NSNotification* note) {
        [self handleTransition:(NSString*)note.object];
    }];
    
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Initialized TripDiaryStateMachine when state = %@",
                                               [TripDiaryStateMachine getStateName:self.currState]]];
    
    locMgr = [[CLLocationManager alloc] init];
    _locDelegate = [[TripDiaryDelegate alloc] initWithMachine:self];
    locMgr.delegate = _locDelegate;
    if ([CLLocationManager authorizationStatus] != kCLAuthorizationStatusAuthorizedAlways) {
        if ([CLLocationManager instancesRespondToSelector:@selector(requestAlwaysAuthorization)]) {
            NSLog(@"Current location authorization = %d, always = %d, requesting always",
                  [CLLocationManager authorizationStatus], kCLAuthorizationStatusAuthorizedAlways);
            [locMgr requestAlwaysAuthorization];
        } else {
            NSLog(@"Don't need to request authorization, system will automatically prompt for it");
        }
    } else {
        NSLog(@"Current location authorization = %d, always = %d",
              [CLLocationManager authorizationStatus], kCLAuthorizationStatusAuthorizedAlways);
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName object:CFCTransitionInitialize];
    }
    
    if ([CMMotionActivityManager isActivityAvailable] == YES) {
        activityMgr = [[CMMotionActivityManager alloc] init];
    } else {
        NSLog(@"UIAlertView: Activity recognition unavailable");
        [TripDiaryStateMachine showAlert:@"Activity recognition is not available on your phone"
                               withTitle:@"Activity recognition disabled"];
    }

    /*
     * Make sure that we start with a clean state, at least while debugging.
     * TODO: Check how often this is initialized, and whether we should do this even when we are out of debugging.
     */
    // [self deleteGeofence:locMgr];
    return [super init];
}

+(void) showAlert:(NSString*)message withTitle:(NSString*)title {
    UIAlertView* alert = [[UIAlertView alloc]
                          initWithTitle:title
                          message:message
                          delegate: NULL
                          cancelButtonTitle:@"OK"
                          otherButtonTitles:nil];
    [alert show];
}

-(void) checkGeofenceState:(GeofenceStatusCallback) callback {
    if (locMgr.monitoredRegions.count > 0) {
        [locMgr requestStateForRegion:locMgr.monitoredRegions.allObjects[0]];
        // _locDelegate.currCallback = callback;
    } else {
        callback(@"no fence");
    }
}

-(void)handleTransition:(NSString*) transition {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    self.currState = [defaults integerForKey:kCurrState];
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Received transition %@ in state %@",
                                               transition,
                                               [TripDiaryStateMachine getStateName:self.currState]]];
    
    if (self.currState == kStartState) {
        [self handleStart:transition];
    } else if (self.currState == kWaitingForTripStartState) {
        [self handleWaitingForTripStart:transition];
    } else if (self.currState == kOngoingTripState) {
        [self handleOngoingTrip:transition];
    } else {
        NSLog(@"Ignoring invalid transition %@ in state %@", transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

-(void)setState:(TripDiaryStates) newState {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    [defaults setInteger:newState forKey:kCurrState];
    
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Moved from %@ to %@",
                                               [TripDiaryStateMachine getStateName:self.currState],
                                               [TripDiaryStateMachine getStateName:newState]]];

    self.currState = newState;
}

/*
 * BEGIN: State transition handlers
 */

/*
 * Starts monitoring changes. This will return the current location asynchronously.
 * In that callback, we set up the geofence.
 
 */
-(void) handleStart:(NSString*) transition {
    // If we are already in the start state, there's nothing much that we need
    // to do, except if we are initialized
    if ([transition isEqualToString:CFCTransitionInitialize]) {
        // TODO: Stop all actions in order to cleanup
        
        // Start monitoring significant location changes at start and never stop
        [TripDiaryActions oneTimeInitTracking:transition withLocationMgr:locMgr withActivityMgr:activityMgr];
    
        // Start location services so that we can get the current location
        // We will receive the first location asynchronously
        [TripDiaryActions createGeofenceHere:locMgr inState:self.currState];
    } else if ([transition isEqualToString:CFCTransitionRecievedSilentPush]) {
        // Nothing to do at this time
    } else if ([transition isEqualToString:CFCTransitionInitComplete]) {
        [self setState:kWaitingForTripStartState];
    } else {
        NSLog(@"Got unexpected transition %@ in state %@, ignoring",
              transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

- (void) handleWaitingForTripStart:(NSString*) transition {
    /* If we delete the geofence, and we are using the more fine grained location detection mode, then we won't be relaunched if the app is terminated. "The standard location service ... does not relaunch iOS apps that have been terminated. And it looks like apps need not be terminated on reboot, they can also be terminated as part of normal OS operation. The system may still terminate the app at any time to reclaim its memory or other resources."
     
        However, it turns out that keeping the geofence around is not good enough because iOS does not assume that we start within the geofence. So it won't restart until it detects an actual in -> out transition, which will only happen again when we visit the start of the trip. So let's delete the geofence and start both types of location services.
     */
    
    // TODO: Make removing the geofence conditional on the type of service
    if ([transition isEqualToString:CFCTransitionExitedGeofence]) {
        // We first start tracking and then delete the geofence to make sure that we are always tracking something
        [TripDiaryActions startTracking:transition withLocationMgr:locMgr withActivityMgr:activityMgr];
        [TripDiaryActions deleteGeofence:locMgr];
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionTripStarted];
    } else if ([transition isEqualToString:CFCTransitionTripStarted]) {
        [self setState:kOngoingTripState];
    } else if ([transition isEqualToString:CFCTransitionRecievedSilentPush]) {
        // NOP at this time
    } else if ([transition isEqualToString:CFCTransitionForceStopTracking]) {
        [TripDiaryActions resetFSM:transition withLocationMgr:locMgr withActivityMgr:activityMgr];
    } else  {
        NSLog(@"Got unexpected transition %@ in state %@, ignoring",
              transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

- (void) handleOngoingTrip:(NSString*) transition {
    if ([transition isEqualToString:CFCTransitionRecievedSilentPush]) {
        if ([TripDiaryActions hasTripEnded]) {
            [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                                object:CFCTransitionTripEndDetected];
            /*
             * Technically, we can turn off the significant location changes here because we
             * have the geofence in place. But if the phone is turned off and turned on when
             * we are outside the geofence, it is unclear that the geofence will still be
             * triggered. Seems safe to just have the significant location tracking on all
             * the time except when the user explicitly requested us to stop.
             */
            
            // The location property may have been updated while the app was killed
            // Another caveat is that if the minimum distance filter is large,
            // the returned location may be relatively old. But in our case, we have
            // just detected the end of the trip, so the update should be fairly recent
            // In case we didn't delete the old geofence, in this case, the new geofence will replace it
            // because they both have the same identifier
        } else {
            NSLog(@"Trip has not yet ended, continuing tracking");
        }
    } else if ([transition isEqualToString:CFCTransitionTripEndDetected]) {
        [TripDiaryActions createGeofenceHere:locMgr inState:self.currState];
    } else if ([transition isEqualToString:CFCTransitionTripRestarted]) {
        NSLog(@"Restarted trip, continuing tracking");
    } else if (true) {
        [TripDiaryActions stopTracking:transition withLocationMgr:locMgr withActivityMgr:activityMgr];
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionTripEnded];
    } else if ([transition isEqualToString:CFCTransitionTripEnded]) {
        [self setState:kWaitingForTripStartState];
    }else {
        NSLog(@"Got unexpected transition %@ in state %@, ignoring",
              transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

/*
 * END: State transition handlers
 */

@end
