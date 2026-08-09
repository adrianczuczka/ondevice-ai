// Reference implementation of the FoundationModelsBridge Kotlin interface.
//
// Add this file to your iOS APP TARGET (it cannot live in this repo's build –
// FoundationModels is Swift-only and the Kotlin framework is consumed from
// Swift, not the other way around). Adjust the import to your exported
// framework name, then register at startup:
//
//     OnDeviceAiIos.shared.bridge = FoundationModelsBridgeImpl()
//
// NOTE: this file is not part of this repo's build, but it is compile-verified
// against the exported OnDeviceAiCore.framework and the iOS 26 SDK's
// FoundationModels (swiftc, SDK 26.5). If your framework baseName differs,
// adjust the import.

import Foundation
import FoundationModels
import OnDeviceAiCore

@available(iOS 26.0, macOS 26.0, *)
public final class FoundationModelsBridgeImpl: NSObject, FoundationModelsBridge {

    private var sessions: [String: LanguageModelSession] = [:]
    private let lock = NSLock()

    public func checkAvailability(completion: @escaping (BridgeAvailability) -> Void) {
        switch SystemLanguageModel.default.availability {
        case .available:
            completion(.available)
        case .unavailable(.deviceNotEligible):
            completion(.deviceNotEligible)
        case .unavailable(.appleIntelligenceNotEnabled):
            completion(.appleIntelligenceNotEnabled)
        case .unavailable(.modelNotReady):
            completion(.modelNotReady)
        @unknown default:
            completion(.deviceNotEligible)
        }
    }

    public func openSession(instructions: String?, temperature: Double, maxOutputTokens: Int32) -> String {
        let session: LanguageModelSession
        if let instructions {
            session = LanguageModelSession(instructions: instructions)
        } else {
            session = LanguageModelSession()
        }
        let id = UUID().uuidString
        lock.lock(); sessions[id] = session; lock.unlock()
        return id
    }

    public func respond(sessionId: String, prompt: String, completion: @escaping (String?, String?) -> Void) {
        guard let session = session(for: sessionId) else {
            completion(nil, "Unknown session: \(sessionId)"); return
        }
        Task {
            do {
                let response = try await session.respond(to: prompt)
                completion(response.content, nil)
            } catch {
                completion(nil, error.localizedDescription)
            }
        }
    }

    public func streamRespond(
        sessionId: String,
        prompt: String,
        onSnapshot: @escaping (String) -> Void,
        completion: @escaping (String?) -> Void
    ) {
        guard let session = session(for: sessionId) else {
            completion("Unknown session: \(sessionId)"); return
        }
        Task {
            do {
                // FoundationModels streams cumulative snapshots; the Kotlin
                // side diffs them into deltas.
                for try await snapshot in session.streamResponse(to: prompt) {
                    onSnapshot(snapshot.content)
                }
                completion(nil)
            } catch {
                completion(error.localizedDescription)
            }
        }
    }

    public func closeSession(sessionId: String) {
        lock.lock(); sessions[sessionId] = nil; lock.unlock()
    }

    private func session(for id: String) -> LanguageModelSession? {
        lock.lock(); defer { lock.unlock() }
        return sessions[id]
    }
}
