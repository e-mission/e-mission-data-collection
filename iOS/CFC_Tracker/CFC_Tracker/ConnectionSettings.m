//
//  ConnectionSettings.m
//  E-Mission
//
//  Created by Kalyanaraman Shankari on 8/23/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import "ConnectionSettings.h"

@interface ConnectionSettings() {
    NSDictionary *connSettingDict;
}
@end

@implementation ConnectionSettings
static ConnectionSettings *sharedInstance;

-(id)init{
    NSString *plistConnPath = [[NSBundle mainBundle] pathForResource:@"connect" ofType:@"plist"];
    connSettingDict = [[NSDictionary alloc] initWithContentsOfFile:plistConnPath];
    return [super init];
}

+ (ConnectionSettings*)sharedInstance
{
    if (sharedInstance == nil) {
        sharedInstance = [ConnectionSettings new];
    }
    return sharedInstance;
}

- (NSURL*)getConnectUrl
{
    return [NSURL URLWithString:[connSettingDict objectForKey: @"connect_url"]];
}

- (BOOL)isSkipAuth
{
    if([[self getConnectUrl].scheme isEqualToString:@"http"]) {
        return true;
    } else {
        return false;
    }
}

- (NSString*)getGoogleWebAppClientID
{
    return [connSettingDict objectForKey: @"google_web_app_client_id"];
}

- (NSString*)getGoogleiOSClientID
{
    return [connSettingDict objectForKey: @"google_ios_client_id"];
}

- (NSString*)getGoogleiOSClientSecret
{
    return [connSettingDict objectForKey: @"google_ios_client_secret"];
}

- (NSString*)getParseAppID
{
    return [connSettingDict objectForKey: @"parse_app_id"];
}

- (NSURL*)getParseClientID
{
    return [connSettingDict objectForKey: @"parse_client_id"];
}

@end
