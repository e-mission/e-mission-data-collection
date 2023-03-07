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
#import "BEMRemotePushNotificationHandler.h"
#import "DataUtils.h"
#import "LocationTrackingConfig.h"
#import "ConfigManager.h"
#import "BEMServerSyncConfigManager.h"
#import "BEMServerSyncPlugin.h"
#import "Cordova/CDVConfigParser.h"
#import <objc/runtime.h>
#import "AuthTokenCreationFactory.h"

@implementation AppDelegate (datacollection)

+ (BOOL)didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    if ([UIApplication instancesRespondToSelector:@selector(registerUserNotificationSettings:)]) {
        [[UIApplication sharedApplication] registerUserNotificationSettings:[UIUserNotificationSettings
                settingsForTypes:UIUserNotificationTypeAlert|UIUserNotificationTypeBadge
                categories:nil]];
    }
    
    if ([BEMServerSyncConfigManager instance].ios_use_remote_push) {
        // NOP - this is handled in javascript
    } else {
        [BEMServerSyncPlugin applySync];
    }
    
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Initialized remote push notification handler %@, finished registering for notifications ",
                                                [BEMRemotePushNotificationHandler instance]]
                                       showUI:FALSE];

    return YES;
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

- (BOOL)application:(UIApplication *)app
            openURL:(NSURL *)url
            options:(NSDictionary *)options {
    if (!url) {
        return NO;
    }
    
    [[NSNotificationCenter defaultCenter] postNotification:[NSNotification notificationWithName:CDVPluginHandleOpenURLNotification object:url userInfo:options]];
    

    return YES;
}


// this happens while we are running ( in the background, or from within our own app )
// only valid if 40x-Info.plist specifies a protocol to handle
- (BOOL)application:(UIApplication*)application
            openURL:(NSURL*)url
  sourceApplication:(NSString*)sourceApplication
         annotation:(id)annotation
{
    if (!url) {
        return NO;
    }
    
    NSDictionary* userInfo = @{UIApplicationOpenURLOptionsSourceApplicationKey: sourceApplication,
                               UIApplicationOpenURLOptionsAnnotationKey: annotation};
    
    // all plugins will get the notification, and their handlers will be called
    [[NSNotificationCenter defaultCenter] postNotification:[NSNotification notificationWithName:CDVPluginHandleOpenURLNotification object:url userInfo:userInfo]];
    
    return YES;
}


- (void)applicationWillTerminate:(UIApplication *)application {
    // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Application is about to terminate"]];
    [LocalNotificationManager showNotificationAfterSecs:[NSString stringWithFormat:NSLocalizedStringFromTable(@"dont-force-kill-please", @"DCLocalizable", nil)]
                                           withUserInfo:NULL
                                              secsLater:60];
}

- (void)application:(UIApplication*)application performFetchWithCompletionHandler:(void (^)(UIBackgroundFetchResult))completionHandler {
    if ([BEMServerSyncConfigManager instance].ios_use_remote_push == NO) {
        [AppDelegate launchTripEndCheckAndRemoteSync:completionHandler];
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Received background fetch call, ignoring"]
                                           showUI:FALSE];
        completionHandler(UIBackgroundFetchResultNewData);
    }
}

// TODO: Figure out better solution for this.
// Maybe a separate plist instead of putting it into the config.xml?

+ (NSString*) getReqConsent
{
    NSString* path = [[NSBundle mainBundle] pathForResource:@"config.xml" ofType:nil];
    NSURL* url = [NSURL fileURLWithPath:path];

    NSXMLParser* configParser = [[NSXMLParser alloc] initWithContentsOfURL:url];
    if (configParser == nil) {
        NSLog(@"Failed to initialize XML parser.");
        return NULL;
    }
    CDVConfigParser* delegate = [[CDVConfigParser alloc] init];
    [configParser setDelegate:((id < NSXMLParserDelegate >)delegate)];
    [configParser parse];
    return [delegate.settings objectForKey:[@"emSensorDataCollectionProtocolApprovalDate" lowercaseString]];
}

+ (void) launchTripEndCheckAndRemoteSync:(void (^)(UIBackgroundFetchResult))completionHandler {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Received background sync call when useRemotePush = %@, about to check whether a trip has ended", @([BEMServerSyncConfigManager instance].ios_use_remote_push)]
                                       showUI:FALSE];
    NSLog(@"About to check whether a trip has ended");
    NSDictionary* localUserInfo = @{@"handler": completionHandler};
    
    /*
    Since we now use a locally stored string as the token, we don't need to refresh it asynchronously
    but let's keep the calls to refresh in case we need to ever restore it
    Note that this should really call forceRefreshToken instead of being a copy-paste

    [[AuthTokenCreationFactory getInstance] getExpirationDate:^(NSString *expirationDate, NSError *error) {
         * Note that we do not condition any further tasks on this refresh. That is because, in general, we expect that
         * the token refreshed at this time will be used to push the next set of values. This is just pre-emptive refreshing,
         * to increase the chance that we will finish pushing our data within the 30 sec interval.
        if (error == NULL) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Finished refreshing token in background, new expiry is %@",expirationDate]
                                               showUI:FALSE];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Check your login - error %@ while refreshing token in background", error]
                                               showUI:TRUE];
        }
    }];
    */
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName object:CFCTransitionRecievedSilentPush userInfo:localUserInfo];
    [AppDelegate checkNativeConsent];
}

+ (void) checkNativeConsent {
    BOOL isConsented = [ConfigManager isConsented:[AppDelegate getReqConsent]];
    if (!isConsented) {
        [LocalNotificationManager showNotification:NSLocalizedStringFromTable(@"new-data-collections-terms", @"DCLocalizable", nil)];
    }
}

@end
