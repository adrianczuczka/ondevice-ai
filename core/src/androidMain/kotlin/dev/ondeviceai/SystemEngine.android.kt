package dev.ondeviceai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import dev.ondeviceai.internal.StructuredPrompting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer

/**
 * Gemini Nano through ML Kit's GenAI Prompt API (AICore). Zero bundled
 * weights – the OS owns and updates the model.
 */
actual class SystemEngine actual constructor() : InferenceEngine {

    actual override val id: String get() = "system"

    actual override suspend fun availability(feature: AiFeature): Availability {
        // Task features currently run as prompt templates, so their
        // availability is the Prompt feature's availability.
        val client = Generation.getClient()
        return try {
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> Availability.Available(describeModel(client))
                FeatureStatus.DOWNLOADABLE -> Availability.Downloadable(estimatedBytes = null)
                FeatureStatus.DOWNLOADING -> Availability.Downloading
                else -> Availability.Unavailable(Availability.Unavailable.Reason.DeviceNotEligible)
            }
        } finally {
            client.close()
        }
    }

    actual override fun download(feature: AiFeature): Flow<DownloadProgress> = flow {
        val client = Generation.getClient()
        try {
            client.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> emit(DownloadProgress.Started)
                    is DownloadStatus.DownloadProgress ->
                        emit(
                            DownloadProgress.InProgress(
                                bytesDownloaded = status.totalBytesDownloaded,
                                totalBytes = null,
                            )
                        )
                    is DownloadStatus.DownloadCompleted -> emit(DownloadProgress.Completed)
                    is DownloadStatus.DownloadFailed -> throw OnDeviceAiException.DownloadFailed(
                        status.e.message ?: "Model download failed",
                        status.e,
                    )
                    else -> Unit
                }
            }
        } finally {
            client.close()
        }
    }

    actual override suspend fun openSession(config: SessionConfig): ChatSession = MlKitChatSession(config)

    private suspend fun describeModel(client: GenerativeModel): ModelInfo = ModelInfo(
        engineId = id,
        modelName = runCatching { client.getBaseModelName() }.getOrNull(),
        contextWindowTokens = runCatching { client.getTokenLimit() }.getOrNull(),
    )
}

internal class MlKitChatSession(
    private val config: SessionConfig,
) : ChatSession {

    private val client: GenerativeModel = Generation.getClient()

    private val _transcript = MutableStateFlow<List<Message>>(emptyList())
    override val transcript: StateFlow<List<Message>> = _transcript.asStateFlow()

    override val model: ModelInfo = ModelInfo(engineId = "system", modelName = "gemini-nano")

    override suspend fun respond(prompt: String): String {
        val response = try {
            client.generateContent(buildRequest(prompt))
        } catch (e: GenAiException) {
            throw OnDeviceAiException.GenerationFailed(e.message ?: "Generation failed", e)
        }
        val text = response.candidates.firstOrNull()?.text
            ?: throw OnDeviceAiException.GenerationFailed("Model returned no candidates")
        record(prompt, text)
        return text
    }

    override fun stream(prompt: String): Flow<String> = flow {
        val accumulated = StringBuilder()
        try {
            client.generateContentStream(buildRequest(prompt)).collect { chunk ->
                val delta = chunk.candidates.firstOrNull()?.text.orEmpty()
                if (delta.isNotEmpty()) {
                    accumulated.append(delta)
                    emit(delta)
                }
            }
        } catch (e: GenAiException) {
            throw OnDeviceAiException.GenerationFailed(e.message ?: "Generation failed", e)
        }
        record(prompt, accumulated.toString())
    }

    override suspend fun <T : Any> respondStructured(prompt: String, serializer: KSerializer<T>): T =
        StructuredPrompting.respondViaJsonPrompt(this, prompt, serializer)
        // TODO(genai-schema): switch to GenerateTypedContentRequest once we map
        //  kotlinx.serialization descriptors to the ML Kit schema compiler.

    override fun close() {
        client.close()
    }

    private fun buildRequest(prompt: String) =
        generateContentRequest(TextPart(composeWithHistory(prompt))) {
            config.instructions?.let { systemInstruction = SystemInstruction(it) }
            config.options.temperature?.let { temperature = it }
            config.options.topK?.let { topK = it }
            config.options.maxOutputTokens?.let { maxOutputTokens = it }
        }

    // The Prompt API's Content carries no role, so history rides in the prompt
    // text until AICore grows real multi-turn requests.
    private fun composeWithHistory(prompt: String): String {
        val history = _transcript.value
        if (history.isEmpty()) return prompt
        return buildString {
            for (message in history) {
                val label = if (message.role == Message.Role.User) "User" else "Assistant"
                appendLine("$label: ${message.text}")
            }
            append("User: ").append(prompt)
        }
    }

    private fun record(prompt: String, reply: String) {
        _transcript.value = _transcript.value +
            Message(Message.Role.User, prompt) +
            Message(Message.Role.Model, reply)
    }
}
