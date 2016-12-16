//
//  Transition.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 10/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface Transition : NSObject

@property NSString* currState;
@property NSString* transition;
@property double ts;

@end
