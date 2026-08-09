package dev.ondeviceai

/**
 * Seam to Apple's FoundationModels framework, which is Swift-only and
 * therefore unreachable from Kotlin/Native directly. The host app implements
 * this interface in Swift (reference: `ios-bridge/FoundationModelsBridgeImpl.swift`)
 * and registers it once at startup:
 *
 * ```swift
 * OnDeviceAiIos.shared.bridge = FoundationModelsBridgeImpl()
 * ```
 *
 * Signatures are deliberately ObjC-export-friendly: callbacks, no suspend,
 * no Kotlin-only types.
 */
interface FoundationModelsBridge {

    fun checkAvailability(completion: (BridgeAvailability) -> Unit)

    /**
     * Opens a LanguageModelSession and returns an opaque session id.
     * Sentinels: `temperature < 0` and `maxOutputTokens < 0` mean "use defaults".
     */
    fun openSession(instructions: String?, temperature: Double, maxOutputTokens: Int): String

    /** Exactly one of (text, errorMessage) is non-null. */
    fun respond(sessionId: String, prompt: String, completion: (String?, String?) -> Unit)

    /**
     * [onSnapshot] receives CUMULATIVE text snapshots – FoundationModels
     * streaming semantics. The Kotlin side diffs them into deltas.
     * [completion] receives an error message, or null on success.
     */
    fun streamRespond(
        sessionId: String,
        prompt: String,
        onSnapshot: (String) -> Unit,
        completion: (String?) -> Unit,
    )

    fun closeSession(sessionId: String)
}

// SCREAMING_SNAKE_CASE so Kotlin's ObjC export yields clean Swift names
// (.deviceNotEligible instead of .devicenoteligible).
enum class BridgeAvailability {
    AVAILABLE,
    DEVICE_NOT_ELIGIBLE,
    APPLE_INTELLIGENCE_NOT_ENABLED,
    MODEL_NOT_READY,
    OS_TOO_OLD,
}

object OnDeviceAiIos {
    var bridge: FoundationModelsBridge? = null
}
