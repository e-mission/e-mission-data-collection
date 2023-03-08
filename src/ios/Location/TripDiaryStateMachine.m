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
#import "SensorControlBackgroundChecker.h"

#import "LocalNotificationManager.h"

#import "BEMBuiltinUserCache.h"
#import "Transition.h"

#import "LocationTrackingConfig.h"
#import "ConfigManager.h"
#import "AuthTokenCreationFactory.h"
#import "DataUtils.h"

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
    } else if (state == kTrackingStoppedState) {
        return @"STATE_TRACKING_STOPPED";
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

+ (TripDiaryDelegate*) delegate {
    return [self instance]->_locDelegate;
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
    if([self.locMgr respondsToSelector:@selector(setAllowsBackgroundLocationUpdates:)]) {
    self.locMgr.allowsBackgroundLocationUpdates = YES;
    }
    _locDelegate = [[TripDiaryDelegate alloc] initWithMachine:self];
    self.locMgr.delegate = _locDelegate;
    self.currState = [defaults integerForKey:kCurrState];
    
    _geofenceLocator = [GeofenceActions new];
    
    // The operations in the one time init tracking are idempotent, so let's start them anyway
    if ([ConfigManager getPriorConsent] != NULL) {
        [TripDiaryActions oneTimeInitTracking:CFCTransitionInitialize withLocationMgr:self.locMgr];
    }
    
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

    // Replaced by a call to [SensorControlBackgroundChecker checkAppState]
    // just after the initialization is complete in the background
    // currently only in `BEMDataCollection initWithConsent`
    // https://github.com/e-mission/e-mission-docs/issues/735#issuecomment-1179774103
    
    if (![ConfigManager instance].is_duty_cycling && self.currState != kTrackingStoppedState) {
        /* If we are not using geofencing, and the tracking is not manually turned off, then we don't need to listen
         to any transitions. We just turn on the tracking here and never stop. Turning off all transitions makes
         it easier for us to ignore silent push as well as the transitions generated from here.
         */
        [TripDiaryActions oneTimeInitTracking:CFCTransitionInitialize withLocationMgr:self.locMgr];
        [self setState:kOngoingTripState withChecks:TRUE];
        [TripDiaryActions startTracking:CFCTransitionExitedGeofence withLocationMgr:self.locMgr];
    }

    return [super init];
}

-(void) registerForNotifications {
    // Register for notifications related to the state machine.
    // Before this, we could get away with doing this only when we were using geofences, but right now,
    // we push data using the remote push notification, so we need to receive the notification in all cases.
    // But then we have to be careful to not detect a trip end when we are not geofencing
        [[NSNotificationCenter defaultCenter] addObserverForName:CFCTransitionNotificationName object:nil queue:nil
                                                      usingBlock:^(NSNotification* note) {
            [self handleTransition:(NSString*)note.object withUserInfo:note.userInfo];
        }];
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
    transitionWrapper.ts = [BuiltinUserCache getCurrentTimeSecs];
    [[BuiltinUserCache database] putMessage:@"key.usercache.transition" value:transitionWrapper];
    [DataUtils saveBatteryAndSimulateUser];
    
    // To make life simple, in the non-geofenced case, we will ignore all transitions other than
    // 1) remote push -> to push data
    // 2) force stop -> to stop tracking
    // 3) force start -> to start tracking
    // 4) visit started -> as backup for broken remote push
    // this means that these transitions are the only ones for which we need to consider whether we are geofenced
    // or not while handling them
    if (![ConfigManager instance].is_duty_cycling &&
        ![transition isEqualToString:CFCTransitionInitialize] &&
        ![transition isEqualToString:CFCTransitionRecievedSilentPush] &&
        ![transition isEqualToString:CFCTransitionForceStopTracking] &&
        ![transition isEqualToString:CFCTransitionTrackingStopped] &&
        ![transition isEqualToString:CFCTransitionStartTracking] &&
        ![transition isEqualToString:CFCTransitionVisitStarted]) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"In TripDiaryStateMachine with geofencing turned off, received ignored transition %@ in state %@, ignoring",
                                                   transition,
                                                   [TripDiaryStateMachine getStateName:self.currState]] showUI:FALSE];
        return;
    }
    
    if (self.currState == kStartState) {
        [self handleStart:transition withUserInfo:userInfo];
    } else if (self.currState == kWaitingForTripStartState) {
        [self handleWaitingForTripStart:transition withUserInfo:userInfo];
    } else if (self.currState == kOngoingTripState) {
        [self handleOngoingTrip:transition withUserInfo:userInfo];
    } else if (self.currState == kTrackingStoppedState) {
        [self handleTrackingStopped:transition withUserInfo:userInfo];
    } else {
        NSLog(@"Ignoring invalid transition %@ in state %@", transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

-(void)setState:(TripDiaryStates) newState withChecks:(BOOL) doChecks {
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    [defaults setInteger:newState forKey:kCurrState];
    
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Moved from %@ to %@",
                                               [TripDiaryStateMachine getStateName:self.currState],
                                               [TripDiaryStateMachine getStateName:newState]]];

    self.currState = newState;
    if (doChecks) {
    [SensorControlBackgroundChecker checkAppState];
}
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
        if([ConfigManager instance].is_duty_cycling) {
        // TODO: Stop all actions in order to cleanup
        
        // Start monitoring significant location changes at start and never stop
        [TripDiaryActions oneTimeInitTracking:transition withLocationMgr:self.locMgr];
    
        // Start location services so that we can get the current location
        // We will receive the first location asynchronously
        [TripDiaryActions createGeofenceHere:self.locMgr withGeofenceLocator:_geofenceLocator inState:self.currState];
        } else {
            // Technically, we don't need this since we move to the ongoing state in the init code.
            // but if tracking is stopped, we can skip that, and then if we turn it on again, we
            // need to turn everything on here as well
            [TripDiaryActions oneTimeInitTracking:transition withLocationMgr:self.locMgr];
            [self setState:kOngoingTripState withChecks:TRUE];
            [TripDiaryActions startTracking:transition withLocationMgr:self.locMgr];
        }
    } else if ([transition isEqualToString:CFCTransitionRecievedSilentPush]) {
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionNOP];
    } else if ([transition isEqualToString:CFCTransitionInitComplete]) {
        // Geofence has been successfully created and we are inside it so we are about to move to
        // the WAITING_FOR_TRIP_START state.
        [self setState:kWaitingForTripStartState withChecks:TRUE];
    } else if ([transition isEqualToString:CFCTransitionGeofenceCreationError]) {
        // if we get a geofence creation error, we stay in the start state.
        NSLog(@"Got transition %@ in state %@, staying in %@ state",
              transition,
              [TripDiaryStateMachine getStateName:self.currState],
              [TripDiaryStateMachine getStateName:self.currState]);
        [self setState:kStartState withChecks:FALSE];
    } else if ([transition isEqualToString:CFCTransitionExitedGeofence]) {
        [TripDiaryActions startTracking:transition withLocationMgr:self.locMgr];
        [TripDiaryActions deleteGeofence:self.locMgr];
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionTripStarted];
    } else if ([transition isEqualToString:CFCTransitionTripStarted]) {
        [self setState:kOngoingTripState withChecks:TRUE];
    } else if ([transition isEqualToString:CFCTransitionForceStopTracking]) {
        // One would think that we don't need to deal with anything other than starting from the start
        // state, but we can be stuck in the start state for a while if it turns out that the geofence is
        // not created correctly. If the user forces us to stop tracking then, we still need to do it.
        [self setState:kTrackingStoppedState withChecks:TRUE];
    } else if ([transition isEqualToString:CFCTransitionStartTracking]) {
        // One would think that we don't need to deal with anything other than starting from the start
        // state, but we can be stuck in the start state for a while if it turns out that the geofence is
        // not created correctly. If the user forces us to stop tracking then, we still need to do it.
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionInitialize];

    } else {
        NSLog(@"Got unexpected transition %@ in state %@, ignoring",
              transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

- (void) handleWaitingForTripStart:(NSString*) transition  withUserInfo:(NSDictionary*) userInfo {
    /* If we delete the geofence, and we are using the more fine grained location detection mode, then we won't be relaunched if the app is terminated. "The standard location service ... does not relaunch iOS apps that have been terminated. And it looks like apps need not be terminated on reboot, they can also be terminated as part of normal OS operation. The system may still terminate the app at any time to reclaim its memory or other resources."
     
        However, it turns out that keeping the geofence around is not good enough because iOS does not assume that we
        start within the geofence. So it won't restart until it detects an actual in -> out transition, which will only happen again when we visit the start of the trip. So let's delete the geofence and start both types of location services.
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
        [self setState:kOngoingTripState withChecks:TRUE];
    } else if ([transition isEqualToString:CFCTransitionRecievedSilentPush]) {
        // Let's push any pending changes since we know that the trip has ended
        [[TripDiaryActions pushTripToServer] continueWithBlock:^id(BFTask *task) {
            // When we send the NOP, the AppDelegate listener will mark the sync as complete
            // if we mark it as complete here and there, then iOS crashes because of
            // but we can't stop marking it as complete there because we need to handle the other NOP cases
            // for e.g. silent push during an ongoing trip. We could send a different notification here, but
            // that will make the state machine even more complex than it currently instead.
            // So instead, we send the NOP after the push is complete. Two birds with one stone!
            [[BuiltinUserCache database] checkAfterPull];
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionDataPushed];
            return nil;
        }];
    } else if ([transition isEqualToString:CFCTransitionForceStopTracking]) {
        [TripDiaryActions resetFSM:transition withLocationMgr:self.locMgr];
    } else if ([transition isEqualToString:CFCTransitionTrackingStopped]) {
        [self setState:kTrackingStoppedState withChecks:TRUE];
    } else  {
        NSLog(@"Got unexpected transition %@ in state %@, ignoring",
              transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

- (void) handleOngoingTrip:(NSString*) transition withUserInfo:(NSDictionary*) userInfo {
    if ([transition isEqualToString:CFCTransitionRecievedSilentPush]) {
        if([ConfigManager instance].is_duty_cycling) {
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
        } else {
            // we are not using the geofence, so we are always in the ongoing state. We still
            // want to push data periodically, though
            [self syncAndNotify];
        }
    } else if ([transition isEqualToString:CFCTransitionVisitStarted]) {
        if ([self isProblematicVisitStart]) {
            return;
        }
        if ([ConfigManager instance].is_duty_cycling) {
        if ([ConfigManager instance].ios_use_visit_notifications_for_detection) {
            [self forceRefreshToken];
            [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                                object:CFCTransitionTripEndDetected];
        }
        } else {
            // we are not using the geofence, so we don't want to mark the trip as ended, but we do want to
            // push data, in case the remote pushes don't work reliably
            [self forceRefreshToken];
            [self syncAndNotify];
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
        // OR
        // we got an error while creating the Geofence, but we do use_visit_notifications enabled
        // so we can use visits for the trip start detection, so we are also
        // about to move to the WAITING_FOR_TRIP_START state
        // TODO: Should this be here, or in EndTripTracking
        [self setState:kWaitingForTripStartState withChecks:TRUE];
        [[BEMServerSyncCommunicationHelper backgroundSync] continueWithBlock:^id(BFTask *task) {
            [[BuiltinUserCache database] checkAfterPull];
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Returning with fetch result = new data"]
                                               showUI:FALSE];
            [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                                object:CFCTransitionDataPushed];
            return nil;
        }];
    } else if ([transition isEqualToString:CFCTransitionGeofenceCreationError]) {
        // setState will call SensorControlBackgroundChecker checkAppState by default
        [self setState:kStartState withChecks:FALSE];
    } else if ([transition isEqualToString:CFCTransitionForceStopTracking]) {
        [TripDiaryActions resetFSM:transition withLocationMgr:self.locMgr];
    } else if ([transition isEqualToString:CFCTransitionTrackingStopped]) {
        [self setState:kTrackingStoppedState withChecks:TRUE];
    } else {
        NSLog(@"Got unexpected transition %@ in state %@, ignoring",
              transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

- (void) handleTrackingStopped:(NSString*) transition withUserInfo:(NSDictionary*) userInfo {
    if ([transition isEqualToString:CFCTransitionRecievedSilentPush]) {
        // We are not actively tracking, but let's push any data that we had before we stopped,
        // and any battery data collected since then
        [self syncAndNotify];
    } else if ([transition isEqualToString:CFCTransitionVisitStarted]) {
        // If we are already in the tracking stopped state, we don't expect to
        // get any other transitions. If we do, there's a bug. Let's alert and turn off
        // again.
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
           @"Found unexpected VISIT_STARTED transition while tracking was stopped, stop everything again"]
           showUI:TRUE];
        [self setState:kTrackingStoppedState withChecks:TRUE];
        [TripDiaryActions resetFSM:transition withLocationMgr:self.locMgr];
    } else if ([transition isEqualToString:CFCTransitionForceStopTracking]) {
        [TripDiaryActions resetFSM:transition withLocationMgr:self.locMgr];
    } else if ([transition isEqualToString:CFCTransitionTrackingStopped]) {
        [self setState:kTrackingStoppedState withChecks:TRUE];
    } else if ([transition isEqualToString:CFCTransitionStartTracking]) {
        [self setState:kStartState withChecks:TRUE];
        // This will run the one time init tracking as well as try to create a geofence
        // if we are moving, then we will be outside the geofence... the existing state machine will take over.
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionInitialize];
    } else {
        NSLog(@"Got unexpected transition %@ in state %@, ignoring",
              transition,
              [TripDiaryStateMachine getStateName:self.currState]);
    }
}

- (void) syncAndNotify
{
    [[BEMServerSyncCommunicationHelper backgroundSync] continueWithBlock:^id(BFTask *task) {
        [[BuiltinUserCache database] checkAfterPull];
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Returning with fetch result = new data"]
                                           showUI:FALSE];
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionDataPushed];
        return nil;
    }];
}

- (void) forceRefreshToken
{
    /*
    Since we now use a locally stored string as the token, we don't need to refresh it asynchronously
    but let's keep the calls to refreshToken in case we need to ever restore it and just convert
    forceRefreshToken to a NOP

    [[AuthTokenCreationFactory getInstance] getExpirationDate:^(NSString *expirationDate, NSError *error) {
         * Note that we do not condition any further tasks on this refresh. That is because, in general, we expect that
         * the token refreshed at this time will be used to push the next set of values. This is just pre-emptive refreshing,
         * to increase the chance that we will finish pushing our data within the 30 sec interval.
        if (error == NULL) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Finished refreshing token in background, new expiry is %@", expirationDate]
                                               showUI:FALSE];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Error %@ while refreshing token in background", error]
                                               showUI:TRUE];
        }
    }];
    */
}

/*
 Check to see if this is a spurious entry that is within one minute of a
 VISIT_ENDED notification
 More details: https://github.com/e-mission/e-mission-docs/issues/372
 and Figure 7.3 (top) and Table 7.4 of Shankari's thesis
 */
- (bool) isProblematicVisitStart
{
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Checking invalid visit started transition..."]];
    NSArray* lastTwoTransitions = [[BuiltinUserCache database]
                               getLastMessage:@"key.usercache.transition" nEntries:2
                               wrapperClass:[ Transition class]];
    long transCount = [lastTwoTransitions count];
    if (transCount < 2) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Checking invalid visit started transition: found %lu prior transitions, returning false", transCount]];
        return false;
    }
    Transition *visitStartT = lastTwoTransitions[0];
    Transition *visitEndT = lastTwoTransitions[1];
    
    if ([visitStartT.transition isEqualToString:CFCTransitionVisitStarted] &&
        [visitEndT.transition isEqualToString:CFCTransitionVisitEnded]) {
        // Now let's check the timestamps
        double deltaTs = visitStartT.ts - visitEndT.ts;
        if (deltaTs < 60) { // 60 secs = 1 minute
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Potentially invalid visit started transition: visitStartT.transition = %@, visitEndT.transition = %@,deltaTs = %f", visitStartT.transition, visitEndT.transition, deltaTs]
                                               showUI:FALSE];
            int trip_end_mins = [ConfigManager instance].trip_end_stationary_mins;
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Scheduling check in %d mins", trip_end_mins]
                                               showUI:TRUE];
            [NSTimer scheduledTimerWithTimeInterval:trip_end_mins
                                                    target:self
                                                    selector:@selector(checkValidVisitStart:)
                                                    userInfo:NULL
                                                    repeats:NO];
            return true;
        }
    }
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"visit started transition is not invalid, returning false"]];
    return false;
}

/*
 * Checking the validity of a visit start `trip_end_stationary_mins` after we received it
 * The visit detection is invalid if we have travelled significantly after we
 * received it
 * The visit detection is valid if we have not
 * We use the `hasTripEnded` call to determine whether we have travelled a lot
 * or not, given that we have done a significant amount of debugging
 */

- (void)checkValidVisitStart:(NSTimer*)theTimer
{
    if ([TripDiaryActions hasTripEnded]) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                           @"checkValidVisitStart: Trip end detected, generating notification %@", CFCTransitionTripEndDetected]
                                           showUI:TRUE];
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionTripEndDetected];
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                           @"checkValidVisitStart: Trip end not detected, continuing tracking..."]
                                           showUI:TRUE];
    }
}

/*
 * END: State transition handlers
 */

@end
