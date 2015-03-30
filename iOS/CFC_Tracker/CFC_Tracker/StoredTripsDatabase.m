//
//  StoredTripsDatabase.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/9/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "StoredTripsDatabase.h"
#import <CoreLocation/CoreLocation.h>

// Table name
#define TABLE_STORED_TRIP @"STORED_TRIP"

// Column names
#define KEY_TS @"START_TIME"
#define KEY_TRIP_JSON @"TRIP_JSON"

#define DB_FILE_NAME @"StoredTripsAuto.db"


@implementation StoredTripsDatabase

static StoredTripsDatabase *_database;

+ (StoredTripsDatabase*)database {
    if (_database == nil) {
        _database = [[StoredTripsDatabase alloc] init];
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

- (void) addTrip:(NSString*)tripJSON atTime:(NSDate*) startTs {
    NSString *insertStatement = [NSString stringWithFormat:@"INSERT INTO %@ (%@, %@) VALUES (?, ?)",
                                 TABLE_STORED_TRIP, KEY_TS, KEY_TRIP_JSON];
    
    sqlite3_stmt *compiledStatement;
    if(sqlite3_prepare_v2(_database, [insertStatement UTF8String], -1, &compiledStatement, NULL) == SQLITE_OK) {
        // The SQLITE_TRANSIENT is used to indicate that the raw data (userMode, tripId, sectionId
        // is not permanent data and the SQLite library should make a copy
        // sqlite3_bind_text(compiledStatement, 1, [[df stringFromDate:[NSDate date]] UTF8String], -1, SQLITE_TRANSIENT);
        sqlite3_bind_int64(compiledStatement, 1, startTs.timeIntervalSince1970);
        sqlite3_bind_text(compiledStatement, 2, [tripJSON UTF8String], -1, SQLITE_TRANSIENT);
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

- (void) updateTrip:(NSString*)tripJSON atTime:(NSDate*)startTs {
    // There is only one entry, so no WHERE clause is needed
    NSString *updateStatement = [NSString stringWithFormat:@"UPDATE %@ SET %@ = ? WHERE %@ = ?",
                                 TABLE_STORED_TRIP, KEY_TRIP_JSON, KEY_TS];
    sqlite3_stmt *compiledStatement;
    if(sqlite3_prepare_v2(_database, [updateStatement UTF8String], -1, &compiledStatement, NULL) == SQLITE_OK) {
        sqlite3_bind_text(compiledStatement, 1, [tripJSON UTF8String], -1, SQLITE_TRANSIENT);
        sqlite3_bind_int64(compiledStatement, 2, startTs.timeIntervalSince1970);

    }
    NSInteger execCode = sqlite3_step(compiledStatement);
    if (execCode != SQLITE_DONE) {
        NSLog(@"Got error code %ld while executing statement %@", (long)execCode, updateStatement);
    }
    sqlite3_finalize(compiledStatement);
}

/*
 * Returns JSON for the last trip, if present, or NULL if there is no trip in the database.
 */

- (NSString*) getLastTrip {
    NSString* selectQuery = [NSString stringWithFormat:@"SELECT %@ FROM %@ ORDER BY %@ DESC LIMIT 1",
                             KEY_TRIP_JSON, TABLE_STORED_TRIP, KEY_TS];
    
    sqlite3_stmt *compiledStatement;
    NSInteger selPrepCode = sqlite3_prepare_v2(_database, [selectQuery UTF8String], -1, &compiledStatement, NULL);
    if (selPrepCode == SQLITE_OK) {
        while (sqlite3_step(compiledStatement) == SQLITE_ROW) {
            // We know that there will be only one entry because of the LIMIT 1 in the select statement
            NSString* tripJSON = [[NSString alloc] initWithUTF8String:(char*)sqlite3_column_text(compiledStatement, 0)];
            return tripJSON;
        }
    }
    return NULL;
}

/*
 * Returns all stored trips. If there are no trips, returns a zero length array.
 */

- (NSArray*) getAllStoredTrips {
    NSMutableArray* retVal = [[NSMutableArray alloc] init];
    /*
    NSString* selectQuery = [NSString stringWithFormat:@"SELECT %@ FROM %@ ORDER BY %@ DESC",
                             KEY_TRIP_JSON, TABLE_STORED_TRIP, KEY_TS];
    */
     NSString* selectQuery = [NSString stringWithFormat:@"SELECT %@ FROM %@",
                              KEY_TRIP_JSON, TABLE_STORED_TRIP];

    
    sqlite3_stmt *compiledStatement;
    NSInteger selPrepCode = sqlite3_prepare_v2(_database, [selectQuery UTF8String], -1, &compiledStatement, NULL);
    if (selPrepCode == SQLITE_OK) {
        while (sqlite3_step(compiledStatement) == SQLITE_ROW) {
            char* rawChars = (char*)sqlite3_column_text(compiledStatement, 0);
            if (rawChars != NULL) {
                NSString* currTripJSON = [[NSString alloc] initWithUTF8String:rawChars];
                NSLog(@"adding object %@ of length %lu when curr list count = %lu",
                      currTripJSON, (unsigned long)currTripJSON.length, (unsigned long)retVal.count);
                [retVal addObject:currTripJSON];
            } else {
                NSLog(@"rawChars == NULL, skipping");
            }
        }
    }
    NSLog(@"Returning array %@", retVal);
    return retVal;
}

- (void) deleteTrips:(NSArray*) startTimesToDelete {
    // Delete all the entries that we just read
    NSString *deleteQuery = [NSString stringWithFormat:@"DELETE FROM %@ WHERE %@ = ?",
                             TABLE_STORED_TRIP, KEY_TS];
    
    sqlite3_stmt *deleteCompiledStatement;
    NSInteger delPrepCode = sqlite3_prepare_v2(_database, [deleteQuery UTF8String], -1, &deleteCompiledStatement, NULL);
    if (delPrepCode == SQLITE_OK) {
        for (int i = 0; i < [startTimesToDelete count]; i++) {
            // Remember that while binding parameters, the index starts from 1
            NSDate* currTs = startTimesToDelete[i];
            sqlite3_bind_int64(deleteCompiledStatement, 1, currTs.timeIntervalSince1970);
            sqlite3_step(deleteCompiledStatement);
            sqlite3_reset(deleteCompiledStatement);
        }
    } else {
        NSLog(@"Error code %ld while compiling query %@", (long)delPrepCode, deleteQuery);
    }
    sqlite3_finalize(deleteCompiledStatement);
}

- (void) clear {
    NSString *deleteQuery = [NSString stringWithFormat:@"DELETE FROM %@", TABLE_STORED_TRIP];
    sqlite3_stmt *compiledStatement;
    NSInteger delPrepCode = sqlite3_prepare_v2(_database, [deleteQuery UTF8String], -1, &compiledStatement, NULL);
    if (delPrepCode == SQLITE_OK) {
        sqlite3_step(compiledStatement);
    }
    sqlite3_finalize(compiledStatement);
}


@end
