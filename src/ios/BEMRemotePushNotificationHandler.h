#import "BEMServerSyncCommunicationHelper.h"

@interface BEMRemotePushNotificationHandler : NSObject
+ (BEMRemotePushNotificationHandler*) instance;
- (void) handleNotifications:(NSNotification*)note;
@property (copy, nonatomic) SilentPushCompletionHandler silentPushHandler;

@end
