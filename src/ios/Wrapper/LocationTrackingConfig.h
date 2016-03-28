//
//  LocationTrackingConfig.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 11/6/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface LocationTrackingConfig : NSObject

@property BOOL is_duty_cycling;
@property BOOL simulate_user_interaction;
@property double accuracy;
@property double accuracy_threshold;
@property double filter_distance;
@property double filter_time;  // unused
@property int geofence_radius;
@property int trip_end_stationary_mins;
@property BOOL ios_use_visit_notifications_for_detection;
// If this is false, we will use background push for sync
@property BOOL ios_use_remote_push_for_sync;
@property int android_geofence_responsiveness; // unused

@end
