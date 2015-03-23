//
//  StoredTripsDatabase.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/9/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <sqlite3.h>

@interface StoredTripsDatabase : NSObject {
    sqlite3 *_database;
}

+ (StoredTripsDatabase*)database;
- (void) addTrip:(NSString*)tripJSON atTime:(NSDate*) startTs;
- (void) updateTrip:(NSString*)tripJSON atTime:(NSDate*)startTs;
- (NSString*) getLastTrip;
- (NSArray*) getAllStoredTrips;
- (void) deleteTrips:(NSArray*) startTimesToDelete;
- (void) clear;
@end
