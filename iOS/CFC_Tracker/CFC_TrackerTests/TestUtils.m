//
//  TestUtils.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/26/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "TestUtils.h"

@implementation TestUtils

+ (CLLocation*)getLocation:(NSArray*) locParams {
    CLLocationCoordinate2D coords = CLLocationCoordinate2DMake(((NSNumber*)locParams[0]).doubleValue,
                                                               ((NSNumber*)locParams[1]).doubleValue);
    return [[CLLocation alloc] initWithCoordinate:coords altitude:10 horizontalAccuracy:50 verticalAccuracy:50 course:90 speed:15 timestamp:[NSDate dateWithTimeIntervalSince1970:((NSNumber*)locParams[2]).doubleValue]];
}

+ (NSString*)dateToString:(NSDate*)date {
    NSDateFormatter *dateFormat = [[NSDateFormatter alloc] init];
    [dateFormat setDateFormat:@"yyyyMMdd'T'HHmmssZ"];
    return [dateFormat stringFromDate:date];
}

@end
