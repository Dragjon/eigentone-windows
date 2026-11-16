package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.AppState
import io.github.dragjon.eigentone.data.Refiner
import io.github.dragjon.eigentone.data.SoundFinder
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Refine's last step compares the refined sound with the starting one. Both sides are built here exactly as
 * that step builds them, and what the plug-in is sent must differ by about as much as the refinement.
 */
class RefineCompareTest {
    private fun eqLeft(state: AppState): FloatArray {
        val p = EffectEngine(ServiceClient()).apply { comparing = true }.paramsFor(state)
        return p["eq"]!!.jsonObject["gainDb"]!!.jsonArray[0].jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
    }

    private fun side(start: AppState, change: List<Double>): AppState {
        val tone = Refiner.apply(start.current.tone, change)
        return start.copy(current = SoundFinder.levelMatched(start.current, tone, start.current, start.autoHeadroom))
    }

    @Test
    fun theRefinedSideReachesThePluginDifferentFromTheStartingSide() {
        val start = AppState()
        // A typical result: a little more bass, a little less upper mids.
        val change = listOf(2.0, 1.5, 0.5, 0.0, 0.0, -0.5, -1.5, -1.0, 0.0, 0.0)
        val refined = eqLeft(side(start, change))
        val plain = eqLeft(side(start, List(change.size) { 0.0 }))
        // The level matching shifts the whole curve, so it's the shape that has to differ.
        val diff = refined.indices.map { (refined[it] - plain[it]).toDouble() }
        val spread = diff.max() - diff.min()
        assertTrue("the two sides differ in shape by ${"%.2f".format(spread)} dB", spread > 2.0)
    }
}
