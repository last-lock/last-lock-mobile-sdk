package com.lastlock.lastlockmobilesdk

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.FOREGROUND_SERVICE
import android.Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import org.junit.Assert.*
import org.junit.Test

class PermissionRequestsTest {
  private val runtime = listOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT)

  @Test
  fun missingConnectedDeviceDeclarationIsDetectedDespiteRuntimeGrants() {
    assertEquals(
      listOf(FOREGROUND_SERVICE_CONNECTED_DEVICE),
      missingStartupPermissions(34, false, runtime) { it != FOREGROUND_SERVICE_CONNECTED_DEVICE },
    )
  }

  @Test
  fun missingBaseServiceDeclarationIsDetectedDespiteRuntimeGrants() {
    assertEquals(
      listOf(FOREGROUND_SERVICE),
      missingStartupPermissions(33, false, runtime) { it != FOREGROUND_SERVICE },
    )
  }

  @Test
  fun serviceDeclarationsRespectVersionBoundaries() {
    assertEquals(emptyList<String>(), missingStartupPermissions(27, false, emptyList()) { false })
    assertEquals(listOf(FOREGROUND_SERVICE), missingStartupPermissions(28, false, emptyList()) { false })
    assertEquals(listOf(FOREGROUND_SERVICE), missingStartupPermissions(33, false, emptyList()) { false })
    assertEquals(
      listOf(FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE),
      missingStartupPermissions(34, false, emptyList()) { false },
    )
  }

  @Test
  fun limitedModeDoesNotRequireServiceDeclarations() {
    assertEquals(emptyList<String>(), missingStartupPermissions(36, true, runtime) { it in runtime })
  }

  @Test
  fun revokedRuntimeGrantIsStillRejectedWhenServiceDeclarationsAreGranted() {
    assertEquals(listOf(BLUETOOTH_SCAN), missingStartupPermissions(36, false, runtime) { it != BLUETOOTH_SCAN })
    assertEquals(emptyList<String>(), missingStartupPermissions(36, false, runtime) { true })
  }

  private class Activity : PermissionAwareActivity {
    var requests = 0
    var failure: Exception? = null
    var requested: List<String>? = null
    var code: Int? = null
    var listener: PermissionListener? = null
    override fun checkPermission(permission: String, pid: Int, uid: Int) = 0
    override fun checkSelfPermission(permission: String) = 0
    override fun shouldShowRequestPermissionRationale(permission: String) = false
    override fun requestPermissions(permissions: Array<String>, requestCode: Int, listener: PermissionListener?) {
      failure?.let { throw it }
      requests++
      requested = permissions.toList()
      code = requestCode
      this.listener = listener
    }
  }

  private val listener = PermissionListener { _, _, _ -> true }

  @Test
  fun deferredRequestUsesReplacementActivityAtExecutionTime() {
    val old = Activity()
    val replacement = Activity()
    var current: PermissionAwareActivity? = old
    val deferred = {
      requestPermissionsSafely(runtime, 4401, listener, { current }) { throw AssertionError(it) }
    }
    current = replacement
    deferred()

    assertEquals(0, old.requests)
    assertEquals(1, replacement.requests)
    assertEquals(runtime, replacement.requested)
    assertEquals(4401, replacement.code)
    assertSame(listener, replacement.listener)
  }

  @Test
  fun missingActivityCompletesFailureRatherThanLeavingRequestPending() {
    var error: Exception? = null
    requestPermissionsSafely(runtime, 4401, listener, { null }) { error = it }
    assertTrue(error is IllegalStateException)
  }

  @Test
  fun requestExceptionIsReportedAndNextRequestCanSucceed() {
    val activity = Activity()
    val cause = IllegalStateException("activity unavailable")
    activity.failure = cause
    var error: Exception? = null
    requestPermissionsSafely(runtime, 4401, listener, { activity }) { error = it }
    assertSame(cause, error)
    activity.failure = null
    requestPermissionsSafely(runtime, 4401, listener, { activity }) { throw AssertionError(it) }
    assertEquals(1, activity.requests)
  }
}
