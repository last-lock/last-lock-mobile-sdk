#import <LastLockMobileSdkSpec/LastLockMobileSdkSpec.h>

#import "LastLockMobileSdkController.h"

@interface LastLockMobileSdk : NativeLastLockMobileSdkSpecBase <NativeLastLockMobileSdkSpec>
@end

@implementation LastLockMobileSdk {
  LastLockMobileSdkController *_controller;
}

RCT_EXPORT_MODULE(LastLockMobileSdk)

+ (BOOL)requiresMainQueueSetup
{
  return YES;
}

- (instancetype)init
{
  if (self = [super init]) {
    _controller = [LastLockMobileSdkController new];
    __weak LastLockMobileSdk *weakSelf = self;
    _controller.onDeviceDiscovered = ^(NSDictionary *body) {
      [weakSelf emitDiscovered:body];
    };
    _controller.onDeviceConnected = ^(NSDictionary *body) {
      [weakSelf emitConnected:body];
    };
    _controller.onDeviceDisconnected = ^(NSDictionary *body) {
      [weakSelf emitDisconnected:body];
    };
    _controller.onBLEStateChanged = ^(NSDictionary *body) {
      [weakSelf emitBleState:body];
    };
    _controller.onUnlockResult = ^(NSDictionary *body) {
      [weakSelf emitUnlockResult:body];
    };
    _controller.onDFUStateChanged = ^(NSDictionary *body) {
      [weakSelf emitDfuState:body];
    };
    _controller.onDeviceInformation = ^(NSDictionary *body) {
      [weakSelf emitDeviceInformation:body];
    };
    _controller.onServerConnectionChange = ^(NSDictionary *body) {
      [weakSelf emitProxyStream:body];
    };
  }
  return self;
}

- (void)initialize:(NSString *)serverAddress
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject
{
  [_controller initialize:serverAddress resolve:resolve reject:reject];
}

- (void)start:(NSString *)uuid
     resolve:(RCTPromiseResolveBlock)resolve
      reject:(RCTPromiseRejectBlock)reject
{
  [_controller start:uuid resolve:resolve reject:reject];
}

- (void)startLimited:(NSString *)uuid
            resolve:(RCTPromiseResolveBlock)resolve
             reject:(RCTPromiseRejectBlock)reject
{
  [_controller start:uuid resolve:resolve reject:reject];
}

- (void)shutdown:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [_controller shutdown:resolve reject:reject];
}

- (void)isDeviceBlacklisted:(NSString *)identifier resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [_controller isDeviceBlacklisted:identifier resolve:resolve reject:reject];
}

- (void)getPass:(NSString *)passId resolve:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject
{
  [_controller getPass:passId resolve:resolve reject:reject];
}

- (void)identify:(NSString *)identifier
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject
{
  [_controller identify:identifier resolve:resolve reject:reject];
}

- (void)setLockEngagedState:(NSString *)identifier
                    engaged:(BOOL)engaged
                    resolve:(RCTPromiseResolveBlock)resolve
                     reject:(RCTPromiseRejectBlock)reject
{
  [_controller setLockEngagedState:identifier engaged:engaged resolve:resolve reject:reject];
}

- (void)checkForDFU:(NSString *)identifier
           resolve:(RCTPromiseResolveBlock)resolve
            reject:(RCTPromiseRejectBlock)reject
{
  [_controller checkForDFU:identifier resolve:resolve reject:reject];
}

- (void)startDFU:(NSString *)identifier
        resolve:(RCTPromiseResolveBlock)resolve
         reject:(RCTPromiseRejectBlock)reject
{
  [_controller startDFU:identifier resolve:resolve reject:reject];
}

- (void)cancelDFU:(RCTPromiseResolveBlock)resolve
          reject:(RCTPromiseRejectBlock)reject
{
  [_controller cancelDFU:resolve reject:reject];
}

- (void)waitForDeviceMode:(NSString *)identifier
              dfuRequired:(BOOL)dfuRequired
                  resolve:(RCTPromiseResolveBlock)resolve
                   reject:(RCTPromiseRejectBlock)reject
{
  [_controller waitForDeviceMode:identifier dfuRequired:dfuRequired resolve:resolve reject:reject];
}

- (void)exitDFUInstallMode:(NSString *)identifier
                   resolve:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
  [_controller exitDFUInstallMode:identifier resolve:resolve reject:reject];
}

- (void)getOrCreatePassPublicKey:(NSString *)passId
                          resolve:(RCTPromiseResolveBlock)resolve
                           reject:(RCTPromiseRejectBlock)reject
{
  [_controller getOrCreatePassPublicKey:passId resolve:resolve reject:reject];
}

- (void)storePass:(NSString *)passId
             pass:(JS::NativeLastLockMobileSdk::UnlockPass &)pass
          resolve:(RCTPromiseResolveBlock)resolve
           reject:(RCTPromiseRejectBlock)reject
{
  NSMutableArray *groups = [NSMutableArray new];
  for (const auto &group : pass.readerGroups()) {
    NSMutableDictionary *material = [@{
      @"readerGroupIdentifier": group.readerGroupIdentifier(),
      @"issuerCAPublicKey": group.issuerCAPublicKey(),
    } mutableCopy];
    if (NSString *certificate = group.issuerCACertificateDer()) {
      material[@"issuerCACertificateDer"] = certificate;
    }
    [groups addObject:material];
  }
  [_controller storePass:passId
                   pass:@{@"accessDocument": pass.accessDocument(), @"readerGroups": groups}
                resolve:resolve
                 reject:reject];
}

- (void)deletePass:(NSString *)passId
           resolve:(RCTPromiseResolveBlock)resolve
            reject:(RCTPromiseRejectBlock)reject
{
  [_controller deletePass:passId resolve:resolve reject:reject];
}

- (void)unlock:(NSString *)identifier
     passId:(NSString *)passId
            resolve:(RCTPromiseResolveBlock)resolve
             reject:(RCTPromiseRejectBlock)reject
{
  [_controller unlock:identifier passId:passId resolve:resolve reject:reject];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeLastLockMobileSdkSpecJSI>(params);
}

- (void)emitDiscovered:(NSDictionary *)body
{
  if (_eventEmitterCallback) {
    [self emitOnDeviceDiscovered:body];
  }
}

- (void)emitConnected:(NSDictionary *)body
{
  if (_eventEmitterCallback) {
    [self emitOnDeviceConnected:body];
  }
}

- (void)emitDisconnected:(NSDictionary *)body
{
  if (_eventEmitterCallback) {
    [self emitOnDeviceDisconnected:body];
  }
}

- (void)emitBleState:(NSDictionary *)body
{
  if (_eventEmitterCallback) {
    [self emitOnBLEStateChanged:body];
  }
}

- (void)emitUnlockResult:(NSDictionary *)body
{
  if (_eventEmitterCallback) {
    [self emitOnUnlockResult:body];
  }
}

- (void)emitDfuState:(NSDictionary *)body
{
  if (_eventEmitterCallback) {
    [self emitOnDFUStateChanged:body];
  }
}

- (void)emitDeviceInformation:(NSDictionary *)body
{
  if (_eventEmitterCallback) {
    [self emitOnDeviceInformation:body];
  }
}

- (void)emitProxyStream:(NSDictionary *)body
{
  if (_eventEmitterCallback) {
    [self emitOnServerConnectionChange:body];
  }
}

@end
