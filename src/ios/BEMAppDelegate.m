    //
//  AppDelegate.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 1/30/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "BEMAppDelegate.h"
#import "LocalNotificationManager.h"
#import "BEMConnectionSettings.h"
#import "BEMServerSyncCommunicationHelper.h"
#import "AuthCompletionHandler.h"
#import "DataUtils.h"
#import <Parse/Parse.h>

typedef void (^SilentPushCompletionHandler)(UIBackgroundFetchResult);

@interface BEMAppDelegate () {
    SilentPushCompletionHandler _silentPushHandler;
}
@end

@implementation BEMAppDelegate

/*
 * Note that it is possible that some of this can happen on startup init
 * instead of every time the application is launched. But I am not sure which
 * ones, and so far, we have always done everything when the application is
 * launched. I am apprehensive that moving to startup init will break things in
 * unexpected ways, specially while we are making a bunch of other changes
 * anyway. So the current plan is that the code will be retained in here, this
 * will be called from the delegate's didFinishLaunchingWithOptions method, and
 * once we know that everything works, I can slowly move changes to
 * startupInit, one by one.
 */


- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    BOOL relaunchLocationMgr = NO;
    if ([launchOptions.allKeys containsObject:UIApplicationLaunchOptionsLocationKey]) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Application launched with LaunchOptionsLocationKey = YES"]];
        relaunchLocationMgr = YES;
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Application launched with LaunchOptionsLocationKey = NO"]];
        
    }
    
    if (_tripDiaryStateMachine == NULL || relaunchLocationMgr) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"tripDiaryStateMachine = %@, relaunchLocationManager = %@, recreating the state machine",
              _tripDiaryStateMachine, @(relaunchLocationMgr)]];
        _tripDiaryStateMachine = [[TripDiaryStateMachine alloc] initRelaunchLocationManager:relaunchLocationMgr];
        [_tripDiaryStateMachine registerForNotifications];
    }
    
    [Parse setApplicationId:[[ConnectionSettings sharedInstance] getParseAppID]
                  clientKey:[[ConnectionSettings sharedInstance] getParseClientID]];
    
    if ([UIApplication instancesRespondToSelector:@selector(registerUserNotificationSettings:)]) {
        [[UIApplication sharedApplication] registerUserNotificationSettings:[UIUserNotificationSettings
                settingsForTypes:UIUserNotificationTypeAlert|UIUserNotificationTypeBadge
                categories:nil]];
    }
    
    if ([UIApplication instancesRespondToSelector:@selector(registerForRemoteNotifications)]) {
        [[UIApplication sharedApplication] registerForRemoteNotifications];
    } else if ([UIApplication instancesRespondToSelector:@selector(registerForRemoteNotificationTypes:)]){
        [[UIApplication sharedApplication] registerForRemoteNotificationTypes:(UIRemoteNotificationTypeBadge|UIRemoteNotificationTypeAlert)];
    } else {
        NSLog(@"registering for remote notifications not supported");
    }
    [[NSNotificationCenter defaultCenter] addObserverForName:CFCTransitionNotificationName object:nil queue:nil
                                                  usingBlock:^(NSNotification *note) {
                                                      [self handleNotifications:note];
                                                  }];
    
    // Handle google+ sign on
    [AuthCompletionHandler sharedInstance].clientId = [[ConnectionSettings sharedInstance] getGoogleiOSClientID];
    [AuthCompletionHandler sharedInstance].clientSecret = [[ConnectionSettings sharedInstance] getGoogleiOSClientSecret];
    return YES;
}

- (void)handleNotifications:(NSNotification*)note {
    if (_silentPushHandler != nil) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Received notification %@ while processing silent push notification", note.object]];
        // we only care about notifications when we are processing a silent remote push notification
        if ([note.object isEqualToString:CFCTransitionRecievedSilentPush]) {
            // We are in the silent push handler, so we ignore the silent push
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Ignoring SILENT_PUSH in the silent push handler"]];
            // Note: Do NOT call the handler here since the state machine may not have quiesced.
            // We want to wait until we know that the state machine has finished handling it.
            // _silentPushHandler(UIBackgroundFetchResultNewData);
        } else if ([note.object isEqualToString:CFCTransitionNOP]) {
            // Next, we think of what the possible responses to the silent push are
            // One option is that the state machine wants to ignore it, possibly because it is not in ONGOING STATE
            // Let us assume that we will return NOP in that case
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Trip diary state machine ignored the silent push"]];
            _silentPushHandler(UIBackgroundFetchResultNewData);
        } else if ([note.object isEqualToString:CFCTransitionTripEndDetected]) {
            // Otherwise, if it is in OngoingTrip, it will try to see whether the trip has ended. If it hasn't,
            // let us assume that we will return a NOP, which is already handled.
            // If it has, then it will return a TripEndDetected and start creating the geofence.
            // Once the geofence is created, we will get a TripEnded, and we want to
            // wait until that point, so we DON'T return here.
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Detected trip end, waiting until geofence is created to return from silent push"]];
        } else if ([note.object isEqualToString:CFCTransitionTripEnded]) {
            // Trip has now ended, so we can push and clear data
            [DataUtils pushAndClearData:^(BOOL status) {
                // We only ever call this with true right now
                if (status == true) {
                    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                               @"Returning with fetch result = new data"]
                                                       showUI:TRUE];
                    _silentPushHandler(UIBackgroundFetchResultNewData);
                } else {
                    /*
                     * We always return "new data", even if there was no data, because there is some evidence
                     * that returning "no data" may cause the app to be killed while returning "new data" might not.
                     * https://github.com/e-mission/e-mission-data-collection/issues/70
                     */
                    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                               @"Sent no data, but Returning with fetch result = new data"]
                                                       showUI:TRUE];
                    _silentPushHandler(UIBackgroundFetchResultNoData);
                }
            }];
        } else if ([note.object isEqualToString:CFCTransitionTripRestarted]) {
            // The other option from TripEndDetected is that the trip is restarted instead of ended.
            // In that case, we still want to finish the handler
            _silentPushHandler(UIBackgroundFetchResultNewData);
        } else {
            // Some random transition. Might as well call the handler and return
            _silentPushHandler(UIBackgroundFetchResultNewData);
        }
        // TODO: Figure out whether we should set it to NULL here or whether parts of
        // the system will still try to access the handler.
        // _silentPushHandler = nil;
    } else {
        // Not processing a silent remote push notification
        NSLog(@"Ignoring silent push notification");
    }
}

- (void)application:(UIApplication *)application
                    didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken {
    NSLog(@"Finished registering for remote notifications with token %@", deviceToken);
    // Store the deviceToken in the current installation and save it to Parse.
    PFInstallation *currentInstallation = [PFInstallation currentInstallation];
    [currentInstallation setDeviceTokenFromData:deviceToken];
    [currentInstallation saveInBackground];
}

- (void)application:(UIApplication *)application
                    didFailToRegisterForRemoteNotificationsWithError:(NSError *)error {
    NSLog(@"Failed to register for remote notifications with error %@", error);
}

- (void)application:(UIApplication *)application
                    didReceiveRemoteNotification:(NSDictionary *)userInfo
                    fetchCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Received remote push, about to check whether a trip has ended"]
                                       showUI:TRUE];
    NSLog(@"About to check whether a trip has ended");
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName object:CFCTransitionRecievedSilentPush];
    _silentPushHandler = completionHandler;
}

- (void)applicationWillResignActive:(UIApplication *)application {
    // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
    // Use this method to pause ongoing tasks, disable timers, and throttle down OpenGL ES frame rates. Games should use this method to pause the game.
}

- (void)applicationDidEnterBackground:(UIApplication *)application {
    // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
    // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Application went to the background"]];
}

- (void)applicationWillEnterForeground:(UIApplication *)application {
    // Called as part of the transition from the background to the inactive state; here you can undo many of the changes made on entering the background.
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Application will enter the background"]];
}

- (void)applicationDidBecomeActive:(UIApplication *)application {
    // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
}

- (void)applicationWillTerminate:(UIApplication *)application {
    // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Application is about to terminate"]];

}

- (void)application:(UIApplication*)application performFetchWithCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler {
    NSLog(@"performFetchWithCompletionHandler called at %@", [NSDate date]);
    [DataUtils pushAndClearData:^(BOOL status) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"in background fetch, finished pushing entries to the server"]
                                           showUI:TRUE];
    }];
}

@end
