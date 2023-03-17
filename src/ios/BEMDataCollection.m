#import "BEMDataCollection.h"
#import "LocalNotificationManager.h"
#import "Wrapper/LocationTrackingConfig.h"
#import "BEMAppDelegate.h"
#import "ConfigManager.h"
#import "DataUtils.h"
#import "StatsEvent.h"
#import "BEMBuiltinUserCache.h"
#import "SensorControlForegroundDelegate.h"
#import "SensorControlBackgroundChecker.h"
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
            [LocalNotificationManager showNotification:NSLocalizedStringFromTable(@"new-data-collections-terms", @"DCLocalizable", nil)];
        }
    }
}

- (id)settingForKey:(NSString*)key
{
    return [self.commandDelegate.settings objectForKey:[key lowercaseString]];
}

- (void)initWithConsent {
    self.tripDiaryStateMachine = [TripDiaryStateMachine instance];
    [SensorControlBackgroundChecker checkAppState];
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
        
        // Refactored code for simplicity
        // for the motion activity code, we just call checkMotionSettingsAndPermission directly
        // but for location, there is an alternative to opening the settings, on iOS11 and iOS12,
        // which is actually easier ("always allow")
        // so in that case, we continue calling the init code in TripDiaryStateMachine
        [self initWithConsent];
        /*
        [TripDiarySettingsCheck checkMotionSettingsAndPermission:FALSE];
         */
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

- (void)fixLocationSettings:(CDVInvokedUrlCommand*)command
{
    [[[SensorControlForegroundDelegate alloc] initWithDelegate:self.commandDelegate
                                                   forCommand:command] checkAndPromptLocationSettings];
}

- (void)isValidLocationSettings:(CDVInvokedUrlCommand*)command
{
    [[[SensorControlForegroundDelegate alloc] initWithDelegate:self.commandDelegate
                                                   forCommand:command] checkLocationSettings];

}

- (void)fixLocationPermissions:(CDVInvokedUrlCommand*)command
{
    [[[SensorControlForegroundDelegate alloc] initWithDelegate:self.commandDelegate
                                                   forCommand:command] checkAndPromptLocationPermissions];
}

- (void)isValidLocationPermissions:(CDVInvokedUrlCommand*)command
{
    [[[SensorControlForegroundDelegate alloc] initWithDelegate:self.commandDelegate
                                                   forCommand:command] checkLocationPermissions];
}

- (void)fixFitnessPermissions:(CDVInvokedUrlCommand*)command
{
    [[[SensorControlForegroundDelegate alloc] initWithDelegate:self.commandDelegate
                                                   forCommand:command] checkAndPromptFitnessPermissions];
}

- (void)isValidFitnessPermissions:(CDVInvokedUrlCommand*)command
{
    [[[SensorControlForegroundDelegate alloc] initWithDelegate:self.commandDelegate
                                                   forCommand:command] checkMotionActivityPermissions];

}

- (void)fixShowNotifications:(CDVInvokedUrlCommand*)command
{
    [[[SensorControlForegroundDelegate alloc] initWithDelegate:self.commandDelegate
                                                   forCommand:command] checkAndPromptNotificationPermission];
}


- (void)isValidShowNotifications:(CDVInvokedUrlCommand*)command
{
    [[[SensorControlForegroundDelegate alloc] initWithDelegate:self.commandDelegate
                                                   forCommand:command] checkNotificationsEnabled];
}

- (void)isNotificationsUnpaused:(CDVInvokedUrlCommand*)command
{
    [self NOP_RETURN_TRUE:command forMethod:@"isNotificationsUnpaused"];
}

- (void)fixUnusedAppRestrictions:(CDVInvokedUrlCommand*)command
{
    [self NOP_RETURN_TRUE:command forMethod:@"fixUnusedAppRestrictions"];
}

- (void)isUnusedAppUnrestricted: (CDVInvokedUrlCommand*) command
{
    [self NOP_RETURN_TRUE:command forMethod:@"isUnusedAppUnrestricted"];
}

- (void)fixIgnoreBatteryOptimizations: (CDVInvokedUrlCommand*)command
{
    [self NOP_RETURN_TRUE:command forMethod:@"fixUnusedAppRestrictions"];
}

- (void)isIgnoreBatteryOptimizations: (CDVInvokedUrlCommand*) command
{
    [self NOP_RETURN_TRUE:command forMethod:@"isUnusedAppUnrestricted"];
}

- (void)fixOEMBackgroundRestrictions: (CDVInvokedUrlCommand*) command
{
    [self NOP_RETURN_TRUE:command forMethod:@"fixOEMBackgroundRestrictions"];
}

- (void)NOP_RETURN_TRUE:(CDVInvokedUrlCommand*) command forMethod:(NSString*) methodName
{
    NSString* callbackId = [command callbackId];
    
    @try {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
            @"%@ called, is NOP on iOS", methodName] showUI:FALSE];
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

- (void)storeBatteryLevel:(CDVInvokedUrlCommand*)command
{
    NSString* callbackId = [command callbackId];
    @try {
        [DataUtils saveBatteryAndSimulateUser];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
    @catch (NSException *exception) {
        NSString* msg = [NSString stringWithFormat: @"While storing battery, error %@", exception];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_ERROR
                                   messageAsString:msg];
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}


- (void)launchInit:(CDVInvokedUrlCommand*)command
{
    [self NOP_RETURN_TRUE:command forMethod:@"launchInit"];
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
