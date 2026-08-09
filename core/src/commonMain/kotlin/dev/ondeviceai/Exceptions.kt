package dev.ondeviceai

sealed class OnDeviceAiException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    /** No engine allowed by the routing policy could serve the request. */
    class NoEngineAvailable(val statuses: Map<String, Availability>) :
        OnDeviceAiException("No engine available. Statuses: $statuses")

    /**
     * A safety system refused the request or the response – Apple's guardrail
     * violations and ML Kit's safety blocks, unified.
     */
    class ContentBlocked(val direction: Direction, message: String) :
        OnDeviceAiException(message) {
        enum class Direction { Input, Output }
    }

    /** System models are small – Apple's window is ~4k tokens. Expect this early. */
    class ContextWindowExceeded(val limitTokens: Int?) :
        OnDeviceAiException(
            "Context window exceeded" + (limitTokens?.let { " (limit: $it tokens)" } ?: "")
        )

    class DownloadFailed(message: String, cause: Throwable? = null) :
        OnDeviceAiException(message, cause)

    /** The model could not produce schema-conforming output within the retry budget. */
    class StructuredOutputParseFailed(val rawOutput: String, cause: Throwable? = null) :
        OnDeviceAiException("Model output did not match the requested schema", cause)

    class GenerationFailed(message: String, cause: Throwable? = null) :
        OnDeviceAiException(message, cause)
}
