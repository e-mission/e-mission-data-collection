//
//  LocalNotificationManager.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 2/2/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "LocalNotificationManager.h"
#import "OngoingTripsDatabase.h"
#import <UIKit/UIKit.h>

@implementation LocalNotificationManager

static int notificationCount = 0;

+(void)clearNotifications {
    notificationCount = 0;
    [[OngoingTripsDatabase database] clearTransitions];
}

+(void)addNotification:(NSString *)notificationMessage {
    NSLog(@"Generating local notification with message %@", notificationMessage);
    notificationCount++;
    [[OngoingTripsDatabase database] addTransition:notificationMessage];

    UILocalNotification *localNotif = [[UILocalNotification alloc] init];
    if (localNotif) {
        localNotif.alertBody = notificationMessage;
        localNotif.applicationIconBadgeNumber = notificationCount;
        [[UIApplication sharedApplication] presentLocalNotificationNow:localNotif];
    }
}

@end
