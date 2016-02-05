//
//  SimpleLocation.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 10/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CLLocation.h>

@interface SimpleLocation : NSObject

- (instancetype)initWithCLLocation:(CLLocation *)loc;

@property double latitude;
@property double longitude;
@property double altitude;

@property double ts;
@property NSString* fmt_time;

@property double sensed_speed;
@property double accuracy;
@property double vaccuracy;
@property double bearing;

@property NSInteger floor;
@property NSString* filter;

- (CLLocationDistance) distanceFromLocation:(SimpleLocation*)otherLoc;

@end
