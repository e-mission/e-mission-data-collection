//
//  ViewController.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 1/30/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "ViewController.h"
#import "TripDiaryStateMachine.h"
#import "DbLogging.h"
#import "AuthInspectorViewController.h"
#import "SignInViewController.h"
#import "AuthCompletionHandler.h"
#import "AppDelegate.h"
#import "GeofenceActions.h"

@interface ViewController () {
    NSArray* _transitionMessages;
    GeofenceActions* _geofenceLocator;
}
@property(strong, nonatomic) SignInViewController *signInViewController;
@property(strong, nonatomic) UINavigationController *navigationController;
@end

@implementation ViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.transitionTable.dataSource = self;
    self.tdsrmCurrState.adjustsFontSizeToFitWidth = YES;
    
    // Do any additional setup after loading the view, typically from a nib.
    self.signInViewController = [[SignInViewController alloc] initWithNibName:nil bundle:nil];
    self.navigationController = (UINavigationController*)self.parentViewController;
    // self.navigationController = [[UINavigationController alloc] initWithRootViewController:self];
    // self.navigationController.navigationBarHidden = NO;

    NSLog(@"navigationController = %@, navigation bar = %@, hidden = %d",
          self.navigationController,
          self.navigationController.navigationBar,
          self.navigationController.navigationBarHidden);
    
//    self.signInViewController.delegate = self;
    [self initializeAuthResultBarButtons];
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

- (void)initializeAuthResultBarButtons
{
    UIBarButtonItem *authButton = [[UIBarButtonItem alloc] initWithTitle:@"Auth" style:UIBarButtonItemStyleBordered target: self action:@selector(showSignInView:)];
    self.navigationItem.leftBarButtonItems = @[authButton];
}

- (void)showSignInView:(id)sender
{
    if ([self.navigationController.viewControllers containsObject:self.signInViewController]) {
        // the sign in view is already visible, don't need to push it again
        NSLog(@"sign in view is already in the navigation chain, skipping the push to the controller...");
    } else {
        [self.navigationController pushViewController:self.signInViewController animated:YES];
    }
}

- (IBAction)checkInGeofence {
    CLLocationManager* newLoc = [CLLocationManager new];
    _geofenceLocator = [GeofenceActions new];
    [_geofenceLocator getLocationForGeofence:newLoc withCallback:^(CLLocation *locationToUse) {
        NSLog(@"Got location %@ to use", locationToUse);
    }];
}

- (IBAction)forceEndTrip:(id)sender {
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                        object:CFCTransitionForceStopTracking];
}

- (IBAction)forceStartTrip:(id)sender {
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                        object:CFCTransitionExitedGeofence];
}

- (IBAction)resetStateMachine:(id)sender {
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                        object:CFCTransitionInitialize];
}

- (IBAction)refreshState:(id)sender {
    NSString* refreshedState = [TripDiaryStateMachine getStateName:[self getTDSM].currState];
    self.tdsrmCurrState.text = refreshedState;
    [self.transitionTable reloadData];
}

- (IBAction)clearTransitions:(id)sender {
    [[DbLogging database] clearTransitions];
    [self.transitionTable reloadData];
}

- (IBAction)switchStateMachineMode:(id)sender {
    
}

- (TripDiaryStateMachine*) getTDSM {
    UIApplication* currApp = [UIApplication sharedApplication];
    AppDelegate* currDelegate = currApp.delegate;
    return currDelegate.tripDiaryStateMachine;
}

- (NSInteger)tableView:(UITableView *)tableView
 numberOfRowsInSection:(NSInteger)section {
    _transitionMessages = [[DbLogging database] getTransitions];
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
