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

- (instancetype)initWithCLBeacon:(CLBeacon *)beacon andEventType:(NSString *) eventType;
- (instancetype)initFakeWithEventType:(NSString *)eventType;

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
