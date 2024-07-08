//
//  Transition.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 10/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

@interface BluetoothBLE : NSObject

- (instancetype)initWithCLBeaconRegion:(CLBeaconRegion*) beaconRegion andEventType:(NSString*) eventType;
- (instancetype)initWithCLBeacon:(CLBeacon *)beacon;
- (instancetype)initFake:(NSString *)eventType anduuid:(NSString*) uuid andmajor:(int) major andminor:(int) minor;

// fields from CLBeacon, modified to be easy to serialize and restore
@property NSString* uuid;
@property NSInteger major;
@property NSInteger minor;
@property NSString* proximity;
@property CLLocationAccuracy accuracy;
@property NSInteger rssi;
@property NSString* eventType;
@property double ts;

+ (NSString*) proximityToString:(CLProximity) proximityObj;
@end
