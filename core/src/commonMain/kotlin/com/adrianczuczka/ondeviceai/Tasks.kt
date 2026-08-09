package com.adrianczuczka.ondeviceai

public enum class SummaryType { Bullets, Paragraph }

public enum class Tone { Professional, Friendly, Elaborate, Shorten }

// v0: these compile to prompt templates over the Prompt feature on every
// platform. The Android engine will route them to ML Kit's LoRA-tuned feature
// APIs (Summarization, Proofreading, Rewriting) once those clients are wired –
// same signatures, better output.

public suspend fun OnDeviceAi.summarize(
    text: String,
    type: SummaryType = SummaryType.Bullets,
): String {
    val shape = when (type) {
        SummaryType.Bullets -> "at most three short bullet points"
        SummaryType.Paragraph -> "a single short paragraph"
    }
    return generate(
        "Summarize the following text as $shape. Output only the summary.\n\n$text"
    )
}

public suspend fun OnDeviceAi.proofread(text: String): String =
    generate(
        "Correct the grammar, spelling, and punctuation of the following text. " +
            "Preserve the meaning and tone. Output only the corrected text.\n\n$text"
    )

public suspend fun OnDeviceAi.rewrite(text: String, tone: Tone = Tone.Professional): String {
    val instruction = when (tone) {
        Tone.Professional -> "in a professional tone"
        Tone.Friendly -> "in a warm, friendly tone"
        Tone.Elaborate -> "with more detail and elaboration"
        Tone.Shorten -> "more concisely, keeping all key information"
    }
    return generate(
        "Rewrite the following text $instruction. Output only the rewritten text.\n\n$text"
    )
}
