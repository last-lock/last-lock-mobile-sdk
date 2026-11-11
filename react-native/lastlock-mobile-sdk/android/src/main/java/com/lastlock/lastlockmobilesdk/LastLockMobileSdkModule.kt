package com.lastlock.lastlockmobilesdk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log as Logger
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.common.LifecycleState
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import android.util.Base64
import com.lastlock.mobile.sdk.LastLockMobileSdk
import com.lastlock.mobile.sdk.MobileException
import com.lastlock.mobile.sdk.MobileErrorCode
import com.lastlock.mobile.sdk.ILastLockMobileSdk
import com.lastlock.mobile.sdk.DiscoveredDevice
import com.lastlock.mobile.sdk.DeviceInformation
import com.lastlock.mobile.sdk.ReaderGroupTrust
import com.lastlock.mobile.sdk.TokenUnlockPass
import com.lastlock.mobile.sdk.CheckDFUResult
import com.lastlock.mobile.sdk.DFUUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class LastLockMobileSdkModule(reactContext: ReactApplicationContext) :
  NativeLastLockMobileSdkSpec(reactContext) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val lastLockMobileSdk: ILastLockMobileSdk = LastLockMobileSdk
  private val scanStarted = AtomicBoolean(false)
  private val permissionRequestInFlight = AtomicBoolean(false)
  private var serverAddress: String? = null

  /** Detaches this module and queues SDK cleanup that can outlive the React Native instance. */
  override fun invalidate() {
    if (activeModule === this) {
      detachListeners()
      shutdownBarrier.launch {
        runCatching { lastLockMobileSdk.shutdown() }.onFailure { Logger.w(NAME, "SDK cleanup failed: ${it.message}") }
      }
      activeModule = null
    }
    scope.cancel()
    super.invalidate()
  }

  /** Waits for earlier cleanup before configuring the SDK and attaching this module's listeners. */
  override fun initialize(serverAddress: String, promise: Promise) {
    this.serverAddress = serverAddress
    scope.launch {
      try {
        shutdownBarrier.await()
        if (bridgeReady.compareAndSet(false, true)) {
          try { LastLockMobileSdk.init(reactApplicationContext.applicationContext, serverAddress) }
          catch (error: Exception) { bridgeReady.set(false); throw error }
        } else {
          // The facade owns cleanup; an adapter never stops BlueLink independently.
          lastLockMobileSdk.shutdown()
          LastLockMobileSdk.setServerAddress(serverAddress)
          scanStarted.set(false)
        }
        activeModule = this@LastLockMobileSdkModule
        attachListeners()
        promise.resolve(null)
      } catch (error: Exception) {
        reject(promise, error)
      }
    }
  }

  override fun start(uuid: String, promise: Promise) {
    startWithPermissions(uuid, promise, limited = false)
  }

  override fun startLimited(uuid: String, promise: Promise) {
    startWithPermissions(uuid, promise, limited = true)
  }

  private fun startWithPermissions(uuid: String, promise: Promise, limited: Boolean) {
    if (serverAddress == null) {
      promise.reject("NOT_INITIALIZED", "LastLockMobileSdk not initialized. Call initialize first.")
      return
    }
    if (limited && reactApplicationContext.lifecycleState != LifecycleState.RESUMED) {
      promise.reject("OPERATION_FAILED", "Call startLimited while the app is in the foreground.")
      return
    }
    val missing = requiredPermissions(limited).filterNot(::hasPermission)
    if (missing.isEmpty()) {
      startScan(uuid, promise, limited)
      return
    }
    if (!permissionRequestInFlight.compareAndSet(false, true)) {
      promise.reject("OPERATION_BUSY", "A permission request is already in progress.")
      return
    }
    // Android 10/11 requires foreground location to be granted first.
    // Android 11 handles the separate background grant in system Settings.
    val background = Manifest.permission.ACCESS_BACKGROUND_LOCATION
    val foreground = missing.filterNot { it == background }
    val firstStage = if (foreground.isNotEmpty()) foreground else missing
    val listener =
      PermissionRequestListener(PERMISSION_REQUEST, firstStage, ::hasPermission) { granted ->
        if (!granted) {
          permissionRequestInFlight.set(false)
          promise.reject("PERMISSION_DENIED", "Bluetooth permissions were denied.")
        } else if (background in missing && background !in firstStage) {
          // Never request background and foreground location in the same dialog.
          val backgroundListener =
            PermissionRequestListener(PERMISSION_REQUEST, listOf(background), ::hasPermission) { backgroundGranted ->
              permissionRequestInFlight.set(false)
              if (backgroundGranted) {
                startScan(uuid, promise, limited)
              } else {
                promise.reject("PERMISSION_DENIED", "Background location is required for full scanning. Enable Allow all the time in Settings.")
              }
            }
          // ReactActivity clears the completed listener after this callback returns.
          // Register the next listener afterwards so it is not cleared as well.
          Handler(Looper.getMainLooper()).post {
            requestPermissions(listOf(background), backgroundListener, promise)
          }
        } else {
          permissionRequestInFlight.set(false)
          startScan(uuid, promise, limited)
        }
      }
    requestPermissions(firstStage, listener, promise)
  }

  private fun requestPermissions(permissions: List<String>, listener: PermissionListener, promise: Promise) {
    requestPermissionsSafely(
      permissions,
      PERMISSION_REQUEST,
      listener,
      currentActivity = {
        reactApplicationContext.currentActivity
          ?.takeUnless { it.isFinishing || it.isDestroyed } as? PermissionAwareActivity
      },
      onFailure = { error ->
        permissionRequestInFlight.set(false)
        reject(promise, error)
      },
    )
  }

  override fun identify(identifier: String, promise: Promise) {
    if (!requireInitialized(promise)) return
    scope.launch {
      try {
        withContext(Dispatchers.IO) { lastLockMobileSdk.identify(identifier) }
        promise.resolve(null)
      } catch (error: Exception) { reject(promise, error) }
    }
  }

  override fun setLockEngagedState(identifier: String, engaged: Boolean, promise: Promise) {
    if (!requireInitialized(promise)) return
    scope.launch {
      try {
        withContext(Dispatchers.IO) { lastLockMobileSdk.setLockEngagedState(identifier, engaged) }
        promise.resolve(null)
      } catch (error: Exception) { reject(promise, error) }
    }
  }

  override fun checkForDFU(identifier: String, promise: Promise) {
    if (!requireInitialized(promise)) return
    scope.launch {
      try {
        val result =
          withContext(Dispatchers.IO) {
            lastLockMobileSdk.checkForDFU(identifier)
          }
        val body = Arguments.createMap()
        when (result) {
          is CheckDFUResult.Timeout -> {
            body.putString("status", "timeout")
            body.putString("firmwareVersion", "")
            body.putString("availableVersion", "")
          }
          is CheckDFUResult.Success -> {
            val state = lastLockMobileSdk.getDFUState()
            body.putString("status", if (state.dfuAvailable) "update_available" else "up_to_date")
            body.putString("firmwareVersion", result.firmwareVersion?.toString() ?: "")
            body.putString("availableVersion", state.dfuImage?.dfuVersion ?: "")
          }
        }
        promise.resolve(body)
      } catch (error: Exception) {
        reject(promise, error)
      }
    }
  }

  override fun startDFU(identifier: String, promise: Promise) {
    if (!requireInitialized(promise)) return
    scope.launch {
      try {
        withContext(Dispatchers.IO) { lastLockMobileSdk.startDFU(identifier) }
        promise.resolve(null)
      } catch (error: Exception) { reject(promise, error) }
    }
  }

  override fun waitForDeviceMode(identifier: String, dfuRequired: Boolean, promise: Promise) {
    if (!requireInitialized(promise)) return
    scope.launch {
      try {
        withContext(Dispatchers.IO) { lastLockMobileSdk.waitForDeviceMode(identifier, dfuRequired) }
        promise.resolve(null)
      } catch (error: Exception) { reject(promise, error) }
    }
  }

  override fun exitDFUInstallMode(identifier: String, promise: Promise) {
    if (!requireInitialized(promise)) return
    scope.launch {
      try {
        withContext(Dispatchers.IO) { lastLockMobileSdk.exitDFUInstallMode(identifier) }
        promise.resolve(null)
      } catch (error: Exception) { reject(promise, error) }
    }
  }

  override fun getOrCreatePassPublicKey(passId: String, promise: Promise) {
    if (passId.isBlank()) {
      promise.reject("INVALID_ARGUMENT", "Pass ID is required.")
      return
    }
    scope.launch {
      try {
        val publicKey =
          withContext(Dispatchers.IO) {
            lastLockMobileSdk.getOrCreatePassPublicKey(passId)
          }
        if (publicKey.isEmpty()) {
          promise.reject("STORAGE_FAILED", "Pass public key is empty.")
          return@launch
        }
        promise.resolve(Base64.encodeToString(publicKey, Base64.NO_WRAP))
      } catch (error: Exception) {
        reject(promise, error)
      }
    }
  }

  override fun storePass(passId: String, pass: ReadableMap, promise: Promise) {
    scope.launch {
      try {
        withContext(Dispatchers.IO) { lastLockMobileSdk.storePass(passId, decodePass(pass)) }
        promise.resolve(null)
      } catch (error: Exception) { reject(promise, error) }
    }
  }

  private fun encodePass(pass: TokenUnlockPass): WritableMap = Arguments.createMap().apply {
    putString("accessDocument", Base64.encodeToString(pass.accessDocument, Base64.NO_WRAP))
    putArray("readerGroups", Arguments.createArray().apply {
      pass.readerGroups.forEach { group ->
        pushMap(Arguments.createMap().apply {
          putString("readerGroupIdentifier", Base64.encodeToString(group.readerGroupIdentifier, Base64.NO_WRAP))
          putString("issuerCAPublicKey", Base64.encodeToString(group.issuerCAPublicKey, Base64.NO_WRAP))
          group.issuerCACertificateDer?.let { putString("issuerCACertificateDer", Base64.encodeToString(it, Base64.NO_WRAP)) }
        })
      }
    })
  }

  override fun getPass(passId: String, promise: Promise) {
    scope.launch {
      try {
        val pass = withContext(Dispatchers.IO) { lastLockMobileSdk.getPass(passId) }
        promise.resolve(pass?.let { encodePass(it) })
      } catch (error: Exception) { reject(promise, error) }
    }
  }

  override fun deletePass(passId: String, promise: Promise) {
    scope.launch {
      try {
        withContext(Dispatchers.IO) { lastLockMobileSdk.deletePass(passId) }
        promise.resolve(null)
      } catch (error: Exception) { reject(promise, error) }
    }
  }

  override fun shutdown(promise: Promise) {
    scope.launch {
      try {
        detachListeners()
        if (serverAddress != null) lastLockMobileSdk.shutdown()
        scanStarted.set(false)
        promise.resolve(null)
      } catch (error: Exception) { reject(promise, error) }
    }
  }

  override fun unlock(identifier: String, passId: String, promise: Promise) {
    if (!requireInitialized(promise)) return
    if (passId.isBlank()) {
      promise.reject("INVALID_ARGUMENT", "Pass ID is required.")
      return
    }
    scope.launch {
      try {
        withContext(Dispatchers.IO) {
          lastLockMobileSdk.unlock(identifier, passId)
        }
        promise.resolve(null)
      } catch (error: Exception) {
        reject(promise, error)
      }
    }
  }

  override fun cancelDFU(promise: Promise) {
    try {
      lastLockMobileSdk.cancelDFU()
      promise.resolve(null)
    } catch (error: Exception) {
      reject(promise, error)
    }
  }

  private fun reject(promise: Promise, error: Exception) {
    val code = when (error) {
      is MobileException -> error.code.name
      is SecurityException -> MobileErrorCode.PERMISSION_DENIED.name
      is IllegalArgumentException -> MobileErrorCode.INVALID_ARGUMENT.name
      else -> MobileErrorCode.OPERATION_FAILED.name
    }
    promise.reject(code, error.message, error)
  }

  private fun requireInitialized(promise: Promise): Boolean {
    if (serverAddress != null) return true
    promise.reject("NOT_INITIALIZED", "LastLockMobileSdk not initialized. Call initialize first.")
    return false
  }

  override fun isDeviceBlacklisted(identifier: String, promise: Promise) {
    if (!requireInitialized(promise)) return
    scope.launch {
      try { promise.resolve(lastLockMobileSdk.isDeviceBlacklisted(identifier)) }
      catch (error: Exception) { reject(promise, error) }
    }
  }

  /** Waits for pending cleanup, rechecks permissions, and starts or promotes the requested scan mode. */
  private fun startScan(uuid: String, promise: Promise, limited: Boolean) {
    scope.launch {
      try {
        shutdownBarrier.await()
        // Recheck at the point of use, including the path that skips the dialog.
        // A service lifecycle exception is asynchronous and cannot be caught here.
        val missing = missingStartupPermissions(
          Build.VERSION.SDK_INT, limited, requiredPermissions(limited).toList(), ::hasPermission,
        )
        if (missing.isNotEmpty()) {
          promise.reject("PERMISSION_DENIED", "Permissions are required before scanning: ${missing.joinToString()}")
          return@launch
        }
        val address = serverAddress
        if (limited) {
          attachListeners()
          lastLockMobileSdk.startLimited(uuid)
          scanStarted.set(true)
          promise.resolve(null)
        } else if (address != null && scanStarted.get()) {
          LastLockMobileSdk.restart(address, uuid)
          attachListeners()
          // Ensure a limited session is promoted to a foreground service, or
          // recreate the service if Android stopped it while the app was hidden.
          lastLockMobileSdk.start(uuid)
          promise.resolve(null)
        } else {
          attachListeners()
          lastLockMobileSdk.start(uuid)
          scanStarted.set(true)
          promise.resolve(null)
        }
      } catch (error: Exception) {
        reject(promise, error)
      }
    }
  }

  private fun detachListeners() {
    lastLockMobileSdk.onServerConnectionChange(null)
    lastLockMobileSdk.onDeviceDiscovered(null)
    lastLockMobileSdk.onDeviceInformation(null)
    lastLockMobileSdk.onBLEStateChanged(null)
    lastLockMobileSdk.onDeviceConnected(null)
    lastLockMobileSdk.onDeviceDisconnected(null)
    lastLockMobileSdk.onUnlockResult(null)
    lastLockMobileSdk.onDFUStateChanged(null)
  }

  private fun attachListeners() {
    lastLockMobileSdk.onServerConnectionChange { connected ->
      emitProxyStream(Arguments.createMap().apply { putString("state", if (connected) "connected" else "disconnected") })
    }
    lastLockMobileSdk.onDeviceDiscovered { emitDiscovered(it.toMap()) }
    lastLockMobileSdk.onBLEStateChanged { emitBleState(Arguments.createMap().apply { putBoolean("state", it) }) }
    lastLockMobileSdk.onDeviceConnected { device -> emitConnected(Arguments.createMap().apply {
      putString("identifier", device.identifier); putString("name", device.name)
    }) }
    lastLockMobileSdk.onDeviceDisconnected { device -> emitDisconnected(Arguments.createMap().apply {
      putString("identifier", device.identifier); putString("name", device.name)
    }) }
    lastLockMobileSdk.onDeviceInformation { info -> emitDeviceInformation(info.toMap()) }
    lastLockMobileSdk.onUnlockResult { event -> emitUnlockResult(Arguments.createMap().apply {
      putString("identifier", event.identifier); putString("name", event.name)
      putString("passId", event.passId); putString("result", event.result.name); putString("message", event.message)
    }) }
    lastLockMobileSdk.onDFUStateChanged { state -> emitDfuState(state.toMap()) }
  }

  private fun hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(reactApplicationContext, permission) ==
      PackageManager.PERMISSION_GRANTED

  private fun requiredPermissions(limited: Boolean): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
      ) + if (limited) emptyArray<String>() else arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      arrayOf(
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.ACCESS_FINE_LOCATION,
      ) + if (limited) emptyArray<String>() else arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
      arrayOf(
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.ACCESS_FINE_LOCATION,
      )
    }
  }

  private fun emitDiscovered(body: WritableMap) {
    if (mEventEmitterCallback != null) {
      emitOnDeviceDiscovered(body)
    }
  }

  private fun emitConnected(body: WritableMap) {
    if (mEventEmitterCallback != null) {
      emitOnDeviceConnected(body)
    }
  }

  private fun emitDisconnected(body: WritableMap) {
    if (mEventEmitterCallback != null) {
      emitOnDeviceDisconnected(body)
    }
  }

  private fun emitBleState(body: WritableMap) {
    if (mEventEmitterCallback != null) {
      emitOnBLEStateChanged(body)
    }
  }

  private fun emitUnlockResult(body: WritableMap) {
    if (mEventEmitterCallback != null) {
      emitOnUnlockResult(body)
    }
  }

  private fun emitDfuState(body: WritableMap) {
    if (mEventEmitterCallback != null) {
      emitOnDFUStateChanged(body)
    }
  }

  private fun emitDeviceInformation(body: WritableMap) {
    if (mEventEmitterCallback != null) {
      emitOnDeviceInformation(body)
    }
  }

  private fun emitProxyStream(body: WritableMap) {
    if (mEventEmitterCallback != null) {
      emitOnServerConnectionChange(body)
    }
  }

  private fun DiscoveredDevice.toMap(): WritableMap = Arguments.createMap().apply {
    putString("identifier", identifier)
    putString("name", name)
    putString("linkDeviceId", linkDeviceId)
    putString("connectionStatus", connectionStatus)
    putString("signalStrength", signalStrength)
    putString("overridingMode", overridingMode)
    putBoolean("installMode", installMode)
    putBoolean("dfuMode", dfuMode)
    putBoolean("hibernate", hibernate)
    putBoolean("serviceMode", serviceMode)
    putBoolean("lowBattery", lowBattery)
    putBoolean("criticalBattery", criticalBattery)
    putBoolean("cooldown", cooldown)
    putBoolean("emergency", emergency)
    putBoolean("busy", busy)
    putBoolean("timeSyncRequest", timeSyncRequest)
    putInt("rssi", rssi)
    putDouble("deviceStateCounter", deviceStateCounter.toDouble())
    putDouble("serverStateCounter", serverStateCounter.toDouble())
    putDouble("oobTimestamp", oobTimestamp?.toDouble() ?: 0.0)
  }

  private fun DeviceInformation.toMap(): WritableMap = Arguments.createMap().apply {
    putString("identifier", identifier)
    putString("firmwareVersion", firmwareVersion)
    putString("bootloaderVersion", bootloaderVersion)
    putDouble("deviceStateCounter", deviceStateCounter.toDouble())
    putDouble("serverStateCounter", serverStateCounter.toDouble())
    oobTimestamp?.let { putDouble("oobTimestamp", it.toDouble()) } ?: putNull("oobTimestamp")
  }

  private fun DFUUiState.toMap(): WritableMap {
    val body = Arguments.createMap()
    body.putString("flowState", flowState.name)
    body.putDouble("installProgress", installProgress.toDouble())
    body.putDouble("postInstallProgress", postInstallProgress.toDouble())
    body.putBoolean("dfuAvailable", dfuAvailable)
    body.putBoolean("dfuImageLoading", dfuImageLoading)
    body.putString("availableVersion", dfuImage?.dfuVersion ?: "")
    return body
  }

  companion object {
    /** Converts the JS pass without dropping groups or requiring optional certificates. */
    internal fun decodePass(pass: ReadableMap): TokenUnlockPass {
      val groups = requireNotNull(pass.getArray("readerGroups")) { "readerGroups is required." }
      return TokenUnlockPass(
        accessDocument = passBytes(pass, "accessDocument"),
        readerGroups = (0 until groups.size()).map { index ->
          val group = requireNotNull(groups.getMap(index)) { "Reader group $index is required." }
          ReaderGroupTrust(
            readerGroupIdentifier = passBytes(group, "readerGroupIdentifier"),
            issuerCAPublicKey = passBytes(group, "issuerCAPublicKey"),
            issuerCACertificateDer = if (group.hasKey("issuerCACertificateDer") && !group.isNull("issuerCACertificateDer")) {
              passBytes(group, "issuerCACertificateDer")
            } else null,
          )
        },
      )
    }

    private fun passBytes(value: ReadableMap, field: String): ByteArray {
      val encoded = requireNotNull(value.getString(field)) { "$field is required." }
      return try {
        java.util.Base64.getDecoder().decode(encoded)
      } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("$field is not valid Base64.", error)
      }
    }

    const val NAME = "LastLockMobileSdk"
    private const val PERMISSION_REQUEST = 4401
    private val shutdownBarrier by lazy {
      ModuleShutdownBarrier(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }
    private val bridgeReady = AtomicBoolean(false)
    private var activeModule: LastLockMobileSdkModule? = null
  }
}
