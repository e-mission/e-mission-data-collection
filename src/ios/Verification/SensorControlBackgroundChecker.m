#import "SensorControlBackgroundChecker.h"
#import "TripDiarySensorControlChecks.h"
#import "TripDiaryStateMachine.h"
#import "LocalNotificationManager.h"
#import "BEMAppDelegate.h"
#import "BEMActivitySync.h"

#import <CoreMotion/CoreMotion.h>
#define OPEN_APP_STATUS_PAGE_ID @362253744

@implementation SensorControlBackgroundChecker

+(NSDictionary*)OPEN_APP_STATUS_PAGE
{
    NSDictionary* config = @{
        @"id": OPEN_APP_STATUS_PAGE_ID,
        @"title": NSLocalizedStringFromTable(@"fix_app_status_title", @"DCLocalizable", nil),
        @"text": NSLocalizedStringFromTable(@"fix_app_status_text", @"DCLocalizable", nil),
        @"data": @{
            @"redirectTo": @"root.main.control",
            @"redirectParams": @{
                @"launchAppStatusModal": @true
            }
        }
    };
    return config;
}

+(void)restartFSMIfStartState
{
    NSUInteger currState = [TripDiaryStateMachine instance].currState;
    if (currState == kStartState) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"Still in start state, sending initialize..."] showUI:TRUE];
        [[NSNotificationCenter defaultCenter] postNotificationName:CFCTransitionNotificationName
                object:CFCTransitionInitialize];
    } else {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"In valid state %@, nothing to do...", [TripDiaryStateMachine getStateName:currState]] showUI:FALSE];
    }
}

+(void)checkAppState
{
    [LocalNotificationManager cancelNotification:OPEN_APP_STATUS_PAGE_ID];
    
    NSArray* allChecks = @[
      @([TripDiarySensorControlChecks checkLocationSettings]),
      @([TripDiarySensorControlChecks checkLocationPermissions]),
      @([TripDiarySensorControlChecks checkMotionActivitySettings]),
      @([TripDiarySensorControlChecks checkMotionActivityPermissions]),
      @([TripDiarySensorControlChecks checkNotificationsEnabled])
    ];
    BOOL allChecksPass = true;
    for (id check in allChecks) {
      allChecksPass = allChecksPass && check;
    }
    
    BOOL locChecksPass = allChecks[0] && allChecks[1];
    
    if (allChecksPass) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"All settings valid, nothing to prompt"]];
        [self restartFSMIfStartState];
    }
    else if (locChecksPass) {
        /*
        Log.i(ctxt, TAG, "all checks = "+allOtherChecksPass+" but location permission status  "+allOtherChecks[0]+" should be true "+
        " so one of the non-location checks must be false: loc permission, motion permission, notification, unused apps" + Arrays.toString(allOtherChecks));
        Log.i(ctxt, TAG, "a non-local check failed, generating only user visible notification");
         */
        [self generateOpenAppSettingsNotification];
    }
    else {
        /*
         Log.i(ctxt, TAG, "location settings are valid, but location permission is not, generating tracking error and visible notification");
         Log.i(ctxt, TAG, "curr status check results = " +
                " loc permission, motion permission, notification, unused apps "+ Arrays.toString(allOtherChecks));
         */
        // Should replace with TRACKING_ERROR but looks like we
        // don't have any
        [[NSNotificationCenter defaultCenter]
            postNotificationName:CFCTransitionNotificationName
                object:CFCTransitionGeofenceCreationError];
        [self generateOpenAppSettingsNotification];
    }
}

+(void)generateOpenAppSettingsNotification
{
    [LocalNotificationManager schedulePluginCompatibleNotification:[self OPEN_APP_STATUS_PAGE] withNewData:NULL];
}
@end
