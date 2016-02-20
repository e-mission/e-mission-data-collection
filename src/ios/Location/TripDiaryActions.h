//
//  TripDiaryActions.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/7/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreMotion/CoreMotion.h>
#import <CoreLocation/CoreLocation.h>
#import "TripDiaryStateMachine.h"
#import "GeofenceActions.h"
#import "BEMServerSyncCommunicationHelper.h"

#define kCurrGeofenceID @"STATIONARY_GEOFENCE_LOCATION"

@interface TripDiaryActions : NSObject

+ (void) resetFSM:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr;

+ (void) oneTimeInitTracking:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr;

+ (void) startTracking:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr;
+ (void)startTrackingLocation:(CLLocationManager*) manager;

+ (void) stopTracking:(NSString*) transition withLocationMgr:(CLLocationManager*)locMgr;
+ (void) stopTrackingLocation:(CLLocationManager*) manager;

+ (void)createGeofenceHere:(CLLocationManager *)manager withGeofenceLocator:(GeofenceActions *)locator
                   inState:(TripDiaryStates)currState;
+ (void)createGeofenceAtLocation:(CLLocationManager *)manager atLocation:(CLLocation*)currLoc;

+ (void)deleteGeofence:(CLLocationManager*)manager;

+ (CLCircularRegion*)getGeofence:(CLLocationManager*)manager;

+ (BOOL)hasTripEnded;

+ (BFTask*) pushTripToServer;

@end
