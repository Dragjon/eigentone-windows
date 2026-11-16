package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.Repo
import io.github.dragjon.eigentone.platform.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Whether Windows plays a quiet tone at the level it's given. On Android a test tone plays outside every
 * effect; on Windows it passes through whatever the sound driver and Windows do before Eigentone's plug-in,
 * and some of that, such as a driver's Loudness Equalization, raises quiet sounds and evens out the volume.
 * The hearing check and the other level tools would then read wrong, so they check first.
 *
 * Plays a barely audible tone with Eigentone standing aside, and compares the level the plug-in receives
 * with the level played.
 */
object LevelCheck {
    private const val TAG = "EigentoneLevel"
    const val LEVEL_DB = -50.0
    private const val HZ = 1000.0
    private const val TONE_MS = 1500
    private const val FADE_MS = 30
    private const val SETTLE_MS = 400L
    private const val SKIP_MS = 300
    private const val GAIN_LIMIT_DB = 3.0
    private const val DRIFT_LIMIT_DB = 2.0
    private const val MIN_READINGS = 6

    /** How far the tone arrived from the level played, and how much it grew while playing. */
    data class Result(val gainDb: Double, val driftDb: Double) {
        val ok: Boolean get() = abs(gainDb) <= GAIN_LIMIT_DB && abs(driftDb) <= DRIFT_LIMIT_DB
    }

    /** The outputs that passed in this session, so a tool doesn't check again each time it starts. */
    private val passed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun passedFor(outputKey: String): Boolean = outputKey in passed

    /**
     * Runs the check. Returns null when it can't be told, such as with the background service not running or
     * something else playing, in which case the tool just goes ahead.
     */
    suspend fun run(): Result? = withContext(Dispatchers.IO) {
        if (!AudioService.client.connected.value || AudioService.musicActive.value) return@withContext null
        val outputKey = Repo.output.value.key
        val wasSuspended = Repo.hearingCheckRunning.value
        Repo.hearingCheckRunning.value = true
        try {
            // The plug-in crossfades to the plain sound when it's told to stand aside.
            delay(SETTLE_MS)
            val readings = java.util.concurrent.ConcurrentLinkedQueue<Pair<Long, Double>>()
            var started = 0L
            coroutineScope {
                val meters = launch {
                    AudioService.client.messages.collect { m ->
                        if (m["op"]?.jsonPrimitive?.content != "meters") return@collect
                        if (m["playing"]?.jsonPrimitive?.booleanOrNull != true) return@collect
                        m["peakDb"]?.jsonPrimitive?.content?.toDoubleOrNull()?.let { readings.add(System.currentTimeMillis() to it) }
                    }
                }
                val pcm = Sound.toPcm(tone())
                val line = Sound.stereoLine(Sound.RATE / 10)
                try {
                    line.start()
                    started = System.currentTimeMillis()
                    line.write(pcm, 0, pcm.size)
                    line.drain()
                } finally {
                    runCatching { line.close() }
                    meters.cancel()
                }
            }
            // The steady middle of the tone, past its fade in and Windows' delay, and before it ends.
            val steady = readings.filter { (at, _) -> at >= started + SKIP_MS && at <= started + TONE_MS }.map { it.second }
            if (steady.size < MIN_READINGS) return@withContext null
            val third = steady.size / 3
            val result = Result(
                gainDb = median(steady) - LEVEL_DB,
                driftDb = median(steady.takeLast(third)) - median(steady.take(third)),
            )
            Log.i(TAG, "Level check on $outputKey: ${"%.1f".format(result.gainDb)} dB off, ${"%.1f".format(result.driftDb)} dB drift")
            if (result.ok) passed.add(outputKey)
            result
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't check the level", e)
            null
        } finally {
            if (!wasSuspended) Repo.hearingCheckRunning.value = false
        }
    }

    private fun tone(): FloatArray {
        val frames = Sound.RATE * TONE_MS / 1000
        val fade = Sound.RATE * FADE_MS / 1000
        val amplitude = 10.0.pow(LEVEL_DB / 20)
        val out = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val envelope = min(1.0, min(i, frames - i).toDouble() / fade)
            val v = (amplitude * envelope * sin(2 * PI * HZ * i / Sound.RATE)).toFloat()
            out[i * 2] = v
            out[i * 2 + 1] = v
        }
        return out
    }

    private fun median(values: List<Double>): Double = values.sorted().let { it[it.size / 2] }
}
