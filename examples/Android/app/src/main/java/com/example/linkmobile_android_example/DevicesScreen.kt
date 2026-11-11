package com.example.linkmobile_android_example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastlock.mobile.sdk.DiscoveredDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(viewModel: ApplicationViewModel) {
    val devices by viewModel.allDevices.collectAsStateWithLifecycle()
    val tokenUnlockState by viewModel.tokenUnlockUiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Discovered Locks") }) },
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                CredentialActions(
                    state = tokenUnlockState,
                    onCreateTemporary = viewModel::createTemporaryUnlockCredential,
                )
            }
        },
    ) { padding ->
        DiscoveredDevicesList(
            devices = devices,
            state = tokenUnlockState,
            onTemporaryUnlock = viewModel::performTemporaryUnlock,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun CredentialActions(
    state: TokenUnlockUiState,
    onCreateTemporary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.status?.let { status ->
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onCreateTemporary,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Issue a 7 day unlock pass")
        }
    }
}

@Composable
private fun DiscoveredDevicesList(
    devices: List<DiscoveredDevice>,
    state: TokenUnlockUiState,
    onTemporaryUnlock: (DiscoveredDevice) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (devices.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No locks discovered yet…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items = devices,
            key = { it.identifier },
        ) { device ->
            DeviceCard(
                device = device,
                state = state,
                onTemporaryUnlock = { onTemporaryUnlock(device) },
            )
        }
    }
}

@Composable
private fun DeviceCard(
    device: DiscoveredDevice,
    state: TokenUnlockUiState,
    onTemporaryUnlock: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Device: ${device.identifier}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onTemporaryUnlock,
                enabled = state.temporaryCredentialReady && !state.isBusy,
            ) {
                Text("BLE Unlock")
            }
        }
    }
}
