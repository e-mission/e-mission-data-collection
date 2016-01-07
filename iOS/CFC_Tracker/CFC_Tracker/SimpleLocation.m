//
//  SimpleLocation.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 10/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "SimpleLocation.h"
#import "DataUtils.h"
#import <CoreLocation/CLLocation.h>

@implementation SimpleLocation
-(id) initWithCLLocation:(CLLocation*) loc {
    self = [super init];
    self.latitude = loc.coordinate.latitude;
    self.longitude = loc.coordinate.longitude;
    self.altitude = loc.altitude;
    
    self.ts = [DataUtils dateToTs:loc.timestamp];
    self.fmt_time = [DataUtils dateToString:loc.timestamp];
    self.sensed_speed = loc.speed;
    self.accuracy = loc.horizontalAccuracy;
    self.vaccuracy = loc.verticalAccuracy;
    self.floor = loc.floor.level;
    self.bearing = loc.course;
    self.filter = @"distance";
    
    return self;
}

- (CLLocationDistance) distanceFromLocation:(SimpleLocation*)otherSimpleLoc {
    CLLocation* currLoc = [[CLLocation alloc] initWithLatitude:self.latitude longitude:self.longitude];
    CLLocation* otherLoc = [[CLLocation alloc] initWithLatitude:otherSimpleLoc.latitude longitude:otherSimpleLoc.longitude];
    return [currLoc distanceFromLocation:otherLoc];
}
@end
