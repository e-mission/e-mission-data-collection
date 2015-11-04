//
//  OngoingTripsDatabase.h
//  E-Mission
//
//  Created by Kalyanaraman Shankari on 9/18/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <sqlite3.h>

@interface DbLogging : NSObject {
    sqlite3 *_database;
}

+ (DbLogging*) database;

-(void)addTransition:(NSString*) transition;
-(NSArray*)getTransitions;
-(void)clearTransitions;

@end
