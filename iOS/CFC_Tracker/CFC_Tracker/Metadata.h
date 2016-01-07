//
//  Metadata.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 10/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface Metadata : NSObject
@property double write_ts;
@property double read_ts;
@property NSString* time_zone;
@property NSString* type;
@property NSString* key;
@property NSString* plugin;
@property NSString* platform;
@end
