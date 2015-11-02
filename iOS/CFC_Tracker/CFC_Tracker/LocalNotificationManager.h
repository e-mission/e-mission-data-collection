//
//  LocalNotificationManager.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 2/2/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface LocalNotificationManager : NSObject

+(void)clearNotifications;
+(void)addNotification:(NSString*) notificationMessage;
+(void)addNotification:(NSString*) notificationMessage showUI:(BOOL)showUI;

@end
