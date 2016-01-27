#import <Cordova/CDV.h>

@interface BEMDataCollection: CDVPlugin

- (void) startupInit:(CDVInvokedUrlCommand*)command;
- (void) launchInit:(CDVInvokedUrlCommand*)command;
- (void) getConfig:(CDVInvokedUrlCommand*)command;

@end
