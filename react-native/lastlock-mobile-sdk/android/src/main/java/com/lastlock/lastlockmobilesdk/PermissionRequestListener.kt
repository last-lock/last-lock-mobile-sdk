package com.lastlock.lastlockmobilesdk

import android.content.pm.PackageManager
import com.facebook.react.modules.core.PermissionListener

/** Only accepts results for this request, and verifies the permissions are actually granted. */
internal class PermissionRequestListener(
  private val requestCode: Int,
  private val requestedPermissions: List<String>,
  private val hasPermission: (String) -> Boolean,
  private val onResult: (Boolean) -> Unit,
) : PermissionListener {
  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray,
  ): Boolean {
    // ReactActivity forwards every result to its current listener, including results
    // from other modules (for example Expo's notification permission request).
    if (requestCode != this.requestCode) return false

    val granted =
      requestedPermissions.isNotEmpty() &&
        permissions.size == grantResults.size &&
        requestedPermissions.all { permission ->
          val index = permissions.indexOf(permission)
          index >= 0 && grantResults[index] == PackageManager.PERMISSION_GRANTED &&
            hasPermission(permission)
        }
    onResult(granted)
    return true
  }
}
