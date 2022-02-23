#import "BEMRemotePushNotificationHandler.h"
#import "TripDiaryStateMachine.h"
#import "SensorControlBackgroundChecker.h"
#import "LocalNotificationManager.h"

@implementation BEMRemotePushNotificationHandler

+ (BEMRemotePushNotificationHandler*) instance {
    static dispatch_once_t once;
    static id sharedInstance;
    dispatch_once(&once, ^{
        sharedInstance = [[self alloc] init];
        // when we create a new instance, we use it to register for notifications
        // this should mean that we register only once for notifications, which should mean
        // that we should stop getting duplicate notifications
        [sharedInstance registerForNotifications];
    });
    return sharedInstance;
}

- (void)registerForNotifications {
    [[NSNotificationCenter defaultCenter] addObserverForName:CFCTransitionNotificationName object:nil queue:nil
                                                  usingBlock:^(NSNotification *note) {
                                                      [self handleNotifications:note];
                                                  }];
}

- (id) init {
    self = [super init];
    self.silentPushHandlerList = [[NSMutableArray alloc] init];
    return self;
}

- (void)notifyAllHandlers:(UIBackgroundFetchResult) result {
    for (SilentPushCompletionHandler handler in self.silentPushHandlerList) {
        handler(result);
    }
    [self.silentPushHandlerList removeAllObjects];
}

- (void)handleNotifications:(NSNotification*)note {
    @synchronized([BEMRemotePushNotificationHandler instance]) {
        if ([note.object isEqualToString:CFCTransitionRecievedSilentPush]) {
            // We are the silent push handler, so we store the handler block, and run the
            // periodic tasks, but don't do anything else
            [BEMRemotePushNotificationHandler performPeriodicActivity];
            NSDictionary* userInfo = note.userInfo;
            SilentPushCompletionHandler newHandler = [userInfo objectForKey:@"handler"];
            if (newHandler != NULL) {
                [self.silentPushHandlerList addObject:newHandler];
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Added SILENT_PUSH handler block %@ to list, new size = %lu", newHandler, (unsigned long)[self.silentPushHandlerList count]]];
            }
        }
    }
    if ([self.silentPushHandlerList count] > 0) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                   @"Received notification %@ while processing silent push notification", note.object] showUI:FALSE];
        // we only care about notifications when we are processing a silent remote push notification
        if ([note.object isEqualToString:CFCTransitionRecievedSilentPush]) {
            // We are the silent push handler, so we store the handler block, but don't do anything else.
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Handler block has already been set, ignoring SILENT_PUSH in the silent push handler"]];
            // Note: Do NOT call the handler here since the state machine may not have quiesced.
            // We want to wait until we know that the state machine has finished handling it.
            // _silentPushHandler(UIBackgroundFetchResultNewData);
        } else if ([note.object isEqualToString:CFCTransitionNOP]) {
            // Next, we think of what the possible responses to the silent push are
            // One option is that the state machine wants to ignore it, possibly because it is not in ONGOING STATE
            // Let us assume that we will return NOP in that case
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Remote push state machine ignored the silent push, fetch result = new data"] showUI:FALSE];
            [self notifyAllHandlers:UIBackgroundFetchResultNewData];
        } else if ([note.object isEqualToString:CFCTransitionTripEndDetected]) {
            // Otherwise, if it is in OngoingTrip, it will try to see whether the trip has ended. If it hasn't,
            // let us assume that we will return a NOP, which is already handled.
            // If it has, then it will return a TripEndDetected and start creating the geofence.
            // Once the geofence is created, we will get a TripEnded, and we want to
            // wait until that point, so we DON'T return here.
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Detected trip end, waiting until geofence is created to return from silent push"]];
        } else if ([note.object isEqualToString:CFCTransitionTripEnded]) {
            // Trip has now ended, so we can push and clear data
                    [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Trip ended, waiting until data is pushed to return from the silent push"]];
        } else if ([note.object isEqualToString:CFCTransitionDataPushed]) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:
                                                       @"Data pushed, fetch result = new data"] showUI:FALSE];
            [self notifyAllHandlers:UIBackgroundFetchResultNewData];
        } else if ([note.object isEqualToString:CFCTransitionTripRestarted]) {
            // The other option from TripEndDetected is that the trip is restarted instead of ended.
            // In that case, we still want to finish the handler
            [self notifyAllHandlers:UIBackgroundFetchResultNewData];
        } else {
            // Some random transition. Might as well call the handler and return
            [self notifyAllHandlers:UIBackgroundFetchResultNewData];
        }
    } else {
        // Not processing a silent remote push notification
        NSLog(@"Ignoring silent push notification");
    }
}

+ (void) performPeriodicActivity
{
    [SensorControlBackgroundChecker checkAppState];
    [SensorControlBackgroundChecker restartFSMIfStartState];
}

@end
