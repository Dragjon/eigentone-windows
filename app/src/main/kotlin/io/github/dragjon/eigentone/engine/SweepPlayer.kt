package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.Sweep
import io.github.dragjon.eigentone.data.SweepOscillator
import io.github.dragjon.eigentone.platform.Context
import io.github.dragjon.eigentone.platform.Log

/** The frequency sweep's tone, as on Android: follows [hz] and [levelDb] live, and fades rather than clicks. */
class SweepPlayer(@Suppress("UNUSED_PARAMETER") context: Context) {
    @Volatile var hz = Sweep.hzAt(0.0)
    @Volatile var levelDb = Sweep.DEFAULT_LEVEL_DB

    /** Fades out while still running, to put a gap between separate notes. */
    @Volatile var muted = false
    @Volatile private var sounding = false
    private var thread: Thread? = null

    /** Returns false if Windows wouldn't play it, so the screen can say so. */
    fun start(): Boolean {
        if (thread != null) return true
        val line = try {
            Sound.stereoLine(BUFFER_FRAMES * 2)
        } catch (e: Exception) {
            Log.w(TAG, "Could not create the tone", e)
            return false
        }
        sounding = true
        thread = Thread({ run(line) }, "SweepTone").also { it.start() }
        return true
    }

    /** Fades out, then lets go of the audio. Takes a fraction of a second. */
    fun stop() {
        sounding = false
        thread?.let { runCatching { it.join(STOP_WAIT_MS) } }
        thread = null
    }

    private fun run(line: javax.sound.sampled.SourceDataLine) {
        val oscillator = SweepOscillator(Sound.RATE)
        val buffer = FloatArray(BUFFER_FRAMES * 2)
        try {
            line.start()
            while (sounding || !oscillator.isSilent) {
                oscillator.fill(buffer, hz, levelDb, sounding && !muted)
                val pcm = Sound.toPcm(buffer)
                line.write(pcm, 0, pcm.size)
            }
            line.drain()
        } catch (e: Exception) {
            Log.w(TAG, "The tone stopped", e)
        } finally {
            runCatching { line.close() }
        }
    }

    private companion object {
        const val TAG = "EigentoneSweep"
        const val BUFFER_FRAMES = 1024
        const val STOP_WAIT_MS = 500L
    }
}
