//
//  Timer.m
//  emission
//
//  Created by Kalyanaraman Shankari on 10/31/16.
//
//

#import "Timer.h"

@interface Timer() {
    NSDate* _startDate;
}
@end

@implementation Timer

- (instancetype) init {
    self = [super init];
    _startDate = [NSDate date];
    return self;
}

- (NSTimeInterval) elapsed_secs {
    return [[NSDate date] timeIntervalSinceDate:_startDate];
}

@end
