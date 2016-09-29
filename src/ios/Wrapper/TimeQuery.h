//
//  TimeQuery.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 10/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface TimeQuery : NSObject

@property NSString* key;
@property double startTs;
@property double endTs;
@property NSDate* startDate;
@property NSDate* endDate;

@end
