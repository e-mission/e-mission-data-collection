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
#import "AuthCompletionHandler.h"
#import "BEMRemotePushNotificationHandler.h"
#import "DataUtils.h"
#import "LocationTrackingConfig.h"
#import "ConfigManager.h"
#import "BEMServerSyncConfigManager.h"
#import "BEMServerSyncPlugin.h"
#import <Parse/Parse.h>
#import <objc/runtime.h>

@implementation AppDelegate (notification)

+ (BOOL)didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    [Parse setApplicationId:[[ConnectionSettings sharedInstance] getParseAppID]
                  clientKey:[[ConnectionSettings sharedInstance] getParseClientID]];
    
    if ([UIApplication instancesRespondToSelector:@selector(registerUserNotificationSettings:)]) {
        [[UIApplication sharedApplication] registerUserNotificationSettings:[UIUserNotificationSettings
                settingsForTypes:UIUserNotificationTypeAlert|UIUserNotificationTypeBadge
                categories:nil]];
    }
    
    if ([BEMServerSyncConfigManager instance].ios_use_remote_push) {
        if ([UIApplication instancesRespondToSelector:@selector(registerForRemoteNotifications)]) {
            [[UIApplication sharedApplication] registerForRemoteNotifications];
        } else if ([UIApplication instancesRespondToSelector:@selector(registerForRemoteNotificationTypes:)]){
            [[UIApplication sharedApplication] registerForRemoteNotificationTypes:(UIRemoteNotificationTypeBadge|UIRemoteNotificationTypeAlert)];
        } else {
            NSLog(@"registering for remote notifications not supported");
        }
    } else {
        [BEMServerSyncPlugin applySync];
    }
    
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Initialized remote push notification handler %@, finished registering for notifications ",
                                                [BEMRemotePushNotificationHandler instance]]
                                       showUI:FALSE];

    // Handle google+ sign on
    [AuthCompletionHandler sharedInstance].clientId = [[ConnectionSettings sharedInstance] getGoogleiOSClientID];
    [AuthCompletionHandler sharedInstance].clientSecret = [[ConnectionSettings sharedInstance] getGoogleiOSClientSecret];
    return YES;
}


- (void)application:(UIApplication *)application
                    didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Finished registering for remote notifications with token %@", deviceToken]];

    // Store the deviceToken in the current installation and save it to Parse.
    PFInstallation *currentInstallation = [PFInstallation currentInstallation];
    [currentInstallation setDeviceTokenFromData:deviceToken];
    [BEMServerSyncPlugin applySync];
    [currentInstallation saveInBackgroundWithBlock:^(BOOL succeeded, NSError *error) {
        if (succeeded) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Successfully registered remote push notifications for token %@ with parse", deviceToken]];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Error %@ while registering remote push notifications for token %@ with parse", error.description, deviceToken] showUI:TRUE];
        }
    }];
}

- (void)application:(UIApplication *)application
                    didFailToRegisterForRemoteNotificationsWithError:(NSError *)error {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Failed to register for remote push notifications with APN %@", error.description] showUI:TRUE];
}

- (void)application:(UIApplication *)application
                    didReceiveRemoteNotification:(NSDictionary *)userInfo
                    fetchCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler {
    if ([BEMServerSyncConfigManager instance].ios_use_remote_push == YES) {
        [self launchTripEndCheckAndRemoteSync:completionHandler];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Received remote push, ignoring"]
                                               showUI:FALSE];
        completionHandler(UIBackgroundFetchResultNewData);
        }
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
                                               @"Application will enter the foreground"]];
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
    if ([BEMServerSyncConfigManager instance].ios_use_remote_push == NO) {
        [self launchTripEndCheckAndRemoteSync:completionHandler];
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Received background fetch call, ignoring"]
                                           showUI:FALSE];
        completionHandler(UIBackgroundFetchResultNewData);
    }
}

- (void) launchTripEndCheckAndRemoteSync:(void (^)(UIBackgroundFetchResult))completionHandler {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Received background sync call when useRemotePush = %@, about to check whether a trip has ended", @([BEMServerSyncConfigManager instance].ios_use_remote_push)]
                                       showUI:FALSE];
    NSLog(@"About to check whether a trip has ended");
    NSDictionary* localUserInfo = @{@"handler": completionHandler};
    [[AuthCompletionHandler sharedInstance] getValidAuth:^(GTMOAuth2Authentication *auth, NSError *error) {
        /*
         * Note that we do not condition any further tasks on this refresh. That is because, in general, we expect that
         * the token refreshed at this time will be used to push the next set of values. This is just pre-emptive refreshing,
         * to increase the chance that we will finish pushing our data within the 30 sec interval.
         */
        if (error == NULL) {
            GTMOAuth2Authentication* currAuth = [AuthCompletionHandler sharedInstance].currAuth;
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Finished refreshing token in background, new expiry is %@", currAuth.expirationDate]
                                               showUI:FALSE];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Error %@ while refreshing token in background", error]
                                               showUI:TRUE];
        }
    } forceRefresh:TRUE];
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName object:CFCTransitionRecievedSilentPush userInfo:localUserInfo];
}

@end
