//
//  GeofenceActions.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 11/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

typedef void(^ValidLocationCallback)(CLLocation* locationToUse);

@interface GeofenceActions : NSObject <CLLocationManagerDelegate>

- (void)getLocationForGeofence:(CLLocationManager *)manager
                 withCallback:(ValidLocationCallback)resultCallback;

- (void)locationManager:(CLLocationManager *)manager
     didUpdateLocations:(NSArray *)locations;

- (void)locationManager:(CLLocationManager *)manager
       didFailWithError:(NSError *)error;
@end
