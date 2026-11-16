package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.Ear
import io.github.dragjon.eigentone.data.HearingTest
import io.github.dragjon.eigentone.platform.Context
import io.github.dragjon.eigentone.platform.Log
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** The hearing check's tones: the same three soft-edged pulses as on Android. */
class TonePlayer(@Suppress("UNUSED_PARAMETER") context: Context) {
    @Volatile private var line: SourceDataLine? = null
    private var thread: Thread? = null

    fun begin() = Unit

    fun end() = stop()

    fun stop() {
        line?.let { runCatching { it.stop(); it.flush(); it.close() } }
        line = null
        thread = null
    }

    /** Returns false if Windows wouldn't play it, so the listener isn't asked about a tone that never happened. */
    fun play(hz: Double, levelDb: Double, ear: Ear): Boolean = try {
        stop()
        val pcm = Sound.toPcm(pulses(hz, levelDb.coerceAtMost(HearingTest.CEILING_DB), ear))
        val next = Sound.stereoLine(pcm.size / 4)
        next.start()
        line = next
        thread = Thread({ runCatching { next.write(pcm, 0, pcm.size); next.drain() } }, "HearingTone").also { it.start() }
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not play a tone", e)
        false
    }

    private fun pulses(hz: Double, levelDb: Double, ear: Ear): FloatArray {
        val amplitude = 10.0.pow(levelDb / 20)
        val pulse = Sound.RATE * PULSE_MS / 1000
        val gap = Sound.RATE * GAP_MS / 1000
        val fade = Sound.RATE * FADE_MS / 1000
        val frames = PULSES * (pulse + gap)
        val out = FloatArray(frames * 2)
        val channel = if (ear == Ear.LEFT) 0 else 1
        for (i in 0 until frames) {
            val position = i % (pulse + gap)
            if (position >= pulse) continue
            // Fading in and out avoids a click, which would be audible even when the tone isn't.
            val envelope = min(1.0, min(position, pulse - position).toDouble() / fade)
            out[i * 2 + channel] = (amplitude * envelope * sin(2 * PI * hz * i / Sound.RATE)).toFloat()
        }
        return out
    }

    companion object {
        private const val TAG = "EigentoneTone"
        private const val PULSES = 3
        private const val PULSE_MS = 300
        private const val GAP_MS = 150
        private const val FADE_MS = 20

        /** How long one tone takes to play. */
        const val TONE_MS = PULSES * (PULSE_MS + GAP_MS).toLong()
    }
}
