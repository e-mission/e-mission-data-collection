#import <Cordova/CDV.h>
#import "TripDiaryStateMachine.h"

@interface BEMDataCollection: CDVPlugin

- (void) pluginInitialize;
- (void) launchInit:(CDVInvokedUrlCommand*)command;
- (void) getConfig:(CDVInvokedUrlCommand*)command;
- (void) setConfig:(CDVInvokedUrlCommand*)command;
- (void) getState:(CDVInvokedUrlCommand*)command;
- (void) forceTransition:(CDVInvokedUrlCommand *)command;
- (void) handleSilentPush:(CDVInvokedUrlCommand *)command;
- (void)getAccuracyOptions:(CDVInvokedUrlCommand *)command;

@property (strong) TripDiaryStateMachine* tripDiaryStateMachine;

@end
