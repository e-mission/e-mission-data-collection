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

#import "BEMBuiltinUserCache.h"
#import "Transition.h"

#import "LocationTrackingConfig.h"
#import "ConfigManager.h"
#import "AuthCompletionHandler.h"

#import <CoreMotion/CoreMotion.h>

@interface TripDiaryStateMachine() {
    TripDiaryDelegate* _locDelegate;
    GeofenceActions* _geofenceLocator;
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

+ (TripDiaryStateMachine*) instance {
    static dispatch_once_t once;
    static id sharedInstance;
    dispatch_once(&once, ^{
        sharedInstance = [[self alloc] init];
        // when we create a new instance, we use it to register for notifications
        // this should mean that we register only once for notifications, which should mean
        // that we should stop getting duplicate notifications
        [sharedInstance registerForNotifications];
    });
    return sharedInstance;
}

- (id) init {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    
    /*
     * We are going to perform actions on the locMgr after this. So let us ensure that we create the loc manager first
     * if required. Note that we actually recreate the locManager in both cases, so we might as well do it outside
     * the if check.
     */
    self.locMgr = [[CLLocationManager alloc] init];
    self.locMgr.pausesLocationUpdatesAutomatically = NO;
    self.locMgr.allowsBackgroundLocationUpdates = YES;
    _locDelegate = [[TripDiaryDelegate alloc] initWithMachine:self];
    self.locMgr.delegate = _locDelegate;
    self.currState = [defaults integerForKey:kCurrState];
    
    _geofenceLocator = [GeofenceActions new];
    
    // The operations in the one time init tracking are idempotent, so let's start them anyway
    [TripDiaryActions oneTimeInitTracking:CFCTransitionInitialize withLocationMgr:self.locMgr];
    
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"initializing TripDiaryStateMachine with state = %@",
                                               [TripDiaryStateMachine getStateName:self.currState]]];
    
    if (self.currState == kOngoingTripState) {
        // If we restarted, we recreate the location manager, but then it won't have
        // the fine location turned on, since that is not carried through over restarts.
        // So let's restart the tracking
        [TripDiaryActions startTracking:CFCTransitionTripRestarted withLocationMgr:self.locMgr];
        // Note that if the phone was shut down when the app was in the ongoing trip state, and it was
        // turned back on at home, we will start tracking here but will most probably not get a visit transition
        // so the data collection will be turned on until the NEXT trip ends. This is why we need remote pushes, I think.
        // would be good to test, though.
    }

    
    if ([CLLocationManager authorizationStatus] != kCLAuthorizationStatusAuthorizedAlways) {
        if ([CLLocationManager instancesRespondToSelector:@selector(requestAlwaysAuthorization)]) {
            NSLog(@"Current location authorization = %d, always = %d, requesting always",
                  [CLLocationManager authorizationStatus], kCLAuthorizationStatusAuthorizedAlways);
            [self.locMgr requestAlwaysAuthorization];
        } else {
            NSLog(@"Don't need to request authorization, system will automatically prompt for it");
        }
    } else {
        NSLog(@"Current location authorization = %d, always = %d",
              [CLLocationManager authorizationStatus], kCLAuthorizationStatusAuthorizedAlways);
        /* The only times we should get here are:
         * - if we re-install a previously installed app, and so it is already authorized for background location collection BUT is in the start state, or
         * - another option might be a re-launch of the app when the user has manually stopped tracking.
         * It would be bad to automatically restart the tracking if the user has manully stopped tracking.
         * One way to deal with this would be to have separate states for "start" and for "tracking suspended".
         * Another way would be to just remove this transition from here...
         * TODO: Figure out how to deal with it.
         */
        if (self.currState == kStartState) {
            [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                                object:CFCTransitionInitialize];
        }
    }
    
    if (![ConfigManager instance].is_duty_cycling) {
        /* If we are not using geofencing, then we don't need to listen to any transitions. We just turn on the tracking here and never stop. Turning off all transitions makes it easier for us to ignore silent
            push as well as the transitions generated from here. Note that this means that we can't turn off
            tracking manually either, so if this is to be a viable alternative, we really need to do something
            more sophisticated in the state machine. But let's start with something simple for now.
         */
        [TripDiaryActions oneTimeInitTracking:CFCTransitionInitialize withLocationMgr:self.locMgr];
        [self setState:kOngoingTripState];
        [TripDiaryActions startTracking:CFCTransitionExitedGeofence withLocationMgr:self.locMgr];
    }

    return [super init];
}

-(void) registerForNotifications {
    // Register for notifications related to the state machine
    if ([ConfigManager instance].is_duty_cycling) {
        // Only if we are using geofencing
        [[NSNotificationCenter defaultCenter] addObserverForName:CFCTransitionNotificationName object:nil queue:nil
                                                      usingBlock:^(NSNotification* note) {
            [self handleTransition:(NSString*)note.object withUserInfo:note.userInfo];
        }];
    }
}

-(void) checkGeofenceState:(GeofenceStatusCallback) callback {
    if (self.locMgr.monitoredRegions.count > 0) {
        [self.locMgr requestStateForRegion:self.locMgr.monitoredRegions.allObjects[0]];
        // _locDelegate.currCallback = callback;
    } else {
        callback(@"no fence");
    }
}

-(void)handleTransition:(NSString*) transition withUserInfo:(NSDictionary*) userInfo {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    self.currState = [defaults integerForKey:kCurrState];
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"In TripDiaryStateMachine, received transition %@ in state %@",
                                               transition,
                                               [TripDiaryStateMachine getStateName:self.currState]] showUI:FALSE];
    Transition* transitionWrapper = [Transition new];
    transitionWrapper.currState = [TripDiaryStateMachine getStateName:self.currState];
    transitionWrapper.transition = transition;
    [[BuiltinUserCache database] putMessage:@"key.usercache.transition" value:transitionWrapper];
    
    if (self.currState == kStartState) {
        [self handleStart:transition withUserInfo:userInfo];
    } else if (self.currState == kWaitingForTripStartState) {
        [self handleWaitingForTripStart:transition withUserInfo:userInfo];
    } else if (self.currState == kOngoingTripState) {
        [self handleOngoingTrip:transition withUserInfo:userInfo];
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
-(void) handleStart:(NSString*) transition withUserInfo:(NSDictionary*) userInfo {
    // If we are already in the start state, there's nothing much that we need
    // to do, except if we are initialized
    if ([transition isEqualToString:CFCTransitionInitialize]) {
        // TODO: Stop all actions in order to cleanup
        
        // Start monitoring significant location changes at start and never stop
        [TripDiaryActions oneTimeInitTracking:transition withLocationMgr:self.locMgr];
    
        // Start location services so that we can get the current location
        // We will receive the first location asynchronously
        [TripDiaryActions createGeofenceHere:self.locMgr withGeofenceLocator:_geofenceLocator inState:self.currState];
    } else if ([transition isEqualToString:CFCTransitionRecievedSilentPush]) {
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionNOP];
    } else if ([transition isEqualToString:CFCTransitionInitComplete]) {
        // Geofence has been successfully created and we are inside it so we are about to move to
        // the WAITING_FOR_TRIP_START state.
        [self setState:kWaitingForTripStartState];
    } else if ([transition isEqualToString:CFCTransitionExitedGeofence]) {
        [TripDiaryActions startTracking:transition withLocationMgr:self.locMgr];
        [TripDiaryActions deleteGeofence:self.locMgr];
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionTripStarted];
    } else if ([transition isEqualToString:CFCTransitionTripStarted]) {
        [self setState:kOngoingTripState];
    } else {
        NSLog(@"Got unexpected transition %@ in state %@, ignoring",
              transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

- (void) handleWaitingForTripStart:(NSString*) transition  withUserInfo:(NSDictionary*) userInfo {
    /* If we delete the geofence, and we are using the more fine grained location detection mode, then we won't be relaunched if the app is terminated. "The standard location service ... does not relaunch iOS apps that have been terminated. And it looks like apps need not be terminated on reboot, they can also be terminated as part of normal OS operation. The system may still terminate the app at any time to reclaim its memory or other resources."
     
        However, it turns out that keeping the geofence around is not good enough because iOS does not assume that we start within the geofence. So it won't restart until it detects an actual in -> out transition, which will only happen again when we visit the start of the trip. So let's delete the geofence and start both types of location services.
     */
    
    // TODO: Make removing the geofence conditional on the type of service
    if ([transition isEqualToString:CFCTransitionExitedGeofence]) {
        // We first start tracking and then delete the geofence to make sure that we are always tracking something
        [TripDiaryActions startTracking:transition withLocationMgr:self.locMgr];
        [TripDiaryActions deleteGeofence:self.locMgr];
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionTripStarted];
    } else if ([transition isEqualToString:CFCTransitionVisitEnded]) {
        if ([ConfigManager instance].ios_use_visit_notifications_for_detection) {
            // We first start tracking and then delete the geofence to make sure that we are always tracking something
            [TripDiaryActions startTracking:transition withLocationMgr:self.locMgr];
            [TripDiaryActions deleteGeofence:self.locMgr];
            [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                                object:CFCTransitionTripStarted];
        }
    } else if ([transition isEqualToString:CFCTransitionTripStarted]) {
        [self setState:kOngoingTripState];
    } else if ([transition isEqualToString:CFCTransitionRecievedSilentPush]) {
        // Let's push any pending changes since we know that the trip has ended
        // SilentPushCompletionHandler handler = [userInfo objectForKey:@"handler"];
        [[TripDiaryActions pushTripToServer] continueWithBlock:^id(BFTask *task) {
            // When we send the NOP, the AppDelegate listener will mark the sync as complete
            // if we mark it as complete here and there, then iOS crashes because of
            // but we can't stop marking it as complete there because we need to handle the other NOP cases
            // for e.g. silent push during an ongoing trip. We could send a different notification here, but
            // that will make the state machine even more complex than it currently instead.
            // So instead, we send the NOP after the push is complete. Two birds with one stone!
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionNOP];
            return nil;
        }];
    } else if ([transition isEqualToString:CFCTransitionForceStopTracking]) {
        [TripDiaryActions resetFSM:transition withLocationMgr:self.locMgr];
    } else if ([transition isEqualToString:CFCTransitionTrackingStopped]) {
        [self setState:kStartState];
    } else  {
        NSLog(@"Got unexpected transition %@ in state %@, ignoring",
              transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

- (void) handleOngoingTrip:(NSString*) transition withUserInfo:(NSDictionary*) userInfo {
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
            [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                                object:CFCTransitionNOP];
        }
    } else if ([transition isEqualToString:CFCTransitionVisitStarted]) {
        if ([ConfigManager instance].ios_use_visit_notifications_for_detection) {
            [self forceRefreshToken];
            [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                                object:CFCTransitionTripEndDetected];
        }
    } else if ([transition isEqualToString:CFCTransitionTripEndDetected]) {
        [TripDiaryActions createGeofenceHere:self.locMgr withGeofenceLocator:_geofenceLocator inState:self.currState];
    } else if ([transition isEqualToString:CFCTransitionTripRestarted]) {
        NSLog(@"Restarted trip, continuing tracking");
    } else if ([transition isEqualToString:CFCTransitionEndTripTracking]) {
        // [TripDiaryActions pushTripToServer];
        [TripDiaryActions stopTracking:CFCTransitionInitialize withLocationMgr:self.locMgr];
        // stopTracking automatically generates TripEnded so we don't need this here.
    } else if ([transition isEqualToString:CFCTransitionTripEnded]) {
        // Geofence has been successfully created and we are inside it so we are about to move to
        // the WAITING_FOR_TRIP_START state.
        // TODO: Should this be here, or in EndTripTracking
        [self setState:kWaitingForTripStartState];
        [[BEMServerSyncCommunicationHelper backgroundSync] continueWithBlock:^id(BFTask *task) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Returning with fetch result = new data"]
                                               showUI:FALSE];
            [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                                object:CFCTransitionDataPushed];
            return nil;
        }];
    } else if ([transition isEqualToString:CFCTransitionForceStopTracking]) {
        [TripDiaryActions resetFSM:transition withLocationMgr:self.locMgr];
    } else if ([transition isEqualToString:CFCTransitionTrackingStopped]) {
        [self setState:kStartState];
    } else {
        NSLog(@"Got unexpected transition %@ in state %@, ignoring",
              transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

- (void) forceRefreshToken
{
    [[AuthCompletionHandler sharedInstance] getValidAuth:^(GTMOAuth2Authentication *auth, NSError *error) {
        /*
         * Note that we do not condition any further tasks on this refresh. That is because, in general, we expect that
         * the token refreshed at this time will be used to push the next set of values. This is just pre-emptive refreshing,
         * to increase the chance that we will finish pushing our data within the 30 sec interval.
         */
        if (error == NULL) {
            GTMOAuth2Authentication* currAuth = [AuthCompletionHandler sharedInstance].currAuth;
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Finished refreshing token in background, new expiry is %@", currAuth.expirationDate]
                                               showUI:FALSE];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Error %@ while refreshing token in background", error]
                                               showUI:TRUE];
        }
    } forceRefresh:TRUE];
}

/*
 * END: State transition handlers
 */

@end
