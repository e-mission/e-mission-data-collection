//
//  LocationTrackingConfig.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 11/6/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface LocationTrackingConfig : NSObject

+ (instancetype) instance;

@property (readonly) BOOL isDutyCycling;
@property (readonly) double accuracy;
@property (readonly) int filterDistance;
@property (readonly) int geofenceRadius;
@property (readonly) int tripEndStationaryMins;
@property (readonly) BOOL useVisitNotificationsForGeofence;
// If this is false, we will use background push for sync
@property (readonly) BOOL useRemotePushForSync;

@end
