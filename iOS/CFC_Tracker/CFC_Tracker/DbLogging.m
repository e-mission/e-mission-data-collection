//
//  OngoingTripsDatabase.m
//  E-Mission
//
//  Created by Kalyanaraman Shankari on 9/18/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import "DbLogging.h"

// Table name
#define TABLE_TRANSITIONS @"TRANSITIONS"

#define KEY_TS @"TIMESTAMP"
#define KEY_MESSAGE @"MESSAGE"

#define DB_FILE_NAME @"DBLogger.db"

@interface DbLogging()

@end

@implementation DbLogging

static DbLogging *_database;

+ (DbLogging*)database {
    if (_database == nil) {
        _database = [[DbLogging alloc] init];
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
            NSLog(@"Copying file from %@ to %@", readableDBPath, sqLiteDb);
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
