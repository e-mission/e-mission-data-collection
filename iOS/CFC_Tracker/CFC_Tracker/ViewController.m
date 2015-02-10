//
//  ViewController.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 1/30/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "ViewController.h"
#import "TripDiaryStateMachine.h"
#import "OngoingTripsDatabase.h"
#import "AppDelegate.h"

@interface ViewController () {
    NSArray* _transitionMessages;
}

@end

@implementation ViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.transitionTable.dataSource = self;
    self.tdsrmCurrState.adjustsFontSizeToFitWidth = YES;
    // Do any additional setup after loading the view, typically from a nib.
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

- (IBAction)checkInGeofence {
    [[self getTDSM] checkGeofenceState:^(NSString *geofenceStatus) {
        self.geofenceCheckResult.text = geofenceStatus;
    }];
}

- (IBAction)forceEndTrip:(id)sender {
    [[self getTDSM] handleTransition:kTransitionStoppedMoving];
}

- (IBAction)forceStartTrip:(id)sender {
    [[self getTDSM] handleTransition:kTransitionExitedGeofence];
}

- (IBAction)resetStateMachine:(id)sender {
    [[self getTDSM] handleTransition:kTransitionInitialize];
}

- (IBAction)refreshState:(id)sender {
    NSString* refreshedState = [self getTDSM].currState;
    self.tdsrmCurrState.text = refreshedState;
    [self.transitionTable reloadData];
}

- (IBAction)clearTransitions:(id)sender {
    [[OngoingTripsDatabase database] clearTransitions];
    [self.transitionTable reloadData];
}

- (TripDiaryStateMachine*) getTDSM {
    UIApplication* currApp = [UIApplication sharedApplication];
    AppDelegate* currDelegate = currApp.delegate;
    return currDelegate.tripDiaryStateMachine;
}

- (NSInteger)tableView:(UITableView *)tableView
 numberOfRowsInSection:(NSInteger)section {
    _transitionMessages = [[OngoingTripsDatabase database] getTransitions];
    return _transitionMessages.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView
         cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    NSInteger row = indexPath.row;
    UITableViewCell* retCell = [[UITableViewCell alloc]
                              initWithStyle: UITableViewCellStyleSubtitle
                              reuseIdentifier:@"SUBTITLE_CELL"];
    retCell.textLabel.text = _transitionMessages[row][0];
    retCell.textLabel.adjustsFontSizeToFitWidth = YES;
    
    retCell.detailTextLabel.text = _transitionMessages[row][1];
    retCell.detailTextLabel.adjustsFontSizeToFitWidth = YES;
    return retCell;
}

@end
