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
#import "BuiltinUserCache.h"
#import "SimpleLocation.h"
#import "TimeQuery.h"
#import "MotionActivity.h"
#import "CommunicationHelper.h"
#import "LocationTrackingConfig.h"

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
        SimpleLocation *middleLoc = ((SimpleLocation*)last3Points[1]);
        SimpleLocation *firstLoc = ((SimpleLocation*)last3Points.lastObject);
        
        NSLog(@"firstDate = %@, middleDate = %@, lastDate = %@", firstLoc.fmt_time, middleLoc.fmt_time, lastLoc.fmt_time);
        NSDate* lastDate = [DataUtils dateFromTs:lastLoc.ts];
        // We are using a distance filter, so if we have no inputs for time greater than the threshold, the trip is done
        if (fabs(lastDate.timeIntervalSinceNow) > tripEndThresholdMins * 60) {
            NSLog(@"interval to the last date = %f, returning YES", lastDate.timeIntervalSinceNow);
            return YES;
        } else {
        // Either the trip has not ended, or it is jumping back and forth between a valid and invalid point.
            if (([firstLoc distanceFromLocation:lastLoc] < [LocationTrackingConfig instance].filterDistance) &&
                ([firstLoc distanceFromLocation:middleLoc] < [LocationTrackingConfig instance].filterDistance)) {
                NSLog(@"distances between first loc and middle loc is %f, and between first loc and last loc is %f, returning YES", [firstLoc distanceFromLocation:middleLoc], [firstLoc distanceFromLocation:lastLoc]);
                return YES;
            }
            NSLog(@"interval to the last date = %f, returning NO", lastDate.timeIntervalSinceNow);
            return NO;
        }
    }
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
    
    NSLog(@"data has %lu bytes, str has size %lu", bytesToSend.length, (unsigned long)strToSend.length);
    return strToSend;
}

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
                                                                            (unsigned long)entriesToPush.count]];
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

@end
