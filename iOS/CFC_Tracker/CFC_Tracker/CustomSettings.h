//
//  CustomSettings.h
//  E-Mission
//
//  Created by Gautham Kesineni on 10/10/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface CustomSettings : NSObject
// +(void)initializeSharedInstanceWithPath:(NSString*)customPath;
+(CustomSettings*) sharedInstance;
-(void) fillCustomSettingsWithDictionary:(NSDictionary*)source;
-(NSString*) getResultsURL;

@end
