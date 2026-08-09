package com.adrianczuczka.ondeviceai

import kotlinx.coroutines.flow.Flow

/**
 * SPI for anything that can serve inference: the system models (default),
 * bundled-weight engines (llama.cpp-style, shipped as separate artifacts so
 * their native binaries stay opt-in), or a cloud escape hatch.
 */
public interface InferenceEngine {

    public val id: String

    /** True when inference never leaves the device. [RoutingPolicy.OnDeviceOnly] filters on this. */
    public val isOnDevice: Boolean get() = true

    public suspend fun availability(feature: AiFeature = AiFeature.Prompt): Availability

    public fun download(feature: AiFeature = AiFeature.Prompt): Flow<DownloadProgress>

    public suspend fun openSession(config: SessionConfig = SessionConfig()): ChatSession
}

public sealed interface RoutingPolicy {

    /** First engine (in constructor order) reporting [Availability.Available]. */
    public data object Default : RoutingPolicy

    /** Never leaves the device – the privacy guarantee, enforced by type. */
    public data object OnDeviceOnly : RoutingPolicy

    /** Full control: receives engine-id → availability, returns the engine id to use. */
    public data class Custom(
        val select: suspend (statuses: Map<String, Availability>) -> String?,
    ) : RoutingPolicy
}

/**
 * The zero-download engine backed by the OS: Gemini Nano via ML Kit GenAI /
 * AICore on Android, Apple Foundation Models on iOS 26+.
 */
public expect class SystemEngine() : InferenceEngine {
    override val id: String
    override suspend fun availability(feature: AiFeature): Availability
    override fun download(feature: AiFeature): Flow<DownloadProgress>
    override suspend fun openSession(config: SessionConfig): ChatSession
}
