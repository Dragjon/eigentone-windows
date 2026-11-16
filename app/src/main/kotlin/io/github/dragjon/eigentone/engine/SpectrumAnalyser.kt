package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.dsp.Eq
import io.github.dragjon.eigentone.dsp.Spectrum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.log2

/**
 * The live spectrum and level, as on Android, in the same form: 64 bands from 0 (silent) to 1 (full scale)
 * on the app's frequency axis. On Windows the plug-in measures what's playing and the service sends it on.
 */
class SpectrumAnalyser(
    private val onBands: (FloatArray) -> Unit,
    private val onLevel: (peakDb: Float, rmsDb: Float) -> Unit = { _, _ -> },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var last = FloatArray(BAND_COUNT)

    fun start(): Boolean {
        stop()
        val client = AudioService.client
        job = scope.launch {
            client.connected.collect { connected ->
                if (connected) client.send(buildJsonObject { put("op", JsonPrimitive("meters")); put("on", JsonPrimitive(true)) })
            }
        }
        scope.launch {
            client.messages.collect { m ->
                if (m["op"]?.jsonPrimitive?.content != "meters") return@collect
                if (m["playing"]?.jsonPrimitive?.booleanOrNull != true) {
                    last = Spectrum.smooth(last, FloatArray(BAND_COUNT))
                    onBands(last)
                    return@collect
                }
                val service = m["spectrumDb"]?.jsonArray?.map { it.jsonPrimitive.doubleOrNull ?: FLOOR_DB } ?: return@collect
                last = Spectrum.smooth(last, toBands(service))
                onBands(last)
                val peak = m["peakDb"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: return@collect
                // The service measures peaks only; the average sits about 9 dB under them in typical music.
                onLevel(peak, peak - TYPICAL_CREST_DB)
            }
        }.also { reader -> job?.invokeOnCompletion { reader.cancel() } }
        return true
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { AudioService.client.send(buildJsonObject { put("op", JsonPrimitive("meters")); put("on", JsonPrimitive(false)) }) }
    }

    /** The service's sixth-octave bands from 20 Hz to 20 kHz, read at each of the app's band centres. */
    private fun toBands(serviceDb: List<Double>): FloatArray = FloatArray(BAND_COUNT) { band ->
        val hz = Eq.logFreq((band + 0.5) / BAND_COUNT)
        val position = (log2(hz / 20.0) / log2(1000.0) * serviceDb.size - 0.5).coerceIn(0.0, serviceDb.size - 1.0)
        val i = position.toInt().coerceAtMost(serviceDb.size - 2)
        val t = position - i
        val db = serviceDb[i] * (1 - t) + serviceDb[i + 1] * t
        ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0.0, 1.0).toFloat()
    }

    private companion object {
        const val BAND_COUNT = 64
        const val FLOOR_DB = -72.0
        const val TYPICAL_CREST_DB = 9f
    }
}
