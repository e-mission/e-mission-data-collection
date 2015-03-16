//
//  AuthCompletionHandler.h
//  E-Mission
//
//  Created by Kalyanaraman Shankari on 4/3/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <GoogleOpenSource/GoogleOpenSource.h>

@protocol AuthCompletionDelegate
// The authorization has finished and is successful if |error| is |nil|.
- (void)finishedWithAuth:(GTMOAuth2Authentication *)auth
                   error:(NSError *)error;
// Finished disconnecting user from the app.
// The operation was successful if |error| is |nil|.
@optional
- (void)didDisconnectWithError:(NSError *)error;

@end

@interface AuthCompletionHandler : NSObject<AuthCompletionDelegate>

@property GTMOAuth2Authentication* currAuth;
@property NSMutableArray* errorArray;
@property(nonatomic, copy) NSString* scope;
@property(nonatomic, copy) NSString* clientId;
@property(nonatomic, copy) NSString* clientSecret;

+(AuthCompletionHandler*) sharedInstance;
-(void)registerFinishDelegate:(id<AuthCompletionDelegate>) delegate;
-(void)unregisterFinishDelegate:(id<AuthCompletionDelegate>) delegate;

-(BOOL)trySilentAuthentication;
-(UIViewController*)getSigninController;
-(void)signOut;

- (NSString*)getIdToken;

/*
 * Both the refresh token methods will call all registered listeners with the new token
 */


/*
-(bool)isTokenExpired;

-(void)refreshToken;
-(void)refreshTokenIfExpired;
*/

@end
