//
//  OngoingTripsDatabase.m
//  E-Mission
//
//  Created by Kalyanaraman Shankari on 9/18/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import "OngoingTripsDatabase.h"
#import <CoreLocation/CoreLocation.h>

// Table name
#define TABLE_ONGOING_TRIP @"TRACK_POINTS"
#define TABLE_TRANSITIONS @"TRANSITIONS"

// Column names
#define KEY_TS @"TIMESTAMP"
#define KEY_LAT @"LAT"
#define KEY_LNG @"LNG"
#define KEY_ALTITUDE @"ALTITUDE"
#define KEY_HORIZ_ACCURACY @"HORIZ_ACCURACY"
#define KEY_VERT_ACCURACY @"VERT_ACCURACY"
#define KEY_MESSAGE @"MESSAGE"
// #define KEY_MODE @"mode"

#define DB_FILE_NAME @"OngoingTripsAuto.db"

@interface OngoingTripsDatabase() <CLLocationManagerDelegate>

@end

@implementation OngoingTripsDatabase

static OngoingTripsDatabase *_database;

+ (OngoingTripsDatabase*)database {
    if (_database == nil) {
        _database = [[OngoingTripsDatabase alloc] init];
    }
    return _database;
}

// TODO: Refactor this into a new database helper class?
- (id)init {
    if ((self = [super init])) {
        NSString *sqLiteDb = [self dbPath:DB_FILE_NAME];
        NSFileManager *fileManager = [NSFileManager defaultManager];
        
        if (![fileManager fileExistsAtPath: sqLiteDb]) {
            // Copy existing database over to create a blank DB.
            // Apparently, we cannot create a new file there to work as the database?
            // http://stackoverflow.com/questions/10540728/creating-an-sqlite3-database-file-through-objective-c
            NSError *error = nil;
            NSString *readableDBPath = [[NSBundle mainBundle] pathForResource:DB_FILE_NAME
                                                                       ofType:nil];
            NSLog(@"Copying file from %@ to %@", sqLiteDb, readableDBPath);
            BOOL success = [[NSFileManager defaultManager] copyItemAtPath:readableDBPath
                                                                   toPath:sqLiteDb
                                                                    error:&error];
            if (!success)
            {
                NSCAssert1(0, @"Failed to create writable database file with message '%@'.", [  error localizedDescription]);
                return nil;
            }
        }
        // if we didn't have a file earlier, we just created it.
        // so we are guaranteed to always have a file when we get here
        assert([fileManager fileExistsAtPath: sqLiteDb]);
        int returnCode = sqlite3_open([sqLiteDb UTF8String], &_database);
        if (returnCode != SQLITE_OK) {
            NSLog(@"Failed to open database because of error code %d", returnCode);
            return nil;
        }
    }
    return self;
}

- (NSString*)dbPath:(NSString*)dbName {
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory,
                                                         NSUserDomainMask, YES);
    NSString *documentsDirectory = [paths objectAtIndex:0];
    NSString *documentsPath = [documentsDirectory
                               stringByAppendingPathComponent:dbName];
    
    return documentsPath;
}

- (void)dealloc {
    sqlite3_close(_database);
}

+(double) getCurrentTimeMillis {
    return [[NSDate date] timeIntervalSince1970]*1000;
}

+(NSString*) getCurrentTimeMillisString {
    return [@([self getCurrentTimeMillis]) stringValue];
}

+(NSDictionary*)toGeoJSON:(CLLocation*) currLoc {
    NSMutableDictionary *retVal = [[NSMutableDictionary alloc] init];
    // Note that GeoJSON format is (lng, lat), not (lat, lng)
    NSArray *coords = @[@(currLoc.coordinate.longitude),
                               @(currLoc.coordinate.latitude)];
    [retVal setObject:@"Point" forKey:@"type"];
    [retVal setObject:coords forKey:@"coordinates"];
    return retVal;
}


-(void)addPoint:(CLLocation *)location {
    NSString *insertStatement = [NSString stringWithFormat:@"INSERT INTO %@ (%@, %@, %@, %@, %@, %@) VALUES (?, ?, ?, ?, ?, ?)",
                                 TABLE_ONGOING_TRIP, KEY_TS, KEY_LAT, KEY_LNG, KEY_ALTITUDE, KEY_HORIZ_ACCURACY, KEY_VERT_ACCURACY];
    sqlite3_stmt *compiledStatement;
    if(sqlite3_prepare_v2(_database, [insertStatement UTF8String], -1, &compiledStatement, NULL) == SQLITE_OK) {
        // The SQLITE_TRANSIENT is used to indicate that the raw data (userMode, tripId, sectionId
        // is not permanent data and the SQLite library should make a copy
        // NSDateFormatter *df = [[NSDateFormatter alloc] init];
        // sqlite3_bind_text(compiledStatement, 1, [[df stringFromDate:[NSDate date]] UTF8String], -1, SQLITE_TRANSIENT);
        sqlite3_bind_int64(compiledStatement, 1, location.timestamp.timeIntervalSince1970);
        sqlite3_bind_double(compiledStatement, 2, location.coordinate.latitude);
        sqlite3_bind_double(compiledStatement, 3, location.coordinate.longitude);
        sqlite3_bind_double(compiledStatement, 4, location.altitude);
        sqlite3_bind_double(compiledStatement, 5, location.horizontalAccuracy);
        sqlite3_bind_double(compiledStatement, 6, location.verticalAccuracy);
    }
    // Shouldn't this be within the prior if?
    // Shouldn't we execute the compiled statement only if it was generated correctly?
    // This is code copied from
    // http://stackoverflow.com/questions/2184861/how-to-insert-data-into-a-sqlite-database-in-iphone
    // Need to check from the raw sources and see where we get
    // Create a new sqlite3 database like so:
    // http://www.raywenderlich.com/902/sqlite-tutorial-for-ios-creating-and-scripting
    NSInteger execCode = sqlite3_step(compiledStatement);
    if (execCode != SQLITE_DONE) {
        NSLog(@"Got error code %ld while executing statement %@", (long)execCode, insertStatement);
    }
    sqlite3_finalize(compiledStatement);
}



/*

-(void)getAndStoreLocation {
    CLLocationManager* locationManager = [[CLLocationManager alloc] init];
    locationManager.delegate = self;
    locationManager.desiredAccuracy = kCLLocationAccuracyKilometer; //meters
    locationManager.distanceFilter = 100; //meters
    if ([locationManager respondsToSelector:@selector(requestWhenInUseAuthorization)]) {
        [locationManager requestWhenInUseAuthorization];
    }
    [locationManager startUpdatingLocation];
}

- (void)locationManager:(CLLocationManager *)manager didUpdateToLocation:(CLLocation *)newLocation fromLocation:(CLLocation *)oldLocation {
    // There is only one entry, so no WHERE clause is needed
    NSString *updateStatement = [NSString stringWithFormat:@"UPDATE %@ SET %@ = ?, %@ = ?",
                                 TABLE_ONGOING_TRIP, KEY_LAT, KEY_LNG];
    sqlite3_stmt *compiledStatement;
    if(sqlite3_prepare_v2(_database, [updateStatement UTF8String], -1, &compiledStatement, NULL) == SQLITE_OK) {
        sqlite3_bind_double(compiledStatement, 1, newLocation.coordinate.latitude);
        sqlite3_bind_double(compiledStatement, 2, newLocation.coordinate.longitude);
    }
    NSInteger execCode = sqlite3_step(compiledStatement);
    if (execCode != SQLITE_DONE) {
        NSLog(@"Got error code %ld while executing statement %@", execCode, updateStatement);
    }
    sqlite3_finalize(compiledStatement);
}



- (NSDictionary*)getOngoingTrip {
    NSMutableDictionary* retVal = [[NSMutableDictionary alloc] init];

    // There is only one entry, so no WHERE clause is needed
    NSString *selectQuery = [NSString stringWithFormat:@"SELECT * FROM %@", TABLE_ONGOING_TRIP];

    sqlite3_stmt *compiledStatement;
    NSInteger selPrepCode = sqlite3_prepare_v2(_database, [selectQuery UTF8String], -1, &compiledStatement, NULL);
    if (selPrepCode == SQLITE_OK) {
        while (sqlite3_step(compiledStatement) == SQLITE_ROW) {
            // Remember that while reading results, the index starts from 0
            NSString* startDate = [[NSString alloc] initWithUTF8String:(char*)sqlite3_column_text(compiledStatement, 0)];
            [retVal setObject:startDate forKey:KEY_START_TIME];
            NSArray *coords = @[@(sqlite3_column_double(compiledStatement, 1)),
                                @(sqlite3_column_double(compiledStatement, 2))];
            [retVal setObject:coords forKey:@"section_start_point"];
            NSString* mode = [[NSString alloc] initWithUTF8String:(char*)sqlite3_column_text(compiledStatement, 3)];
            [retVal setObject:mode forKey:KEY_MODE];
        }
    } else {
        NSLog(@"Error code %ld while compiling query %@", selPrepCode, selectQuery);
    }
    sqlite3_finalize(compiledStatement);
    assert(retVal.count == 0 || retVal.count == 3);
    if (retVal.count == 0) {
        return NULL;
    } else {
        return retVal;
    }
}
*/

-(NSArray*) getLastPoints:(int) nPoints {
    NSString* selectQuery = [NSString stringWithFormat:@"SELECT * FROM %@ ORDER BY %@ DESC LIMIT %d",
                             TABLE_ONGOING_TRIP, KEY_TS, nPoints];
    return [self getPointsForQuery:selectQuery];
}

-(NSArray*) getEndPoints {
    NSString* startQuery = [NSString stringWithFormat:@"SELECT * FROM %@ ORDER BY %@ LIMIT 1",
                             TABLE_ONGOING_TRIP, KEY_TS];
    NSString* endQuery = [NSString stringWithFormat:@"SELECT * FROM %@ ORDER BY %@ DESC LIMIT 1",
                             TABLE_ONGOING_TRIP, KEY_TS];
    CLLocation* firstPoint = [self getPointsForQuery:startQuery][0];
    CLLocation* endPoint = [self getPointsForQuery:endQuery][1];
    return @[firstPoint, endPoint];
}

-(NSArray*) getPointsFrom:(NSDate*)startTime to:(NSDate*)endTime {
    NSString* matchQuery = [NSString stringWithFormat:@"SELECT * FROM %@ WHERE %@ >= %f AND %@ < %f",
                            TABLE_ONGOING_TRIP, KEY_TS, (double)startTime.timeIntervalSince1970,
                                                KEY_TS, (double)endTime.timeIntervalSince1970];
    return [self getPointsForQuery:matchQuery];
}

-(NSArray*)getPointsForQuery:(NSString*) selectQuery {
    NSMutableArray* retVal = [[NSMutableArray alloc] init];
    
    sqlite3_stmt *compiledStatement;
    NSInteger selPrepCode = sqlite3_prepare_v2(_database, [selectQuery UTF8String], -1, &compiledStatement, NULL);
    if (selPrepCode == SQLITE_OK) {
        while (sqlite3_step(compiledStatement) == SQLITE_ROW) {
            CLLocation* currPoint = [self getLocationFromCursor:compiledStatement];
            [retVal addObject:currPoint];
        }
    }
    return retVal;
}

-(CLLocation*)getLocationFromCursor:(sqlite3_stmt*) compiledStmt {
    return [[CLLocation alloc]
            initWithCoordinate:CLLocationCoordinate2DMake(sqlite3_column_double(compiledStmt, 1),
                                                          sqlite3_column_double(compiledStmt, 2))
            altitude:sqlite3_column_double(compiledStmt, 3)
            horizontalAccuracy:sqlite3_column_double(compiledStmt, 4)
            verticalAccuracy:sqlite3_column_double(compiledStmt, 5)
            timestamp:[NSDate dateWithTimeIntervalSince1970:sqlite3_column_int64(compiledStmt, 0)]];
}

/* TODO: Consider refactoring this along with the code in TripSectionDB to have generic read code.
 * Unfortunately, the code in TripSectionDB sometimes reads blobs, which require a different read method,
 * so this refactoring is likely to be non-trivial
 */

- (void)clear {
    NSString *deleteQuery = [NSString stringWithFormat:@"DELETE FROM %@", TABLE_ONGOING_TRIP];
    sqlite3_stmt *compiledStatement;
    NSInteger delPrepCode = sqlite3_prepare_v2(_database, [deleteQuery UTF8String], -1, &compiledStatement, NULL);
    if (delPrepCode == SQLITE_OK) {
        sqlite3_step(compiledStatement);
    }
    sqlite3_finalize(compiledStatement);
}

/*
 * BEGIN: Transition logging
 */

-(void)addTransition:(NSString *)transition {
    NSString *insertStatement = [NSString stringWithFormat:@"INSERT INTO %@ (%@, %@) VALUES (?, ?)",
                                 TABLE_TRANSITIONS, KEY_TS, KEY_MESSAGE];
    
    sqlite3_stmt *compiledStatement;
    if(sqlite3_prepare_v2(_database, [insertStatement UTF8String], -1, &compiledStatement, NULL) == SQLITE_OK) {
        // The SQLITE_TRANSIENT is used to indicate that the raw data (userMode, tripId, sectionId
        // is not permanent data and the SQLite library should make a copy
        sqlite3_bind_int64(compiledStatement, 1, [NSDate date].timeIntervalSince1970);
        sqlite3_bind_text(compiledStatement, 2, [transition UTF8String], -1, SQLITE_TRANSIENT);
    }
    // Shouldn't this be within the prior if?
    // Shouldn't we execute the compiled statement only if it was generated correctly?
    // This is code copied from
    // http://stackoverflow.com/questions/2184861/how-to-insert-data-into-a-sqlite-database-in-iphone
    // Need to check from the raw sources and see where we get
    // Create a new sqlite3 database like so:
    // http://www.raywenderlich.com/902/sqlite-tutorial-for-ios-creating-and-scripting
    NSInteger execCode = sqlite3_step(compiledStatement);
    if (execCode != SQLITE_DONE) {
        NSLog(@"Got error code %ld while executing statement %@", (long)execCode, insertStatement);
    }
    sqlite3_finalize(compiledStatement);
}

-(NSArray*)getTransitions {
    NSMutableArray* retVal = [[NSMutableArray alloc] init];
    
    // We're going to get all of them, so no WHERE clause is needed
    NSString *selectQuery = [NSString stringWithFormat:@"SELECT * FROM %@", TABLE_TRANSITIONS];
    
    sqlite3_stmt *compiledStatement;
    NSInteger selPrepCode = sqlite3_prepare_v2(_database, [selectQuery UTF8String], -1, &compiledStatement, NULL);
    if (selPrepCode == SQLITE_OK) {
        while (sqlite3_step(compiledStatement) == SQLITE_ROW) {
            // Remember that while reading results, the index starts from 0
            long long ts = sqlite3_column_int64(compiledStatement, 0);
            NSString* dateStr = [NSDateFormatter localizedStringFromDate:[NSDate dateWithTimeIntervalSince1970:ts]
                                                               dateStyle:NSDateFormatterShortStyle
                                                               timeStyle:NSDateFormatterShortStyle];
            NSString* message = [[NSString alloc] initWithUTF8String:(char*)sqlite3_column_text(compiledStatement, 1)];
            // NSString* msgWithTime = [NSString stringWithFormat:@"%@:%@", [NSDate dateWithTimeIntervalSince1970:ts], message];
            [retVal addObject:@[dateStr, message]];
        }
    } else {
        NSLog(@"Error code %ld while compiling query %@", (long)selPrepCode, selectQuery);
    }
    sqlite3_finalize(compiledStatement);
    return retVal;
}

-(void)clearTransitions {
    NSString *deleteQuery = [NSString stringWithFormat:@"DELETE FROM %@", TABLE_TRANSITIONS];
    sqlite3_stmt *compiledStatement;
    NSInteger delPrepCode = sqlite3_prepare_v2(_database, [deleteQuery UTF8String], -1, &compiledStatement, NULL);
    if (delPrepCode == SQLITE_OK) {
        sqlite3_step(compiledStatement);
    }
    sqlite3_finalize(compiledStatement);
}

/*
 * END: Transition logging
 */

@end
