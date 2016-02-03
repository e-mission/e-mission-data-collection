//
//  MotionActivity.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 11/2/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreMotion/CoreMotion.h>

@interface MotionActivity : NSObject

- (instancetype)initWithCMMotionActivity:(CMMotionActivity *)activity;

@property BOOL stationary;
@property BOOL walking;
@property BOOL running;
@property BOOL automotive;
@property BOOL cycling;
@property BOOL unknown;

@property double ts;
@property NSString* confidence;

@end
