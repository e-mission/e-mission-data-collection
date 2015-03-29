//
//  TripDiaryStateMachineTests.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/19/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>

#import "TestUtils.h"

#import "TripDiaryDelegate.h"
#import "TripDiaryStateMachine.h"
#import "TripDiaryActions.h"
#import "AppDelegate.h"
#import "DataUtils.h"

@interface TripDiaryStateMachineTests : XCTestCase
@property TripDiaryStateMachine* testStateMachine;
@property TripDiaryDelegate* testDelegate;
@property NSNotificationCenter* defaultCenter;

@property NSArray* stepsToExecute;
@property int currStep;

@property id tripDiaryStateChangeObserver;

@property NSString* transitionWaitingFor;
@property BOOL waitForTransition;
@end

typedef void(^ExecutionStep)();

/*
 * The trip diary state machine works by tracking location automatically. On android, there is system support for
 * mocking the location service. On iOS, the system support for mocking the location service involves running things
 * through the UI, which doesn't sound that great for automating tests. Therefore, we plan to mock directly using
 * the delegate and the NotificationCenter.
 * 
 * In particular, we can simulate location updates by instantiating a TripDiaryDelegate and calling the various
 * locationManager methods with the appropriate parameters. We can check that the state transitions were correct by
 * looking at the transitions generated. We can even generate transitions if we want to simulate a change that is not
 * easily triggerable via the UI.
 */

@implementation TripDiaryStateMachineTests

- (void)setUp {
    [super setUp];
    // We could set this to the delegate of the state machine
    // But that is not available publicly
    [DataUtils clearOngoingDb];
    [DataUtils clearStoredDb];

    UIApplication* currApp = [UIApplication sharedApplication];
    AppDelegate* currDelegate = currApp.delegate;
    self.testStateMachine = currDelegate.tripDiaryStateMachine;
    self.testDelegate = self.testStateMachine.locMgr.delegate;
    self.defaultCenter = [NSNotificationCenter defaultCenter];
    [self.defaultCenter postNotificationName:CFCTransitionNotificationName
                                      object:CFCTransitionTrackingStopped];
    self.tripDiaryStateChangeObserver = [[NSNotificationCenter defaultCenter] addObserverForName:CFCTransitionNotificationName object:nil queue:nil usingBlock:^(NSNotification* note) {
        [self handleTransition:(NSString*)note.object];
    }];
}

- (void)tearDown {
    self.testStateMachine = NULL;
    self.testDelegate = NULL;
    [[NSNotificationCenter defaultCenter] removeObserver:self.tripDiaryStateChangeObserver];
    [super tearDown];
}

- (void) handleTransition:(NSString*) transition {
    NSLog(@"In test code, recieved transition %@", transition);
    if (self.transitionWaitingFor == nil) {
        NSLog(@"Nobody was waiting for this transition anyway");
    } else {
        if ([self.transitionWaitingFor isEqualToString:transition]) {
            self.transitionWaitingFor = nil;
            if (self.waitForTransition) {
                [self executeNextStep];
            } else {
                NSLog(@"Nobody was waiting for this transition anyway");
            }
        } else {
            XCTAssert(NO, @"Recieved transition %@ while waiting for transition %@", transition, self.transitionWaitingFor);
        }
    }
}

- (void) executeSteps:(NSArray*) steps {
    self.stepsToExecute = steps;
    self.currStep = -1;
    [self executeNextStep];
}

- (void) executeNextStep {
    if ((self.currStep == -1) || (self.currStep < (self.stepsToExecute.count - 1))) {
        self.currStep = self.currStep + 1;
        ExecutionStep currAction = self.stepsToExecute[self.currStep];
        currAction();
    } else {
        /*
        XCTAssert(NO, @"Ignoring step since we are already at the final action (%d > %lu)",
                  self.currStep, (self.stepsToExecute.count - 1));
         */
    }
}

- (void)testInitNormal {
    NSMutableArray* steps = [[NSMutableArray alloc] init];
    [steps addObject:^(void) {
        self.transitionWaitingFor = CFCTransitionInitialize;
        self.waitForTransition = YES;
    }];
    [steps addObject:^(void) {
        XCTAssert(self.testStateMachine.currState == kStartState, @"State check passed");
        NSArray* locArray = @[[TestUtils getLocation:@[@10, @20, @1]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray];
        [self executeNextStep];
    }];
    
    [steps addObject:^(void) {
        self.transitionWaitingFor = CFCTransitionInitComplete;
        self.waitForTransition = NO;
        [self executeNextStep];
    }];

    [steps addObject:^(void) {
        XCTAssert(self.testStateMachine.currState == kStartState, @"State check passed");
        NSArray* locArray = @[[TestUtils getLocation:@[@10, @20, @1]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didDetermineState:CLRegionStateInside
                                 forRegion:[TripDiaryActions getGeofence:self.testStateMachine.locMgr]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray];
    }];
    
    [self executeSteps:steps];
    [self.defaultCenter postNotificationName:CFCTransitionNotificationName
                                      object:CFCTransitionInitialize];

    /* Let us say that we have 4 steps
     * The last step is at index 3
     * so when currStep = 3, we have finished executing all steps
     * so if currStep < 3, we wait. If it is >=3, we quit.
     */
    while(self.currStep < (self.stepsToExecute.count-1)) {
        [NSThread sleepForTimeInterval:5];
    }
    
    XCTAssert(self.testStateMachine.currState == kWaitingForTripStartState,
              @"Current state is %lu, expecting %lu", self.testStateMachine.currState, kWaitingForTripStartState);
}

- (void)testInitInMotion {
    NSLog(@"At the beginning of the test, currState = %lu", self.testStateMachine.currState);
    NSMutableArray* steps = [[NSMutableArray alloc] init];
    [steps addObject:^(void) {
        self.transitionWaitingFor = CFCTransitionInitialize;
        self.waitForTransition = YES;
    }];
    [steps addObject:^(void) {
        NSArray* locArray = @[[TestUtils getLocation:@[@10, @20, @1]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray];
        [self executeNextStep];
    }];
    
    /*
     * For some reason, having the trip state machine handle the exited geofence transition by generating a
     * new notification (trip started) seems to change the order of delivery of the exited geofence transition to this listener.
     * In other words, the test code receives ExitedGeofence AFTER TripStarted.
     * 2015-03-21 23:30:15.344 cfctracker[27606:2440860] In test code, recieved transition T_INITIALIZE
     * 2015-03-21 23:30:24.826 cfctracker[27606:2440860] In test code, recieved transition T_TRIP_STARTED
     * 2015-03-21 23:30:26.238 cfctracker[27606:2440860] In test code, recieved transition T_TRIP_STARTED
     * 2015-03-21 23:30:26.238 cfctracker[27606:2440860] In test code, recieved transition T_EXITED_GEOFENCE
     */
    [steps addObject:^(void) {
        self.transitionWaitingFor = CFCTransitionTripStarted;
        self.waitForTransition = NO;
        [self executeNextStep];
    }];
    
    [steps addObject:^(void) {
        XCTAssert(self.testStateMachine.currState == kStartState, @"State check passed");
        NSArray* locArray = @[[TestUtils getLocation:@[@10, @20, @1]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didDetermineState:CLRegionStateOutside
                                 forRegion:[TripDiaryActions getGeofence:self.testStateMachine.locMgr]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray];
    }];
    
    [self executeSteps:steps];
    [self.defaultCenter postNotificationName:CFCTransitionNotificationName
                                      object:CFCTransitionInitialize];
    
    /* Let us say that we have 4 steps
     * The last step is at index 3
     * so when currStep = 3, we have finished executing all steps
     * so if currStep < 3, we wait. If it is >=3, we quit.
     */
    while(self.currStep < (self.stepsToExecute.count-1)) {
        [NSThread sleepForTimeInterval:5];
    }
    
    XCTAssert(self.testStateMachine.currState == kOngoingTripState,
              @"Current state is %lu, expecting %lu", self.testStateMachine.currState, kOngoingTripState);
}

- (void) testStartNormal {
    [self testInitNormal];
    
    NSMutableArray* steps = [[NSMutableArray alloc] init];
    [steps addObject:^(void) {
        self.transitionWaitingFor = CFCTransitionTripStarted;
        self.waitForTransition = YES;
    }];
    
    [self executeSteps:steps];
    [self.defaultCenter postNotificationName:CFCTransitionNotificationName
                                      object:CFCTransitionExitedGeofence];
    
    /* Let us say that we have 4 steps
     * The last step is at index 3
     * so when currStep = 3, we have finished executing all steps
     * so if currStep < 3, we wait. If it is >=3, we quit.
     */
    while(self.currStep < (self.stepsToExecute.count-1)) {
        [NSThread sleepForTimeInterval:5];
    }
    
    XCTAssert(self.testStateMachine.currState == kOngoingTripState,
              @"Current state is %lu, expecting %lu", self.testStateMachine.currState, kOngoingTripState);
}

- (void) testOngoingTrip {
    NSLog(@"At the beginning of the test, currState = %lu", self.testStateMachine.currState);
    NSMutableArray* steps = [[NSMutableArray alloc] init];

    [self testStartNormal];
    
    [steps addObject:^(void) {
        NSArray* locArray1 = @[[TestUtils getLocation:@[@10, @20, @1]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray1];
        
        NSArray* locArray2 = @[[TestUtils getLocation:@[@12, @22, @2]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray2];
        
        NSArray* locArray3 = @[[TestUtils getLocation:@[@14, @24, @3]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray3];
    }];

    [self executeSteps:steps];
    
    NSArray* retPoints = [DataUtils getLastPoints:5];
    XCTAssert(retPoints.count == 3, @"ret Points.count = %lu, expected 3", retPoints.count);
    
    CLLocation* firstPoint = (CLLocation*)retPoints[2];
    XCTAssert(firstPoint.coordinate.latitude == 10, @"firstPoint.coordinate.latitude = %f, expected 10",
              firstPoint.coordinate.latitude);
    XCTAssert(firstPoint.coordinate.longitude == 20, @"firstPoint.coordinate.latitude = %f, expected 20",
              firstPoint.coordinate.longitude);
    
    CLLocation* lastPoint = (CLLocation*)retPoints[0];
    XCTAssert(lastPoint.coordinate.latitude == 14, @"lastPoint.coordinate.latitude = %f, expected 14", lastPoint.coordinate.latitude);
    XCTAssert(lastPoint.coordinate.longitude == 24, @"lastPoint.coordinate.latitude = %f, expected 24",
              lastPoint.coordinate.longitude);
}

- (void) testTripNotEnded {
    NSLog(@"At the beginning of the test, currState = %lu", self.testStateMachine.currState);
    [self testStartNormal];

    NSMutableArray* steps = [[NSMutableArray alloc] init];
    NSDate* hourAgo = [NSDate dateWithTimeIntervalSinceNow:(-60 * 60)];
    
    [steps addObject:^(void) {
        NSArray* locArray1 = @[[TestUtils getLocation:@[@10, @20,
                                          [NSNumber numberWithDouble:hourAgo.timeIntervalSince1970]]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray1];
        
        NSArray* locArray2 = @[[TestUtils getLocation:@[@12, @22,
                                          [NSNumber numberWithDouble:(hourAgo.timeIntervalSince1970 + 10 * 60)]]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray2];
        
        NSArray* locArray3 = @[[TestUtils getLocation:@[@14, @24,
                                          [NSNumber numberWithDouble:(hourAgo.timeIntervalSince1970 + 50 * 60)]]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray3];
    }];
    
    [self executeSteps:steps];
    [self.defaultCenter postNotificationName:CFCTransitionNotificationName
                                      object:CFCTransitionRecievedSilentPush];
    
    // Trip hasn't ended, we will stay in the ongoing state
    XCTAssert(self.testStateMachine.currState == kOngoingTripState,
              @"Current state is %lu, expecting %lu", self.testStateMachine.currState, kOngoingTripState);
}

- (void) testTripEnded {
    NSLog(@"At the beginning of the test, currState = %lu", self.testStateMachine.currState);
    [self testStartNormal];
    
    NSMutableArray* steps = [[NSMutableArray alloc] init];
    NSDate* hourAgo = [NSDate dateWithTimeIntervalSinceNow:(-60 * 60)];
    
    [steps addObject:^(void) {
        NSArray* locArray1 = @[[TestUtils getLocation:@[@10, @20,
                                                        [NSNumber numberWithDouble:hourAgo.timeIntervalSince1970]]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray1];
        
        NSArray* locArray2 = @[[TestUtils getLocation:@[@12, @22,
                                                        [NSNumber numberWithDouble:(hourAgo.timeIntervalSince1970 + 5 * 60)]]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray2];
        
        NSArray* locArray3 = @[[TestUtils getLocation:@[@14, @24,
                                                        [NSNumber numberWithDouble:(hourAgo.timeIntervalSince1970 + 10 * 60)]]]];
        [self.testDelegate locationManager:self.testStateMachine.locMgr didUpdateLocations:locArray3];
        [self executeNextStep];
    }];
    [steps addObject:^(void) {
        self.transitionWaitingFor = CFCTransitionTripEndDetected;
        self.waitForTransition = YES;
    }];
    [steps addObject:^(void) {
        XCTAssert(self.testStateMachine.currState == kOngoingTripState, @"State check passed");
        [self.testDelegate locationManager:self.testStateMachine.locMgr didDetermineState:CLRegionStateInside
                                 forRegion:[TripDiaryActions getGeofence:self.testStateMachine.locMgr]];
    }];
    /*
     * Note that we wait for trip ended instead of EndTripTracking because TripEnded is generated while handling
     * EndTripTracking and that reverses the order for the test code (see testInitInMotion above). Since
     * EndTripTracking is an intermediate transition, we just check for the final transition instead.
     */
    [steps addObject:^(void) {
        self.transitionWaitingFor = CFCTransitionTripEnded;
        self.waitForTransition = YES;
    }];

    
    [self executeSteps:steps];
    [self.defaultCenter postNotificationName:CFCTransitionNotificationName
                                      object:CFCTransitionRecievedSilentPush];
    
    // Trip hasn't ended, we will stay in the ongoing state
    XCTAssert(self.testStateMachine.currState == kWaitingForTripStartState,
              @"Current state is %lu, expecting %lu", self.testStateMachine.currState, kWaitingForTripStartState);
}

@end
