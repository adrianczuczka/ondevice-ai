package dev.ondeviceai

import kotlinx.coroutines.flow.Flow

/**
 * SPI for anything that can serve inference: the system models (default),
 * bundled-weight engines (llama.cpp-style, shipped as separate artifacts so
 * their native binaries stay opt-in), or a cloud escape hatch.
 */
interface InferenceEngine {

    val id: String

    /** True when inference never leaves the device. [RoutingPolicy.OnDeviceOnly] filters on this. */
    val isOnDevice: Boolean get() = true

    suspend fun availability(feature: AiFeature = AiFeature.Prompt): Availability

    fun download(feature: AiFeature = AiFeature.Prompt): Flow<DownloadProgress>

    suspend fun openSession(config: SessionConfig = SessionConfig()): ChatSession
}

sealed interface RoutingPolicy {

    /** First engine (in constructor order) reporting [Availability.Available]. */
    data object Default : RoutingPolicy

    /** Never leaves the device – the privacy guarantee, enforced by type. */
    data object OnDeviceOnly : RoutingPolicy

    /** Full control: receives engine-id → availability, returns the engine id to use. */
    data class Custom(
        val select: suspend (statuses: Map<String, Availability>) -> String?,
    ) : RoutingPolicy
}

/**
 * The zero-download engine backed by the OS: Gemini Nano via ML Kit GenAI /
 * AICore on Android, Apple Foundation Models on iOS 26+.
 */
expect class SystemEngine() : InferenceEngine {
    override val id: String
    override suspend fun availability(feature: AiFeature): Availability
    override fun download(feature: AiFeature): Flow<DownloadProgress>
    override suspend fun openSession(config: SessionConfig): ChatSession
}
