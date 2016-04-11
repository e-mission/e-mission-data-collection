#import "BEMDataCollection.h"
#import "LocalNotificationManager.h"
#import "Wrapper/LocationTrackingConfig.h"
#import "BEMAppDelegate.h"
#import "ConfigManager.h"
#import "DataUtils.h"
#import <CoreLocation/CoreLocation.h>

@implementation BEMDataCollection

- (void)pluginInitialize
{
    // TODO: We should consider adding a create statement to the init, similar
    // to android - then it doesn't matter if the pre-populated database is not
    // copied over.
    NSLog(@"BEMDataCollection:pluginInitialize singleton -> initialize statemachine and delegate");
    // NOTE: This CANNOT be part of a background thread because when the geofence creation, and more importantly,
    // the visit notification, are run as part of a background thread, they don't work.
    // I tried to move this into a background thread as part of a18f8f9385bdd9e37f7b412b386a911aee9a6ea0 and had
    // to revert it because even visit notification, which had been the bedrock of my existence so far, stopped
    // working although I made an explicit stop at the education library on the way to Soda.
    self.tripDiaryStateMachine = [TripDiaryStateMachine instance];
    NSDictionary* emptyOptions = @{};
    [AppDelegate didFinishLaunchingWithOptions:emptyOptions];
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
