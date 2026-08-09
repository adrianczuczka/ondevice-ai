package com.adrianczuczka.ondeviceai

import com.adrianczuczka.ondeviceai.internal.StructuredPrompting
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructuredPromptingTest {

    @Serializable
    data class Advice(
        val wearJacket: Boolean,
        val reason: String,
        val confidence: Int? = null,
    )

    @Test
    fun rendersReadableSchema() {
        val schema = StructuredPrompting.renderSchema(Advice.serializer().descriptor)
        assertTrue("wearJacket" in schema)
        assertTrue("reason" in schema)
        assertTrue("(optional)" in schema)
    }

    @Test
    fun extractsJsonFromFencedOutput() {
        val raw = "Sure! Here you go:\n```json\n{\"wearJacket\": true, \"reason\": \"cold\"}\n```"
        assertEquals(
            "{\"wearJacket\": true, \"reason\": \"cold\"}",
            StructuredPrompting.extractJson(raw),
        )
    }

    @Test
    fun extractJsonPassesThroughPlainObjects() {
        val raw = "{\"a\": 1}"
        assertEquals(raw, StructuredPrompting.extractJson(raw))
    }
}
