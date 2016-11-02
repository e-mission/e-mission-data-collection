//
//  StatsEvent.m
//  emission
//
//  Created by Kalyanaraman Shankari on 10/31/16.
//
//

#import "StatsEvent.h"

static NSDictionary *statsNamesDict;
static NSString* appVersion;

@implementation StatsEvent
+ (void)initialize
{
    if(self == [StatsEvent class]) {
        // Read the list of valid keys
        NSString *plistStatNamesPath = [[NSBundle mainBundle] pathForResource:@"app_stats" ofType:@"plist"];
        statsNamesDict = [[NSDictionary alloc] initWithContentsOfFile:plistStatNamesPath];
        
        // Read the app version
        NSString *emissionInfoPath = [[NSBundle mainBundle] pathForResource:@"Info" ofType:@"plist"];
        NSDictionary *infoDict = [[NSDictionary alloc] initWithContentsOfFile:emissionInfoPath];
        appVersion = [infoDict objectForKey:@"CFBundleShortVersionString"];
    }
}

/*
 * If we want to be really safe, we should really create methods for each of these. But I am
 * not enthused about that level of busy work. While this does not provide a compile time check,
 * it at least provides a run time check. Let's stick with that for now.
 */
+ (NSString*)getStatName:(NSString*)label {
    return [statsNamesDict objectForKey:label];
}

-(instancetype)initForEvent:(NSString *)statName
{
    return [self initForReading:statName withReading:-1];
}

-(instancetype)initForReading:(NSString *)statName withReading:(double)reading
{
    self = [super init];
    self.name = [StatsEvent getStatName:statName];
    self.ts = [[NSDate date] timeIntervalSince1970];
    self.reading = reading;
    self.client_app_version = appVersion;
    self.client_os_version = [[UIDevice currentDevice] systemVersion];
    return self;
}

@end
