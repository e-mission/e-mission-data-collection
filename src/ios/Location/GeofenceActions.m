//
//  GeofenceActions.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 11/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "GeofenceActions.h"
#import "TripDiaryActions.h"
#import "LocalNotificationManager.h"

@interface GeofenceActions() {
    CLLocationManager* _locMgr;
    ValidLocationCallback _currCallback;
}
@end

@implementation GeofenceActions

-(id)init {
    /*
     * Now start tracking with a new location manager. This new location manager has a new delegate, so it will
     * callback here instead of to the main function. This allows us to greatly simplify the code for the location
     * tracking in the main delegate, since we will only get those callbacks when we are doing the real tracking.
     */
    _locMgr = [CLLocationManager new];
    _locMgr.pausesLocationUpdatesAutomatically = FALSE;
    _locMgr.allowsBackgroundLocationUpdates = TRUE;
    // I am not sure that we really need this, but basically, when we are creating the geofence,
    // we want a good quality point quickly. If the user configured settings were for medium accuracy
    // and a large distance filter, then we might get a poor accuracy point first, and then get no updates
    // for a while, by which time the remote push handler might have been killed.
    // Using a high accuracy, no filter call gives us a better chance that we will get a high accuracy update
    // quickly. Note that I have never seen us get a poor accuracy point here, but better to be safe than sorry.
    _locMgr.desiredAccuracy = kCLLocationAccuracyBest;
    _locMgr.distanceFilter = kCLDistanceFilterNone;
    _locMgr.delegate = self;
    return [super init];
}


-(void)setCallback:(ValidLocationCallback)resultCallback {
    _currCallback = resultCallback;
}

- (void)getLocationForGeofence:(CLLocationManager *)manager
                 withCallback:(ValidLocationCallback)resultCallback {
    // We will stop tracking after the geofence has been created
    // This will allow us to get multiple locations if necessary so that we can get an accurate point
    // to create the geofence, and we won't run into
    // https://github.com/e-mission/e-mission-data-collection/issues/66
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Getting location for geofence creation"] showUI:TRUE];
    [self setCallback:resultCallback];
    [_locMgr startUpdatingLocation];
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Started high accuracy no filter tracking to get geofence location"] showUI:TRUE];
}

- (void)dealloc
{
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Deallocating GeofenceActions delegate"]showUI:TRUE];
}

/*
 * We should only receive updates for the current loc manager
 */

- (void)locationManager:(CLLocationManager *)manager
     didUpdateLocations:(NSArray *)locations {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:@"While trying to create geofence location, recieved %ld location updates ", (unsigned long)locations.count] showUI:TRUE];
    
    if (locations.count == 0) {
        NSLog(@"locations.count = %lu in didUpdateLocations, early return", (unsigned long)locations.count);
        return;
    }
    
    NSAssert(locations.count > 0, @"locations.count = %lu in didUpdateLocations after early return check!", (unsigned long)locations.count);
    CLLocation *lastLocation = locations[locations.count - 1];
    NSLog(@"lastLocation is %f, %f", lastLocation.coordinate.longitude, lastLocation.coordinate.latitude);
    

    // Find the last location
    if (lastLocation.horizontalAccuracy > 200) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"While trying to create geofence, found invalid accuracy %@, skipping", @(lastLocation.horizontalAccuracy)] showUI:TRUE];
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Creating geofence with accuracy %@ at location %@", @(lastLocation.horizontalAccuracy), lastLocation] showUI:TRUE];
        // We don't have access to this manager anywhere else, so let's stop tracking right here.
        // Note that the "manager" here is the manager that is passed into this delegate function,
        // which is the locally created locMgr, and not the manager that was passed in to this function
        // to begin with.
        [_locMgr stopUpdatingLocation];
        _currCallback(lastLocation);
    }
}

- (void)locationManager:(CLLocationManager *)manager
       didFailWithError:(NSError *)error {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"While creating geofence, location tracking failed with error %@", error]];
}

@end
