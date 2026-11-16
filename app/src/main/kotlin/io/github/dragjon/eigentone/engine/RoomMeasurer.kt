package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.RoomMeasurement
import io.github.dragjon.eigentone.platform.Context
import io.github.dragjon.eigentone.platform.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

/**
 * Measure a speaker, as on Android: plays the measurement sequence and records it at the same time. Uses
 * Windows' default microphone, which is a USB measurement microphone when one is set as the default.
 */
class RoomMeasurer(@Suppress("UNUSED_PARAMETER") context: Context) {
    /**
     * A USB microphone that's connected, by name, or null if there's none, as on Android, where that's the
     * microphone used. Java can't tell which microphone is Windows' default, which is the one recorded from,
     * so the screen says to choose it there.
     */
    fun microphone(): String? = runCatching {
        val wanted = DataLine.Info(TargetDataLine::class.java, Sound.monoFormat())
        AudioSystem.getMixerInfo()
            .filter { info -> USB_NAMES.any { info.name.contains(it, ignoreCase = true) } }
            .firstOrNull { info -> AudioSystem.getMixer(info).isLineSupported(wanted) }
            ?.name
    }.getOrNull()

    /** Plays the sequence once and returns the recording, or null if Windows wouldn't record or play. */
    suspend fun measure(onProgress: (Float) -> Unit): FloatArray? = withContext(Dispatchers.IO) {
        val signal = RoomMeasurement.signal()
        val rate = RoomMeasurement.SAMPLE_RATE
        val format = javax.sound.sampled.AudioFormat(rate.toFloat(), 16, 1, true, false)
        val recorder = try {
            AudioSystem.getTargetDataLine(format).apply { open(format, rate) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not record", e)
            return@withContext null
        }
        val player = try {
            AudioSystem.getSourceDataLine(format).apply { open(format, rate / 2) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not play", e)
            recorder.close()
            return@withContext null
        }
        val recording = FloatArray(signal.size)
        try {
            recorder.start()
            player.start()
            coroutineScope {
                // Plays on its own, so writing never waits on reading.
                launch(Dispatchers.IO) {
                    val chunk = rate / 20
                    var written = 0
                    while (written < signal.size) {
                        ensureActive()
                        val n = minOf(chunk, signal.size - written)
                        val pcm = Sound.toPcm(signal, written, n)
                        player.write(pcm, 0, pcm.size)
                        written += n
                    }
                }
                val buffer = ByteArray(rate / 20 * 2)
                var read = 0
                while (read < recording.size) {
                    ensureActive()
                    val n = recorder.read(buffer, 0, minOf(buffer.size, (recording.size - read) * 2)) / 2
                    if (n <= 0) break
                    for (i in 0 until n) {
                        val v = (buffer[i * 2].toInt() and 0xff) or (buffer[i * 2 + 1].toInt() shl 8)
                        recording[read + i] = v.toShort() / 32768f
                    }
                    read += n
                    onProgress(read.toFloat() / recording.size)
                }
            }
            recording
        } catch (e: Exception) {
            Log.w(TAG, "Measurement stopped", e)
            null
        } finally {
            runCatching { player.stop(); player.close() }
            runCatching { recorder.stop(); recorder.close() }
        }
    }

    private companion object {
        const val TAG = "EigentoneMeasure"

        /** How Windows names a USB microphone, or the measurement microphones people use most. */
        val USB_NAMES = listOf("USB", "UMIK", "miniDSP", "Dayton", "ECM8000", "Behringer")
    }
}
