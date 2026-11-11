package com.example.linkmobile_android_example

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastlock.mobile.sdk.LastLockMobileSdk
import com.lastlock.mobile.sdk.DiscoveredDevice
import com.lastlock.mobile.sdk.ILastLockMobileSdk
import com.lastlock.mobile.sdk.UnlockEvent
import com.lastlock.mobile.sdk.UnlockPass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TokenUnlockUiState(
    val isBusy: Boolean = false,
    val temporaryCredentialReady: Boolean = false,
    val status: String? = null,
)

class ApplicationViewModel(
    context: Context,
    businessServerUrl: String,
) : ViewModel() {
    private val lastLockMobileSdk: ILastLockMobileSdk = LastLockMobileSdk
    private val tokenUnlockClient = TokenUnlockClient(businessServerUrl)
    private val temporaryCredentialStore = TemporaryUnlockCredentialStore(context)

    private val _allDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val allDevices = _allDevices.asStateFlow()

    private val _tokenUnlockUiState = MutableStateFlow(TokenUnlockUiState())
    val tokenUnlockUiState = _tokenUnlockUiState.asStateFlow()

    init {
        lastLockMobileSdk.onDeviceDiscovered { device ->
            viewModelScope.launch(Dispatchers.Main) {
                _allDevices.update { devices -> devices.filterNot { it.identifier == device.identifier } + device }
            }
        }
        lastLockMobileSdk.onUnlockResult(::onUnlockResult)
        refreshTemporaryCredentialState()
    }

    /** Creates or reuses the persisted credential and stores its seven-day unlock pass. */
    fun createTemporaryUnlockCredential() {
        viewModelScope.launch {
            setBusy("Creating temporary BLE credential…")

            try {
                val passId = temporaryCredentialStore.getOrCreatePassId()
                val publicKey = lastLockMobileSdk.getOrCreatePassPublicKey(passId)
                val pass: UnlockPass = tokenUnlockClient.issueTemporaryUnlockPass(publicKey)

                lastLockMobileSdk.storePass(passId, pass)
                _tokenUnlockUiState.update {
                    it.copy(
                        isBusy = false,
                        temporaryCredentialReady = true,
                        status = "Temporary unlock pass is ready for seven days.",
                    )
                }
            } catch (error: Exception) {
                _tokenUnlockUiState.update {
                    it.copy(isBusy = false, temporaryCredentialReady = false, status = error.userMessage())
                }
                Log.e(TAG, "Unable to create temporary unlock credential", error)
            }
        }
    }

    /** Queues a BLE unlock using the persisted temporary credential. */
    fun performTemporaryUnlock(device: DiscoveredDevice) {
        viewModelScope.launch {
            val passId = temporaryCredentialStore.existingPassId() ?: run {
                setStatus("Create a temporary unlock pass before unlocking.")
                return@launch
            }

            if (lastLockMobileSdk.getPass(passId) == null) {
                refreshTemporaryCredentialState()
                setStatus("Create a temporary unlock pass before unlocking.")
                return@launch
            }

            setBusy("Unlocking ${device.identifier} with the temporary credential…")
            try {
                lastLockMobileSdk.unlock(device.identifier, passId)
            } catch (error: Exception) {
                _tokenUnlockUiState.update {
                    it.copy(isBusy = false, status = error.userMessage())
                }
                Log.e(TAG, "Unable to queue temporary unlock", error)
            }
        }
    }

    private fun onUnlockResult(event: UnlockEvent) {
        if (event.passId != temporaryCredentialStore.existingPassId()) return
        _tokenUnlockUiState.update {
            it.copy(isBusy = false, status = "${event.identifier}: ${event.message}")
        }
    }

    /** Releases SDK activity and logs cleanup failures after the ViewModel's own scope ends. */
    override fun onCleared() {
        lastLockMobileSdk.onDeviceDiscovered(null)
        lastLockMobileSdk.onUnlockResult(null)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                lastLockMobileSdk.shutdown()
            } catch (error: Exception) {
                Log.w(TAG, "SDK shutdown failed", error)
            }
        }
        super.onCleared()
    }

    private fun refreshTemporaryCredentialState() {
        viewModelScope.launch {
            val passId = temporaryCredentialStore.existingPassId()
            val hasPass = passId != null && lastLockMobileSdk.getPass(passId) != null
            _tokenUnlockUiState.update { it.copy(temporaryCredentialReady = hasPass) }
        }
    }

    private fun setBusy(status: String) {
        _tokenUnlockUiState.update { it.copy(isBusy = true, status = status) }
    }

    private fun setStatus(status: String) {
        _tokenUnlockUiState.update { it.copy(status = status) }
    }

    private fun Exception.userMessage(): String = message ?: "An unexpected error occurred."

    private companion object {
        const val TAG = "LastLockMobileSdkExample"
    }
}
