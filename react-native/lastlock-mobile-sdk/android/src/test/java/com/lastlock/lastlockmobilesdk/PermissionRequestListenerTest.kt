package com.lastlock.lastlockmobilesdk

import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequestListenerTest {
  private val scan = "android.permission.BLUETOOTH_SCAN"
  private val connect = "android.permission.BLUETOOTH_CONNECT"
  private val notifications = "android.permission.POST_NOTIFICATIONS"
  private val requested = listOf(scan, connect)
  private var result: Boolean? = null

  private fun listener(granted: Set<String> = requested.toSet()) =
    PermissionRequestListener(4401, requested, { it in granted }) { result = it }

  @Test
  fun notificationResultDoesNotCompleteBluetoothRequest() {
    val listener = listener()
    assertFalse(listener.onRequestPermissionsResult(13, arrayOf(notifications), intArrayOf(PERMISSION_GRANTED)))
    assertNull(result)

    assertTrue(listener.onRequestPermissionsResult(4401, requested.toTypedArray(), intArrayOf(0, 0)))
    assertEquals(true, result)
  }

  @Test
  fun matchingRequestCodeWithWrongPermissionsCannotStartBluetooth() {
    listener().onRequestPermissionsResult(4401, arrayOf(notifications), intArrayOf(PERMISSION_GRANTED))
    assertEquals(false, result)
  }

  @Test
  fun successfulCallbackCannotOverrideMissingRuntimeGrant() {
    listener(setOf(scan)).onRequestPermissionsResult(4401, requested.toTypedArray(), intArrayOf(0, 0))
    assertEquals(false, result)
  }

  @Test
  fun deniedPermissionCannotStartBluetooth() {
    listener().onRequestPermissionsResult(4401, requested.toTypedArray(), intArrayOf(PERMISSION_GRANTED, PERMISSION_DENIED))
    assertEquals(false, result)
  }

  @Test
  fun canceledRequestCannotStartBluetooth() {
    listener().onRequestPermissionsResult(4401, emptyArray(), intArrayOf())
    assertEquals(false, result)
  }

  @Test
  fun incompleteResultsCannotStartBluetooth() {
    listener().onRequestPermissionsResult(4401, requested.toTypedArray(), intArrayOf(PERMISSION_GRANTED))
    assertEquals(false, result)
  }

  @Test
  fun grantsAreMatchedByPermissionNameRegardlessOfOrder() {
    listener().onRequestPermissionsResult(4401, arrayOf(connect, scan), intArrayOf(0, 0))
    assertEquals(true, result)
  }
}
