package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.Ear
import io.github.dragjon.eigentone.data.Repo
import io.github.dragjon.eigentone.platform.Context
import io.github.dragjon.eigentone.platform.Log
import kotlinx.serialization.json.jsonPrimitive

/**
 * Checks, end to end, that a tool's tone reaches Windows and Eigentone's plug-in while the engine stands aside:
 * plays one quiet tone the way the hearing check does and logs what the plug-in's meter read before and during
 * it. Run with EIGENTONE_SELFTEST=tone; the result is the last "SelfTest" line in the log.
 */
object SelfTest {
    private const val TAG = "SelfTest"

    fun tone() = Thread({
        Thread.sleep(4000)
        val peaks = java.util.concurrent.ConcurrentLinkedQueue<Pair<Long, Double>>()
        val reader = Thread({
            kotlinx.coroutines.runBlocking {
                AudioService.client.messages.collect { m ->
                    if (m["op"]?.jsonPrimitive?.content == "meters") {
                        m["peakDb"]?.jsonPrimitive?.content?.toDoubleOrNull()?.let { peaks.add(System.currentTimeMillis() to it) }
                    }
                }
            }
        }, "SelfTestMeters").apply { isDaemon = true; start() }
        Log.i(TAG, "start: musicActive=${AudioService.musicActive.value}")
        Repo.hearingCheckRunning.value = true
        Thread.sleep(1500)
        val quietUntil = System.currentTimeMillis()
        val player = TonePlayer(Context.default)
        val played = player.play(1000.0, -40.0, Ear.LEFT)
        val toneFrom = System.currentTimeMillis()
        Thread.sleep(TonePlayer.TONE_MS + 300)
        player.end()
        Repo.hearingCheckRunning.value = false
        val before = peaks.filter { it.first < quietUntil }.maxOfOrNull { it.second }
        val during = peaks.filter { it.first >= toneFrom }.maxOfOrNull { it.second }
        Log.i(TAG, "played=$played before=$before during=$during readings=${peaks.size}")
        reader.interrupt()
    }, "SelfTest").apply { isDaemon = true; start() }

    /** Runs the level check the hearing check uses, and logs its result. */
    fun level() = Thread({
        Thread.sleep(4000)
        val result = kotlinx.coroutines.runBlocking { LevelCheck.run() }
        Log.i(TAG, "level=$result ok=${result?.ok}")
    }, "SelfTest").apply { isDaemon = true; start() }
}
