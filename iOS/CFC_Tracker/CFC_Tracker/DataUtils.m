//
//  DataUtils.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/9/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/NSObjCRuntime.h>
#import <objc/objc.h>
#import <objc/runtime.h>
#import "DataUtils.h"
#import "OngoingTripsDatabase.h"
#import "StoredTripsDatabase.h"
#import "LocalNotificationManager.h"
#import "BuiltinUserCache.h"
#import "SimpleLocation.h"
#import "TimeQuery.h"
#import "CommunicationHelper.h"

@implementation DataUtils

+ (NSString*)dateToString:(NSDate*)date {
    NSDateFormatter *dateFormat = [[NSDateFormatter alloc] init];
    [dateFormat setDateFormat:@"yyyy-MM-dd'T'HH:mm:ssZ"];
    return [dateFormat stringFromDate:date];
}

+ (NSDate*)dateFromString:(NSString*)string {
    NSDateFormatter *dateFormat = [[NSDateFormatter alloc] init];
    [dateFormat setDateFormat:@"yyyy-MM-dd'T'HH:mm:ssZ"];
    return [dateFormat dateFromString:string];
}

+ (double)dateToTs:(NSDate*)date {
    return [date timeIntervalSince1970];
}

+ (NSDate*)dateFromTs:(double)ts {
    return [NSDate dateWithTimeIntervalSince1970:ts];
}

/*
 * Converts the specified wrapper object into a dictionary that can be serialized using
 * NSJSONSerialization. It does this by doing the following:
 * - Determine the set of properties in the class
 * - Generate a dictionary for the set of properties using the  NSKeyValueCoding protocol
 * (dictionaryWithValuesForKeys).
 * The code to do this is largely inspired by: https://dzone.com/articles/objective-c-categories-groovy#!
 * and https://www.bignerdranch.com/blog/inside-the-bracket-part-6-using-the-runtime-api/
 * 
 * Note that we could create a wrapper protocol which would define this, but
 * then it won't work on existing objects such as CLLocation. There are fancy ways to add decorations to existing classes
 * in objective C, but then you would need to add a decoration for each type of object.
 * 
 * Let's keep things simple here.
 */
+ (NSDictionary*)wrapperToDict:(NSObject*)obj {
    if ([self isKindOfClass:[NSDictionary class]] || [self isKindOfClass:[NSArray class]]) {
        return (NSDictionary*)obj;
    } else {
        unsigned int propertyCount = 0;
        objc_property_t* properties = class_copyPropertyList([obj class], &propertyCount);
        NSMutableArray *keys = [NSMutableArray new];
        for (NSUInteger i = 0; i < propertyCount; i++) {
            objc_property_t property = *(properties + i);
            [keys addObject:[NSString stringWithCString:property_getName(property) encoding:NSASCIIStringEncoding]];
        }
        free(properties);
        NSDictionary *dictionary = [obj dictionaryWithValuesForKeys:keys];
        return dictionary;
    }
}

+ (void)dictToWrapper:(NSDictionary*)dict wrapper:(NSObject*)obj {
    return [obj setValuesForKeysWithDictionary:dict];
}

+ (NSString*)wrapperToString:(NSObject*)obj {
    NSDictionary* wrapperDict = [DataUtils wrapperToDict:obj];
    NSString* serializedString = [DataUtils saveToJSONString:wrapperDict];
    return serializedString;
}

+ (NSObject*)stringToWrapper:(NSString*)str wrapperClass:(Class)cls {
    NSDictionary* wrapperDict = [DataUtils loadFromJSONString:str];
    NSObject* obj = [cls new];
    [self dictToWrapper:wrapperDict wrapper:obj];
    return obj;
}

+ (void) addPoint:(CLLocation*) currLoc {
    NSLog(@"addPoint(%@) called", currLoc);
    [[OngoingTripsDatabase database] addPoint:currLoc];
}

+ (NSArray*) getLastPoints:(int) nPoints {
    NSLog(@"addLastPoints(%d) called", nPoints);
    return [[BuiltinUserCache database] getLastSensorData:@"key.usercache.location" nEntries:nPoints wrapperClass:[SimpleLocation class]];
}


+ (void) addModeChange:(EMActivity*) activity {
    [[OngoingTripsDatabase database] addModeChange:activity];
}

+ (void) clearOngoingDb {
    [[OngoingTripsDatabase database] clear];
}

+ (void) clearStoredDb {
    [[StoredTripsDatabase database] clear];
}

/*
 * Return a list of activities along with the intervals for them.
 */

+(void)storeMotionActivities:(CMMotionActivityManager*) manager
                    fromDate:(NSDate*) fromDate
                      toDate:(NSDate*) toDate {
    NSOperationQueue* mq = [NSOperationQueue mainQueue];
    [manager queryActivityStartingFromDate:fromDate toDate:toDate toQueue:mq withHandler:^(NSArray *activities, NSError *error) {
        if (error == nil) {
            /*
             * This conversion allows us to unit test this code, since we cannot create valid CMMotionActivity
             * segments. We can create a CMMotionActivity, but we cannot set any of its properties.
             */
            NSMutableArray* convertedActivities = [[NSMutableArray alloc] init];
            for (int i = 0; i < activities.count; i++) {
                CMMotionActivity* activity = (CMMotionActivity*)activities[i];
                EMActivity* convertedActivity = [[EMActivity alloc] init];
                convertedActivity.mode = [EMActivity getRelevantActivity:activity];
                convertedActivity.confidence = activity.confidence;
                convertedActivity.startDate = activity.startDate;
            }
            [self saveActivityList:convertedActivities];
        } else {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Got error %@ while querying activity from %@ to %@",
                                                       error, fromDate, toDate]];
            NSLog(@"Got error %@ while querying activity from %@ to %@", error, fromDate, toDate);
        }
    }];
}

+ (void) saveActivityList:(NSArray*) activities {
    EMActivity* oldActivity = NULL;
    
    for (int i = 0; i < activities.count; i++) {
        EMActivity* activity = (EMActivity*)activities[i];
        // TODO: Figure out a better way to detect use the complexity in the activities.
        // Right now, let us do something stupid and easy.
        if (activity.confidence == CMMotionActivityConfidenceHigh) {
            if (oldActivity == NULL ||
                oldActivity.mode != activity.mode) {
                [DataUtils addModeChange:activity];
                oldActivity = activity;
            }
        }
    }
}

+ (void) endTrip {
    NSLog(@"DataUtils.endTrip called");
    @try {
        [self convertOngoingToStored];
    } @catch (NSException* eInitial) {
        NSLog(@"DataUtils: Got initial error %@ while saving ongoing trips, retrying once to see if it is a transient error", eInitial);
        @try {
            [self convertOngoingToStored];
        } @catch (NSException* eFinal) {
            NSLog(@"Got final error %@ while saving ongoing trips, deleting DB and abandoning", eFinal);
        }
    }
    [self clearOngoingDb];
}

+(BOOL) hasTripEnded:(int)tripEndThresholdMins {
    
    NSArray* last3Points = [DataUtils getLastPoints:3];
    if (last3Points.count == 0) {
        /*
         * There are no points in the database. This means that no trip has been started in the past 30 minutes.
         * This should never happen because we only invoke this when we are in the ongoing trip state, so let's generate a
         * notification here.
         */
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"last3Points.count = %lu while checking for trip end",
                                                   (unsigned long)last3Points.count]];
        // Let's also return "NO", since it is the safer option
        return NO;
    } else {
        SimpleLocation *lastLoc = ((SimpleLocation*)last3Points.firstObject);
        SimpleLocation *firstLoc = ((SimpleLocation*)last3Points.lastObject);
        
        NSLog(@"firstDate = %@, lastDate = %@", firstLoc.fmt_time, lastLoc.fmt_time);
        NSDate* lastDate = [DataUtils dateFromTs:lastLoc.ts];
        if (fabs(lastDate.timeIntervalSinceNow) > tripEndThresholdMins * 60) {
            NSLog(@"interval to the last date = %f, returning YES", lastDate.timeIntervalSinceNow);
            return YES;
        } else {
            NSLog(@"interval to the last date = %f, returning NO", lastDate.timeIntervalSinceNow);
            return NO;
        }
    }
}


+ (NSDate*) getMidnight {
    NSDate* now = [NSDate date];
    NSCalendar* cal = [NSCalendar autoupdatingCurrentCalendar];
    unsigned unitFlags = NSYearCalendarUnit | NSMonthCalendarUnit | NSDayCalendarUnit;
    NSDateComponents* midnightComponents = [cal components:unitFlags fromDate:now];
    return [cal dateFromComponents:midnightComponents];
}

+ (void) convertOngoingToStored {
    NSLog(@"convertOngoingToStored called");
    OngoingTripsDatabase* ongoingDb = [OngoingTripsDatabase database];
    StoredTripsDatabase* storedDb = [StoredTripsDatabase database];
    
    NSArray* endPoints = [ongoingDb getEndPoints];
    NSLog(@"endPoints = %@ with count %ld", endPoints, (unsigned long)endPoints.count);
    if (endPoints.count == 0) {
        // The trip has no points, so we can't store anything
        return;
    }
    CLLocation* startPoint = endPoints[0];
    CLLocation* endPoint = endPoints[1];
    
    NSLog(@"startPoint = %@, endPoint = %@", startPoint, endPoint);
    if (startPoint == NULL && endPoint == NULL) {
        // The trip has no points, so we can't store anything
        return;
    }
    
    /*
     * Now, we need to get the set of activities. If we are just using the iOS activity
     * detection, we can just read them and process them in real time. But we first save
     * to the database and then read from the database. This allows us greater flexibility:
     * - in terms of unit testing, we can test the segmentation by manually storing entries
     * to the database.
     * -  Gives us more flexibility to switch to our own mode detection, as opposed to the
     * iOS mode detection, we will have read/load built in and won't have to change the trip
     * formatting code.
     */
    
    if ([CMMotionActivityManager isActivityAvailable] == YES) {
        CMMotionActivityManager* activityMgr = [[CMMotionActivityManager alloc] init];
        [self storeMotionActivities:activityMgr fromDate:startPoint.timestamp toDate:endPoint.timestamp];
    } else {
        NSLog(@"Activity recognition unavailable, skipping segmentation");
    }
    
    NSMutableDictionary* startPlace = NULL;
    NSString* lastTripString = [storedDb getLastTrip];
    NSLog(@"lastTripString = %@", lastTripString);
    if (lastTripString == NULL) {
        // This is the first time we have started running, don't have any data so don't have any pending trip to complete.
        // Let's just create an object with the current location and a start time of midnight today
        startPlace = [self getJSONPlace:startPoint];
        NSDate* midnightDate = [self getMidnight];
        NSTimeInterval midnightTs = midnightDate.timeIntervalSince1970;
        [startPlace setValue:[self dateToString:midnightDate] forKey:@"startTime"];
        [startPlace setValue:[NSNumber numberWithDouble:midnightTs] forKey:@"startTimeTs"];
        NSString* startPlaceJSON = [self saveToJSONString:startPlace];
        
        /*
         * This is a variation from the android code because it looks like update works like upsert on
         * android but not on iOS. May want to fix the android code in the same way for better maintainability.
         * In android, we call updateTrip for both newly created trips, and old ones.
         */
        [storedDb addTrip:startPlaceJSON atTime:midnightDate];
    } else {
        startPlace = [self loadFromJSONString:[storedDb getLastTrip]];
    }

    // Our end point in the start place is when this trip starts
    [startPlace setValue:[self dateToString:startPoint.timestamp] forKey:@"endTime"];
    
    // Update the current stored value with the end time
    NSDate* startPlaceStartDate = [self dateFromString:[startPlace objectForKey:@"startTime"]];
    [storedDb updateTrip:[self saveToJSONString:startPlace] atTime:startPlaceStartDate];

    
    NSMutableDictionary* completedTrip = [[NSMutableDictionary alloc] init];
    [completedTrip setValue:@"move" forKey:@"type"];
    [completedTrip setValue:[self dateToString:startPoint.timestamp] forKey:@"startTime"];
    [completedTrip setValue:[NSNumber numberWithDouble:startPoint.timestamp.timeIntervalSince1970] forKey:@"startTimeTs"];
    
    // TODO: If we put the end time of the trip into the DB like this, it may not be consistent
    // with the track point queries, which are based on elapsed time. Figure out whether we need
    // to regenerate actual time from start time, start elapsed time and end elapsed time as well
    // This may not be necessary because we only end the trip when we have been stable for ~ 15 minutes
    [completedTrip setValue:[self dateToString:endPoint.timestamp] forKey:@"endTime"];

    NSMutableArray* activityArray = [[NSMutableArray alloc] init];
    NSArray* modeChanges = [[OngoingTripsDatabase database] getModeChanges:startPoint.timestamp toDate:endPoint.timestamp];
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"In DataUtils, found %lu mode changes", modeChanges.count]];

    if (modeChanges.count > 0) {
        activityArray = [self createSections:modeChanges withStartPoint:startPoint withEndPoint:endPoint];
    } else {
        /*
         * Since we don't have an iPhone6, we currently don't have any activities stored, so we will create one activity/section of
         * type "unknown"
         */
        NSMutableDictionary* oneSection = [self createSection:@"unknown"
                                                    withStart:startPoint.timestamp withEnd:endPoint.timestamp
                                                       fromDb:ongoingDb];
        [activityArray addObject:oneSection];
    }
    if (activityArray.count > 0) {
        completedTrip[@"activities"] = activityArray;
    }

    [storedDb addTrip:[self saveToJSONString:completedTrip] atTime:startPoint.timestamp];
    
    // We are in the end place starting now.
    // Dunno when we will stop.
    // But when we do, this will be the startPlace and we will set the endTime above
    NSMutableDictionary* endPlace = [self getJSONPlace:endPoint];
    NSLog(@"endPlace = %@", endPlace);
    [storedDb addTrip:[self saveToJSONString:endPlace] atTime:endPoint.timestamp];
}

+ (NSMutableDictionary*) getTrackPoint:(CLLocation*) loc {
    NSLog(@"getTrackPoint(%@) called", loc);
    
    NSMutableDictionary* retObject = [[NSMutableDictionary alloc] init];
    [retObject setValue:[self dateToString:loc.timestamp] forKey:@"time"];
    [retObject setValue:[NSNumber numberWithDouble:loc.coordinate.latitude] forKey:@"lat"];
    [retObject setValue:[NSNumber numberWithDouble:loc.coordinate.longitude] forKey:@"lon"];
    
    NSMutableDictionary* extrasObject = [[NSMutableDictionary alloc] init];
    [extrasObject setValue:[NSNumber numberWithDouble:loc.horizontalAccuracy] forKey:@"hAccuracy"];
    [extrasObject setValue:[NSNumber numberWithDouble:loc.verticalAccuracy] forKey:@"vAccuracy"];
    [extrasObject setValue:[NSNumber numberWithDouble:loc.altitude] forKey:@"altitude"];
    
    [retObject setValue:extrasObject forKey:@"extras"];
    return retObject;
}

+ (NSMutableDictionary*) createSection:(NSString*)activityType
                      withStart:(NSDate*) startSectionTime
                        withEnd:(NSDate*) endSectionTime
                         fromDb:(OngoingTripsDatabase*) ongoingDb {
    
    NSMutableDictionary* currSection = [[NSMutableDictionary alloc] init];
    // TODO: Switch to timestamps throughout system
    [currSection setValue:[self dateToString:startSectionTime] forKey:@"startTime"];
    [currSection setValue:[self dateToString:endSectionTime] forKey:@"endTime"];
    [currSection setValue:activityType forKey:@"activity"];
    [currSection setValue:activityType forKey:@"group"];
    [currSection setValue:[NSNumber numberWithDouble:[endSectionTime timeIntervalSinceDate:startSectionTime]] forKey:@"duration"];
    
    NSArray* pointsForSection = [ongoingDb getPointsFrom:startSectionTime to:endSectionTime];
    NSMutableArray* trackPoints = [[NSMutableArray alloc] init];
    double distance = 0;
    for(int j = 0; j < pointsForSection.count; j++) {
        NSMutableDictionary* currPoint = [self getTrackPoint:pointsForSection[j]];
        [trackPoints addObject:currPoint];
        /*
         * While calculating distance, say we have 3 points, we find the distance between the 0th and first point when 
         * j = 0, the first and second point when j = 1. We don't need to find any distance when j = 2.
         */
        if (j < pointsForSection.count - 1) {
            distance = distance + [pointsForSection[j] distanceFromLocation:pointsForSection[j+1]];
        }
    }
    [currSection setValue:trackPoints forKey:@"trackPoints"];
    [currSection setValue:[NSNumber numberWithDouble:distance] forKey:@"distance"];
    return currSection;
}

+ (NSMutableArray*) createSections:(NSArray*)modeChanges
                 withStartPoint:(CLLocation*)startPoint
                   withEndPoint:(CLLocation*)endPoint {
    NSMutableArray* retVal = [[NSMutableArray alloc] init];
    if (modeChanges.count == 0) {
        NSLog(@"Found zero mode changes, creating one unknown section");

        NSMutableDictionary* currSection = [self createSection:@"unknown"
                                                     withStart:startPoint.timestamp
                                                       withEnd:endPoint.timestamp
                                                        fromDb:[OngoingTripsDatabase database]];
        [retVal addObject:currSection];
        return retVal;
    } else if (modeChanges.count == 1) {
        EMActivity* theChange = modeChanges[0];
        NSLog(@"Found one mode change, creating one section of type %@", [theChange getActivityName]);
    
        NSMutableDictionary* currSection = [self createSection:[theChange getActivityName]
                                                     withStart:startPoint.timestamp
                                                       withEnd:startPoint.timestamp
                                                        fromDb:[OngoingTripsDatabase database]];
        [retVal addObject:currSection];
        return retVal;
    } else {
        assert(modeChanges.count > 1);
        NSLog(@"Found more than one mode change, iterating through them");
        for (int i = 0; i < modeChanges.count; i++) {
            EMActivity* currActivity = (EMActivity*)modeChanges[i];
            NSDate* sectionStartTime = NULL;
            if (i == 0) {
                // If this is the first point, we want to count points from the start of the trip
                // but we won't know what activity they are in. Let us assume that they are merged
                // with the current activity.
                sectionStartTime = startPoint.timestamp;
            } else {
                sectionStartTime = currActivity.startDate;
            }
            
            NSDate* sectionEndTime = NULL;
            if (i == modeChanges.count - 1) {
                sectionEndTime = endPoint.timestamp;
            } else {
                EMActivity* nextActivity = (EMActivity*)modeChanges[i+1];
                sectionEndTime = nextActivity.startDate;
            }
            
            NSMutableDictionary* currSection = [self createSection:[currActivity getActivityName]
                                                         withStart:sectionStartTime
                                                           withEnd:sectionEndTime
                                                            fromDb:[OngoingTripsDatabase database]];
            [retVal addObject:currSection];
        }
        return retVal;
    }
}

// Seriously, what is the equivalent of throws JSONException in Objective C?
+ (NSMutableDictionary*) getJSONPlace:(CLLocation*) loc {
    NSLog(@"getJSONPlace(%@) called", loc);
    
    NSMutableDictionary* retObj = [[NSMutableDictionary alloc] init];
    [retObj setValue:@"place" forKey:@"type"];
    [retObj setValue:[self dateToString:loc.timestamp] forKey:@"startTime"];
    
    // The server code currently expects string formatted dates, which are then sent to the
    // app which also expects string formatted dates.
    // TODO: Change everything to millisecond timestamps to avoid confusion
    [retObj setValue:[NSNumber numberWithDouble:loc.timestamp.timeIntervalSince1970] forKey:@"startTimeTs"];
    
    NSMutableDictionary* placeObj = [[NSMutableDictionary alloc] init];
    
    NSMutableDictionary* locationObj = [[NSMutableDictionary alloc] init];
    [locationObj setValue:[NSNumber numberWithDouble:loc.coordinate.latitude] forKey:@"lat"];
    [locationObj setValue:[NSNumber numberWithDouble:loc.coordinate.longitude] forKey:@"lon"];
    
    [placeObj setValue:locationObj forKey:@"location"];
    [placeObj setValue:@"unknown" forKey:@"id"];
    [placeObj setValue:@"unknown" forKey:@"type"];
    
    [retObj setValue:placeObj forKey:@"place"];
    return retObj;
}


// TODO: Refactor this to be common with the TripSection loading code when we merge this into e-mission
+ (NSMutableDictionary*)loadFromJSONData:(NSData*)jsonData {
    NSError *error;
    NSMutableDictionary *jsonDict = [NSJSONSerialization JSONObjectWithData:jsonData
                                                                    options:NSJSONReadingMutableContainers
                                                                      error: &error];
    if (jsonDict == nil) {
        NSLog(@"error %@ while parsing json object %@", error, jsonData);
    }
    return jsonDict;
}

+ (NSMutableDictionary*)loadFromJSONString:(NSString *)jsonString {
    NSData *jsonData = [jsonString dataUsingEncoding:NSUTF8StringEncoding];
    return [self loadFromJSONData:jsonData];
}

+ (NSData*)saveToJSONData:(NSDictionary*) jsonDict {
    NSError *error;
    NSData *bytesToSend = [NSJSONSerialization dataWithJSONObject:jsonDict
                                                          options:kNilOptions error:&error];
    return bytesToSend;
}

+ (NSString*)saveToJSONString:(NSDictionary*) jsonDict {
    NSData *bytesToSend = [self saveToJSONData:jsonDict];
    
    // This is copied from the earlier code, but doesn't seem to work here!
    // Need to recheck original code in e-mission-phone as well
    // NSString *strToSend = [NSString stringWithUTF8String:[bytesToSend bytes]];
    NSString *strToSend = [[NSString alloc] initWithData:bytesToSend encoding:NSUTF8StringEncoding];
    
    NSLog(@"data has %lu bytes, str has size %lu", bytesToSend.length, strToSend.length);
    return strToSend;
}

+ (void) pushAndClearData:(void (^)(BOOL))completionHandler {
    NSArray* entriesToPush = [[BuiltinUserCache database] syncPhoneToServer];
    if (entriesToPush.count == 0) {
        NSLog(@"No data to send, returning early");
    } else {
        [CommunicationHelper phone_to_server:entriesToPush
                             completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
                                 [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                                            @"successfully pushed %ld entries to the server",
                                                                            (unsigned long)entriesToPush.count]];
                                 // Only delete trips after they have been successfully pushed
                                 if (error == nil) {
                                     TimeQuery* tq = [BuiltinUserCache getTimeQuery:entriesToPush];
                                     [[BuiltinUserCache database] clearEntries:tq];
                                 }
                                 NSLog(@"Returning from silent push");
                                 completionHandler(TRUE);
                             }];
    }
}

+ (NSArray*) getTripsToPush {
    NSLog(@"getTripsToPush() called");
    
    StoredTripsDatabase* storedDb = [StoredTripsDatabase database];

    // TODO: Decide how to deal with staying in a place overnight (say from 8pm to 8am)
    // Do we have a place that ends at midnight and another that starts the next morning
    // Or do we have a single place that extends from 8pm to 8am?
    // Right now, return a single place that extends from 8pm to 8am to make the code easier
    
    NSArray* allTrips = [storedDb getAllStoredTrips];
    // TODO: Difference from the android code. Need to unify.
    if (allTrips.count == 0) {
        
        return allTrips;
    }
    
    NSMutableArray* retVal = [[NSMutableArray alloc] init];
    for (int i = 0; i < allTrips.count - 1; i++) {
        [retVal addObject:[self loadFromJSONString:allTrips[i]]];
    }
    return retVal;
}

+ (void) deletePushedTrips:(NSArray*) tripsToPush {
    NSLog(@"deletePushedTrips(%lu) called", (unsigned long)tripsToPush.count);
    
    StoredTripsDatabase* storedDb = [StoredTripsDatabase database];
    NSMutableArray* tsArray = [[NSMutableArray alloc] init];
    for (int i = 0; i < tripsToPush.count; i++) {
        [tsArray addObject:[self dateFromString:[tripsToPush[i] objectForKey:@"startTime"]]];
    }
    [storedDb deleteTrips:tsArray];
}

+ (void) deleteAllStoredTrips {
    StoredTripsDatabase* storedDb = [StoredTripsDatabase database];
    [storedDb clear];
}

@end
