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
#import "BEMBuiltinUserCache.h"

@interface GeofenceActions() {
    CLLocationManager* _locMgr;
    ValidLocationCallback _currCallback;
}
@end

static NSString* const INVALID_POINT_KEY = @"INVALID_POINT";
static NSString* const ERROR_KEY = @"ERROR_KEY";
static int const GIVE_UP_AFTER_SECS = 30 * 60; // 30 mins

@implementation GeofenceActions

-(id)init {
    /*
     * Now start tracking with a new location manager. This new location manager has a new delegate, so it will
     * callback here instead of to the main function. This allows us to greatly simplify the code for the location
     * tracking in the main delegate, since we will only get those callbacks when we are doing the real tracking.
     */
    _locMgr = [CLLocationManager new];
    _locMgr.pausesLocationUpdatesAutomatically = FALSE;
    if([_locMgr respondsToSelector:@selector(setAllowsBackgroundLocationUpdates:)]) {
        _locMgr.allowsBackgroundLocationUpdates = TRUE;
    }
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
        [[BuiltinUserCache database] putLocalStorage:INVALID_POINT_KEY
            jsonValue:@{ @"type" : @"Point",
            @"coordinates": @[@(lastLocation.coordinate.longitude), @(lastLocation.coordinate.latitude)],
            @"accuracy": @(lastLocation.horizontalAccuracy)
        }];
        [NSTimer scheduledTimerWithTimeInterval:GIVE_UP_AFTER_SECS
                                                target:self
                                                selector:@selector(checkGeofenceCreationError:)
                                                userInfo:NULL
                                                repeats:NO];
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Creating geofence with accuracy %@ at location %@", @(lastLocation.horizontalAccuracy), lastLocation] showUI:TRUE];
        // We don't have access to this manager anywhere else, so let's stop tracking right here.
        // Note that the "manager" here is the manager that is passed into this delegate function,
        // which is the locally created locMgr, and not the manager that was passed in to this function
        // to begin with.
        [_locMgr stopUpdatingLocation];
        [[BuiltinUserCache database] removeLocalStorage:ERROR_KEY];
        [[BuiltinUserCache database] removeLocalStorage:INVALID_POINT_KEY];
        _currCallback(lastLocation);
    }
}

- (void)locationManager:(CLLocationManager *)manager
       didFailWithError:(NSError *)error {
    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"While creating geofence, location tracking failed with error %@", error]];
    [[BuiltinUserCache database] putLocalStorage:ERROR_KEY
                                       jsonValue:@{ @"error" : @(error.code) }
    ];
    [NSTimer scheduledTimerWithTimeInterval:GIVE_UP_AFTER_SECS
                                            target:self
                                            selector:@selector(checkGeofenceCreationError:)
                                            userInfo:NULL
                                            repeats:NO];
}

- (void)checkGeofenceCreationError:(NSTimer*)theTimer
{
    NSMutableDictionary* existingInvalidPoint = 
        [[BuiltinUserCache database] getLocalStorage:INVALID_POINT_KEY withMetadata:NO];
    NSMutableDictionary* existingErrors = 
        [[BuiltinUserCache database] getLocalStorage:ERROR_KEY withMetadata:NO];

    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                               @"invalid points = %@, existingErrors = %@", existingInvalidPoint, existingErrors]];

    NSString* errorDescription = NULL;

    if (existingInvalidPoint != NULL) {
        errorDescription = NSLocalizedStringFromTable(@"bad-loc-tracking-problem", @"DCLocalizable", nil);
    }
    if (existingErrors != NULL) {
        errorDescription = NSLocalizedStringFromTable(@"location-turned-off-problem", @"DCLocalizable", nil);
    }

    if (errorDescription != NULL) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Found error in geofence creation, generating user notification"]];
        NSDictionary* notifyOptions = @{@"id": @223562, // BADLOC on a phone keypad,
                                        @"title": errorDescription,
                                        @"autoclear": @TRUE,
                                        @"at": @([NSDate date].timeIntervalSince1970 + 60), // now + 60 secs
                                        @"data": @{@"redirectTo": @"root.main.control"}
                                        };
        [LocalNotificationManager showNotificationAfterSecs:errorDescription
                                               withUserInfo:notifyOptions secsLater:60];
        
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Found error in geofence creation, stopping updates and creating callback"]];
        [_locMgr stopUpdatingLocation];
        // Retain this local storage (remove the next two lines) if we want to
        // distinguish between errors and invalid points later.
        [[BuiltinUserCache database] removeLocalStorage:ERROR_KEY];
        [[BuiltinUserCache database] removeLocalStorage:INVALID_POINT_KEY];
        _currCallback(nil);
    }
}

@end
