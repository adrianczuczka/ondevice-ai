package com.adrianczuczka.ondeviceai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * A stateful conversation with an on-device model. Sessions hold native
 * resources on both platforms – always release them, ideally with [use].
 */
public interface ChatSession : AutoCloseable {

    public val model: ModelInfo

    public val transcript: StateFlow<List<Message>>

    public suspend fun respond(prompt: String): String

    /**
     * Streams the response as text deltas. ML Kit already emits deltas;
     * Apple's Foundation Models emit cumulative snapshots, which the iOS
     * engine diffs into deltas so behavior is identical across platforms.
     */
    public fun stream(prompt: String): Flow<String>

    /**
     * Responds as an instance of a `@Serializable` class. On iOS this is
     * intended to compile to true constrained decoding via
     * `DynamicGenerationSchema`; the current fallback on both platforms is a
     * JSON-prompt + validate + bounded-retry loop.
     *
     * @throws OnDeviceAiException.StructuredOutputParseFailed if the model
     * cannot produce schema-conforming output within the retry budget.
     */
    public suspend fun <T : Any> respondStructured(prompt: String, serializer: KSerializer<T>): T
}

public suspend inline fun <reified T : Any> ChatSession.respondStructured(prompt: String): T =
    respondStructured(prompt, serializer())

public data class Message(val role: Role, val text: String) {
    public enum class Role { User, Model }
}
