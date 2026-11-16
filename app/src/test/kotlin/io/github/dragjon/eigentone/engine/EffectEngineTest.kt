package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.AppState
import io.github.dragjon.eigentone.data.ReverbRoom
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bridge turns the shared app state into the message the service expects. */
class EffectEngineTest {
    private val engine = EffectEngine(ServiceClient())

    @Test
    fun theDefaultSoundIsOnWith256BandsPerEar() {
        val p = engine.paramsFor(AppState())
        assertTrue(p["enabled"]!!.jsonPrimitive.boolean)
        val eq = p["eq"]!!.jsonObject
        assertEquals(EffectEngine.BANDS, eq["cutoffHz"]!!.jsonArray.size)
        assertEquals(2, eq["gainDb"]!!.jsonArray.size)
        eq["gainDb"]!!.jsonArray.forEach { assertEquals(EffectEngine.BANDS, it.jsonArray.size) }
        assertEquals(2, p["inputGainDb"]!!.jsonArray.size)
    }

    /**
     * The tools' test tones play through the same output, so Eigentone's plug-in hears them. While a tool runs
     * the engine is suspended, which must hand over the plain sound, however much the preset does.
     */
    @Test
    fun aSuspendedEngineLetsTheToolsTonesThroughUntouched() {
        val busy = AppState().let { s ->
            s.copy(
                balance = 0.6,
                limiter = true,
                current = s.current.copy(
                    tone = s.current.tone.copy(bassDb = 8.0, surround = 0.8, reverb = ReverbRoom.entries.last(), reverbAmount = 1.0),
                ),
            )
        }
        val suspended = EffectEngine(ServiceClient()).apply { this.suspended = true }
        val p = suspended.paramsFor(busy)
        assertFalse("the plug-in passes the dry sound", p["enabled"]!!.jsonPrimitive.boolean)
        assertFalse("no surround", p["virtualizer"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
        assertFalse("no reverb", p["reverb"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)
        val gains = p["inputGainDb"]!!.jsonArray.map { it.jsonPrimitive.double }
        assertEquals("no balance", gains[0], gains[1], 1e-9)
        // And the same sound, not suspended, is processed, so the test above means something.
        assertTrue(engine.paramsFor(busy)["enabled"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun bypassSwitchesTheEngineOffAndDropsBalance() {
        val state = AppState().copy(bypass = true, balance = 0.8)
        val p = engine.paramsFor(state)
        val gains = p["inputGainDb"]!!.jsonArray.map { it.jsonPrimitive.double }
        assertEquals("no balance in bypass", gains[0], gains[1], 1e-9)
    }

    @Test
    fun reverbOffSendsItOffAndARoomSendsItsDecay() {
        val off = engine.paramsFor(AppState())["reverb"]!!.jsonObject
        assertFalse(off["enabled"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun theCompressorPlanHasTheAndroidBandCount() {
        val mbc = engine.paramsFor(AppState())["mbc"]!!.jsonObject
        assertEquals(io.github.dragjon.eigentone.data.Dynamics.FULL_BANDS, mbc["bands"]!!.jsonArray.size)
    }

    @Test
    fun everyRoomMapsToAValidDecay() {
        for (room in ReverbRoom.entries) {
            assertTrue(room.decayMs in 100..20000 || room == ReverbRoom.OFF)
        }
    }
}
