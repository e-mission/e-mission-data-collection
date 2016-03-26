#import <Cordova/CDV.h>
#import "TripDiaryStateMachine.h"

@interface BEMDataCollection: CDVPlugin

- (void) pluginInitialize;
- (void) launchInit:(CDVInvokedUrlCommand*)command;
- (void) getConfig:(CDVInvokedUrlCommand*)command;
- (void) updateConfig:(CDVInvokedUrlCommand*)command;
- (void) getState:(CDVInvokedUrlCommand*)command;
- (void) forceTripStart:(CDVInvokedUrlCommand *)command;
- (void) forceTripEnd:(CDVInvokedUrlCommand *)command;
- (void) forceRemotePush:(CDVInvokedUrlCommand *)command;

@property (strong) TripDiaryStateMachine* tripDiaryStateMachine;

@end
