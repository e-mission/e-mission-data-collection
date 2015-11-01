//
//  CommunicationHelper.h
//  CFC_Tracker
//
//  Created by Kalyanaraman Shankari on 3/24/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface CommunicationHelper : NSObject
// Wrappers for our specific functionality
// Note that we are using POST even for methods that retrieve data such as
// getUnclassifiedSections, since it contains personally identifiable information
// and we use a user token for authentication
+(void)getCustomSettings:(void (^)(NSData *data, NSURLResponse *response, NSError *error))completionHandler;
+(void)createUserProfile:(void (^)(NSData *data, NSURLResponse *response, NSError *error))completionHandler;
+(void)getUnclassifiedSections:(void (^)(NSData *data, NSURLResponse *response, NSError *error))completionHandler;
+(void)setClassifiedSections:(NSArray*)sectionDicts completionHandler:(void (^)(NSData *data, NSURLResponse *response, NSError *error))completionHandler;
+(void)movesCallback:(NSMutableDictionary*)movesParams completionHandler:(void (^)(NSData *data, NSURLResponse *response, NSError *error))completionHandler;
+(void)setClientStats:(NSDictionary*)statsToSend completionHandler:(void (^)(NSData *data, NSURLResponse *response, NSError *error))completionHandler;
+(void)phone_to_server:(NSArray*) entriesToPush completionHandler:(void (^)(NSData *data, NSURLResponse *response, NSError *error))completionHandler;;

// Generic GET and POST methods
+(void)getData:(NSURL *)url completionHandler:(void (^)(NSData *data, NSURLResponse *response, NSError *error))completionHandler;
-(id)initPost:(NSURL *)url data:(NSMutableDictionary*)jsonDict completionHandler:(void (^)(NSData *data, NSURLResponse *response, NSError *error))completionHandler;

-(void)execute;

@property (nonatomic, strong) NSURL* mUrl;
@property (nonatomic, strong) NSMutableDictionary* mJsonDict;
@property (nonatomic, strong) void (^mCompletionHandler)(NSData *data, NSURLResponse *response, NSError *error);
@end
