//
//  ViewController.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 1/30/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "ViewController.h"
#import "TripDiaryStateMachine.h"
#import "AppDelegate.h"

@interface ViewController ()

@end

@implementation ViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view, typically from a nib.
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

- (IBAction)checkInGeofence {
    [[self getTDSM] checkGeofenceState:self.geofenceCheckResult];
}

- (IBAction)forceEndTrip:(id)sender {
    [[self getTDSM] handleTransition:kTransitionStoppedMoving];
}

- (IBAction)forceStartTrip:(id)sender {
    [[self getTDSM] handleTransition:kTransitionInitialize];
}


- (TripDiaryStateMachine*) getTDSM {
    UIApplication* currApp = [UIApplication sharedApplication];
    AppDelegate* currDelegate = currApp.delegate;
    return currDelegate.tripDiaryStateMachine;
}

@end
