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
#import "BEMServerSyncCommunicationHelper.h"
#define NotificationCallback @"NOTIFICATION_CALLBACK"

@interface AppDelegate (datacollection)

+ (BOOL)didFinishLaunchingWithOptions:(NSDictionary *)launchOptions;
+ (NSString*) getReqConsent;
+ (void) checkNativeConsent;
+ (void) launchTripEndCheckAndRemoteSync:(void (^)(UIBackgroundFetchResult))completionHandler;
- (void)applicationDidBecomeActive:(UIApplication *)application;

@end

