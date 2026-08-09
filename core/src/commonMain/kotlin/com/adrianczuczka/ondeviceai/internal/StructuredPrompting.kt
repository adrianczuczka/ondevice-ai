package com.adrianczuczka.ondeviceai.internal

import com.adrianczuczka.ondeviceai.ChatSession
import com.adrianczuczka.ondeviceai.OnDeviceAiException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json

/**
 * Structured output via JSON prompting with a bounded validate-and-retry loop.
 * This is the portable fallback; the iOS engine should eventually swap it for
 * true constrained decoding (SerialDescriptor → DynamicGenerationSchema), and
 * Android for the ML Kit genai-schema path.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object StructuredPrompting {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun <T : Any> respondViaJsonPrompt(
        session: ChatSession,
        prompt: String,
        serializer: KSerializer<T>,
        maxAttempts: Int = 2,
    ): T {
        val schema = renderSchema(serializer.descriptor)
        var request = buildString {
            appendLine(prompt)
            appendLine()
            appendLine("Respond with a single JSON object matching this schema, and nothing else:")
            append(schema)
        }
        var lastRaw = ""
        repeat(maxAttempts) {
            val raw = session.respond(request)
            lastRaw = raw
            try {
                return json.decodeFromString(serializer, extractJson(raw))
            } catch (_: IllegalArgumentException) {
                request =
                    "Your previous reply was not a valid JSON object matching this schema:\n" +
                        "$schema\nReply with only the corrected JSON object."
            }
        }
        throw OnDeviceAiException.StructuredOutputParseFailed(lastRaw)
    }

    /** Pulls the outermost JSON object out of chatter and markdown fences. */
    internal fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start in 0..<end) raw.substring(start, end + 1) else raw.trim()
    }

    /** Human-readable schema for the prompt – small models follow examples better than JSON Schema. */
    internal fun renderSchema(descriptor: SerialDescriptor, indent: String = ""): String =
        when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> buildString {
                appendLine("{")
                val last = descriptor.elementsCount - 1
                for (i in 0..last) {
                    val child = descriptor.getElementDescriptor(i)
                    val optional =
                        if (child.isNullable || descriptor.isElementOptional(i)) " (optional)" else ""
                    val comma = if (i < last) "," else ""
                    appendLine(
                        "$indent  \"${descriptor.getElementName(i)}\": " +
                            renderSchema(child, "$indent  ").trim() + optional + comma
                    )
                }
                append("$indent}")
            }
            StructureKind.LIST ->
                "[${renderSchema(descriptor.getElementDescriptor(0), indent).trim()}, …]"
            StructureKind.MAP ->
                "{\"<key>\": ${renderSchema(descriptor.getElementDescriptor(1), indent).trim()}}"
            SerialKind.ENUM ->
                "\"<one of: ${descriptor.elementNames.joinToString(" | ")}>\""
            PrimitiveKind.STRING -> "\"<string>\""
            PrimitiveKind.BOOLEAN -> "<true|false>"
            PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE ->
                "<integer>"
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> "<number>"
            else -> "<value>"
        }
}
