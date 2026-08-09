package dev.ondeviceai

/**
 * The answer to "can this device run this feature right now?" – deliberately
 * richer than a boolean, because the right UI differs per case: show the
 * feature, offer a download, nudge the user to a settings toggle, or hide it.
 */
sealed interface Availability {

    /** Ready for inference now. */
    data class Available(val model: ModelInfo) : Availability

    /**
     * Supported, but model weights must be fetched first – call
     * [OnDeviceAi.download] and show progress. Android-only state today;
     * Apple's Foundation Models are provisioned by the OS.
     */
    data class Downloadable(val estimatedBytes: Long?) : Availability

    /** A download is already in flight. */
    data object Downloading : Availability

    data class Unavailable(val reason: Reason) : Availability {
        enum class Reason {
            /** Hardware will never run it – hide the feature. */
            DeviceNotEligible,

            /** Apple Intelligence off / AICore disabled – user-fixable, deep-link to settings. */
            NotEnabled,

            /** Needs a newer OS (iOS 26+ / AICore-capable Android). */
            OsTooOld,

            /** Enabled but the model is still provisioning – retry later. */
            ModelNotReady,

            /** This [AiFeature] has no implementation on this engine. */
            FeatureNotSupported,

            /**
             * The integration itself is not set up: on iOS the host app has not
             * registered a [FoundationModelsBridge]; on Android the startup
             * initializer was disabled without a manual init.
             */
            NotConfigured,
        }
    }
}

/** What is actually serving inference, as far as the OS reveals it. */
data class ModelInfo(
    val engineId: String,
    val modelName: String? = null,
    val contextWindowTokens: Int? = null,
)

sealed interface DownloadProgress {
    data object Started : DownloadProgress
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long?) : DownloadProgress
    data object Completed : DownloadProgress
}
