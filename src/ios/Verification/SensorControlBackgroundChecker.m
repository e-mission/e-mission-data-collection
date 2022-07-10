#import "SensorControlBackgroundChecker.h"
#import "TripDiarySensorControlChecks.h"
#import "TripDiaryStateMachine.h"
#import "LocalNotificationManager.h"
#import "BEMAppDelegate.h"
#import "BEMActivitySync.h"
#import "ConfigManager.h"

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
    
    NSArray<NSNumber*>* allChecks = @[
      @([TripDiarySensorControlChecks checkLocationSettings]),
      @([TripDiarySensorControlChecks checkLocationPermissions]),
      @([TripDiarySensorControlChecks checkMotionActivitySettings]),
      @([TripDiarySensorControlChecks checkMotionActivityPermissions]),
      @([TripDiarySensorControlChecks checkNotificationsEnabled])
    ];
    BOOL allChecksPass = TRUE;
    for (NSNumber* check in allChecks) {
      allChecksPass = allChecksPass && check.boolValue;
    }
    
    BOOL locChecksPass = allChecks[0].boolValue && allChecks[1].boolValue;
    
    if (allChecksPass) {
        [LocalNotificationManager addNotification:[NSString stringWithFormat:@"All settings valid, nothing to prompt"]];
        [self restartFSMIfStartState];
    }
    else {
        if ([ConfigManager getPriorConsent] == NULL) {
            [LocalNotificationManager addNotification:[NSString stringWithFormat:@"User has not yet consented, ignoring failed checks"]];
        } else {
            if (locChecksPass) {
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"allChecksPass = %@, but location permimssions pass, so one of the non-location checks must be false: [loc settings, loc permissions, motion settings, motion permissions, notification] = %@", @(allChecksPass), allChecks]];
                [self generateOpenAppSettingsNotification];
            }
            else {
                [LocalNotificationManager addNotification:[NSString stringWithFormat:@"allChecksPass = %@, but location checks fail, generating error and notification, allChecks: [loc settings, loc permissions, motion settings, motion permissions, notification] = %@", @(allChecksPass), allChecks]];
                // Should replace with TRACKING_ERROR but looks like we
                // don't have any
                [[NSNotificationCenter defaultCenter]
                    postNotificationName:CFCTransitionNotificationName
                        object:CFCTransitionGeofenceCreationError];
                [self generateOpenAppSettingsNotification];
            }
        }
    }
}

+(void)generateOpenAppSettingsNotification
{
    [LocalNotificationManager schedulePluginCompatibleNotification:[self OPEN_APP_STATUS_PAGE] withNewData:NULL];
}
@end
