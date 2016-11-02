//
//  StatsEvent.h
//  emission
//
//  Created by Kalyanaraman Shankari on 10/31/16.
//
//

#import <Foundation/Foundation.h>

@interface StatsEvent : NSObject

- (instancetype)initForEvent:(NSString*)statName;
- (instancetype)initForReading:(NSString*)statName withReading:(double)reading;

@property NSString* name;
@property double ts;
@property double reading;
@property NSString* client_app_version;
@property NSString* client_os_version;

@end
