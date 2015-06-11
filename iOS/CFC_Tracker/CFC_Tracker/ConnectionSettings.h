//
//  ConnectionSettings.h
//  E-Mission
//
//  Created by Kalyanaraman Shankari on 8/23/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface ConnectionSettings : NSObject
+(ConnectionSettings*) sharedInstance;
-(NSURL*) getConnectUrl;
-(BOOL)isSkipAuth;
-(NSString*) getGoogleWebAppClientID;
-(NSString*) getGoogleiOSClientID;
-(NSString*) getGoogleiOSClientSecret;
-(NSString*) getParseAppID;
-(NSString*) getParseClientID;
@end
