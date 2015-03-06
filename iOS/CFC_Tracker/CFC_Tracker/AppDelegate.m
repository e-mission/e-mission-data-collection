    //
//  AppDelegate.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 1/30/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "AppDelegate.h"
#import "LocalNotificationManager.h"
#import "ConnectionSettings.h"
#import <Parse/Parse.h>

@interface AppDelegate () {
//    TripDiaryStateMachine* _stateMachine;
}
@end

@implementation AppDelegate


- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    // Once the application has launched, set up a geofence around the current location
    _tripDiaryStateMachine = [[TripDiaryStateMachine alloc] init];
    
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
    
    if ([launchOptions.allKeys containsObject:UIApplicationLaunchOptionsLocationKey]) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Application launched with LaunchOptionsLocationKey = YES"]];
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Application launched with LaunchOptionsLocationKey = NO"]];

    }
    return YES;
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
    NSLog(@"About to check whether a trip has ended");
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Received remote push notification"]];
    completionHandler(UIBackgroundFetchResultNewData);
//    [PFPush handlePush:userInfo];
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

@end
