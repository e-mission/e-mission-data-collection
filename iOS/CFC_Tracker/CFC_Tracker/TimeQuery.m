//
//  TimeQuery.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 10/27/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import "TimeQuery.h"
#import "DataUtils.h"

@implementation TimeQuery

- (void)setStartDate:(NSDate *)startDate
{
    self.startTs = [DataUtils dateToTs:startDate];
}

- (NSDate *)startDate
{
    return [DataUtils dateFromTs:self.startTs];
}

- (void)setEndDate:(NSDate *)endDate
{
    self.endTs = [DataUtils dateToTs:endDate];
}

- (NSDate *)endDate
{
    return [DataUtils dateFromTs:self.endTs];
}

@end
