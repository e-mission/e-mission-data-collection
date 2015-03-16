//
//  CustomSettings.m
//  E-Mission
//
//  Created by Gautham Kesineni on 10/10/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import "CustomSettings.h"
#import "ConnectionSettings.h"

@interface CustomSettings() {
    NSString *settingsPath;
    NSMutableDictionary *customSettingsDict;
}

@end

@implementation CustomSettings
static CustomSettings *sharedInstance;

-(id)init {
    settingsPath = [self loadCustomSettings];
    customSettingsDict = [[NSMutableDictionary alloc] initWithContentsOfFile:settingsPath];
    return [super init];
}

// returns the path to the location of the custom settings
- (NSString*) loadCustomSettings
{
    NSError *error;
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *documentsDirectory = [paths objectAtIndex:0];
    NSString *path = [documentsDirectory stringByAppendingPathComponent:@"custom_settings.plist"];
    
    NSFileManager *fileManager = [NSFileManager defaultManager];
    
    if(![fileManager fileExistsAtPath:path]) {
        NSString *bundle = [[NSBundle mainBundle] pathForResource:@"custom_settings" ofType:@"plist"];
        [fileManager copyItemAtPath:bundle toPath:path error:&error];
    }
    
    if (error) {
        NSLog(@"There was an error copying the plist!");
    }
    
    return path;
}

- (void) fillCustomSettingsWithDictionary:(NSDictionary*)source
{
    for(NSString *str in [source allKeys]) {
        [customSettingsDict setObject:[source objectForKey:str] forKey:str];
    }
    
    [customSettingsDict writeToFile:settingsPath atomically:YES];
    
}

+ (CustomSettings*)sharedInstance
{
    if (sharedInstance == nil) {
        sharedInstance = [CustomSettings new];
    }
    return sharedInstance;
}

- (NSString *)getResultsURL
{
//    return [NSURL URLWithString:@"/compare" relativeToURL:[[ConnectionSettings sharedInstance] getConnectUrl]];
    return [customSettingsDict objectForKey:@"result_url"];
}

@end
