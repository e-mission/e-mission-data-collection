//
//  TestUtils.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/26/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

@interface TestUtils : NSObject

+ (CLLocation*)getLocation:(NSArray*) locParams;
+ (NSString*)dateToString:(NSDate*)date;

@end
