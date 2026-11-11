import LastLockMobileSdk
import SwiftUI

struct DeviceTile: View {
    let device: DiscoveredDevice
    let isEnabled: Bool
    let isUnlocking: Bool
    let onUnlock: () async -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text("Device: \(device.identifier)")
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button {
                Task { await onUnlock() }
            } label: {
                HStack(spacing: 8) {
                    if isUnlocking {
                        ProgressView()
                            .tint(.black)
                    }
                    Text("BLE Unlock")
                }
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 18)
                .frame(minHeight: 44)
            }
            .buttonStyle(.plain)
            .foregroundStyle(.black)
            .background(Color(red: 0.68, green: 0.82, blue: 0.69), in: Capsule())
            .disabled(!isEnabled)
            .opacity(isEnabled ? 1 : 0.5)
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 20))
    }
}

struct ContentView: View {
    @StateObject private var applicationController = ApplicationController()

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Discovered Locks")
                .font(.title2)
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 24)

            if applicationController.allDevices.isEmpty {
                Spacer()
                Text("No locks discovered yet…")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(applicationController.allDevices, id: \.identifier) { device in
                            DeviceTile(
                                device: device,
                                isEnabled: applicationController.temporaryCredentialReady
                                    && !applicationController.isBusy,
                                isUnlocking: applicationController.unlockingDeviceID
                                    == String(describing: device.identifier),
                                onUnlock: {
                                    await applicationController.performTemporaryCredentialUnlock(device: device)
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 12)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground))
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(alignment: .leading, spacing: 10) {
                if let status = applicationController.status {
                    Text(status)
                        .font(.footnote)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Button {
                    Task { await applicationController.createTemporaryUnlockCredential() }
                } label: {
                    HStack(spacing: 8) {
                        if applicationController.isIssuingPass {
                            ProgressView()
                                .tint(.black)
                        }
                        Text("Issue a 7 day unlock pass")
                    }
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.black)
                .background(Color(red: 0.68, green: 0.82, blue: 0.69), in: Capsule())
                .disabled(applicationController.isBusy)
                .opacity(applicationController.isBusy ? 0.5 : 1)
            }
            .padding(12)
            .background(Color(uiColor: .systemBackground))
        }
    }
}
