//
//  OngoingTripsDatabase.h
//  E-Mission
//
//  Created by Kalyanaraman Shankari on 9/18/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <sqlite3.h>
#import <CoreLocation/CoreLocation.h>
#import "EMActivity.h"

@interface OngoingTripsDatabase : NSObject {
    sqlite3 *_database;
}

+ (OngoingTripsDatabase*) database;
+ (double) getCurrentTimeMillis;
+ (NSString*) getCurrentTimeMillisString;
+ (NSDictionary*) toGeoJSON:(CLLocation*) currLoc;

// We implement the same interface as the android code, to use somewhat tested code
-(void) addPoint:(CLLocation*) location;
-(NSArray*) getLastPoints:(int) nPoints;
-(NSArray*) getEndPoints;
-(NSArray*) getPointsFrom:(NSDate*)startTs to:(NSDate*)endTs;
-(void)clear;

-(void)addTransition:(NSString*) transition;
-(NSArray*)getTransitions;
-(void)clearTransitions;

// We can use the existing query method to read the activities that iOS has already stored.
// However, it is not possible for

-(void) addModeChange:(EMActivity*) activity;
-(NSArray*) getModeChanges:(NSDate*) date toDate:(NSDate*) toDate;

@end
