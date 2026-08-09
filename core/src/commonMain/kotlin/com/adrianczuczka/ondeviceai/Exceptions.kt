package com.adrianczuczka.ondeviceai

import kotlin.time.Duration

public sealed class OnDeviceAiException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /**
     * The platform's rolling per-app inference quota is exhausted (AICore
     * returns this after ~40 back-to-back generations). Retry after
     * [retryDelay] when the platform provides one, or fall back.
     */
    public class Busy(public val retryDelay: Duration?, cause: Throwable? = null) :
        OnDeviceAiException(
            "On-device model is busy (per-app quota)" +
                (retryDelay?.let { " – retry after $it" } ?: ""),
            cause,
        )

    /**
     * The platform refuses inference from backgrounded apps (AICore policy).
     * Generate from a foreground session or fall back – background workers
     * cannot use the on-device path.
     */
    public class BackgroundBlocked(cause: Throwable? = null) :
        OnDeviceAiException("On-device inference is blocked while the app is in the background", cause)

    /** No engine allowed by the routing policy could serve the request. */
    public class NoEngineAvailable(public val statuses: Map<String, Availability>) :
        OnDeviceAiException("No engine available. Statuses: $statuses")

    /**
     * A safety system refused the request or the response – Apple's guardrail
     * violations and ML Kit's safety blocks, unified.
     */
    public class ContentBlocked(public val direction: Direction, message: String) :
        OnDeviceAiException(message) {
        public enum class Direction { Input, Output }
    }

    /** System models are small – Apple's window is ~4k tokens. Expect this early. */
    public class ContextWindowExceeded(public val limitTokens: Int?) :
        OnDeviceAiException(
            "Context window exceeded" + (limitTokens?.let { " (limit: $it tokens)" } ?: "")
        )

    public class DownloadFailed(message: String, cause: Throwable? = null) :
        OnDeviceAiException(message, cause)

    /** The model could not produce schema-conforming output within the retry budget. */
    public class StructuredOutputParseFailed(public val rawOutput: String, cause: Throwable? = null) :
        OnDeviceAiException("Model output did not match the requested schema", cause)

    public class GenerationFailed(message: String, cause: Throwable? = null) :
        OnDeviceAiException(message, cause)
}
