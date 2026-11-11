package com.lastlock.lastlockmobilesdk

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class LastLockMobileSdkPackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return if (name == LastLockMobileSdkModule.NAME) LastLockMobileSdkModule(reactContext) else null
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
    return ReactModuleInfoProvider {
      mapOf(
        LastLockMobileSdkModule.NAME to
          ReactModuleInfo(
            LastLockMobileSdkModule.NAME,
            LastLockMobileSdkModule::class.java.name,
            false,
            false,
            false,
            true,
          ),
      )
    }
  }
}
