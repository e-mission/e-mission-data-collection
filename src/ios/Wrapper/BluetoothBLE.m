//
//  Transition.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 10/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "BluetoothBLE.h"
#import "DataUtils.h"
#import <stdlib.h>

@implementation BluetoothBLE

-(id) initWithCLBeaconRegion:(CLBeaconRegion*) beaconRegion andEventType:(NSString*) eventType {
    assert([eventType isEqualToString:@"REGION_ENTER"] || [eventType isEqualToString:@"REGION_EXIT"]);
    self = [super init];
    self.uuid = beaconRegion.UUID.UUIDString;
    self.major = beaconRegion.major.integerValue;
    self.minor = beaconRegion.minor.integerValue;
    self.proximity = [BluetoothBLE proximityToString:CLProximityUnknown];
    self.accuracy = -1;
    self.rssi = -1;
    
    self.eventType = eventType;
    self.ts = [DataUtils dateToTs:[NSDate now]];
    return self;
}

-(id) initWithCLBeacon:(CLBeacon*) beacon {
    self = [super init];
    self.uuid = beacon.UUID.UUIDString;
    self.major = beacon.major.integerValue;
    self.minor = beacon.minor.integerValue;
    self.proximity = [BluetoothBLE proximityToString:beacon.proximity];
    self.accuracy = beacon.accuracy;
    self.rssi = beacon.rssi;
    
    self.eventType = @"RANGE_UPDATE"; // we only get CLBeacon objects on range updates
    self.ts = [DataUtils dateToTs:beacon.timestamp];
    return self;
}

-(id) initFake:(NSString*) eventType anduuid:(NSString*) uuid andmajor:(int) major andminor:(int)minor {
    self = [super init];
    self.uuid = uuid;
    self.eventType = eventType;
    self.ts = [DataUtils dateToTs:[NSDate now]];
   
    if ([eventType isEqualToString:@"RANGE_UPDATE"]) {
        self.major = major;
        self.minor = minor;
        self.proximity = [BluetoothBLE proximityToString:CLProximityNear];
        self.accuracy = arc4random_uniform(100);
        self.rssi = arc4random_uniform(10);
    }
    
    return self;
}

+ (NSString*) proximityToString:(CLProximity) proximityObj {
    if (proximityObj == CLProximityImmediate) { return @"ProximityImmediate"; };
    if (proximityObj == CLProximityNear)  { return @"ProximityNear"; };
    if (proximityObj == CLProximityFar) { return @"ProximityFar"; };
    return @"ProximityUnknown";
}


@end
