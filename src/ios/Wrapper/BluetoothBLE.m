//
//  Transition.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 10/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "BluetoothBLE.h"
#import "DataUtils.h"

@implementation BluetoothBLE

-(id) initWithCLBeacon:(CLBeacon*) beacon andEventType:(NSString*) eventType {
    self = [super init];
    self.uuid = beacon.UUID;
    self.major = beacon.major.integerValue;
    self.minor = beacon.minor.integerValue;
    self.proximity = [BluetoothBLE proximityToString:beacon.proximity];
    self.accuracy = beacon.accuracy;
    self.rssi = beacon.rssi;
    
    self.eventType = eventType;
    self.ts = [DataUtils dateToTs:beacon.timestamp];
    return self;
}

-(id) initFakeWithEventType:(NSString*) eventType {
    self = [super init];
    self.uuid = [NSUUID UUID].UUIDString;
    self.major = 4538;
    self.minor = 1256;
    self.proximity = [BluetoothBLE proximityToString:CLProximityNear];
    self.accuracy = 100;
    self.rssi = 10;
    
    self.eventType = eventType;
    self.ts = [DataUtils dateToTs:[NSDate now]];
    return self;
}

+ (NSString*) proximityToString:(CLProximity) proximityObj {
    if (proximityObj == CLProximityImmediate) { return @"ProximityImmediate"; };
    if (proximityObj == CLProximityNear)  { return @"ProximityNear"; };
    if (proximityObj == CLProximityFar) { return @"ProximityFar"; };
    return @"ProximityUnknown";
}


@end
