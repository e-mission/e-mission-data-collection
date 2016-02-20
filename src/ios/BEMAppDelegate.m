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
#import "DataUtils.h"
#import <Parse/Parse.h>
#import <objc/runtime.h>

static char tripDiaryKey;
static char silentPushNotificationHandlerKey;

@implementation AppDelegate (notification)

// its dangerous to override a method from within a category.
// Instead we will use method swizzling. we set this up in the load call.
+ (void)load
{
    Method original, swizzled;

    original = class_getInstanceMethod(self, @selector(init));
    swizzled = class_getInstanceMethod(self, @selector(swizzled_init));
    method_exchangeImplementations(original, swizzled);
}

- (AppDelegate *)swizzled_init
{
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(createNotificationChecker:)
                                                 name:@"UIApplicationDidFinishLaunchingNotification" object:nil];

    // This actually calls the original init method over in AppDelegate. Equivilent to calling super
    // on an overrided method, this is not recursive, although it appears that way. neat huh?
    return [self swizzled_init];
}

// This code will be called immediately after application:didFinishLaunchingWithOptions:. We need
// to process notifications in cold-start situations
- (void)createNotificationChecker:(NSNotification *)notification
{
    if (notification)
    {
        NSDictionary *launchOptions = [notification userInfo];
            [self didFinishLaunchingWithOptions:launchOptions];
        }
    }

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

- (BOOL)didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    BOOL relaunchLocationMgr = NO;
    if ([launchOptions.allKeys containsObject:UIApplicationLaunchOptionsLocationKey]) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Application launched with LaunchOptionsLocationKey = YES"]];
        relaunchLocationMgr = YES;
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Application launched with LaunchOptionsLocationKey = NO"]];
        
    }
    
    if (self.tripDiaryStateMachine == NULL || relaunchLocationMgr) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"tripDiaryStateMachine = %@, relaunchLocationManager = %@, recreating the state machine",
              self.tripDiaryStateMachine, @(relaunchLocationMgr)]];
        self.tripDiaryStateMachine = [[TripDiaryStateMachine alloc] initRelaunchLocationManager:relaunchLocationMgr];
        [self.tripDiaryStateMachine registerForNotifications];
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
    if (self.silentPushHandler != nil) {
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
            self.silentPushHandler(UIBackgroundFetchResultNewData);
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
            [[BEMServerSyncCommunicationHelper pushAndClearUserCache] continueWithBlock:^id(BFTask *task) {
                    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                               @"Returning with fetch result = new data"]
                                                       showUI:TRUE];
                    self.silentPushHandler(UIBackgroundFetchResultNewData);
                return nil;
            }];
        } else if ([note.object isEqualToString:CFCTransitionTripRestarted]) {
            // The other option from TripEndDetected is that the trip is restarted instead of ended.
            // In that case, we still want to finish the handler
            self.silentPushHandler(UIBackgroundFetchResultNewData);
        } else {
            // Some random transition. Might as well call the handler and return
            self.silentPushHandler(UIBackgroundFetchResultNewData);
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
    [currentInstallation saveInBackgroundWithBlock:^(BOOL succeeded, NSError *error) {
        if (succeeded) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Successfully registered remote push notifications with parse"]];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Error %@ while registering for remote push notifications with parse", error.description] showUI:TRUE];
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
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"Received remote push, about to check whether a trip has ended"]
                                       showUI:TRUE];
    NSLog(@"About to check whether a trip has ended");
    self.silentPushHandler = completionHandler;
    NSLog(@"After setting the silent push handler, we have %@", self.silentPushHandler);
    NSDictionary* localUserInfo = @{@"handler": completionHandler};
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName object:CFCTransitionRecievedSilentPush userInfo:localUserInfo];
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
    [[BEMServerSyncCommunicationHelper backgroundSync] continueWithBlock:^id(BFTask *task) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"in background fetch, finished pushing entries to the server"]
                                           showUI:TRUE];
        completionHandler(UIBackgroundFetchResultNewData);
        return nil;
    }];
}

// The accessors use an Associative Reference since you can't define a iVar in a category
// http://developer.apple.com/library/ios/#documentation/cocoa/conceptual/objectivec/Chapters/ocAssociativeReferences.html
- (TripDiaryStateMachine *)tripDiaryStateMachine
{
    return objc_getAssociatedObject(self, &tripDiaryKey);
}

- (void)setTripDiaryStateMachine:(TripDiaryStateMachine *)tripDiaryStateMachine
{
    objc_setAssociatedObject(self, &tripDiaryKey, tripDiaryStateMachine, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (SilentPushCompletionHandler) silentPushHandler
{
    return objc_getAssociatedObject(self, &silentPushNotificationHandlerKey);
}

- (void)setSilentPushHandler:(SilentPushCompletionHandler)silentPushHandler
{
    objc_setAssociatedObject(self, &silentPushNotificationHandlerKey, silentPushHandler, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}


- (void)dealloc
{
    self.tripDiaryStateMachine = nil; // clear the association and release the object
    self.silentPushHandler = nil;
}


@end
