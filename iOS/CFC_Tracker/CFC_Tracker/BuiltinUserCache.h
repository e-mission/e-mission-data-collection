//
//  ClientStatsDatabase.h
//  E-Mission
//
//  Created by Kalyanaraman Shankari on 9/18/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <sqlite3.h>
#import "TimeQuery.h"

#define CLIENT_STATS_DB_NIL_VALUE @"none"

@interface BuiltinUserCache : NSObject {
    sqlite3 *_database;
}

+ (BuiltinUserCache*) database;
+ (double) getCurrentTimeSecs;
+ (NSString*) getCurrentTimeSecsString;

-(NSDictionary*)createSensorData:key write_ts:(NSDate*)write_ts timezone:(NSString*)tz data:(NSObject*)data;

// We implement the same interface as the android code, to use somewhat tested code
- (void) putSensorData:(NSString*) label value:(NSObject*)value;
- (void) putMessage:(NSString*) label value:(NSObject*)value;

- (NSArray*) getSensorDataForInterval:(NSString*) key tq:(TimeQuery*)tq wrapperClass:(Class)cls;
- (NSArray*) getLastSensorData:(NSString*) key nEntries:(int)nEntries wrapperClass:(Class)cls;

- (NSArray*) getMessageForInterval:(NSString*) key tq:(TimeQuery*)tq wrapperClass:(Class)cls;
- (NSArray*) getLastMessage:(NSString*) key nEntries:(int)nEntries wrapperClass:(Class)cls;

- (double) getTsOfLastTransition;
- (NSArray*) syncPhoneToServer;

+ (TimeQuery*) getTimeQuery:(NSArray*)pointList;
+ (NSString*) getTimezone:(NSDictionary*)entry;
+ (NSDate*) getWriteTs:(NSDictionary*)entry;

- (void) clearEntries:(TimeQuery*)tq;
- (void) clear;
@end
