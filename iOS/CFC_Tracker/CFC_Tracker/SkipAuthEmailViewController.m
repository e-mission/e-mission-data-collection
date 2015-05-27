//
//  SkipAuthEmailViewController.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 5/26/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "SkipAuthEmailViewController.h"
#import "AuthCompletionHandler.h"

@interface SkipAuthEmailViewController ()

@end

@implementation SkipAuthEmailViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view from its nib.
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

- (IBAction)handleEditingDone:(UITextField *)sender {
    NSString* userEmail = sender.text;
    NSLog(@"Got email %@ when editing is done", userEmail);
}

- (IBAction)handleLogin:(id)sender {
    NSString* userEmail = _userEmailField.text;
    GTMOAuth2Authentication* fakeAuth = [AuthCompletionHandler createFakeAuth:userEmail];
    [GTMOAuth2ViewControllerTouch saveParamsToKeychainForName:kKeychainItemName authentication:fakeAuth];
    [[AuthCompletionHandler sharedInstance] viewController:NULL finishedWithAuth:fakeAuth error:NULL];
    [self.navigationController popViewControllerAnimated:YES];
}

/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/

@end
