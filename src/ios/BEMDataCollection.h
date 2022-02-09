#import <Cordova/CDV.h>
#import "TripDiaryStateMachine.h"

@interface BEMDataCollection: CDVPlugin

- (void) pluginInitialize;
- (void) markConsented:(CDVInvokedUrlCommand*)command;
- (void) fixLocationSettings:(CDVInvokedUrlCommand*)command;
- (void) isValidLocationSettings:(CDVInvokedUrlCommand*)command;
- (void) fixLocationPermissions:(CDVInvokedUrlCommand*)command;
- (void) isValidLocationPermissions:(CDVInvokedUrlCommand*)command;
- (void) fixFitnessPermissions:(CDVInvokedUrlCommand*)command;
- (void) isValidFitnessPermissions:(CDVInvokedUrlCommand*)command;
- (void) fixShowNotifications:(CDVInvokedUrlCommand*)command;
- (void) isValidShowNotifications:(CDVInvokedUrlCommand*)command;
- (void) isNotificationsUnpaused:(CDVInvokedUrlCommand*)command;
- (void) fixOEMBackgroundRestrictions: (CDVInvokedUrlCommand*) command;
- (void) launchInit:(CDVInvokedUrlCommand*)command;
- (void) getConfig:(CDVInvokedUrlCommand*)command;
- (void) setConfig:(CDVInvokedUrlCommand*)command;
- (void) getState:(CDVInvokedUrlCommand*)command;
- (void) forceTransition:(CDVInvokedUrlCommand *)command;
- (void) handleSilentPush:(CDVInvokedUrlCommand *)command;
- (void)getAccuracyOptions:(CDVInvokedUrlCommand *)command;

@property (strong) TripDiaryStateMachine* tripDiaryStateMachine;

@end
