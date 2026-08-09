package com.adrianczuczka.ondeviceai

public data class SessionConfig(
    /** System prompt applied to the whole session. */
    val instructions: String? = null,
    val options: GenerationOptions = GenerationOptions(),
)

/**
 * Best-effort generation hints. Fields a platform cannot honor are ignored
 * silently rather than throwing – e.g. [topK] applies on Android's Prompt API
 * and is ignored by Apple's Foundation Models.
 */
public data class GenerationOptions(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val topK: Int? = null,
)
