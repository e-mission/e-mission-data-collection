//
//  UserCacheTest.m
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 10/29/15.
//  Copyright © 2015 Kalyanaraman Shankari. All rights reserved.
//

#import <XCTest/XCTest.h>
#import "TestUtils.h"

#import "BuiltinUserCache.h"
#import "SimpleLocation.h"
#import "Transition.h"
#import "DataUtils.h"
#import "Metadata.h"
#import "TripDiaryStateMachine.h"

#define METADATA_TAG @"metadata"
#define DATA_TAG @"data"

@interface UserCacheTest : XCTestCase

@end

@implementation UserCacheTest

- (void)setUp {
    [super setUp];
    // Put setup code here. This method is called before the invocation of each test method in the class.
    [[BuiltinUserCache database] clear];
}

- (void)tearDown {
    // Put teardown code here. This method is called after the invocation of each test method in the class.
    [super tearDown];
}

- (void)testGetPutSensorData {
    CLLocation* testLoc = [TestUtils getLocation:@[@37, @-122, @1]];
    SimpleLocation* wrapperLoc = [[SimpleLocation alloc] initWithCLLocation:testLoc];
    // Put the sensor data
    [[BuiltinUserCache database] putSensorData:@"key.usercache.location" value:wrapperLoc];
    
    // Now read it back
    NSArray* readVal = [[BuiltinUserCache database] getLastSensorData:@"key.usercache.location" nEntries:3 wrapperClass:[SimpleLocation class]];
    XCTAssert(readVal.count == 1);
    XCTAssert(((SimpleLocation*)readVal[0]).latitude == 37);
}

- (void)testGetPutMessage {
    Transition* wrapperTrans = [Transition new];
    wrapperTrans.currState = @"testCurrState";
    wrapperTrans.transition = @"testTrans";
    // Put the message
    [[BuiltinUserCache database] putMessage:@"key.usercache.transition" value:wrapperTrans];
    
    // Now read it back
    NSArray* readVal = [[BuiltinUserCache database] getLastMessage:@"key.usercache.transition" nEntries:3 wrapperClass:[Transition class]];
    XCTAssert(readVal.count == 1);
    XCTAssert([((Transition*)readVal[0]).currState isEqual:@"testCurrState"]);
}

- (void)testSyncPhoneToServer {
    [self testGetPutSensorData];
    [self testGetPutMessage];
    NSArray* dataToSend = [[BuiltinUserCache database] syncPhoneToServer];
    NSLog(@"dataToSend = %@", dataToSend);
    NSDictionary* locEntry = dataToSend[0];
    NSDictionary* locEntryMetadata = [locEntry objectForKey:METADATA_TAG];
    NSDictionary* locEntryData = [locEntry objectForKey:DATA_TAG];
    XCTAssert([[locEntryMetadata objectForKey:@"key"] isEqual:@"background/location"]);
    XCTAssert([[locEntryData objectForKey:@"latitude"] isEqual:@37]);
}

- (void)testSearchSerializedString {
    double beforeTs = [DataUtils dateToTs:[NSDate date]];
    Transition* ts = [Transition new];
    ts.currState = [TripDiaryStateMachine getStateName:kOngoingTripState];
    ts.transition = CFCTransitionTripEnded;
    [[BuiltinUserCache database] putMessage:@"key.usercache.transition" value:ts];
    
    double lastTripEnd = [[BuiltinUserCache database] getTsOfLastTransition];
    double currTs = [DataUtils dateToTs:[NSDate date]];
    NSLog(@"beforeTs = %f, lastTripEnd = %f, currTs = %f", beforeTs, lastTripEnd, currTs);
    XCTAssert(lastTripEnd >= beforeTs && lastTripEnd <= currTs);
}

- (void)testExample {
    // This is an example of a functional test case.
    // Use XCTAssert and related functions to verify your tests produce the correct results.
}

- (void)testPerformanceExample {
    // This is an example of a performance test case.
    [self measureBlock:^{
        // Put the code you want to measure the time of here.
    }];
}

@end
