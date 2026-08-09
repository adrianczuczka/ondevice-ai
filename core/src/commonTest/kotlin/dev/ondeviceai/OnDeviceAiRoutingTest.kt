package dev.ondeviceai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OnDeviceAiRoutingTest {

    private val available = Availability.Available(ModelInfo(engineId = "any"))

    private fun unavailable(
        reason: Availability.Unavailable.Reason = Availability.Unavailable.Reason.DeviceNotEligible,
    ) = Availability.Unavailable(reason)

    @Test
    fun selectsFirstAvailableEngine() = runTest {
        val second = FakeEngine("second", available)
        val ai = OnDeviceAi(listOf(FakeEngine("first", unavailable()), second))
        assertSame(second.session, ai.chat())
    }

    @Test
    fun availabilityPrefersAvailableOverDownloadable() = runTest {
        val ai = OnDeviceAi(
            listOf(FakeEngine("d", Availability.Downloadable(null)), FakeEngine("a", available))
        )
        assertIs<Availability.Available>(ai.availability())
    }

    @Test
    fun availabilityFallsBackToDownloadable() = runTest {
        val ai = OnDeviceAi(
            listOf(FakeEngine("u", unavailable()), FakeEngine("d", Availability.Downloadable(null)))
        )
        assertIs<Availability.Downloadable>(ai.availability())
    }

    @Test
    fun onDeviceOnlyPolicyIgnoresCloudEngines() = runTest {
        val cloud = FakeEngine("cloud", available, isOnDevice = false)
        val ai = OnDeviceAi(
            listOf(FakeEngine("device", unavailable()), cloud),
            RoutingPolicy.OnDeviceOnly,
        )
        assertFailsWith<OnDeviceAiException.NoEngineAvailable> { ai.chat() }
    }

    @Test
    fun customPolicyPicksEngineById() = runTest {
        val target = FakeEngine("target", unavailable())
        val ai = OnDeviceAi(
            listOf(FakeEngine("other", available), target),
            RoutingPolicy.Custom { "target" },
        )
        assertSame(target.session, ai.chat())
    }

    @Test
    fun generateClosesItsSession() = runTest {
        val engine = FakeEngine("e", available)
        val ai = OnDeviceAi(listOf(engine))
        assertEquals("ok", ai.generate("hi"))
        assertTrue(engine.session.closed)
    }

    @Test
    fun noEngineAvailableCarriesPerEngineStatuses() = runTest {
        val ai = OnDeviceAi(listOf(FakeEngine("x", unavailable())))
        val e = assertFailsWith<OnDeviceAiException.NoEngineAvailable> { ai.chat() }
        assertEquals(setOf("x"), e.statuses.keys)
    }

    @Test
    fun downloadRoutesToTheDownloadableEngine() = runTest {
        val ai = OnDeviceAi(
            listOf(FakeEngine("u", unavailable()), FakeEngine("d", Availability.Downloadable(1000)))
        )
        val events = ai.download().toList()
        assertEquals(DownloadProgress.Completed, events.last())
    }
}
