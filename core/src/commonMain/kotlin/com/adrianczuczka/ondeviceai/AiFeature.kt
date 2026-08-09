package com.adrianczuczka.ondeviceai

/**
 * The on-device AI capabilities this library can route to.
 *
 * [Prompt] is free-form generation and the primitive everything else builds on.
 * The task features map to platform-tuned implementations where they exist
 * (ML Kit's LoRA-adapted feature APIs on Android) and to vetted prompt
 * templates elsewhere.
 */
public enum class AiFeature {
    Prompt,
    Summarization,
    Proofreading,
    Rewriting,
    ImageDescription,
}
