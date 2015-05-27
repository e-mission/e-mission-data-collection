//
//  AuthCompletionHandler.m
//  E-Mission
//
//  Created by Kalyanaraman Shankari on 4/3/14.
//  Copyright (c) 2014 Kalyanaraman Shankari. All rights reserved.
//

/*
 * Shared sign-in instance only allows us to have a single delegate. But we may want multiple auths - for screen, for background jobs, for multiple background jobs running in parallel...
     So we go to our familiar listener pattern
 */

#import "AuthCompletionHandler.h"
#import "ConnectionSettings.h"
#import "SkipAuthEmailViewController.h"
#import <GoogleOpenSource/GoogleOpenSource.h>
#import <GoogleOpenSource/GTMOAuth2ViewControllerTouch.h>


@interface AuthCompletionHandler() {
    NSMutableArray* listeners;
}
@end

@implementation AuthCompletionHandler
static AuthCompletionHandler *sharedInstance;

-(id)init{
    listeners = [NSMutableArray new];
    return [super init];
}

+ (AuthCompletionHandler*)sharedInstance
{
    if (sharedInstance == nil) {
        NSLog(@"creating new AuthCompletionHandler sharedInstance");
        sharedInstance = [AuthCompletionHandler new];
    }
    return sharedInstance;
}

- (void)registerFinishDelegate:(id<AuthCompletionDelegate>) delegate {
    @synchronized(sharedInstance) {
        NSLog(@"About to add delegate, nListeners = %lu", (unsigned long)sharedInstance->listeners.count);
        [sharedInstance->listeners addObject:delegate];
        NSLog(@"After adding delegate, nListeners = %lu", (unsigned long)sharedInstance->listeners.count);
    }
    
}

- (void)unregisterFinishDelegate:(id<AuthCompletionDelegate>) delegate {
    @synchronized(sharedInstance) {
        [sharedInstance->listeners removeObject:delegate];
    }
}

- (void)finishedWithAuth:(GTMOAuth2Authentication *)auth error:(NSError *)error {
    // TODO: Improve this by caching copy of the listeners, so that the finishedWithAuth
    // calls, which can involve a remote call, can happen in parallel
    // This is would be a performance optimization
    NSLog(@"AuthCompletionHandler.finishedWithAuth called, nListeners = %lu", (unsigned long)listeners.count);
    @synchronized(self) {
        for (int i = 0; i < listeners.count; i++) {
            NSLog(@"AuthCompletionHandler.finishedWithAuth notifying listener %d", i);
            [listeners[i] finishedWithAuth:auth error:error];
        }
    }
}

/*
 * trySilentAuthentication can be called from both the background and UI views. So it will not
 * popup the sign in view automatically. Instead, it will just return false if there is no authentication
 * token stored in the keystore.
 */

- (BOOL)trySilentAuthentication {
    if (self.currAuth == NULL) {
        GTMOAuth2Authentication* tempAuth = [GTMOAuth2ViewControllerTouch authForGoogleFromKeychainForName:kKeychainItemName
                                                                              clientID:self.clientId
                                                                          clientSecret:self.clientSecret];
        if(tempAuth.canAuthorize) {
            self.currAuth = tempAuth;
        } else {
            NSLog(@"Authentication %@ stored in keychain is no longer valid", tempAuth);
        }
    }
    if (self.currAuth.canAuthorize) {
        // We are currently signed in
        return YES;
    }
    
    // We are not currently signed in
    // TODO: Consider folding in the checks for expired tokens and
    return NO;
}

-(UIViewController*)getSigninController {
    if ([[ConnectionSettings sharedInstance] isSkipAuth]) {
        // Display a simple view where you can enter the email address
        SkipAuthEmailViewController* controller = [[SkipAuthEmailViewController alloc] initWithNibName:nil bundle:nil];
        return controller;
    }
    // Display the autentication view.
    SEL finishedSel = @selector(viewController:finishedWithAuth:error:);
    
    GTMOAuth2ViewControllerTouch *viewController;
    viewController.signIn.shouldFetchGoogleUserEmail = YES;
    viewController.signIn.shouldFetchGoogleUserProfile = NO;
    
    viewController = [GTMOAuth2ViewControllerTouch controllerWithScope:self.scope
                                                              clientID:self.clientId
                                                          clientSecret:self.clientSecret
                                                      keychainItemName:kKeychainItemName
                                                              delegate:self
                                                      finishedSelector:finishedSel];
    return viewController;
}


- (void)signOut {
    if ([self.currAuth.serviceProvider isEqual:kGTMOAuth2ServiceProviderGoogle]) {
        // remove the token from Google's servers
        [GTMOAuth2ViewControllerTouch revokeTokenForGoogleAuthentication:self.currAuth];
    }
    
    // remove the stored Google authentication from the keychain, if any
    [GTMOAuth2ViewControllerTouch removeAuthFromKeychainForName:kKeychainItemName];
    
    // remove the stored DailyMotion authentication from the keychain, if any
    [GTMOAuth2ViewControllerTouch removeAuthFromKeychainForName:kKeychainItemName];
    
    // Discard our retained authentication object.
    self.currAuth = nil;
}

+ (GTMOAuth2Authentication*) createFakeAuth:(NSString*) userEmail {
    GTMOAuth2Authentication* retAuth = [[GTMOAuth2Authentication alloc] init];
    retAuth.userEmail = userEmail;
    retAuth.refreshToken = userEmail;
    retAuth.accessToken = userEmail;
    // Make sure that it expires way in the future
    retAuth.expirationDate = [NSDate dateWithTimeIntervalSinceNow:3600 * 365];
    retAuth.parameters = [[NSMutableDictionary alloc] initWithDictionary:@{@"id_token" : retAuth.userEmail,
                                                                           @"refresh_token" : retAuth.refreshToken,
                                                                           @"access_token" : retAuth.accessToken,
                                                                           @"email": retAuth.userEmail,
                                                                           }];
    return retAuth;
}

- (void)viewController:(GTMOAuth2ViewControllerTouch *)viewController
      finishedWithAuth:(GTMOAuth2Authentication *)auth
                 error:(NSError *)error {
    if (error != nil) {
        // Authentication failed (perhaps the user denied access, or closed the
        // window before granting access)
        NSLog(@"Authentication error: %@", error);
        NSData *responseData = [[error userInfo] objectForKey:@"data"]; // kGTMHTTPFetcherStatusDataKey
        if ([responseData length] > 0) {
            // show the body of the server's authentication failure response
            NSString *str = [[NSString alloc] initWithData:responseData
                                                   encoding:NSUTF8StringEncoding];
            NSLog(@"%@", str);
        }
        
        self.currAuth = nil;
    } else {
        // Authentication succeeded
        //
        // At this point, we either use the authentication object to explicitly
        // authorize requests, like
        //
        //  [auth authorizeRequest:myNSURLMutableRequest
        //       completionHandler:^(NSError *error) {
        //         if (error == nil) {
        //           // request here has been authorized
        //         }
        //       }];
        //
        // or store the authentication object into a fetcher or a Google API service
        // object like
        ///
        //   [fetcher setAuthorizer:auth];
        
        // save the authentication object
        self.currAuth = auth;
    }
    
    [self finishedWithAuth:auth error:error];
}

-(NSString*)getIdToken {
    if (self.currAuth != NULL) {
        if (self.currAuth.canAuthorize) {
            return [self.currAuth.parameters valueForKey:@"id_token"];
        }
    }
    return NULL;
}

@end
