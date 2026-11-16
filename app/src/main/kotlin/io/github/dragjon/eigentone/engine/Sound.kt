package io.github.dragjon.eigentone.engine

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.random.Random

/**
 * Playing float sound through Windows. Java's sound lines take 16-bit samples everywhere, so floats are
 * converted with a little random dither, which keeps very quiet test tones clean rather than gritty.
 */
internal object Sound {
    const val RATE = 48000

    fun stereoLine(bufferFrames: Int): SourceDataLine {
        val format = AudioFormat(RATE.toFloat(), 16, 2, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format, bufferFrames * 4)
        return line
    }

    fun monoFormat(): AudioFormat = AudioFormat(RATE.toFloat(), 16, 1, true, false)

    /** Floats in -1..1 to little-endian 16-bit, dithered. */
    fun toPcm(samples: FloatArray, from: Int = 0, count: Int = samples.size - from): ByteArray {
        val out = ByteArray(count * 2)
        for (i in 0 until count) {
            val dither = (Random.nextFloat() - Random.nextFloat())
            val v = (samples[from + i] * 32767f + dither).coerceIn(-32768f, 32767f).toInt()
            out[i * 2] = v.toByte()
            out[i * 2 + 1] = (v shr 8).toByte()
        }
        return out
    }
}
