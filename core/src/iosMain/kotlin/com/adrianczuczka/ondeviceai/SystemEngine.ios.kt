package com.adrianczuczka.ondeviceai

import com.adrianczuczka.ondeviceai.internal.StructuredPrompting
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Apple Foundation Models (iOS 26+), reached through the app-registered
 * [FoundationModelsBridge] because the framework is Swift-only.
 */
public actual class SystemEngine actual constructor() : InferenceEngine {

    actual override val id: String get() = "system"

    actual override suspend fun availability(feature: AiFeature): Availability {
        // All features (including task templates) ride on the base model.
        val bridge = OnDeviceAiIos.bridge
            ?: return Availability.Unavailable(Availability.Unavailable.Reason.NotConfigured)
        return suspendCancellableCoroutine { continuation ->
            bridge.checkAvailability { result ->
                continuation.resume(
                    when (result) {
                        BridgeAvailability.AVAILABLE -> Availability.Available(
                            ModelInfo(
                                engineId = id,
                                modelName = "apple-foundation-model",
                                contextWindowTokens = 4096,
                            )
                        )
                        BridgeAvailability.DEVICE_NOT_ELIGIBLE ->
                            unavailable(Availability.Unavailable.Reason.DeviceNotEligible)
                        BridgeAvailability.APPLE_INTELLIGENCE_NOT_ENABLED ->
                            unavailable(Availability.Unavailable.Reason.NotEnabled)
                        BridgeAvailability.MODEL_NOT_READY ->
                            unavailable(Availability.Unavailable.Reason.ModelNotReady)
                        BridgeAvailability.OS_TOO_OLD ->
                            unavailable(Availability.Unavailable.Reason.OsTooOld)
                    }
                )
            }
        }
    }

    /** The OS provisions Foundation Models itself; there is never an app-driven download. */
    actual override fun download(feature: AiFeature): Flow<DownloadProgress> = emptyFlow()

    actual override suspend fun openSession(config: SessionConfig): ChatSession {
        val bridge = OnDeviceAiIos.bridge ?: throw OnDeviceAiException.NoEngineAvailable(
            mapOf(id to unavailable(Availability.Unavailable.Reason.NotConfigured))
        )
        return FoundationModelsChatSession(bridge, config)
    }

    private fun unavailable(reason: Availability.Unavailable.Reason) =
        Availability.Unavailable(reason)
}

internal class FoundationModelsChatSession(
    private val bridge: FoundationModelsBridge,
    config: SessionConfig,
) : ChatSession {

    private val sessionId: String = bridge.openSession(
        instructions = config.instructions,
        temperature = config.options.temperature?.toDouble() ?: -1.0,
        maxOutputTokens = config.options.maxOutputTokens ?: -1,
    )

    private val _transcript = MutableStateFlow<List<Message>>(emptyList())
    override val transcript: StateFlow<List<Message>> = _transcript.asStateFlow()

    override val model: ModelInfo = ModelInfo(
        engineId = "system",
        modelName = "apple-foundation-model",
        contextWindowTokens = 4096,
    )

    override suspend fun respond(prompt: String): String =
        suspendCancellableCoroutine { continuation ->
            bridge.respond(sessionId, prompt) { text, error ->
                if (text != null) {
                    record(prompt, text)
                    continuation.resume(text)
                } else {
                    continuation.resumeWithException(
                        OnDeviceAiException.GenerationFailed(error ?: "Generation failed")
                    )
                }
            }
        }

    override fun stream(prompt: String): Flow<String> = callbackFlow {
        var last = ""
        bridge.streamRespond(
            sessionId = sessionId,
            prompt = prompt,
            onSnapshot = { snapshot ->
                // FoundationModels streams cumulative snapshots; normalize to deltas.
                val delta = if (snapshot.startsWith(last)) snapshot.substring(last.length) else snapshot
                last = snapshot
                if (delta.isNotEmpty()) trySend(delta)
            },
            completion = { error ->
                if (error == null) {
                    record(prompt, last)
                    close()
                } else {
                    close(OnDeviceAiException.GenerationFailed(error))
                }
            },
        )
        awaitClose { }
    }

    override suspend fun <T : Any> respondStructured(prompt: String, serializer: KSerializer<T>): T =
        StructuredPrompting.respondViaJsonPrompt(this, prompt, serializer)
        // TODO(constrained-decoding): map SerialDescriptor → DynamicGenerationSchema
        //  in the Swift bridge for guaranteed-valid output.

    override fun close() {
        bridge.closeSession(sessionId)
    }

    private fun record(prompt: String, reply: String) {
        _transcript.value = _transcript.value +
            Message(Message.Role.User, prompt) +
            Message(Message.Role.Model, reply)
    }
}
