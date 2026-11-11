package com.example.linkmobile_android_example

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.linkmobile_android_example.ui.theme.LastLockMobileSdkAndroidExampleTheme
import com.lastlock.mobile.sdk.LastLockMobileSdk

internal fun requiredLinkPermissions(sdkInt: Int): List<String> = when {
    sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.POST_NOTIFICATIONS,
    )
    sdkInt >= Build.VERSION_CODES.S -> listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )
    else -> listOf(
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) + if (sdkInt >= Build.VERSION_CODES.Q) {
        listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        emptyList()
    }
}

internal fun nextLinkPermissionRequest(missing: List<String>): List<String> {
    // Grant foreground location before requesting background access.
    val foreground = missing.filterNot { it == Manifest.permission.ACCESS_BACKGROUND_LOCATION }
    return foreground.ifEmpty { missing }
}

class MainActivity : ComponentActivity() {
    private val businessServerUrl = "http://<local_server_domain>:3006"
    private val applicationViewModel by lazy {
        ApplicationViewModel(
            context = applicationContext,
            businessServerUrl = businessServerUrl,
        )
    }
    private var permissionsGranted = false
    private val permissions = requiredLinkPermissions(Build.VERSION.SDK_INT)

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.isNotEmpty() && results.values.all { it }) {
            // Recheck all grants before starting, or advance to background location.
            checkAndRequestPermissions()
        } else {
            Log.w("MainActivity", "Missing permissions")
            showPermissionRecovery()
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun checkAndRequestPermissions() {
        val missingPermissions = permissions.filterNot(::hasPermission)

        if (missingPermissions.isEmpty()) {
            if (!permissionsGranted) {
                Log.d("MainActivity", "Permissions granted!")
                startLastLockMobileSdk()
                permissionsGranted = true
            }
        } else {
            val nextStage = nextLinkPermissionRequest(missingPermissions)
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R &&
                nextStage == listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) {
                // Android 11 requires Allow all the time to be enabled in Settings.
                showPermissionRecovery()
            } else {
                requestPermissionLauncher.launch(nextStage.toTypedArray())
            }
        }
    }

    private fun showPermissionRecovery() {
        val missing = permissions.filterNot(::hasPermission)
        if (missing.isEmpty()) {
            checkAndRequestPermissions()
            return
        }
        val backgroundLocationMissing = Build.VERSION.SDK_INT == Build.VERSION_CODES.R &&
            missing == listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val openSettings = backgroundLocationMissing || nextLinkPermissionRequest(missing).any {
            !shouldShowRequestPermissionRationale(it)
        }
        val message = when {
            backgroundLocationMissing ->
                "To discover locks in the background, allow location access all the time in app Settings."
            Manifest.permission.ACCESS_FINE_LOCATION in missing ->
                "Allow location access so this app can discover nearby locks."
            Manifest.permission.BLUETOOTH_SCAN in missing || Manifest.permission.BLUETOOTH_CONNECT in missing ->
                "Allow Nearby devices access so this app can discover and connect to locks."
            Manifest.permission.POST_NOTIFICATIONS in missing ->
                "Allow notifications so this app can show its Bluetooth service notification."
            else -> "Grant the required permissions to enable lock discovery."
        }
        AlertDialog.Builder(this)
            .setTitle("Permission required")
            .setMessage(message)
            .setPositiveButton(if (openSettings) "Open Settings" else "Try again") { _, _ ->
                if (openSettings) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } else {
                    checkAndRequestPermissions()
                }
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun startLastLockMobileSdk() {
        LastLockMobileSdk.start()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        enableEdgeToEdge()

        setContent {
            LastLockMobileSdkAndroidExampleTheme {
                DeviceScreen(viewModel = applicationViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume startup after the user grants access in Settings. Do not open
        // permission dialogs here; onResume also runs during permission requests.
        if (permissions.all(::hasPermission)) {
            checkAndRequestPermissions()
        } else {
            permissionsGranted = false
        }
    }
}
