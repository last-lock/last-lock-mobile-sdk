package com.lastlock.lastlockmobilesdk

import android.Manifest
import android.os.Build
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener

/** Include install-time service declarations without putting them in runtime dialogs. */
internal fun missingStartupPermissions(
  sdkInt: Int,
  limited: Boolean,
  runtimePermissions: List<String>,
  hasPermission: (String) -> Boolean,
): List<String> = buildList {
  addAll(runtimePermissions)
  if (!limited) {
    if (sdkInt >= Build.VERSION_CODES.P) add(Manifest.permission.FOREGROUND_SERVICE)
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
    }
  }
}.distinct().filterNot(hasPermission)

/** Resolve the activity at execution time, including deferred background-location requests. */
internal fun requestPermissionsSafely(
  permissions: List<String>,
  requestCode: Int,
  listener: PermissionListener,
  currentActivity: () -> PermissionAwareActivity?,
  onFailure: (Exception) -> Unit,
) {
  try {
    val activity = currentActivity()
      ?: throw IllegalStateException("Bluetooth permissions require a current activity. Retry while the app is visible.")
    activity.requestPermissions(permissions.toTypedArray(), requestCode, listener)
  } catch (error: Exception) {
    onFailure(error)
  }
}
