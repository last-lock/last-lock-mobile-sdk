# Last Lock Mobile for React Native

Enroll the app for attestation with Last Lock before devices can be discovered or unlocked. Attestation is tied to the app's bundle id and Android package name.

This sample is TimeKeeper on React Native 0.86 with the New Architecture enabled. It depends on the production `@last-lock/lastlock-mobile-sdk` package at version `0.0.1`, which supplies the native SDK integration.

## Run TimeKeeper

From this directory:

```bash
npm install
```

> **Before `npm install`:**
> - Export `NODE_AUTH_TOKEN` with a GitHub token that can read `last-lock` packages. Your `.npmrc` should route `@last-lock` to GitHub Packages and use that token.
> - For Android, set `gpr.user` and `gpr.key` in your `~/.gradle/gradle.properties`, or export `GITHUB_ACTOR` and `GITHUB_TOKEN` in your environment. Gradle uses these to download `lastlock-mobile-sdk-android` dependencies.

---

### iOS

1. **Install Pod dependencies:**
   After `npm install`, run `npx expo prebuild --clean --platform ios` to regenerate native iOS code and ensure dependencies are up to date, then run `pod install` inside the `ios` directory:
   ```bash
   npx expo prebuild --clean --platform ios
   cd ios
   pod install
   cd ..
   ```
   Run `npx expo prebuild --clean --platform ios` and `pod install` again any time you add or change a native dependency (`@last-lock/lastlock-mobile-sdk` updates, React Native updates, etc).

2. **Start the dev server (Metro):**
   In a separate terminal:
   ```bash
   npm start
   # or
   npx expo start
   ```

3. **Run the app on a physical iPhone (iOS 17+ required):**
   ```bash
   npm run ios -- --device
   # or
   expo run:ios --device
   ```
   For a **release build** on a connected device:
   ```bash
   npx expo run:ios --configuration Release --device
   ```
   > **Note:** Metro dev server hot-reloads JavaScript only. Any changes to native code (including updates to native modules, pod dependencies, or bridge code) require re-running `npx expo prebuild --clean --platform ios`, followed by `pod install` and then `expo run:ios`.

---

### Android

1. **Start the dev server (Metro):**
   In a separate terminal, run:
   ```bash
   npm start
   # or
   npx expo start
   ```

2. **Run in debug mode:**
   On a device or emulator (API 26+ required):
   ```bash
   npm run android
   # or
   expo run:android
   ```
   > **Note:** This will install a debug build. Native code changes require rerunning this command to take effect.

3. **Run a release build on a device:**
   To install a release build directly:
   ```bash
   npx expo run:android --variant release --device
   ```

4. **Build a release APK for distribution:**
   To generate a release APK file:
   ```bash
   cd android
   ./gradlew assembleRelease
   ```
   - The APK will be created at `android/app/build/outputs/apk/release/app-release.apk`.
   - You can then install it manually using:
     ```bash
     adb install app/build/outputs/apk/release/app-release.apk
     ```
   > For production-ready signing, consult the official React Native/Expo docs for release keystore configuration.

---

**Summary Table**

| When to run              | What to do                                                            |
|--------------------------|----------------------------------------------------------------------|
| After adding native deps | `npm install` (then `cd ios && pod install` for iOS)                  |
| Native code changes      | Rerun `expo run:ios` or `expo run:android` to pick up changes         |
| JavaScript/TypeScript   | Use Metro / dev server reload (`npm start`)                            |
| Release install iOS      | `npx expo run:ios --configuration Release --device`                   |
| Release install Android  | `npx expo run:android --variant release --device`                     |
| Build Android APK        | `cd android && ./gradlew assembleRelease`, output: `app-release.apk`  |

---

The app automatically calls `initialize` and `start` on launch from `app/_layout.tsx`.

Use a physical iPhone on iOS 17 or later, and an Android device/emulator with `minSdk` 26.

Two ways to integrate:

1. **Use the package.** Install `@last-lock/lastlock-mobile-sdk` and its Expo plugin. This is what TimeKeeper does.
2. **Wire it yourself.** Link the same SDK and write your own native module with the same JavaScript shape.

Both paths use the same SDK and the same JavaScript contract. The package is the maintained bridge. The do-it-yourself path is for an app that cannot take the package.

## How a call moves

```
JavaScript
  initialize(serverAddress) / start(uuid) / unlock(identifier, passId)
  onDeviceDiscovered(listener) and the other event subscriptions
        │
        ▼
TurboModule "LastLockMobileSdk"
        │
        ├── Android: LastLockMobileSdkModule.kt
        └── iOS:     LastLockMobileSdkModule.mm  →  LastLockMobileSdkController (Swift)
        │
        ▼
Last Lock Mobile SDK
  Android  com.lastlock.dev:lastlock-mobile-sdk-android:0.0.1-rn
  iOS      Swift package product LastLockMobileSdk at 0.0.1-rn
```

`initialize` stores the plane host. `start` begins a BLE scan for a service UUID. `unlock` presents a stored token-unlock pass to a device that scan already found. Store the pass first with `getOrCreatePassPublicKey` and `storePass`.

Events are TurboModule callbacks, subscribed with `onDeviceDiscovered(listener)` and the matching functions. Each call returns `{ remove() }`.

| Function | Payload |
| --- | --- |
| `onDeviceDiscovered` | `identifier`, `name`, `rssi`, `connectionStatus`, `signalStrength` |
| `onDeviceConnected` | `identifier`, `name` |
| `onDeviceDisconnected` | `identifier`, `name` |
| `onBLEStateChanged` | `state` boolean. `true` means the radio is on. |
| `onUnlockResult` | `identifier`, `name`, `result`, `message`. Fired for token unlock. |

`start` takes the service UUID as four hex characters, such as `"4801"`. The SDK expands that to `00004801-0000-1000-8000-00805F9B34FB`. The server address is a host with no port. The SDK uses TLS on 443.

A discovered lock's `identifier` is the device id from the advertising packet, not the Bluetooth address.

Native code changes require a new install (`npx expo run:ios --device` or `npx expo run:android`). Reloading Metro only updates JavaScript.

## Requirements

- React Native New Architecture (`newArchEnabled: true`).
- iOS 17 or later, on a physical iPhone.
- Android `minSdk` 26.
- GitHub Packages credentials for the Android SDK: `gpr.user` and `gpr.key` in `~/.gradle/gradle.properties`, or `GITHUB_ACTOR` and `GITHUB_TOKEN` in the environment.

## Option 1: Use the package

TimeKeeper depends on the published package `0.0.5-allison-sim`. That build includes the DFU and identify methods `DeviceDebugPanel.tsx` calls. The project `.npmrc` sends `@last-lock` to GitHub Packages. Android and iOS inside that package use native SDK `0.0.1-rn`.

```json
{
  "dependencies": {
    "@last-lock/lastlock-mobile-sdk": "0.0.5-allison-sim"
  }
}
```

Then install and add the plugin first in `app.json`:

```json
{
  "expo": {
    "newArchEnabled": true,
    "plugins": ["@last-lock/lastlock-mobile-sdk/app.plugin.js"]
  }
}
```

```bash
npm install
npx expo prebuild
npx expo run:ios --device
npx expo run:android
```

The plugin does the native setup:

- Copies `LastLockMobileSdkModule.swift`, `LastLockMobileSdkModule.mm`, and `LastLockMobileSdkController.h` into the iOS app target.
- After `pod install`, links the Swift package `https://github.com/last-lock/link-mobile-sdk-dev.git` at `0.0.1-rn`, product `LastLockMobileSdk`.
- Sets the iOS deployment target to 17.
- Adds Bluetooth purpose strings when `Info.plist` does not already have them.
- Sets `android.minSdkVersion` to 26.
- Adds `https://maven.pkg.github.com/last-lock/link-mobile-sdk-dev` to the Android repositories so Gradle can resolve `com.lastlock.dev:lastlock-mobile-sdk-android:0.0.1-rn`.

Android autolinking registers `LastLockMobileSdkPackage`. The iOS bridge is compiled in the app target because that target is what links the Swift package. `LastLockMobileSdkRN` is a small CocoaPod so React Native codegen can emit `LastLockMobileSdkSpec`. It does not contain the bridge.

JavaScript:

```ts
import {
  initialize,
  start,
  unlock,
  onDeviceDiscovered,
  onBLEStateChanged,
} from '@last-lock/lastlock-mobile-sdk';

await initialize('plane.lastlock.com');
await start('FC5A');

const discovered = onDeviceDiscovered((device) => {
  console.log(device.identifier, device.rssi);
});
const radio = onBLEStateChanged((event) => {
  console.log('BLE state:', event.state);
});

await unlock(device.identifier, passId);

discovered.remove();
radio.remove();
```

TimeKeeper calls `initialize` and `start` from `app/_layout.tsx`, and keeps the device list in `app/useDeviceDiscovery.ts`.

## Option 2: Wire it yourself

Skip the package when the app needs its own native module. Link the same SDK and implement a TurboModule named `LastLockMobileSdk` with the methods and events above. The package sources under `seamless/mobile/react-native/lastlock-mobile-sdk` are the reference.

### Android

1. Add the GitHub Packages repository to the app's `android/build.gradle` `allprojects.repositories` block, with `gpr.user` / `gpr.key` or `GITHUB_ACTOR` / `GITHUB_TOKEN`.
2. Depend on `com.lastlock.dev:lastlock-mobile-sdk-android:0.0.1-rn`.
3. Set `android.minSdkVersion=26` in `android/gradle.properties`.
4. Add a Kotlin module that extends the codegen class `NativeLastLockMobileSdkSpec`, and a `BaseReactPackage` whose `ReactModuleInfo` marks the module as a TurboModule.
5. Give the library a `codegenConfig` named `LastLockMobileSdkSpec` with `jsSrcsDir` pointing at the TurboModule spec. Apply `com.facebook.react` when `newArchEnabled` is true so codegen runs.
6. Rebuild the app. The generated `Android-autolinking.cmake` must list `react_codegen_LastLockMobileSdkSpec`. An incremental Gradle build can keep an older file that omits it, and JavaScript then reports that the native module is missing. `./gradlew clean` from `android/` forces that file to regenerate.

### iOS

1. Add the Swift package `https://github.com/last-lock/link-mobile-sdk-dev.git`, exact version `0.0.1-rn`, product `LastLockMobileSdk`, to the app target.
2. Set the deployment target to iOS 17 in `ios/Podfile.properties.json` (`ios.deploymentTarget`) and on the app target. The Podfile reads that property, falling back to 16.4, which CocoaPods will reject for this SDK.
3. Add `NSBluetoothAlwaysUsageDescription` and `NSBluetoothPeripheralUsageDescription` to `Info.plist`. Without them Core Bluetooth stays off and `onBLEStateChanged` reports `false`.
4. Implement the module in Objective-C++. The generated spec header is ObjC++ and Swift cannot import it. Subclass `NativeLastLockMobileSdkSpecBase`, conform to `NativeLastLockMobileSdkSpec`, and implement `getTurboModule:` by returning `NativeLastLockMobileSdkSpecJSI`. Put the Swift SDK calls in a separate controller, as `LastLockMobileSdkController` does.
5. Compile those sources in the app target, next to the Swift package. A stored Swift property must not be named `bridge`.
6. Include the library in iOS codegen. `react-native.config.js` setting `platforms.ios` to `null` skips codegen, and the spec header is never generated. A podspec that does not also compile the bridge sources is enough. `pod install` must log `Processing LastLockMobileSdkSpec`.

### JavaScript

Define the spec with `CodegenTypes.EventEmitter` for each event, and load the module with `TurboModuleRegistry.get('LastLockMobileSdk')`. Subscribe with `native.onDeviceDiscovered(listener)`.

## Rebuild checklist

- `BLE state: true` appears before any `Device discovered` log.
- `Device discovered` includes identifier `180` when the lock is advertising service `4801`.
- Android shows the same identifier. The name on Android is the peripheral address. The name on iOS is the Core Bluetooth identifier.
