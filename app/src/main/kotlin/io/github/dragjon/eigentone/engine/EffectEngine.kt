package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.AppState
import io.github.dragjon.eigentone.data.Dynamics
import io.github.dragjon.eigentone.data.Processing
import io.github.dragjon.eigentone.data.ProcessingTarget
import io.github.dragjon.eigentone.data.ReverbRoom
import io.github.dragjon.eigentone.dsp.CurveMapper
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The Windows counterpart of the Android app's EffectEngine. Everything up to the final processing is the
 * shared logic: [Processing.target] works out the sound, [CurveMapper] samples its curve and [Dynamics.plan]
 * its compressor bands, exactly as on Android. Only the last step differs: instead of calling
 * DynamicsProcessing, the result goes to the service as one message, and the plug-in in Windows' audio
 * engine plays it. The plug-in crossfades every change itself, so there's no ramping here.
 */
class EffectEngine(private val client: ServiceClient) {
    private val mapper = CurveMapper(BANDS)

    /** Level taken off for loudness matching and headroom, as on Android. */
    var attenuationDb = 0.0
    var scheduledNight = false
    var outputKey = ""
    /** A tool such as the hearing check is playing its own sound, so the EQ steps aside, as on Android. */
    var suspended = false
    var comparing = false

    @Volatile private var lastSent: JsonObject? = null
    @Volatile private var latest: AppState? = null

    init {
        // A restarted service has its saved settings, but send ours again in case they're newer.
        client.onConnected = { latest?.let { apply(it, force = true) } }
    }

    fun apply(state: AppState, force: Boolean = false) {
        latest = state
        val params = paramsFor(state)
        if (!force && params == lastSent) return
        if (client.send(buildJsonObject { put("op", "apply"); put("params", params) })) lastSent = params
        if (System.getenv("EIGENTONE_SELFTEST") != null) {
            io.github.dragjon.eigentone.platform.Log.i("SelfTest", "apply enabled=${params["enabled"]} input=${params["inputGainDb"]} limiter=${params["limiter"]}")
        }
    }

    fun paramsFor(state: AppState): JsonObject {
        val effective = if (suspended) state.copy(bypass = true, matchBypass = false) else state
        val target = Processing.target(effective, attenuationDb, scheduledNight, outputKey)
        return paramsFor(target, effective)
    }

    private fun paramsFor(target: ProcessingTarget, state: AppState): JsonObject {
        val left = mapper.gains(target.filters)
        val right = if (target.rightFilters === target.filters) left else mapper.gains(target.rightFilters)
        // Bypass is the plain sound to compare against, so it has no balance either.
        val balance = if (state.bypass) 0.0 else state.balance
        val plan = Dynamics.plan(target.dynamics, target.tamers, Dynamics.FULL_BANDS)
        val room = Processing.reverbFor(state, suspended)

        return buildJsonObject {
            put("enabled", target.effectEnabled)
            putJsonArray("inputGainDb") {
                add(JsonPrimitive(target.inputDb + Processing.leftTrimDb(balance)))
                add(JsonPrimitive(target.inputDb + Processing.rightTrimDb(balance)))
            }
            putJsonObject("eq") {
                put("cutoffHz", floats(mapper.cutoffs))
                putJsonArray("gainDb") {
                    add(floats(left))
                    add(floats(right))
                }
            }
            putJsonObject("mbc") {
                put("enabled", plan.any { it.active })
                putJsonArray("bands") {
                    for (planned in plan) {
                        val b = planned.band
                        add(
                            buildJsonObject {
                                put("enabled", planned.active)
                                put("cutoffHz", planned.cutoffHz)
                                put("attackMs", b.attackMs)
                                put("releaseMs", b.releaseMs)
                                put("ratio", b.ratio)
                                put("thresholdDb", b.thresholdDb)
                                put("kneeDb", b.kneeDb)
                                put("postGainDb", b.makeupDb)
                            },
                        )
                    }
                }
            }
            putJsonObject("virtualizer") {
                put("enabled", target.surround > 0)
                put("strength", target.surround)
            }
            putJsonObject("reverb") {
                put("enabled", room != ReverbRoom.OFF)
                put("roomLevelMb", 0)
                put("roomHfLevelMb", room.roomHfLevel.toInt())
                put("decayTimeMs", room.decayMs)
                put("decayHfRatio", room.decayHfRatio / 1000.0)
                put("reflectionsLevelMb", REFLECTIONS_MB)
                put("reflectionsDelayMs", REFLECTIONS_DELAY_MS)
                // Android's own reverb takes away an offset that this engine doesn't, so the amount maps directly.
                put("reverbLevelMb", QUIETEST_MB + (0 - QUIETEST_MB) * state.current.tone.reverbAmount.coerceIn(0.0, 1.0))
                put("reverbDelayMs", REVERB_DELAY_MS)
                put("diffusion", room.diffusion / 1000.0)
                put("density", room.density / 1000.0)
            }
            putJsonObject("limiter") {
                val l = target.limiterSettings
                put("enabled", target.limiter)
                put("attackMs", l.attackMs)
                put("releaseMs", l.releaseMs)
                put("ratio", l.ratio)
                put("thresholdDb", l.thresholdDb)
                put("postGainDb", l.postGainDb)
            }
        }
    }

    private fun floats(values: FloatArray): JsonArray = buildJsonArray { for (v in values) add(JsonPrimitive(v)) }

    companion object {
        /** The engine takes up to 256 bands and designs one smooth filter from them, so more is only finer. */
        const val BANDS = 256
        private const val QUIETEST_MB = -3000.0
        private const val REFLECTIONS_MB = -1000
        private const val REFLECTIONS_DELAY_MS = 20
        private const val REVERB_DELAY_MS = 30
    }
}
