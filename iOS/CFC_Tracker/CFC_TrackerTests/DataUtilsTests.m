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
#import "StoredTripsDatabase.h"

@interface DataUtilsTests : XCTestCase

@end

@implementation DataUtilsTests

- (void)setUp {
    [super setUp];
    // Put setup code here. This method is called before the invocation of each test method in the class.
    [DataUtils clearOngoingDb];
    [DataUtils clearStoredDb];
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

- (void)testDateToString {
    NSDate* testDate = [NSDate dateWithTimeIntervalSinceReferenceDate:0];
    NSString* testDateString = [DataUtils dateToString:testDate];
    XCTAssert([testDateString isEqualToString:@"20001231T160000-0800"]);
}

- (void)testAddPoint {
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
    [DataUtils addPoint:[self makeLoc:@[@37, @-122, @1]]];
    XCTAssertEqual([DataUtils getLastPoints:5].count, 1);
}

- (void)testMultipleAddPoints {
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
    NSArray* fourPoints = [self getFourPoints];
    for (int i = 0; i < fourPoints.count; i++) {
        [DataUtils addPoint:fourPoints[i]];
    }
    XCTAssertEqual([DataUtils getLastPoints:5].count, 4);
}

- (void) testClearOngoingDb {
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
    [DataUtils addPoint:[self makeLoc:@[@37, @-122, @1]]];
    XCTAssertEqual([DataUtils getLastPoints:5].count, 1);
    [DataUtils clearOngoingDb];
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
}

- (void) testGetJSONPlace {
    CLLocation* loc = [self makeLoc:@[@37, @-122, @1]];
    NSDictionary* pls = [DataUtils getJSONPlace:loc];
    XCTAssertNotNil([pls objectForKey:@"place"], @"No object found for key place");
    XCTAssert([[pls objectForKey:@"type"] isEqualToString:@"place"], @"object for key 'type' != 'place'");
    XCTAssert([[pls objectForKey:@"startTime"] isEqualToString:[TestUtils dateToString:loc.timestamp]],
                   @"'startTime' is %@, expected %@", [pls objectForKey:@"startTime"], [TestUtils dateToString:loc.timestamp]);
    NSLog(@"loc = %@", loc);

    XCTAssert([[pls objectForKey:@"startTimeTs"] isEqualToNumber:[NSNumber numberWithDouble:loc.timestamp.timeIntervalSince1970]],
                   @"'startTimeTs' is %@, expected %@", [pls objectForKey:@"startTimeTs"],
                   [NSNumber numberWithDouble:loc.timestamp.timeIntervalSince1970]);

     
    
    XCTAssertNotNil([[pls objectForKey:@"place"] objectForKey:@"location"], @"No object found for key place.location");
    NSDictionary* locationDict = [(NSDictionary*)[pls objectForKey:@"place"] objectForKey:@"location"];
    XCTAssert([[locationDict objectForKey:@"lat"] isEqualToNumber:[NSNumber numberWithDouble:37]],
                    @"Incorrect latitude found for key place.location - found %@, expecting %d",
                        [locationDict objectForKey:@"lat"], 37);
    XCTAssert([[locationDict objectForKey:@"lon"] isEqualToNumber:[NSNumber numberWithDouble:-122]],
                   @"Incorrect longitude found for key place.location - found %@, expecting %d",
                        [locationDict objectForKey:@"lon"], -122);
}

- (void) testGetTrackPoint {
    CLLocation* loc = [self makeLoc:@[@37, @-122, @1]];
    NSDictionary* tp = [DataUtils getTrackPoint:loc];
    
    XCTAssert([[tp objectForKey:@"time"] isEqualToString:[TestUtils dateToString:loc.timestamp]]);
    XCTAssert([[tp objectForKey:@"lat"] isEqualToNumber:[NSNumber numberWithDouble:37]],
              @"Incorrect latitude found for key trackpoint.lat - found %@, expecting %d",
              [tp objectForKey:@"lat"], 37);
    XCTAssert([[tp objectForKey:@"lon"] isEqualToNumber:[NSNumber numberWithDouble:-122]],
              @"Incorrect longitude found for key trackpoint.lon - found %@, expecting %d",
              [tp objectForKey:@"lon"], -122);
    
    NSDictionary* extras = [tp objectForKey:@"extras"];
    XCTAssert([[extras objectForKey:@"hAccuracy"] isEqualToNumber:[NSNumber numberWithDouble:50]],
              @"Incorrect horizontal accuracy found for key trackpoint.lon - found %@, expecting %d",
              [extras objectForKey:@"hAccuracy"], 50);
    XCTAssert([[extras objectForKey:@"vAccuracy"] isEqualToNumber:[NSNumber numberWithDouble:50]],
              @"Incorrect vertical accuracy found for key trackpoint.lon - found %@, expecting %d",
              [extras objectForKey:@"vAccuracy"], 50);
}


- (void) testConvertOngoingToStoredNoEntries {
    // First try with no entries in the ongoing trip DB
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
    [DataUtils convertOngoingToStored];
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
    XCTAssertEqual([DataUtils getTripsToPush].count, 0);
}

/*
 * Also tests getTripsToPush and deletePushedTrips
 */
- (void) testConvertOngoingToStored {
    // First try with no entries in the ongoing trip DB
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
    
    NSArray* fourPoints = [self getFourPoints];
    for (int i = 0; i < fourPoints.count; i++) {
        [DataUtils addPoint:fourPoints[i]];
    }
    XCTAssertEqual([DataUtils getLastPoints:5].count, 4);
    
    // Add in mode changes so that we get activities
    // TODO: Add it once we implement activity detection
    [DataUtils convertOngoingToStored];

    XCTAssertEqual([[StoredTripsDatabase database] getAllStoredTrips].count, 3);
    NSArray* toPush = [DataUtils getTripsToPush];
    
    XCTAssertEqual(toPush.count, 2);
    NSLog(@"toPush[0] = %@", toPush[0]);
    NSLog(@"toPush[1] = %@", toPush[1]);
    
    XCTAssertNotNil([toPush[0] objectForKey:@"place"]);

    
    XCTAssert([[toPush[0] objectForKey:@"endTime"] isEqualToString:[TestUtils dateToString:
                                                                   ((CLLocation*)fourPoints[0]).timestamp]]);
    
    XCTAssert([[toPush[1] objectForKey:@"type"] isEqualToString:@"move"]);

    
#if 0
    No sections for now until we get iPhone 6
    JSONArray sectionArray = toPush.getJSONObject(1).getJSONArray("activities");
    assertEquals(sectionArray.length(), 4);
    
    for (int i = 0; i < sectionArray.length(); i++) {
        assertTrue(sectionArray.getJSONObject(i).has("duration"));
        assertTrue(sectionArray.getJSONObject(i).has("distance"));
    }
    
    for (int i = 0; i < sectionArray.length(); i++) {
        JSONArray pointsArray = sectionArray.getJSONObject(i).getJSONArray("trackPoints");
        Log.d(TAG, "pointsArray("+i+") = "+pointsArray);
        if (i != (sectionArray.length() - 1)) {
            assertEquals(pointsArray.length(), 1);
        } else {
            /*
             * Everything except the final segment has one point. The final segment is from the
             * last mode change to the end of the trip, which has no points in this test because
             * the last mode change is at the end of the trip.
             */
            assertEquals(pointsArray.length(), 0);
        }
    }
#endif
    
    [DataUtils deletePushedTrips:toPush];
    XCTAssertEqual([[StoredTripsDatabase database] getAllStoredTrips].count, 1);
}

- (void) testEndTrip {
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
    NSArray* fourPoints = [self getFourPoints];
    for (int i = 0; i < fourPoints.count; i++) {
        [DataUtils addPoint:fourPoints[i]];
    }
    XCTAssertEqual([DataUtils getLastPoints:5].count, 4);
    [DataUtils endTrip];
    XCTAssertEqual([DataUtils getLastPoints:5].count, 0);
}

@end
