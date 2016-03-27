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

- (void)forceTripStart:(CDVInvokedUrlCommand *)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionExitedGeofence];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK
                                   messageAsString:CFCTransitionExitedGeofence];
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

- (void)forceTripEnd:(CDVInvokedUrlCommand *)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                                                            object:CFCTransitionTripEndDetected];
        CDVPluginResult* result = [CDVPluginResult
                                   resultWithStatus:CDVCommandStatus_OK
                                   messageAsString:CFCTransitionTripEndDetected];
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


- (void)forceRemotePush:(CDVInvokedUrlCommand *)command
{
    NSString* callbackId = [command callbackId];
    
    @try {
        UIApplication* appl = [UIApplication sharedApplication];
        NSDictionary* dummyInfo = @{};
        [appl.delegate application:appl didReceiveRemoteNotification:dummyInfo
            fetchCompletionHandler:^(UIBackgroundFetchResult fetchResult) {
            CDVPluginResult* result = [CDVPluginResult
                                       resultWithStatus:CDVCommandStatus_OK
                                       messageAsNSUInteger:fetchResult];
            [self.commandDelegate sendPluginResult:result callbackId:callbackId];
        }];
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

- (void) onAppTerminate {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"onAppTerminate called"] showUI:FALSE];    
}

@end
