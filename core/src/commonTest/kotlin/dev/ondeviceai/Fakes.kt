package dev.ondeviceai

import dev.ondeviceai.internal.StructuredPrompting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.KSerializer

internal class FakeChatSession(
    private val replies: MutableList<String> = mutableListOf("ok"),
) : ChatSession {

    val prompts = mutableListOf<String>()
    var closed = false
        private set

    private val _transcript = MutableStateFlow<List<Message>>(emptyList())
    override val transcript: StateFlow<List<Message>> = _transcript.asStateFlow()
    override val model: ModelInfo = ModelInfo(engineId = "fake")

    override suspend fun respond(prompt: String): String {
        prompts += prompt
        return if (replies.size > 1) replies.removeAt(0) else replies.first()
    }

    override fun stream(prompt: String): Flow<String> = flow { emit(respond(prompt)) }

    override suspend fun <T : Any> respondStructured(prompt: String, serializer: KSerializer<T>): T =
        StructuredPrompting.respondViaJsonPrompt(this, prompt, serializer)

    override fun close() {
        closed = true
    }
}

internal class FakeEngine(
    override val id: String,
    private val status: Availability,
    override val isOnDevice: Boolean = true,
    val session: FakeChatSession = FakeChatSession(),
) : InferenceEngine {

    override suspend fun availability(feature: AiFeature): Availability = status

    override fun download(feature: AiFeature): Flow<DownloadProgress> =
        flowOf(DownloadProgress.Started, DownloadProgress.Completed)

    override suspend fun openSession(config: SessionConfig): ChatSession = session
}
