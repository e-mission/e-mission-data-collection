//
//  DataUtils.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/9/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "DataUtils.h"
#import "OngoingTripsDatabase.h"
#import "StoredTripsDatabase.h"

@implementation DataUtils

+ (NSString*)dateToString:(NSDate*)date {
    NSDateFormatter *dateFormat = [[NSDateFormatter alloc] init];
    [dateFormat setDateFormat:@"yyyyMMddTHHmmssz"];
    return [dateFormat stringFromDate:date];
}

+ (NSDate*)dateFromString:(NSString*)string {
    NSDateFormatter *dateFormat = [[NSDateFormatter alloc] init];
    [dateFormat setDateFormat:@"yyyyMMddTHHmmssz"];
    return [dateFormat dateFromString:string];
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

+ (NSDate*) getMidnight {
    NSDate* now = [NSDate date];
    NSCalendar* cal = [NSCalendar calendarWithIdentifier:NSGregorianCalendar];
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
    CLLocation* startPoint = endPoints[0];
    CLLocation* endPoint = endPoints[1];
    
    NSLog(@"startPoint = %@, endPoint = %@", startPoint, endPoint);
    if (startPoint == NULL && endPoint == NULL) {
        // The trip has no points, so we can't store anything
        return;
    }
    
    NSDictionary* startPlace = NULL;
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
    } else {
        startPlace = [self loadFromJSONString:[storedDb getLastTrip]];
    }
    

    NSLog(@"updated startPlace = %@", startPlace);
    NSDate* startPlaceStartDate = [self dateFromString:[startPlace objectForKey:@"startTime"]];
    // Our end point in the start place is when this trip starts
    [startPlace setValue:[self dateToString:startPoint.timestamp] forKey:@"endTime"];
    
    [storedDb updateTrip:[self saveToJSONString:startPlace] atTime:startPlaceStartDate];
    
    NSDictionary* completedTrip = [[NSDictionary alloc] init];
    [completedTrip setValue:@"move" forKey:@"type"];
    [completedTrip setValue:[self dateToString:startPoint.timestamp] forKey:@"startTime"];
    [completedTrip setValue:[NSNumber numberWithDouble:startPoint.timestamp.timeIntervalSince1970] forKey:@"startTimeTs"];
    
    // TODO: If we put the end time of the trip into the DB like this, it may not be consistent
    // with the track point queries, which are based on elapsed time. Figure out whether we need
    // to regenerate actual time from start time, start elapsed time and end elapsed time as well
    // This may not be necessary because we only end the trip when we have been stable for ~ 15 minutes
    [completedTrip setValue:[self dateToString:endPoint.timestamp] forKey:@"endTime"];

    NSMutableArray* activityArray = [[NSMutableArray alloc] init];
    /* 
     * Since we don't have an iPhone6, we currently don't have any activities stored, so we will create one activity/section of
     * type "unknown"
     */
    NSDictionary* oneSection = [self createSection:@"unknown" withStart:startPoint.timestamp withEnd:endPoint.timestamp
                                            fromDb:ongoingDb];
    [activityArray addObject:oneSection];
    [storedDb addTrip:[self saveToJSONString:completedTrip] atTime:startPoint.timestamp];
    
    // We are in the end place starting now.
    // Dunno when we will stop.
    // But when we do, this will be the startPlace and we will set the endTime above
    NSDictionary* endPlace = [self getJSONPlace:endPoint];
    NSLog(@"endPlace = %@", endPlace);
    [storedDb addTrip:[self saveToJSONString:endPlace] atTime:endPoint.timestamp];
}

+ (NSDictionary*) getTrackPoint:(CLLocation*) loc {
    NSLog(@"getTrackPoint(%@) called", loc);
    
    NSDictionary* retObject = [[NSDictionary alloc] init];
    [retObject setValue:[self dateToString:loc.timestamp] forKey:@"time"];
    [retObject setValue:[NSNumber numberWithDouble:loc.coordinate.latitude] forKey:@"lat"];
    [retObject setValue:[NSNumber numberWithDouble:loc.coordinate.longitude] forKey:@"lon"];
    
    NSDictionary* extrasObject = [[NSDictionary alloc] init];
    [extrasObject setValue:[NSNumber numberWithDouble:loc.horizontalAccuracy] forKey:@"hAccuracy"];
    [extrasObject setValue:[NSNumber numberWithDouble:loc.verticalAccuracy] forKey:@"vAccuracy"];
    [extrasObject setValue:[NSNumber numberWithDouble:loc.altitude] forKey:@"altitude"];
    
    [retObject setValue:extrasObject forKey:@"extras"];
    return retObject;
}

+ (NSDictionary*) createSection:(NSString*)activityType
                      withStart:(NSDate*) startSectionTime
                        withEnd:(NSDate*) endSectionTime
                         fromDb:(OngoingTripsDatabase*) ongoingDb {
    
    NSDictionary* currSection = [[NSDictionary alloc] init];
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
        NSDictionary* currPoint = [self getTrackPoint:pointsForSection[j]];
        [trackPoints addObject:currPoint];
        distance = distance + [pointsForSection[j] distanceFromLocation:pointsForSection[j+1]];
    }
    [currSection setValue:trackPoints forKey:@"trackPoints"];
    [currSection setValue:[NSNumber numberWithDouble:distance] forKey:@"distance"];
    return currSection;
}

+ (void) clearOngoingDb {
    [[OngoingTripsDatabase database] clear];
}

// Seriously, what is the equivalent of throws JSONException in Objective C?
+ (NSDictionary*) getJSONPlace:(CLLocation*) loc {
    NSLog(@"getJSONPlace(%@) called", loc);
    
    NSDictionary* retObj = [[NSMutableDictionary alloc] init];
    [retObj setValue:@"place" forKey:@"type"];
    [retObj setValue:[self dateToString:loc.timestamp] forKey:@"startTime"];
    
    // The server code currently expects string formatted dates, which are then sent to the
    // app which also expects string formatted dates.
    // TODO: Change everything to millisecond timestamps to avoid confusion
    [retObj setValue:[NSNumber numberWithDouble:loc.timestamp.timeIntervalSince1970] forKey:@"startTimeTs"];
    // retObj.put("startTimeTs", loc.getTime());
    
    NSDictionary* placeObj = [[NSMutableDictionary alloc] init];
    
    NSDictionary* locationObj = [[NSMutableDictionary alloc] init];
    [locationObj setValue:[NSNumber numberWithDouble:loc.coordinate.latitude] forKey:@"lat"];
    [locationObj setValue:[NSNumber numberWithDouble:loc.coordinate.longitude] forKey:@"lon"];
    
    [placeObj setValue:locationObj forKey:@"location"];
    [placeObj setValue:@"unknown" forKey:@"id"];
    [placeObj setValue:@"unknown" forKey:@"type"];
    
    [retObj setValue:placeObj forKey:@"place"];
    return retObj;
}


// TODO: Refactor this to be common with the TripSection loading code when we merge this into e-mission
+ (NSDictionary*)loadFromJSONData:(NSData*)jsonData {
    NSError *error;
    NSMutableDictionary *jsonDict = [NSJSONSerialization JSONObjectWithData:jsonData
                                                                    options:NSJSONReadingMutableContainers
                                                                      error: &error];
    if (jsonDict == nil) {
        NSLog(@"error %@ while parsing json object %@", error, jsonData);
    }
    return jsonDict;
}

+ (NSDictionary*)loadFromJSONString:(NSString *)jsonString {
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
    NSString *strToSend = [NSString stringWithUTF8String:[bytesToSend bytes]];
    return strToSend;
}

+ (NSArray*) getTripsToPush {
    NSLog(@"getTripsToPush() called");
    
    StoredTripsDatabase* storedDb = [StoredTripsDatabase database];

    // TODO: Decide how to deal with staying in a place overnight (say from 8pm to 8am)
    // Do we have a place that ends at midnight and another that starts the next morning
    // Or do we have a single place that extends from 8pm to 8am?
    // Right now, return a single place that extends from 8pm to 8am to make the code easier
    
    NSArray* allTrips = [storedDb getAllStoredTrips];
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
