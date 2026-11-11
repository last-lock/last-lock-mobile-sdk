package com.example.linkmobile_android_example

import android.app.Application
import com.lastlock.mobile.sdk.LastLockMobileSdk

class ExampleApplication: Application() {
    override fun onCreate() {
        super.onCreate()

       val linkAddress = "plane.lastlock.com"
        LastLockMobileSdk.init(this, linkAddress)
    }
}