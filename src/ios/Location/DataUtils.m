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
#import "LocalNotificationManager.h"
#import "BEMBuiltinUserCache.h"
#import "SimpleLocation.h"
#import "TimeQuery.h"
#import "MotionActivity.h"
#import "BEMCommunicationHelper.h"
#import "LocationTrackingConfig.h"
#import "ConfigManager.h"
#import "Battery.h"

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

/*
 * Returns the last n filtered points from the local database.
 */
+ (NSArray*) getLastPoints:(int) nPoints {
    NSLog(@"addLastPoints(%d) called", nPoints);
    return [[BuiltinUserCache database] getLastSensorData:@"key.usercache.filtered_location" nEntries:nPoints wrapperClass:[SimpleLocation class]];
}

/*
 * Returns the last n filtered points from the local database.
 */
+ (NSArray*) getPointsSince:(int) nMinutes {
    NSLog(@"getPointsSince(%d) called", nMinutes);
    
    NSDate* dateNow = [NSDate date];
    double nowTs = [DataUtils dateToTs:dateNow];
    double startTs = nowTs - nMinutes * 60;
    
    TimeQuery* tq = [TimeQuery new];
    tq.key = [[BuiltinUserCache database] getStatName:@"metadata.usercache.write_ts"];
    tq.startTs = startTs;
    tq.endTs = nowTs;

    return [[BuiltinUserCache database] getSensorDataForInterval:@"key.usercache.filtered_location" tq:tq wrapperClass:[SimpleLocation class]];
}


+ (NSArray*) distanceFrom:(SimpleLocation*)lastPoint forArray:(NSArray*)lastNPoints {
    NSMutableArray* retArray = [[NSMutableArray alloc] init];
    for (int i = 0; i < lastNPoints.count; i++) {
        [retArray addObject:@(fabs([lastPoint distanceFromLocation:lastNPoints[i]]))];
    }
    return retArray;
}

+ (NSArray*) distanceBetweenPoints:(NSArray*)lastNPoints {
    NSMutableArray* retArray = [[NSMutableArray alloc] init];
    if (lastNPoints.count == 0 || lastNPoints.count == 1) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"lastNPoints.count = %@, early return", @(lastNPoints.count)]];
        return retArray;
    }
    // We know that there are at least two points, so it is safe to access the i+1th point
    for (int i = 0; i < lastNPoints.count - 1; i++) {
        [retArray addObject:@(fabs([lastNPoints[i] distanceFromLocation:lastNPoints[i+1]]))];
    }
    return retArray;
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
                                                   @"last5Points.count = %lu while checking for trip end",
                                                   (unsigned long)last3Points.count]];
        // Let's also return "NO", since it is the safer option
        return NO;
    }
    
    // There are points in the database
    NSArray* pointsWithinThreshold = [DataUtils getPointsSince:tripEndThresholdMins];
    SimpleLocation *lastLoc = ((SimpleLocation*)last3Points.firstObject);
    NSDate* lastDate = [DataUtils dateFromTs:lastLoc.ts];
    
    if (pointsWithinThreshold.count == 0) {
        // We have a distance filter, so if all points are more than the trip end threshold ago, trip has ended
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"interval to the last date = %f, returning YES", lastDate.timeIntervalSinceNow] showUI:TRUE];
        return YES;
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"found %lu points in the last %d minutes, checking their distances from here", (unsigned long)pointsWithinThreshold.count, tripEndThresholdMins * 60] showUI:TRUE];

        // But what if we receive a bunch of noisy updates although the trip has ended. I thought that this
        // might result in points that are below the distance filter, but it actually results in points that
        // are just above the distance filter distance - e.g.
        // So the challenge is distinguishing loitering points from ongoing trip points when
        // both of them generate points that are above the distance filter (e.g. even when the trip has ended,
        // with filterDistance = 5m, the distance between first and middle was 5.382 and the
        // distance between first and last was 5.595. Let's try comparing both last to all of them, and
        // the inter point distances.
        // Doesn't help - all the values are larger than the filter, e.g. for filter = 5m,
        // the distances are:
        // distances from last = (0, "5.893507513473614", "12.62960144819151", "19.24935624718944", "24.63277283953604")
        // and distances between = ("5.893507513473614", "6.746749622275789", "6.990052045853307", "5.470282826818653")
        // Generating local notification with message maxFrom = 24.63277283953604, maxBetween = 6.990052045853307
        // The point is that with a distance filter, the last n points may actually include part of the real trip.
        // So instead, we only use points that are within the trip threshold filter. If they are all within the
        // the geofence threshold, then we have been within the threshold for the trip end time, and so we can end
        // the trip. Note that this means that a smaller geofence radius will reinstate the geofence less quickly,
        // so there will be greater power drain, which gives us a nice tradeoff between accuracy and power drain.
        
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"distances from last = %@",
                                                   [self distanceFrom:lastLoc forArray:pointsWithinThreshold]]];
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"distances between = %@",
                                                   [self distanceBetweenPoints:pointsWithinThreshold]]];
        
        NSNumber* maxFrom = [[self distanceFrom:lastLoc forArray:pointsWithinThreshold] valueForKeyPath:@"@max.self"];
        NSNumber* maxBetween = [[self distanceBetweenPoints:pointsWithinThreshold] valueForKeyPath:@"@max.self"];
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"maxFrom = %@, maxBetween = %@",
                                                   maxFrom, maxBetween]];
        
        // One option might be to compare against the geofence radius instead of the filterDistance, since it
        // represents our "loitering radius". The problem, though, is that if we look at too few points, then
        // our updates will always be less than the geofence radius (e.g. with a filter distance = 5m, and 5
        // points, we expect a max distance of 25m). So we first calculate the number of points that we expect
        // to get to the geofence radius, assuming a straight line. With a filter distance = 5m and a
        // geofence radius = 100m, this would be 20 points. Then, we compare the distance between the first
        // and last points. But what if the points are not in a straight line (e.g. they could be in a curve).
        if ([maxFrom doubleValue] < [ConfigManager instance].geofence_radius) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"max distance in the past %d minutes is %@, returning YES", tripEndThresholdMins, maxFrom] showUI:TRUE];
            return YES;
        }
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"max distance in the past %d minutes is %@, returning NO", tripEndThresholdMins, maxFrom] showUI:TRUE];
        return NO;
    }
}

// TODO: Refactor this to be common with the TripSection loading code when we merge this into e-mission
+ (NSMutableDictionary*)loadFromJSONData:(NSData*)jsonData {
    NSError *error;
    NSMutableDictionary *jsonDict = [NSJSONSerialization JSONObjectWithData:jsonData
                                                                    options:NSJSONReadingMutableContainers
                                                                      error: &error];
    if (jsonDict == nil) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"error %@ while parsing json object %@", error, jsonData] showUI:FALSE];
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
    
    NSLog(@"data has %lu bytes, str has size %lu", bytesToSend.length, (unsigned long)strToSend.length);
    return strToSend;
}

+ (void) saveBatteryAndSimulateUser
{
    // TODO: Figure out whether this should be here or in the server sync code or in the trip machine code
    if ([UIDevice currentDevice].isBatteryMonitoringEnabled == NO) {
        [UIDevice currentDevice].batteryMonitoringEnabled = YES;
    }
    Battery* batteryInfo = [Battery new];
    batteryInfo.battery_level_ratio = [UIDevice currentDevice].batteryLevel;
    batteryInfo.battery_status = [UIDevice currentDevice].batteryState;
    batteryInfo.ts = [BuiltinUserCache getCurrentTimeSecs];
    [[BuiltinUserCache database] putMessage:@"key.usercache.battery" value:batteryInfo];
    if ([ConfigManager instance].simulate_user_interaction == YES) {
        UILocalNotification *localNotif = [[UILocalNotification alloc] init];
        if (localNotif) {
            localNotif.alertBody = [NSString stringWithFormat:@"Battery level = %@", @(batteryInfo.battery_level_ratio * 100)];
            localNotif.soundName = UILocalNotificationDefaultSoundName;
            [[UIApplication sharedApplication] presentLocalNotificationNow:localNotif];
        }
    }
}

#if FALSE

+ (void) pushAndClearData:(void (^)(BOOL))completionHandler {
    /*
     * In iOS, we can only sign up for activity updates when the app is in the foreground
     * (from https://developer.apple.com/library/ios/documentation/CoreMotion/Reference/CMMotionActivityManager_class/index.html#//apple_ref/occ/instm/CMMotionActivityManager/startActivityUpdatesToQueue:withHandler:)
     * "The handler block is executed on a best effort basis and updates are not delivered while your app is suspended. If updates arrived while your app was suspended, the last update is delivered to your app when it resumes execution."
     * However, apple automatically stores the activities, and they can be retrieved in a batch.
     * https://developer.apple.com/library/ios/documentation/CoreMotion/Reference/CMMotionActivityManager_class/index.html#//apple_ref/occ/instm/CMMotionActivityManager/queryActivityStartingFromDate:toDate:toQueue:withHandler:
     * "A delay of up to several minutes in reported activities is expected."
     *
     * Since we now detect trip end only after the user has been stationary for a while, this should be fine.
     * We need to test this more carefully when we switch to the visit-based tracking.
     */
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"pushAndClearData called"] showUI:TRUE];
    NSArray* locEntriesToPush = [[BuiltinUserCache database] syncPhoneToServer];
    if (locEntriesToPush.count == 0) {
        NSLog(@"No location data to send, returning early");
        completionHandler(false);
        return;
    }
    TimeQuery* tq = [BuiltinUserCache getTimeQuery:locEntriesToPush];
    
    if ([CMMotionActivityManager isActivityAvailable] == YES) {
        CMMotionActivityManager* activityMgr = [[CMMotionActivityManager alloc] init];
        NSOperationQueue* mq = [NSOperationQueue mainQueue];
        [activityMgr queryActivityStartingFromDate:tq.startDate toDate:tq.endDate toQueue:mq withHandler:^(NSArray *activities, NSError *error) {
            if (error == nil) {
                /*
                 * This conversion allows us to unit test this code, since we cannot create valid CMMotionActivity
                 * segments. We can create a CMMotionActivity, but we cannot set any of its properties.
                 */
                NSArray* motionEntries = [self convertToEntries:activities locationEntries:locEntriesToPush];
                NSArray* combinedArray = [locEntriesToPush arrayByAddingObjectsFromArray:motionEntries];
                [self pushAndClearCombinedData:combinedArray timeQuery:tq completionHandler:completionHandler];
            } else {
                [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                           @"Got error %@ while querying activity from %@ to %@",
                                                           error, tq.startDate, tq.endDate]];
                NSLog(@"Got error %@ while querying activity from %@ to %@", error, tq.startDate, tq.endDate);
                completionHandler(false);
            }
        }];
    } else {
        NSLog(@"Activity recognition unavailable, skipping segmentation");
        [self pushAndClearCombinedData:locEntriesToPush timeQuery:tq completionHandler:completionHandler];
    }
}

/*
 * THIS IS BROKEN wrt timezones. If we changed timezones during the course of a trip, this would make all activities
 * be in the last timezone. Need to find the timezones from the location entries and pass it in here instead.
 */

+ (NSArray*) convertToEntries:(NSArray*)activities locationEntries:(NSArray*)locEntries {
    /*
     * Iterate over the location entries and make a map of time range -> timezone that we can use to set the
     * time zones on the activity objects.
     */
    NSMutableArray* timezoneChanges = [NSMutableArray new];
    NSString* prevTimezone = NULL;
    
    for (int i=0; i < locEntries.count; i++) {
        NSDictionary* currEntry = locEntries[i];
        NSString* currTimezone = [BuiltinUserCache getTimezone:currEntry];
        if (![currTimezone isEqual:prevTimezone]) {
            [timezoneChanges addObject:currEntry];
        }
    }
    
    assert(timezoneChanges.count > 0);
    
    NSEnumerator* timezoneChangesEnum = timezoneChanges.objectEnumerator;
    NSDictionary* currTimezoneChange = [timezoneChangesEnum nextObject];
    NSDictionary* nextTimezoneChange = [timezoneChangesEnum nextObject];
    assert(currTimezoneChange != nil);
    
    NSMutableArray* entryArray = [NSMutableArray new];
    for (int i=0; i < activities.count; i++) {
        CMMotionActivity* activity = activities[i];
        MotionActivity* activityWrapper = [[MotionActivity alloc] initWithCMMotionActivity:activity];
        
        // if startDate > next timezone change.date, then we want to move to the next entry
        if (nextTimezoneChange != nil &&
            [activity.startDate compare:[BuiltinUserCache getWriteTs:nextTimezoneChange]] == NSOrderedDescending) {
            currTimezoneChange = nextTimezoneChange;
            nextTimezoneChange = [timezoneChangesEnum nextObject];
        }
        [entryArray addObject:[[BuiltinUserCache database] createSensorData:@"key.usercache.activity" write_ts:activity.startDate timezone:[BuiltinUserCache getTimezone:currTimezoneChange] data:activityWrapper]];
    }
    return entryArray;
}

+ (void) pushAndClearCombinedData:(NSArray*)entriesToPush timeQuery:(TimeQuery*)tq completionHandler:(void (^)(BOOL))completionHandler {
    if (entriesToPush.count == 0) {
        NSLog(@"No data to send, returning early");
    } else {
        [CommunicationHelper phone_to_server:entriesToPush
                             completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
                                 [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                                            @"successfully pushed %ld entries to the server",
                                                                            (unsigned long)entriesToPush.count]
                                                                    showUI:TRUE];
                                 // Only delete trips after they have been successfully pushed
                                 if (error == nil) {
                                     [[BuiltinUserCache database] clearEntries:tq];
                                 }
                                 NSLog(@"Returning from silent push");
                                 completionHandler(TRUE);
                             }];
    }
}

+ (void) deleteAllEntries {
    [[BuiltinUserCache database] clear];
}
#endif

@end
