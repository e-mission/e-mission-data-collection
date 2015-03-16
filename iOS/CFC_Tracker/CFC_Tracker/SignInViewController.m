//
//  SignInViewController.m
//
//  Copyright 2012 Google Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#import "AuthInspectorViewController.h"
#import "SignInViewController.h"
#import "AuthCompletionHandler.h"
#import "ConnectionSettings.h"
// #import "ClientStatsDatabase.h"
#import "CommunicationHelper.h"
#import "CustomSettings.h"

#import <GoogleOpenSource/GoogleOpenSource.h>
// #import <GooglePlus/GooglePlus.h>
#import <QuartzCore/QuartzCore.h>

typedef void(^AlertViewActionBlock)(void);

@interface SignInViewController () <AuthCompletionDelegate>

@property (nonatomic, copy) void (^confirmActionBlock)(void);
@property (nonatomic, copy) void (^cancelActionBlock)(void);

@end

static NSString * const kPlaceholderEmailAddress = @"<Email>";

// Labels for the cells that have in-cell control elements
// static NSString * const kConnectToMovesLabel = @"Connect to Moves";
static NSString * const kForceSyncLabel = @"Force Sync";
static NSString * const kGetServerToken = @"Get Server Token";

// Strings for Alert Views
static NSString * const kSignOutAlertViewTitle = @"Warning";
static NSString * const kSignOutAlertViewMessage =
    @"Modifying this element will sign you out of G+. Are you sure you wish to continue?";
static NSString * const kSignOutAlertCancelTitle = @"Cancel";
static NSString * const kSignOutAlertConfirmTitle = @"Continue";

@implementation SignInViewController {
  // This is an array of arrays, each one corresponding to the cell
  // labels for its respective section
  NSArray *_sectionCellLabels;

  // These sets contain the labels corresponding to cells that have various
  // types (each cell either drills down to another table view, contains an
  // in-cell switch, or contains a slider)
  NSArray *_switchCells;
    
  // Set that contains buttons
  NSArray *_buttonCells;
  
  // code to retain stats about the app
  // ClientStatsDatabase* _statsDb;
}

#pragma mark - View lifecycle

- (void)gppInit {
    NSLog(@"SignInView.gppInit called!");
  _sectionCellLabels = @[
    @[ kForceSyncLabel, kGetServerToken ]
  ];

  _switchCells = @[ ];
  _buttonCells = @[ kForceSyncLabel, kGetServerToken ];

  // _statsDb = [[ClientStatsDatabase alloc] init];
  // Make sure the GPPSignInButton class is linked in because references from
  // xib file doesn't count.
  AuthCompletionHandler *signIn = [AuthCompletionHandler sharedInstance];
  signIn.scope = @"https://www.googleapis.com/auth/plus.me";
  // signIn.delegate = [AuthCompletionHandler sharedInstance];
  [signIn registerFinishDelegate:self];
}

- (id)initWithNibName:(NSString *)nibNameOrNil
               bundle:(NSBundle *)nibBundleOrNil {
    NSLog(@"SignInView.initWithNibName called!");
  self = [super initWithNibName:nibNameOrNil bundle:nibBundleOrNil];
  if (self) {
    [self gppInit];
  }
  return self;
}

- (id)initWithCoder:(NSCoder *)aDecoder {
  self = [super initWithCoder:aDecoder];
  if (self) {
    [self gppInit];
  }
  return self;
}

- (void)viewWillAppear:(BOOL)animated {
  [self adoptUserSettings];
  if ([AuthCompletionHandler sharedInstance].currAuth == NULL) {
      NSLog(@"In SignInViewController.viewWillAppear, authentication is NULL, trying to re-authenticate");
      [[AuthCompletionHandler sharedInstance] trySilentAuthentication];
  }
  [self reportAuthStatus];
  [self updateButtons];
  [self.tableView reloadData];

  [super viewWillAppear:animated];
}

#pragma mark - GPPSignInDelegate

- (void)finishedWithAuth:(GTMOAuth2Authentication *)auth
                   error:(NSError *)error {
  NSLog(@"SignInViewController.finishedWithAuth called with auth = %@ and error = %@", auth, error);
  if (error) {
    _signInAuthStatus.text =
        [NSString stringWithFormat:@"Status: Authentication error: %@", error];
    return;
  }
    [self createUserProfile];
  [self reportAuthStatus];
  [self updateButtons];
}

- (void)createUserProfile {
    // Send a message to the server to create the account
    NSString* TAG = @"createUserProfile";
    [CommunicationHelper createUserProfile:^(NSData *data, NSURLResponse *response, NSError *error) {
        NSDictionary *resultJSON;
        NSError *conversionError;
        if (error) {
            NSLog(@"In %@, there was an error with connecting to the server!", TAG);
        }
        
        resultJSON = [NSJSONSerialization JSONObjectWithData:data options:NSJSONReadingMutableLeaves error:&conversionError];
        
        if (conversionError){
            NSLog(@"In %@, there was a conversion error from JSON to NSDictionary!", TAG);
        }
        // We are going to ignore the resultJSON for now, but won't ignore it in the future, when we improve error handling
        [self fetchCustomSettings];
    }];
}

- (void) fetchCustomSettings
{
    NSString* TAG = @"fetchCustomSettings";
    [CommunicationHelper getCustomSettings:^(NSData *data, NSURLResponse *response, NSError *error) {
        NSDictionary *sectionJSON;
        NSError *conversionError;
        if (error) {
            NSLog(@"In %@, there was an error with connecting to the server!", TAG);
        }
        
        sectionJSON = [NSJSONSerialization JSONObjectWithData:data options:NSJSONReadingMutableLeaves error:&conversionError];
        
        if (conversionError){
            NSLog(@"In %@, there was a conversion error from JSON to NSDictionary!", TAG);
        }
        
        [[CustomSettings sharedInstance] fillCustomSettingsWithDictionary:sectionJSON];
    }];
}

- (void)didDisconnectWithError:(NSError *)error {
  if (error) {
    _signInAuthStatus.text =
        [NSString stringWithFormat:@"Status: Failed to disconnect: %@", error];
  } else {
    _signInAuthStatus.text =
        [NSString stringWithFormat:@"Status: Disconnected"];
  }
  [self refreshUserInfo];
  [self updateButtons];
}

#pragma mark - Helper methods

// Updates the GPPSignIn shared instance and the GPPSignInButton
// to reflect the configuration settings that the user set
- (void)adoptUserSettings {
}

// Temporarily force the sign in button to adopt its minimum allowed frame
// so that we can find out its minimum allowed width (used for setting the
// range of the width slider).
- (CGFloat)minimumButtonWidth {
  CGRect frame = self.signInButton.frame;
  self.signInButton.frame = CGRectZero;

  CGFloat minimumWidth = self.signInButton.frame.size.width;
  self.signInButton.frame = frame;

  return minimumWidth;
}

- (void)reportAuthStatus {
  if ([AuthCompletionHandler sharedInstance].currAuth) {
    _signInAuthStatus.text = @"Status: Authenticated";
  } else {
    // To authenticate, use Google+ sign-in button.
    _signInAuthStatus.text = @"Status: Not authenticated";
  }
  [self refreshUserInfo];
}

// Update the interface elements containing user data to reflect the
// currently signed in user.
- (void)refreshUserInfo {
  if ([AuthCompletionHandler sharedInstance].currAuth == nil) {
    self.userEmailAddress.text = kPlaceholderEmailAddress;
    return;
  }

  self.userEmailAddress.text = [AuthCompletionHandler sharedInstance].currAuth.userEmail;
}

// Adjusts "Sign in", "Sign out", and "Disconnect" buttons to reflect
// the current sign-in state (ie, the "Sign in" button becomes disabled
// when a user is already signed in).
- (void)updateButtons {
  BOOL authenticated = ([AuthCompletionHandler sharedInstance].currAuth != nil);

  self.signInButton.enabled = !authenticated;
  self.signOutButton.enabled = authenticated;
  self.disconnectButton.enabled = authenticated;
  self.credentialsButton.hidden = !authenticated;

  if (authenticated) {
    self.signInButton.alpha = 0.5;
    self.signOutButton.alpha = self.disconnectButton.alpha = 1.0;
  } else {
    self.signInButton.alpha = 1.0;
    self.signOutButton.alpha = self.disconnectButton.alpha = 0.5;
  }
}

// Creates and shows an UIAlertView asking the user to confirm their action as it will log them
// out of their current G+ session

- (void)showSignOutAlertViewWithConfirmationBlock:(void (^)(void))confirmationBlock
                                      cancelBlock:(void (^)(void))cancelBlock {
  if ([[AuthCompletionHandler sharedInstance] currAuth]) {
    self.confirmActionBlock = confirmationBlock;
    self.cancelActionBlock = cancelBlock;

    UIAlertView *alertView = [[UIAlertView alloc] initWithTitle:kSignOutAlertViewTitle
                                                        message:kSignOutAlertViewMessage
                                                       delegate:self
                                              cancelButtonTitle:kSignOutAlertCancelTitle
                                              otherButtonTitles:kSignOutAlertConfirmTitle, nil];
    [alertView show];
  }
}

#pragma mark - IBActions

- (IBAction)signOut:(id)sender {
  [[AuthCompletionHandler sharedInstance] signOut];
  [self reportAuthStatus];
  [self updateButtons];
}

- (IBAction)disconnect:(id)sender {
  [[AuthCompletionHandler sharedInstance] signOut];
}

- (IBAction)showAuthInspector:(id)sender {
  AuthInspectorViewController *authInspector =
  [[AuthInspectorViewController alloc] init];
  [[self navigationController] pushViewController:authInspector animated:YES];
}

- (IBAction)signIn:(id)sender {
    UIViewController* loginScreen = [[AuthCompletionHandler sharedInstance] getSigninController];
    [[self navigationController] pushViewController:loginScreen animated:YES];
}

#pragma mark - UIAlertView Delegate

- (void)alertView:(UIAlertView *)alertView clickedButtonAtIndex:(NSInteger)buttonIndex {
  if (buttonIndex == alertView.cancelButtonIndex) {
    if (_cancelActionBlock) {
      _cancelActionBlock();
    }
  } else {
    if (_confirmActionBlock) {
      _confirmActionBlock();
      [self refreshUserInfo];
      [self updateButtons];
    }
  }

  _cancelActionBlock = nil;
  _confirmActionBlock = nil;
}

#pragma mark - UITableView Data Source

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
  return [_sectionCellLabels count];
}

- (NSInteger)tableView:(UITableView *)tableView
 numberOfRowsInSection:(NSInteger)section {
  return [_sectionCellLabels[section] count];
}

- (NSString *)tableView:(UITableView *)tableView
    titleForHeaderInSection:(NSInteger)section {
  if (section == 0) {
    return @"Sign-in Button Configuration";
  } else if (section == 1) {
    return @"External connections";
  } else {
    return nil;
  }
}

- (BOOL)tableView:(UITableView *)tableView
    shouldHighlightRowAtIndexPath:(NSIndexPath *)indexPath {
  NSString *label = _sectionCellLabels[indexPath.section][indexPath.row];
  if ([_buttonCells containsObject:label]) {
      return YES;
  } else {
      return NO;
  }
  return NO;
}

- (UITableViewCell *)tableView:(UITableView *)tableView
         cellForRowAtIndexPath:(NSIndexPath *)indexPath {
  static NSString * const kSwitchCell = @"SwitchCell";
  static NSString * const kButtonCell = @"ButtonCell";

  NSString *label = _sectionCellLabels[indexPath.section][indexPath.row];
  UITableViewCell *cell;
  NSString *identifier;

  if ([_switchCells containsObject:label]) {
    identifier = kSwitchCell;
  } else if ([_buttonCells containsObject:label]) {
      identifier = kButtonCell;
  }

  cell = [tableView dequeueReusableCellWithIdentifier:identifier];

  if (cell == nil) {
    cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleValue1
                                  reuseIdentifier:identifier];
  }

  if (identifier == kSwitchCell) {
    UISwitch *toggle = [[UISwitch alloc] initWithFrame:CGRectZero];
    cell.accessoryView = toggle;
  } else if (identifier == kButtonCell) {
      // For some reason, I am unable to add a button to a table cell,
      // either as an accessory, or to the content view.
      // However, we can detect clicks directly in the table cells, so
      // let us use that for now, and debug the button stuff later.
      /*
      UIButton *button = [UIButton buttonWithType: UIButtonTypeSystem];
      button.titleLabel.text = kConnectToMovesLabel;
      if ([label isEqualToString:kConnectToMovesLabel]) {
          [button addTarget:self action:@selector(connectToMoves:)
                forControlEvents: UIControlEventValueChanged];
      }
      [cell.contentView addSubview:button];
       */
  }
  cell.textLabel.text = label;
  return cell;
}

- (void)tableView:(UITableView *)tableView
    didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
  [tableView deselectRowAtIndexPath:indexPath animated:YES];
  UITableViewCell *selectedCell = [tableView cellForRowAtIndexPath:indexPath];
  NSString *label = selectedCell.textLabel.text;
  if ([label isEqualToString:kForceSyncLabel]) {
      UIApplication *theApp = [UIApplication sharedApplication];
      [theApp.delegate application:theApp performFetchWithCompletionHandler:^(UIBackgroundFetchResult result) {
          NSLog(@"Completion handler result = %lu", result);
      }];
  } else if ([label isEqualToString:kGetServerToken]) {
      [[AuthCompletionHandler sharedInstance] trySilentAuthentication];
      NSString *token = [AuthCompletionHandler sharedInstance].getIdToken;
      NSLog(@"serverToken = %@", token);
      UITextView *textView = tableView.tableFooterView.subviews[0];
      textView.text = token;
  }
}

@end
