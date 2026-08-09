package dev.ondeviceai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Facade over one or more [InferenceEngine]s.
 *
 * ```kotlin
 * val ai = OnDeviceAi()
 * when (val a = ai.availability()) {
 *     is Availability.Available    -> ai.chat().use { it.stream("…").collect(::print) }
 *     is Availability.Downloadable -> ai.download().collect { /* progress UI */ }
 *     is Availability.Downloading  -> { /* spinner */ }
 *     is Availability.Unavailable  -> { /* a.reason drives the UI */ }
 * }
 * ```
 */
class OnDeviceAi(
    private val engines: List<InferenceEngine> = listOf(SystemEngine()),
    private val policy: RoutingPolicy = RoutingPolicy.Default,
) {

    init {
        require(engines.isNotEmpty()) { "OnDeviceAi needs at least one engine" }
    }

    /**
     * Best availability across engines the [policy] allows: an Available
     * engine wins, else an in-flight or possible download, else Unavailable.
     */
    suspend fun availability(feature: AiFeature = AiFeature.Prompt): Availability {
        val statuses = candidates().map { it.availability(feature) }
        return statuses.firstOrNull { it is Availability.Available }
            ?: statuses.firstOrNull { it is Availability.Downloading }
            ?: statuses.firstOrNull { it is Availability.Downloadable }
            ?: statuses.lastOrNull()
            ?: Availability.Unavailable(Availability.Unavailable.Reason.FeatureNotSupported)
    }

    /** Per-engine detail, for logging or [RoutingPolicy.Custom]-style decisions in app code. */
    suspend fun engineStatuses(feature: AiFeature = AiFeature.Prompt): Map<String, Availability> =
        statusMap(feature)

    /** Fetches weights for the first engine that reports [Availability.Downloadable]. */
    fun download(feature: AiFeature = AiFeature.Prompt): Flow<DownloadProgress> = flow {
        val engine = candidates().firstOrNull {
            val a = it.availability(feature)
            a is Availability.Downloadable || a is Availability.Downloading
        } ?: throw OnDeviceAiException.NoEngineAvailable(statusMap(feature))
        emitAll(engine.download(feature))
    }

    suspend fun chat(config: SessionConfig = SessionConfig()): ChatSession =
        selectEngine().openSession(config)

    /** One-shot generation over a throwaway session. */
    suspend fun generate(prompt: String, options: GenerationOptions = GenerationOptions()): String =
        chat(SessionConfig(options = options)).use { it.respond(prompt) }

    private fun candidates(): List<InferenceEngine> = when (policy) {
        RoutingPolicy.OnDeviceOnly -> engines.filter { it.isOnDevice }
        else -> engines
    }

    private suspend fun statusMap(feature: AiFeature): Map<String, Availability> =
        candidates().associate { it.id to it.availability(feature) }

    private suspend fun selectEngine(): InferenceEngine {
        val candidates = candidates()
        when (policy) {
            is RoutingPolicy.Custom -> {
                val statuses = statusMap(AiFeature.Prompt)
                val id = policy.select(statuses)
                return candidates.firstOrNull { it.id == id }
                    ?: throw OnDeviceAiException.NoEngineAvailable(statuses)
            }
            else -> {
                val statuses = mutableMapOf<String, Availability>()
                for (engine in candidates) {
                    val a = engine.availability(AiFeature.Prompt)
                    statuses[engine.id] = a
                    if (a is Availability.Available) return engine
                }
                throw OnDeviceAiException.NoEngineAvailable(statuses)
            }
        }
    }
}
