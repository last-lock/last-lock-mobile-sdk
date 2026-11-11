import Foundation
import LastLockMobileSdk
import React

@objc(LastLockMobileSdkController)
class LastLockMobileSdkController: NSObject {
    @objc var onDeviceDiscovered: ((NSDictionary) -> Void)?
    @objc var onDeviceConnected: ((NSDictionary) -> Void)?
    @objc var onDeviceDisconnected: ((NSDictionary) -> Void)?
    @objc var onBLEStateChanged: ((NSDictionary) -> Void)?
    @objc var onUnlockResult: ((NSDictionary) -> Void)?
    @objc var onDFUStateChanged: ((NSDictionary) -> Void)?
    @objc var onDeviceInformation: ((NSDictionary) -> Void)?
    @objc var onServerConnectionChange: ((NSDictionary) -> Void)?

    private var serverAddress: String?
    private var serviceUUID: String?
    private var lastLockMobileSdk: LastLockMobileSdk?
    private var events: LastLockMobileSdkEventBridge?
    private var rescanning = false

    @objc(initialize:resolve:reject:)
    func initialize(
        _ serverAddress: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject _: @escaping RCTPromiseRejectBlock
    ) {
        self.serverAddress = serverAddress
        resolve(nil)
    }

    @objc(start:resolve:reject:)
    func start(
        _ uuid: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let serverAddress else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not initialized. Call initialize first.", nil)
            return
        }

        Task { @MainActor in
            self.serviceUUID = uuid
            let alreadyInitialized = LastLockMobileSdk.isInitialized
            LastLockMobileSdk.initialize(serverAddress: serverAddress, uuid: uuid)
            let lastLockMobileSdk = LastLockMobileSdk.shared
            self.lastLockMobileSdk = lastLockMobileSdk
            do {
                if alreadyInitialized {
                    try await lastLockMobileSdk.restart(serverAddress: serverAddress, uuid: uuid)
                }
                let events = self.events ?? LastLockMobileSdkEventBridge(controller: self)
                self.events = events
                events.attach(to: lastLockMobileSdk)
                lastLockMobileSdk.onDFUStateChanged { [weak self] state in self?.emitDFUState(state) }
                lastLockMobileSdk.onServerConnectionChange { [weak self] connected in
                    self?.onServerConnectionChange?(["state": connected ? "connected" : "disconnected"])
                }
                if !alreadyInitialized { self.rescan() }
                resolve(nil)
            } catch { rejectBridgeError(reject, error) }
        }
    }

    func rescan() {
        guard !rescanning, let serviceUUID, let lastLockMobileSdk else { return }
        rescanning = true
        lastLockMobileSdk.start(UUID: serviceUUID)
        rescanning = false
    }

    @objc(identify:resolve:reject:)
    func identify(
        _ identifier: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        runCommand(identifier, reject: reject) { lastLockMobileSdk, identifier in
            try await lastLockMobileSdk.identify(identifier: identifier)
            resolve(nil)
        }
    }

    @objc(setLockEngagedState:engaged:resolve:reject:)
    func setLockEngagedState(
        _ identifier: String,
        engaged: Bool,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        runCommand(identifier, reject: reject) { lastLockMobileSdk, identifier in
            try await lastLockMobileSdk.setLockEngagedState(identifier: identifier, engaged: engaged)
            resolve(nil)
        }
    }

    @objc(checkForDFU:resolve:reject:)
    func checkForDFU(
        _ identifier: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        Task {
            do {
                let result = try await lastLockMobileSdk.checkForDFU(identifier: identifier)
                if result is CheckDFUResult.Timeout {
                    resolve([
                        "status": "timeout",
                        "firmwareVersion": "",
                        "availableVersion": "",
                    ])
                    return
                }
                let state = lastLockMobileSdk.getDFUState()
                let current = (result as? CheckDFUResult.Success)?.firmwareVersion
                resolve([
                    "status": state.dfuAvailable ? "update_available" : "up_to_date",
                    "firmwareVersion": firmwareText(current),
                    "availableVersion": state.dfuImage?.dfuVersion ?? "",
                ])
            } catch {
                rejectBridgeError(reject, error)
            }
        }
    }

    @objc(startDFU:resolve:reject:)
    func startDFU(
        _ identifier: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        Task {
            do {
                try await lastLockMobileSdk.startDFU(identifier: identifier)
                resolve(nil)
            } catch { rejectBridgeError(reject, error) }
        }
    }

    @objc(waitForDeviceMode:dfuRequired:resolve:reject:)
    func waitForDeviceMode(
        _ identifier: String,
        dfuRequired: Bool,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        Task {
            do {
                try await lastLockMobileSdk.waitForDeviceMode(identifier: identifier, dfuRequired: dfuRequired)
                resolve(nil)
            } catch { rejectBridgeError(reject, error) }
        }
    }

    @objc(exitDFUInstallMode:resolve:reject:)
    func exitDFUInstallMode(
        _ identifier: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        Task {
            do {
                try await lastLockMobileSdk.exitDFUInstallMode(identifier: identifier)
                resolve(nil)
            } catch { rejectBridgeError(reject, error) }
        }
    }

    @objc(getOrCreatePassPublicKey:resolve:reject:)
    func getOrCreatePassPublicKey(
        _ passId: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        if passId.isEmpty {
            reject("INVALID_ARGUMENT", "Pass ID is required.", nil)
            return
        }
        Task {
            do {
                let publicKey = try await lastLockMobileSdk.getOrCreatePassPublicKey(passId: passId)
                if publicKey.isEmpty {
                    reject("STORAGE_FAILED", "Pass public key is empty.", nil)
                    return
                }
                resolve(publicKey.base64EncodedString())
            } catch {
                rejectBridgeError(reject, error)
            }
        }
    }

    @objc(storePass:pass:resolve:reject:)
    func storePass(
        _ passId: String,
        pass material: NSDictionary,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        let pass: UnlockPass
        do {
            pass = try decodePass(material)
        } catch {
            rejectBridgeError(reject, error)
            return
        }
        Task {
            do {
                try await lastLockMobileSdk.storePass(passId: passId, pass: pass)
                resolve(nil)
            } catch {
                rejectBridgeError(reject, error)
            }
        }
    }

    @objc(deletePass:resolve:reject:)
    func deletePass(
        _ passId: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        Task {
            do { try await lastLockMobileSdk.deletePass(passId: passId); resolve(nil) }
            catch { rejectBridgeError(reject, error) }
        }
    }

    @objc(shutdown:reject:)
    func shutdown(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        Task { @MainActor in
            do {
                try await lastLockMobileSdk?.shutdown()
                events = nil
                resolve(nil)
            } catch { rejectBridgeError(reject, error) }
        }
    }

    @objc(getPass:resolve:reject:)
    func getPass(_ passId: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        Task {
            do {
                guard let pass = try await lastLockMobileSdk.getPass(passId: passId) else { resolve(nil); return }
                resolve([
                    "accessDocument": pass.accessDocument.toData().base64EncodedString(),
                    "readerGroups": pass.readerGroups.map { group -> [String: Any] in
                        var result: [String: Any] = [
                            "readerGroupIdentifier": group.readerGroupIdentifier.toData().base64EncodedString(),
                            "issuerCAPublicKey": group.issuerCAPublicKey.toData().base64EncodedString(),
                        ]
                        if let certificate = group.issuerCACertificateDer {
                            result["issuerCACertificateDer"] = certificate.toData().base64EncodedString()
                        }
                        return result
                    },
                ])
            } catch { rejectBridgeError(reject, error) }
        }
    }

    private func decodePass(_ material: NSDictionary) throws -> UnlockPass {
        func bytes(_ object: NSDictionary, _ field: String) throws -> KotlinByteArray {
            guard let encoded = object[field] as? String,
                  let data = Data(base64Encoded: encoded) else {
                throw LastLockMobileSdkError("\(field) is required and must be valid Base64.")
            }
            return kotlinBytes(data)
        }
        guard let groups = material["readerGroups"] as? [NSDictionary] else {
            throw LastLockMobileSdkError("readerGroups is required.")
        }
        return UnlockPass(
            accessDocument: try bytes(material, "accessDocument"),
            readerGroups: try groups.map { group in
                ReaderGroupTrust(
                    readerGroupIdentifier: try bytes(group, "readerGroupIdentifier"),
                    issuerCAPublicKey: try bytes(group, "issuerCAPublicKey"),
                    issuerCACertificateDer: group["issuerCACertificateDer"] == nil || group["issuerCACertificateDer"] is NSNull
                        ? nil : try bytes(group, "issuerCACertificateDer")
                )
            }
        )
    }

    @objc(unlock:passId:resolve:reject:)
    func unlock(
        _ identifier: String,
        passId: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        if passId.isEmpty {
            reject("INVALID_ARGUMENT", "Pass ID is required.", nil)
            return
        }
        Task {
            do {
                try await lastLockMobileSdk.unlock(identifier: identifier, passId: passId)
                resolve(nil)
            } catch {
                rejectBridgeError(reject, error)
            }
        }
    }

    @objc(isDeviceBlacklisted:resolve:reject:)
    func isDeviceBlacklisted(_ identifier: String, resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        Task {
            do { resolve(try await lastLockMobileSdk.isDeviceBlacklisted(identifier: identifier)) }
            catch { rejectBridgeError(reject, error) }
        }
    }

    @objc(cancelDFU:reject:)
    func cancelDFU(
        _ resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        lastLockMobileSdk.cancelDFU()
        resolve(nil)
    }

    private func runCommand(
        _ identifier: String,
        reject: @escaping RCTPromiseRejectBlock,
        body: @escaping (LastLockMobileSdk, String) async throws -> Void
    ) {
        guard let lastLockMobileSdk = lastLockMobileSdk else {
            reject("NOT_INITIALIZED", "LastLockMobileSdk not started. Call start first.", nil)
            return
        }
        Task {
            do {
                try await body(lastLockMobileSdk, identifier)
            } catch {
                rejectBridgeError(reject, error)
            }
        }
    }

    private func emitDFUState(_ state: DFUUiState) {
        let payload: [String: Any] = [
            "flowState": state.flowState.name,
            "installProgress": NSNumber(value: state.installProgress),
            "postInstallProgress": NSNumber(value: state.postInstallProgress),
            "dfuAvailable": NSNumber(value: state.dfuAvailable),
            "dfuImageLoading": NSNumber(value: state.dfuImageLoading),
            "availableVersion": state.dfuImage?.dfuVersion ?? "",
        ]
        let send = { [weak self] in
            self?.onDFUStateChanged?(payload as NSDictionary)
            return ()
        }
        if Thread.isMainThread {
            send()
        } else {
            DispatchQueue.main.async(execute: send)
        }
    }

}

final class LastLockMobileSdkEventBridge {
    weak var controller: LastLockMobileSdkController?

    init(controller: LastLockMobileSdkController) { self.controller = controller }

    func attach(to lastLockMobileSdk: LastLockMobileSdk) {
        lastLockMobileSdk.onBLEStateChanged { [weak self] state in self?.emit(self?.controller?.onBLEStateChanged, ["state": state]) }
        lastLockMobileSdk.onDeviceDiscovered { [weak self] device in
            self?.emit(self?.controller?.onDeviceDiscovered, [
                "identifier": device.identifier, "name": device.name, "linkDeviceId": device.linkDeviceId,
                "rssi": device.rssi, "connectionStatus": device.connectionStatus, "signalStrength": device.signalStrength,
                "installMode": device.installMode, "dfuMode": device.dfuMode, "hibernate": device.hibernate,
                "serviceMode": device.serviceMode, "lowBattery": device.lowBattery, "criticalBattery": device.criticalBattery,
                "cooldown": device.cooldown, "emergency": device.emergency, "busy": device.busy,
                "timeSyncRequest": device.timeSyncRequest, "overridingMode": device.overridingMode,
                "deviceStateCounter": device.deviceStateCounter, "serverStateCounter": device.serverStateCounter,
                "oobTimestamp": device.oobTimestamp?.int64Value ?? 0,
            ])
        }
        lastLockMobileSdk.onDeviceInformation { [weak self] info in
            self?.emit(self?.controller?.onDeviceInformation, [
                "identifier": info.identifier, "firmwareVersion": info.firmwareVersion,
                "bootloaderVersion": info.bootloaderVersion as Any? ?? NSNull(),
                "deviceStateCounter": info.deviceStateCounter, "serverStateCounter": info.serverStateCounter,
                "oobTimestamp": info.oobTimestamp.map { NSNumber(value: $0.int64Value) } as Any? ?? NSNull(),
            ])
        }
        lastLockMobileSdk.onDeviceConnected { [weak self] device in
            self?.emit(self?.controller?.onDeviceConnected, ["identifier": device.identifier, "name": device.name])
        }
        lastLockMobileSdk.onDeviceDisconnected { [weak self] device in
            self?.emit(self?.controller?.onDeviceDisconnected, ["identifier": device.identifier, "name": device.name])
        }
        lastLockMobileSdk.onUnlockResult { [weak self] result in
            self?.emit(self?.controller?.onUnlockResult, [
                "identifier": result.identifier, "name": result.name, "passId": result.passId,
                "result": result.result.name, "message": result.message,
            ])
        }
    }

    private func emit(_ handler: ((NSDictionary) -> Void)?, _ body: [String: Any]) {
        let send = { handler?(body as NSDictionary); return () }
        if Thread.isMainThread { send() } else { DispatchQueue.main.async(execute: send) }
    }
}

private struct LastLockMobileSdkError: LocalizedError {
    let message: String
    let code: MobileErrorCode
    init(_ message: String, code: MobileErrorCode = .invalidArgument) { self.message = message; self.code = code }
    var errorDescription: String? { message }
}

private func rejectBridgeError(_ reject: RCTPromiseRejectBlock, _ error: Error) {
    let sdkError = (error as? MobileException) ?? ((error as NSError).kotlinException as? MobileException)
    let code = lastLockMobileSdkErrorCode(error) ?? (error as? LastLockMobileSdkError)?.code ?? .operationFailed
    let message = sdkError?.message ?? error.localizedDescription
    reject(code.name, message, error)
}

private func firmwareText(_ version: DeviceFirmwareVersion?) -> String {
    guard let version else { return "" }
    return "\(version.major).\(version.minor).\(version.revision)-\(version.build)"
}

private func kotlinBytes(_ data: Data) -> KotlinByteArray {
    let bytes = KotlinByteArray(size: Int32(data.count))
    for (index, byte) in data.enumerated() {
        bytes.set(index: Int32(index), value: Int8(bitPattern: byte))
    }
    return bytes
}
