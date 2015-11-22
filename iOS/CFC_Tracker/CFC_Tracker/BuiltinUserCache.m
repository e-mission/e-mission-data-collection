//
//  ClientStatsDatabase.m
//  E-Mission
//
//  Created by Kalyanaraman Shankari on 9/18/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import "BuiltinUserCache.h"
#import "DataUtils.h"
#import "SimpleLocation.h"
#import "Metadata.h"
#import "LocationTrackingConfig.h"

// Table name
#define TABLE_USER_CACHE @"userCache"

// Column names
#define KEY_WRITE_TS @"write_ts"
#define KEY_READ_TS @"read_ts"
#define KEY_TIMEZONE @"timezone"
#define KEY_TYPE @"type"
#define KEY_KEY @"key"
#define KEY_PLUGIN @"plugin"
#define KEY_DATA @"data"

#define METADATA_TAG @"metadata"
#define DATA_TAG @"data"

#define SENSOR_DATA_TYPE @"sensor-data"
#define MESSAGE_TYPE @"message"

#define DB_FILE_NAME @"userCacheDB"

@interface BuiltinUserCache() {
    NSDictionary *statsNamesDict;
    NSString* appVersion;
}
@end

@implementation BuiltinUserCache

static BuiltinUserCache *_database;

+ (BuiltinUserCache*)database {
    if (_database == nil) {
        _database = [[BuiltinUserCache alloc] init];
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
            NSString *ongoingTripsDBPath = [[NSBundle mainBundle] pathForResource:@"OngoingTripsUserCache.db"
                                                                       ofType:nil];

            NSLog(@"ongoingTripsDBPath = %@", ongoingTripsDBPath);
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
        // Read the list of valid keys
        NSString *plistStatNamesPath = [[NSBundle mainBundle] pathForResource:@"usercache_keys" ofType:@"plist"];
        statsNamesDict = [[NSDictionary alloc] initWithContentsOfFile:plistStatNamesPath];
        
        NSString *infoPath = [[NSBundle mainBundle] pathForResource:@"Info" ofType:@"plist"];
        NSDictionary *infoDict = [[NSDictionary alloc] initWithContentsOfFile:infoPath];
        appVersion = [infoDict objectForKey:@"CFBundleShortVersionString"];
    }
    return self;
}

/*
 * If we want to be really safe, we should really create methods for each of these. But I am not enthused about that level of typing.
 * While this does not provide a compile time check, it at least provides a run time check. Let's stick with that for now.
 */
- (NSString*)getStatName:(NSString*)label {
    return [statsNamesDict objectForKey:label];
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

+(double) getCurrentTimeSecs {
    return [[NSDate date] timeIntervalSince1970];
}

+(NSString*) getCurrentTimeSecsString {
    return [@([self getCurrentTimeSecs]) stringValue];
}

-(NSDictionary*)createSensorData:key write_ts:(NSDate*)write_ts timezone:(NSString*)ts data:(NSObject*)data {
    Metadata* md = [Metadata new];
    md.write_ts = [DataUtils dateToTs:write_ts];
    md.time_zone = ts;
    md.type = SENSOR_DATA_TYPE;
    md.key = [self getStatName:key];
    NSDictionary* mdDict = [DataUtils wrapperToDict:md];
    NSDictionary* dataDict = [DataUtils wrapperToDict:data];
    
    NSMutableDictionary* entry = [NSMutableDictionary new];
    [entry setObject:mdDict forKey:METADATA_TAG];
    [entry setObject:dataDict forKey:DATA_TAG];
    return entry;
}

-(void)putSensorData:(NSString *)label value:(NSObject *)value {
    [self putValue:label value:value type:SENSOR_DATA_TYPE];
}

-(void)putMessage:(NSString *)label value:(NSObject *)value {
    [self putValue:label value:value type:MESSAGE_TYPE];
}

-(void)putValue:(NSString*)key value:(NSObject*)value type:(NSString*)type {
    NSString* statName = [self getStatName:key];
    double currTimeSecs = [BuiltinUserCache getCurrentTimeSecs];
    if (statName == NULL) {
        [NSException raise:@"unknown stat" format:@"stat %@ not defined in app_stats plist", key];
    }
    
    NSString *insertStatement = [NSString stringWithFormat:@"INSERT INTO %@ (%@, %@, %@, %@, %@) VALUES (?, ?, ?, ?, ?)",
                                 TABLE_USER_CACHE, KEY_WRITE_TS, KEY_TIMEZONE, KEY_TYPE, KEY_KEY, KEY_DATA];
    sqlite3_stmt *compiledStatement;
    if(sqlite3_prepare_v2(_database, [insertStatement UTF8String], -1, &compiledStatement, NULL) == SQLITE_OK) {
        // The SQLITE_TRANSIENT is used to indicate that the raw data (userMode, tripId, sectionId
        // is not permanent data and the SQLite library should make a copy
        sqlite3_bind_double(compiledStatement, 1, currTimeSecs); // timestamp
        sqlite3_bind_text(compiledStatement, 2, [[NSTimeZone localTimeZone].name UTF8String], -1, SQLITE_TRANSIENT); // timezone
        sqlite3_bind_text(compiledStatement, 3, [type UTF8String], -1, SQLITE_TRANSIENT); // type
        sqlite3_bind_text(compiledStatement, 4, [statName UTF8String], -1, SQLITE_TRANSIENT); // key
        sqlite3_bind_text(compiledStatement, 5, [[DataUtils wrapperToString:value] UTF8String], -1, SQLITE_TRANSIENT); // data


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
 * This version supports NULL cstrings
 */

- (NSString*) toNSString:(char*)cString
{
    if (cString == NULL) {
        return CLIENT_STATS_DB_NIL_VALUE;
    } else {
        return [[NSString alloc] initWithUTF8String:cString];
    }
}

- (NSArray*) readSelectResults:(NSString*) selectQuery nCols:(int)nCols {
    NSMutableArray* retVal = [[NSMutableArray alloc] init];
    
    sqlite3_stmt *compiledStatement;
    NSInteger selPrepCode = sqlite3_prepare_v2(_database, [selectQuery UTF8String], -1, &compiledStatement, NULL);
    if (selPrepCode == SQLITE_OK) {
        while (sqlite3_step(compiledStatement) == SQLITE_ROW) {
            NSMutableArray* currRow = [[NSMutableArray alloc] init];
            // Remember that while reading results, the index starts from 0
            for (int resultCol = 0; resultCol < nCols; resultCol++) {
                NSString* currResult = [self toNSString:(char*)sqlite3_column_text(compiledStatement, resultCol)];
                [currRow addObject:currResult];
            }
            [retVal addObject:currRow];
        }
    } else {
        NSLog(@"Error code %ld while compiling query %@", (long)selPrepCode, selectQuery);
    }
    sqlite3_finalize(compiledStatement);
    return retVal;
}

- (NSArray*) getValuesForInterval:(NSString*) key tq:(TimeQuery*)tq type:(NSString*)type wrapperClass:(Class)cls {
    /*
     * Note: the first getKey(keyRes) is the key of the message (e.g. 'background/location').
     * The second getKey(tq.keyRes) is the key of the time query (e.g. 'write_ts')
     */
    NSMutableArray* retWrapperArr = [NSMutableArray new];
    NSString* queryString = [NSString
                             stringWithFormat:@"SELECT %@ FROM %@ WHERE %@ = '%@' AND %@ >= %f AND %@ <= %f ORDER BY write_ts DESC",
                             KEY_DATA, TABLE_USER_CACHE, KEY_KEY, [self getStatName:key],
                             [self getStatName:tq.timeKey], tq.startTs, [self getStatName:tq.timeKey], tq.endTs];
    NSArray *wrapperJSON = [self readSelectResults:queryString nCols:1];
    for (int i = 0; i < wrapperJSON.count; i++) {
        // Because we passed in nCols = 1, there will be a single value in each array row
        [retWrapperArr addObject:[DataUtils stringToWrapper:wrapperJSON[i][0] wrapperClass:cls]];
    }
    return retWrapperArr;
}

- (NSArray*) getSensorDataForInterval:(NSString*) key tq:(TimeQuery*)tq wrapperClass:(__unsafe_unretained Class)cls {
    return [self getValuesForInterval:key tq:tq type:SENSOR_DATA_TYPE wrapperClass:cls];
}

- (NSArray*) getMessageForInterval:(NSString*) key tq:(TimeQuery*)tq wrapperClass:(__unsafe_unretained Class)cls {
    return [self getValuesForInterval:key tq:tq type:MESSAGE_TYPE wrapperClass:cls];
}

- (NSArray*) getLastValues:(NSString*) key nEntries:(int)nEntries type:(NSString*)type wrapperClass:(Class)cls {
    /*
     * Note: the first getKey(keyRes) is the key of the message (e.g. 'background/location').
     * The second getKey(tq.keyRes) is the key of the time query (e.g. 'write_ts')
     */
    NSMutableArray* retWrapperArr = [NSMutableArray new];
    NSString* queryString = [NSString
                             stringWithFormat:@"SELECT %@ FROM %@ WHERE %@ = '%@' AND %@ = '%@' ORDER BY write_ts DESC LIMIT %d",
                             KEY_DATA, TABLE_USER_CACHE, KEY_KEY, [self getStatName:key], KEY_TYPE, type, nEntries];
    NSArray *wrapperJSON = [self readSelectResults:queryString nCols:1];
    for (int i = 0; i < wrapperJSON.count; i++) {
        // Because we passed in nCols = 1, there will be a single value in each array row
        [retWrapperArr addObject:[DataUtils stringToWrapper:wrapperJSON[i][0] wrapperClass:cls]];
    }
    return retWrapperArr;
}

- (NSArray*) getLastSensorData:(NSString*) key nEntries:(int)nEntries wrapperClass:(Class)cls {
    return [self getLastValues:key nEntries:nEntries type:SENSOR_DATA_TYPE wrapperClass:cls];
}

- (NSArray*) getLastMessage:(NSString*)key nEntries:(int)nEntries wrapperClass:(Class)cls {
    return [self getLastValues:key nEntries:nEntries type:MESSAGE_TYPE wrapperClass:cls];
}

-(double)getTsOfLastTransition {
    NSString* whereClause = @" '%_transition_:_T_TRIP_ENDED_%' ORDER BY write_ts DESC LIMIT 1";
    NSString* selectQuery = [NSString stringWithFormat:@"SELECT write_ts FROM %@ WHERE %@ = '%@' AND %@ LIKE %@", TABLE_USER_CACHE, KEY_KEY,
                             [self getStatName:@"key.usercache.transition"], KEY_DATA, whereClause];

    NSLog(@"selectQuery = %@", selectQuery);
    sqlite3_stmt *compiledStatement;
    NSInteger selPrepCode = sqlite3_prepare_v2(_database, [selectQuery UTF8String], -1,
                                               &compiledStatement, NULL);
    if (selPrepCode == SQLITE_OK) {
        if (sqlite3_step(compiledStatement) == SQLITE_ROW) {
            return sqlite3_column_double(compiledStatement, 0);
        } else {
            NSLog(@"There are no T_TRIP_ENDED entries in the usercache. A sync must have just completed.");
            return -1;
        }
    } else {
        NSLog(@"Error %ld while compiling query %@", selPrepCode, selectQuery);
        return -1;
    }
}

-(double)getTsOfLastEntry {
    NSString* selectQuery = [NSString stringWithFormat:@"SELECT write_ts FROM %@ ORDER BY write_ts DESC LIMIT 1", TABLE_USER_CACHE];
    
    NSLog(@"selectQuery = %@", selectQuery);
    sqlite3_stmt *compiledStatement;
    NSInteger selPrepCode = sqlite3_prepare_v2(_database, [selectQuery UTF8String], -1,
                                               &compiledStatement, NULL);
    if (selPrepCode == SQLITE_OK) {
        if (sqlite3_step(compiledStatement) == SQLITE_ROW) {
            return sqlite3_column_double(compiledStatement, 0);
        } else {
            NSLog(@"There are no T_TRIP_ENDED entries in the usercache. A sync must have just completed.");
            return -1;
        }
    } else {
        NSLog(@"Error %ld while compiling query %@", selPrepCode, selectQuery);
        return -1;
    }
}

-(double)getLastTs {
    if ([LocationTrackingConfig instance].isDutyCycling) {
        return [self getTsOfLastTransition];
    } else {
        return [self getTsOfLastEntry];
    }
}


/*
 * Since we are using distance based filtering for iOS, and there is no reliable background sync, we push the data
 * only when we detect a trip end. If this is done through push notifications, we do so by looking to see if the
 * last location point was generated more than threshold ago. If this is done through visit detection, then we don't
 * use any location points. So we assume that we can just send all the points over to the server and don't have to 
 * find the last transition to trip end like we do on android.
 */

- (NSArray*) syncPhoneToServer {
    double lastTripEndTs = [self getLastTs];
    if (lastTripEndTs < 0) {
        NSLog(@"lastTripEndTs = %f, nothing to push, returning", lastTripEndTs);
        // we don't have a completed trip, we don't want to push anything yet
        return [NSArray new];
    }
    
    NSString *retrieveDataQuery = [NSString stringWithFormat:@"SELECT * FROM %@ WHERE %@='%@' OR %@='%@' ORDER BY %@", TABLE_USER_CACHE, KEY_TYPE, MESSAGE_TYPE, KEY_TYPE, SENSOR_DATA_TYPE, KEY_WRITE_TS];
    NSMutableArray *resultArray = [NSMutableArray new];

    sqlite3_stmt *compiledStatement;
    NSInteger selPrepCode = sqlite3_prepare_v2(_database, [retrieveDataQuery UTF8String], -1,
                                               &compiledStatement, NULL);
    if (selPrepCode == SQLITE_OK) {
        while (sqlite3_step(compiledStatement) == SQLITE_ROW) {
            // Remember that while reading results, the index starts from 0
            Metadata* md = [Metadata new];
            md.write_ts = sqlite3_column_double(compiledStatement, 0);
            md.read_ts = sqlite3_column_double(compiledStatement, 1);
            md.time_zone = [self toNSString:(char*)sqlite3_column_text(compiledStatement, 2)];
            md.type = [self toNSString:(char*)sqlite3_column_text(compiledStatement, 3)];
            md.key = [self toNSString:(char*)sqlite3_column_text(compiledStatement, 4)];
            md.plugin = [self toNSString:(char*)sqlite3_column_text(compiledStatement, 5)];
            NSDictionary* mdDict = [DataUtils wrapperToDict:md];
            NSDictionary* dataDict = [DataUtils loadFromJSONString:[self toNSString:(char*)sqlite3_column_text(compiledStatement, 6)]];
            
            NSMutableDictionary* entry = [NSMutableDictionary new];
            [entry setObject:mdDict forKey:METADATA_TAG];
            [entry setObject:dataDict forKey:DATA_TAG];
            
            [resultArray addObject:entry];
        }
    } else {
        NSLog(@"Error code %ld while compiling query %@", (long)selPrepCode, retrieveDataQuery);
    }

    /*
     * If there are no stats, there's no need to send any metadata either
     */
    
    sqlite3_finalize(compiledStatement);
    return resultArray;
}

+ (TimeQuery*) getTimeQuery:(NSArray*)pointList {
    assert(pointList.count != 0);
    Metadata* startMd = [Metadata new];
    [DataUtils dictToWrapper:[pointList[0] objectForKey:METADATA_TAG] wrapper:startMd];
    double start_ts = startMd.write_ts;
    
    Metadata* endMd = [Metadata new];
    [DataUtils dictToWrapper:[pointList[pointList.count - 1] objectForKey:METADATA_TAG] wrapper:endMd];
    double end_ts = endMd.write_ts;

    // Start slightly before and end slightly after to make sure that we get all entries
    TimeQuery* tq = [TimeQuery new];
    tq.timeKey = KEY_WRITE_TS;
    tq.startTs = start_ts - 1;
    tq.endTs = end_ts + 1;
    return tq;
}

+ (NSString*) getTimezone:(NSDictionary*)entry {
    Metadata* md = [Metadata new];
    [DataUtils dictToWrapper:[entry objectForKey:METADATA_TAG] wrapper:md];
    return md.time_zone;
}

+ (NSDate*) getWriteTs:(NSDictionary *)entry {
    Metadata* md = [Metadata new];
    [DataUtils dictToWrapper:[entry objectForKey:METADATA_TAG] wrapper:md];
    return [DataUtils dateFromTs:md.write_ts];
}

/* TODO: Consider refactoring this along with the code in TripSectionDB to have generic read code.
 * Unfortunately, the code in TripSectionDB sometimes reads blobs, which require a different read method,
 * so this refactoring is likely to be non-trivial
 */

- (void)clearEntries:(TimeQuery*)tq {
    NSLog(@"Clearing entries for timequery %@", tq);
    NSString* deleteQuery = [NSString stringWithFormat:@"DELETE FROM %@ WHERE %@ > %f AND %@ < %f",
                             TABLE_USER_CACHE, tq.timeKey, tq.startTs, tq.timeKey, tq.endTs];
    [self clearQuery:deleteQuery];
}

- (void)clear {
    NSString *deleteQuery = [NSString stringWithFormat:@"DELETE FROM %@", TABLE_USER_CACHE];
    [self clearQuery:deleteQuery];
}

- (void)clearQuery:(NSString*)deleteQuery {
    sqlite3_stmt *compiledStatement;
    NSInteger delPrepCode = sqlite3_prepare_v2(_database, [deleteQuery UTF8String], -1, &compiledStatement, NULL);
    if (delPrepCode == SQLITE_OK) {
        sqlite3_step(compiledStatement);
    }
    sqlite3_finalize(compiledStatement);
}

@end
