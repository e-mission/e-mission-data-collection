//
//  AppDelegate.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 1/30/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "TripDiaryStateMachine.h"
#import "AppDelegate.h"

typedef void (^SilentPushCompletionHandler)(UIBackgroundFetchResult);

@interface AppDelegate (notification)

@property (retain, nonatomic) TripDiaryStateMachine *tripDiaryStateMachine;
@property (copy, nonatomic) SilentPushCompletionHandler silentPushHandler;

- (BOOL)didFinishLaunchingWithOptions:(NSDictionary *)launchOptions;
- (void)application:(UIApplication *)application didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken;
- (void)application:(UIApplication *)application didFailToRegisterForRemoteNotificationsWithError:(NSError *)error;
- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo fetchCompletionHandler:( void (^)(UIBackgroundFetchResult))completionHandler;
- (void)applicationDidBecomeActive:(UIApplication *)application;

@end

