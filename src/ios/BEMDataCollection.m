#import "BEMDataCollection.h"
#import "LocalNotificationManager.h"
#import "Wrapper/LocationTrackingConfig.h"
#import "BEMAppDelegate.h"
#import "ConfigManager.h"
#import "DataUtils.h"
#import "StatsEvent.h"
#import "BEMBuiltinUserCache.h"
#import <CoreLocation/CoreLocation.h>

@implementation BEMDataCollection

- (void)pluginInitialize
{
    // TODO: We should consider adding a create statement to the init, similar
    // to android - then it doesn't matter if the pre-populated database is not
    // copied over.
    NSLog(@"BEMDataCollection:pluginInitialize singleton -> initialize statemachine and delegate");
    
    StatsEvent* se = [[StatsEvent alloc] initForEvent:@"app_launched"];
    [[BuiltinUserCache database] putMessage:@"key.usercache.client_nav_event" value:se];

    // NOTE: This CANNOT be part of a background thread because when the geofence creation, and more importantly,
    // the visit notification, are run as part of a background thread, they don't work.
    // I tried to move this into a background thread as part of a18f8f9385bdd9e37f7b412b386a911aee9a6ea0 and had
    // to revert it because even visit notification, which had been the bedrock of my existence so far, stopped
    // working although I made an explicit stop at the education library on the way to Soda.
    NSString* reqConsent = [self settingForKey:@"emSensorDataCollectionProtocolApprovalDate"];
    BOOL isConsented = [ConfigManager isConsented:reqConsent];
    if (isConsented) {
        [self initWithConsent];
    } else {
        NSDictionary* introDoneResult = [[BuiltinUserCache database] getLocalStorage:@"intro_done" withMetadata:NO];
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"intro_done result = %@", introDoneResult]];
        if (introDoneResult != NULL) {
            [LocalNotificationManager showNotification:@"New data collection terms - collection paused until consent"];
        }
    }
}

- (id)settingForKey:(NSString*)key
{
    return [self.commandDelegate.settings objectForKey:[key lowercaseString]];
}

- (void)initWithConsent {
    self.tripDiaryStateMachine = [TripDiaryStateMachine instance];
    NSDictionary* emptyOptions = @{};
    [AppDelegate didFinishLaunchingWithOptions:emptyOptions];
}

- (void)markConsented:(CDVInvokedUrlCommand*)command
{
    NSString* callbackId = [command callbackId];
    @try {
        NSDictionary* newDict = [[command arguments] objectAtIndex:0];
        ConsentConfig* newCfg = [ConsentConfig new];
        [DataUtils dictToWrapper:newDict wrapper:newCfg];
        [ConfigManager setConsented:newCfg];
        
        [self initWithConsent];
        
        /*
         * We don't strictly need to do this here since we don't use the data right now, but when we read
         * the data to sync to the server. But doing it here allows us to notify users who don't have a sufficiently
         * late model iPhone (https://github.com/e-mission/e-mission-phone/issues/60), and also gets all the notifications
         * out of the way so that the user is not confronted with a random permission popup hours after installing the app.
         * If/when we deal with users saying "no" to the permission prompts, it will make it easier to handle this in one
         * place as well.
         */
        
        if ([CMMotionActivityManager isActivityAvailable] == YES) {
            CMMotionActivityManager* activityMgr = [[CMMotionActivityManager alloc] init];
            NSOperationQueue* mq = [NSOperationQueue mainQueue];
            NSDate* startDate = [NSDate new];
            NSTimeInterval dayAgoSecs = 24 * 60 * 60;
            NSDate* endDate = [NSDate dateWithTimeIntervalSinceNow:-(dayAgoSecs)];
            [activityMgr queryActivityStartingFromDate:startDate toDate:endDate toQueue:mq withHandler:^(NSArray *activities, NSError *error) {
                if (error == nil) {
                    [LocalNotificationManager addNotification:@"activity recognition works fine"];
                } else {
                    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Error %@ while reading activities, travel mode detection may be unavailable", error]];
                    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Error while reading activities"
                                                                    message:@"Travel mode detection may be unavailable."
                                                                   delegate:nil
                                                          cancelButtonTitle:@"OK"
                                                          otherButtonTitles:nil];
                    [alert show];
                }
            }];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Activity detection unsupported, all trips will be UNKNOWN"]];
            UIAlertView *alert = [[UIAlertView alloc] initWithTitle:@"Activity detection unsupported"
                                                            message:@"Travel mode detection unavailable - all trips will be UNKNOWN."
                                                           delegate:nil
                                                  cancelButtonTitle:@"OK"
                                                  otherButtonTitles:nil];
            [alert show];
        }

        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While updating settings, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (void)launchInit:(CDVInvokedUrlCommand*)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
            @"launchInit called, is NOP on iOS"] showUI:FALSE];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (void)getConfig:(CDVInvokedUrlCommand *)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        LocationTrackingConfig* cfg = [ConfigManager instance];
        NSDictionary* retDict = [DataUtils wrapperToDict:cfg];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK
                                   messageAsDictionary:retDict];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (void)setConfig:(CDVInvokedUrlCommand *)command
{
    NSString* callbackId = [command callbackId];
    @try {
        NSDictionary* newDict = [[command arguments] objectAtIndex:0];
        LocationTrackingConfig* newCfg = [LocationTrackingConfig new];
        [DataUtils dictToWrapper:newDict wrapper:newCfg];
        [ConfigManager updateConfig:newCfg];
        [self restartCollection];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While updating settings, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }

}

- (void)getState:(CDVInvokedUrlCommand *)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        NSString* stateName = [TripDiaryStateMachine getStateName:self.tripDiaryStateMachine.currState];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK
                                   messageAsString:stateName];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting state, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (void)forceTransition:(CDVInvokedUrlCommand *)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        NSString* standardNotification = [[command arguments] objectAtIndex:0];
        NSString* iosNotification = [self getTransitionMap][standardNotification];
        if (iosNotification != nil) {
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                                object:iosNotification];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK
                                       messageAsString:iosNotification];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
        } else {
            NSString* msg = [NSString stringWithFormat: @"Unknown transition %@, ignoring", standardNotification];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (void)handleSilentPush:(CDVInvokedUrlCommand *)command
{
    NSString* callbackId = [command callbackId];
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"handleSilentPush outside the try block = %@", callbackId]];
    @try {
        void (^resultHandler)(UIBackgroundFetchResult) = ^(UIBackgroundFetchResult fetchResult){
                NSAssert(fetchResult == UIBackgroundFetchResultNewData,
                         @"fetchResult = %lu, expected %lu", (unsigned long)fetchResult, (unsigned long)UIBackgroundFetchResultNewData);
                CDVPluginResult* result = [CDVPluginResult
                                           resultWithStatus:CDVCommandStatus_OK];
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"in handleSilentPush, sending result to id %@", callbackId] showUI:TRUE];
                [self.commandDelegate sendPluginResult:result callbackId:callbackId];
        };
        dispatch_async(dispatch_get_main_queue(), ^{
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Before invoking launchTripEndCheckAndRemoteSync, id = %@", callbackId]];
            [AppDelegate launchTripEndCheckAndRemoteSync:resultHandler];
        });
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting settings, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (void)getAccuracyOptions:(CDVInvokedUrlCommand *)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        NSMutableDictionary* retVal = [NSMutableDictionary new];
        retVal[@"kCLLocationAccuracyBestForNavigation"] = @(kCLLocationAccuracyBestForNavigation);
        retVal[@"kCLLocationAccuracyBest"] = @(kCLLocationAccuracyBest);
        retVal[@"kCLLocationAccuracyNearestTenMeters"] = @(kCLLocationAccuracyNearestTenMeters);
        retVal[@"kCLLocationAccuracyHundredMeters"] = @(kCLLocationAccuracyHundredMeters);
        retVal[@"kCLLocationAccuracyKilometer"] = @(kCLLocationAccuracyKilometer);
        retVal[@"kCLLocationAccuracyThreeKilometers"] = @(kCLLocationAccuracyThreeKilometers);
        CDVPluginResult* result = [CDVPluginResult
                                           resultWithStatus:CDVCommandStatus_OK
                                           messageAsDictionary:retVal];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While getting accuracy options, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

- (NSDictionary*) getTransitionMap {
    NSMutableDictionary* retVal = [NSMutableDictionary new];
    retVal[@"INITIALIZE"] = CFCTransitionInitialize;
    retVal[@"EXITED_GEOFENCE"] = CFCTransitionExitedGeofence;
    retVal[@"STOPPED_MOVING"] = CFCTransitionTripEndDetected;
    retVal[@"STOP_TRACKING"] = CFCTransitionForceStopTracking;
    retVal[@"START_TRACKING"] = CFCTransitionStartTracking;
    retVal[@"RECEIVED_SILENT_PUSH"] = CFCTransitionRecievedSilentPush;
    retVal[@"VISIT_STARTED"] = CFCTransitionVisitStarted;
    retVal[@"VISIT_ENDED"] = CFCTransitionVisitEnded;
    return retVal;
}

- (void) restartCollection {
    /*
     Super hacky solution, but works for now. Steps are:
     * Stop tracking
     * [SKIPPED on iOS because stopping is synchronous] Poll for state change
     * Start tracking
     * Ugh! My eyeballs hurt to even read that?!
     */
    if (self.tripDiaryStateMachine.currState == kTrackingStoppedState) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"in restartCollection, state is already TRACKING_STOPPED, early return"]];
        return;
    }
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                        object:CFCTransitionForceStopTracking];
    
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"after stopping tracking, state is %@",
                                               [TripDiaryStateMachine getStateName:self.tripDiaryStateMachine.currState]]];
     
    // This may take a while to complete, but we don't care
    [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                         object:CFCTransitionStartTracking];
}

- (void) onAppTerminate {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"onAppTerminate called"] showUI:FALSE];    
}

@end
