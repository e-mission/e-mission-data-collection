//
//  AppDelegate.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 1/30/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "TripDiaryStateMachine.h"

@interface AppDelegate : UIResponder <UIApplicationDelegate>

@property (strong, nonatomic) UIWindow *window;
@property (strong, nonatomic) TripDiaryStateMachine *tripDiaryStateMachine;

+ (void) getAndPushTrips;

@end

