#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>

NS_ASSUME_NONNULL_BEGIN

typedef void (^LastLockMobileSdkEventBlock)(NSDictionary *body);

@interface LastLockMobileSdkController : NSObject

@property (nonatomic, copy, nullable) LastLockMobileSdkEventBlock onDeviceDiscovered;
@property (nonatomic, copy, nullable) LastLockMobileSdkEventBlock onDeviceConnected;
@property (nonatomic, copy, nullable) LastLockMobileSdkEventBlock onDeviceDisconnected;
@property (nonatomic, copy, nullable) LastLockMobileSdkEventBlock onBLEStateChanged;
@property (nonatomic, copy, nullable) LastLockMobileSdkEventBlock onUnlockResult;
@property (nonatomic, copy, nullable) LastLockMobileSdkEventBlock onDeviceInformation;
@property (nonatomic, copy, nullable) LastLockMobileSdkEventBlock onServerConnectionChange;

- (void)initialize:(NSString *)serverAddress
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject;

- (void)start:(NSString *)uuid
     resolve:(RCTPromiseResolveBlock)resolve
      reject:(RCTPromiseRejectBlock)reject;

- (void)shutdown:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
- (void)isDeviceBlacklisted:(NSString *)identifier resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;
- (void)getPass:(NSString *)passId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject;

- (void)identify:(NSString *)identifier
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject;

- (void)getOrCreatePassPublicKey:(NSString *)passId
                          resolve:(RCTPromiseResolveBlock)resolve
                           reject:(RCTPromiseRejectBlock)reject;

- (void)storePass:(NSString *)passId
             pass:(NSDictionary *)pass
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject;

- (void)deletePass:(NSString *)passId
           resolve:(RCTPromiseResolveBlock)resolve
            reject:(RCTPromiseRejectBlock)reject;

- (void)unlock:(NSString *)identifier
     passId:(NSString *)passId
            resolve:(RCTPromiseResolveBlock)resolve
             reject:(RCTPromiseRejectBlock)reject;

// Beta: installation controls and firmware updates.
@property (nonatomic, copy, nullable) LastLockMobileSdkEventBlock onDFUStateChanged;

/** Beta: only works while the device is in install mode. */
- (void)setLockEngagedState:(NSString *)identifier
                    engaged:(BOOL)engaged
                    resolve:(RCTPromiseResolveBlock)resolve
                     reject:(RCTPromiseRejectBlock)reject;

- (void)checkForDFU:(NSString *)identifier
           resolve:(RCTPromiseResolveBlock)resolve
            reject:(RCTPromiseRejectBlock)reject;

- (void)startDFU:(NSString *)identifier
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject;

- (void)cancelDFU:(RCTPromiseResolveBlock)resolve
          reject:(RCTPromiseRejectBlock)reject;

- (void)waitForDeviceMode:(NSString *)identifier
              dfuRequired:(BOOL)dfuRequired
                  resolve:(RCTPromiseResolveBlock)resolve
                   reject:(RCTPromiseRejectBlock)reject;

- (void)exitDFUInstallMode:(NSString *)identifier
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject;

@end

NS_ASSUME_NONNULL_END
