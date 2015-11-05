//
//  DataUtilsTests.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/26/15.
//  Copyright (c) 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>
#import "TestUtils.h"

#import "DataUtils.h"
#import "BuiltinUserCache.h"
#import "SimpleLocation.h"
#import "Transition.h"
#import "TripDiaryStateMachine.h"

@interface DataUtilsTests : XCTestCase

@end

@implementation DataUtilsTests

- (void)setUp {
    [super setUp];
    // Put setup code here. This method is called before the invocation of each test method in the class.
    [[BuiltinUserCache database] clear];
}

- (void)tearDown {
    // Put teardown code here. This method is called after the invocation of each test method in the class.
    [super tearDown];
}

- (CLLocation*)makeLoc:(NSArray*) locParams {
    return [TestUtils getLocation:locParams];
}

- (NSArray*) getFourPoints {
    NSMutableArray* fourPoints = [[NSMutableArray alloc] init];
    [fourPoints addObject:[self makeLoc:@[@37, @-122, @1]]];
    [fourPoints addObject:[self makeLoc:@[@37, @-121, @2]]];
    [fourPoints addObject:[self makeLoc:@[@37, @-120, @3]]];
    [fourPoints addObject:[self makeLoc:@[@37, @-120, @4]]];
    return fourPoints;
}

- (void) testLocToJSONWrapper {
    CLLocation* testLoc = [self makeLoc:@[@37, @-122, @1]];
    SimpleLocation* wrapperLoc = [[SimpleLocation alloc] initWithCLLocation:testLoc];
    // Let's try to serialize it directly
    NSLog(@"location object is %@, wrapperLoc = %@, valid = %d", testLoc, wrapperLoc,
          [NSJSONSerialization isValidJSONObject:wrapperLoc]);
    NSString* serializedString = [DataUtils wrapperToString:wrapperLoc];
    NSLog(@"location object is %@, json is %@", testLoc, serializedString);
}

-(void) testDynamicClassCreation {
    Class cls = [SimpleLocation class];
    NSLog(@"Simple location class is %@", cls);
    XCTAssert([SimpleLocation class] != NULL);
    NSLog(@"Created object = %@", [cls new]);
    XCTAssert([cls new] != NULL);
}

-(void) testJSONToLoc {
    CLLocation* testLoc = [self makeLoc:@[@37, @-122, @1]];
    SimpleLocation* wrapperLoc = [[SimpleLocation alloc] initWithCLLocation:testLoc];
    NSString* serializedString = [DataUtils wrapperToString:wrapperLoc];
    SimpleLocation* readWrapperLoc = (SimpleLocation*)[DataUtils stringToWrapper:serializedString wrapperClass:[SimpleLocation class]];
    XCTAssert(readWrapperLoc.latitude == wrapperLoc.latitude);
    XCTAssert(readWrapperLoc.ts == wrapperLoc.ts);
}

- (void)testDateToString {
    NSDate* testDate = [NSDate dateWithTimeIntervalSinceReferenceDate:0];
    NSString* testDateString = [DataUtils dateToString:testDate];
    XCTAssert([testDateString isEqualToString:@"2000-12-31T16:00:00-0800"]);
}


- (CLLocation*) dateHoursAgo:(int)hours {
    NSDate* hoursAgo = [NSDate dateWithTimeIntervalSinceNow:(-hours*60*60)];
    NSTimeInterval timeAgo = hoursAgo.timeIntervalSince1970;
    NSNumber* numberTimeAgo = [NSNumber numberWithDouble:timeAgo];
    return [self makeLoc:@[@(37+hours), @(-122-hours), numberTimeAgo]];
}


- (void) testEndTripWhileOngoing {
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
    NSArray* fourPoints = [self getFourPoints];
    for (int i = 0; i < fourPoints.count; i++) {
        [[BuiltinUserCache database] putSensorData:@"key.usercache.location" value:[[SimpleLocation alloc]initWithCLLocation:fourPoints[i]]];
    }
    XCTAssertEqual([DataUtils getLastPoints:5].count, 4);
    [DataUtils pushAndClearData:^(BOOL status) {
        XCTAssert(status == TRUE);
    }];
    XCTAssertEqual([DataUtils getLastPoints:5].count, 4);
}

- (void) testEndTripWhileEnded {
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
    NSArray* fourPoints = [self getFourPoints];
    for (int i = 0; i < fourPoints.count; i++) {
        [[BuiltinUserCache database] putSensorData:@"key.usercache.location" value:[[SimpleLocation alloc]initWithCLLocation:fourPoints[i]]];
    }
    Transition* ts = [Transition new];
    ts.currState = [TripDiaryStateMachine getStateName:kOngoingTripState];
    ts.transition = CFCTransitionTripEnded;
    [[BuiltinUserCache database] putSensorData:@"key.usercache.transition" value:ts];
    
    XCTAssertEqual([DataUtils getLastPoints:5].count, 4);
    [DataUtils pushAndClearData:^(BOOL status) {
        XCTAssert(status == TRUE);
    }];
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
}


@end
