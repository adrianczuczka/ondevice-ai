package com.adrianczuczka.ondeviceai

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StructuredRetryTest {

    @Serializable
    data class Advice(val wearJacket: Boolean, val reason: String)

    @Test
    fun parsesValidJsonFirstTry() = runTest {
        val session = FakeChatSession(
            mutableListOf("""{"wearJacket": true, "reason": "cold"}""")
        )
        val advice: Advice = session.respondStructured("jacket?")
        assertEquals(Advice(wearJacket = true, reason = "cold"), advice)
        assertEquals(1, session.prompts.size)
        assertTrue("wearJacket" in session.prompts[0], "schema should be in the prompt")
    }

    @Test
    fun retriesOnceOnInvalidJsonThenParses() = runTest {
        val session = FakeChatSession(
            mutableListOf("sorry, no JSON here", """{"wearJacket": false, "reason": "warm"}""")
        )
        val advice: Advice = session.respondStructured("jacket?")
        assertEquals(Advice(wearJacket = false, reason = "warm"), advice)
        assertEquals(2, session.prompts.size)
    }

    @Test
    fun throwsAfterRetryBudgetExhausted() = runTest {
        val session = FakeChatSession(mutableListOf("garbage forever"))
        assertFailsWith<OnDeviceAiException.StructuredOutputParseFailed> {
            session.respondStructured<Advice>("jacket?")
        }
        assertEquals(2, session.prompts.size)
    }
}
