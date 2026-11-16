package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.Repo
import io.github.dragjon.eigentone.platform.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Which app is playing, for per-app presets, as Android reads it from media notifications. Windows keeps its
 * audio sessions per signed-in user, out of the background service's sight, so the app runs Eigentone's agent
 * in this session. It writes a line whenever the apps playing change; the one that started most recently is
 * the playing app. With none playing, the last one stays.
 */
object AppWatcher {
    private const val TAG = "EigentoneApps"
    private const val RESTART_MS = 5000L
    @Volatile private var started = false

    /** The agent: shipped beside the app, or named by EIGENTONE_AGENT when running from a build. */
    private fun agent(): File? {
        System.getenv("EIGENTONE_AGENT")?.let { return File(it).takeIf(File::exists) }
        val dir = System.getProperty("compose.application.resources.dir") ?: return null
        return File(dir, "eigentone-agent.exe").takeIf(File::exists)
    }

    fun start() {
        if (started) return
        started = true
        Thread({
            while (true) {
                val exe = agent()
                if (exe == null) {
                    Log.w(TAG, "The agent isn't with the app, so per-app presets can't see which app is playing")
                    return@Thread
                }
                runCatching {
                    // Its standard input stays open while Eigentone runs; when Eigentone goes, the agent quits.
                    val process = ProcessBuilder(exe.absolutePath).redirectErrorStream(true).start()
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> runCatching { handle(Json.parseToJsonElement(line).jsonObject) } }
                    }
                }.onFailure { Log.w(TAG, "The agent stopped", it) }
                Thread.sleep(RESTART_MS)
            }
        }, "AppWatcher").apply { isDaemon = true }.start()
    }

    internal fun handle(message: JsonObject) {
        if (message["op"]?.jsonPrimitive?.content != "apps") return
        val apps = message["apps"]?.jsonArray?.map { it.jsonObject } ?: return
        apps.forEach { a ->
            val exe = a["exe"]?.jsonPrimitive?.content ?: return@forEach
            a["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { AppNames.remember(exe, it) }
        }
        val newest = apps.maxByOrNull { it["since"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L }
            ?.get("exe")?.jsonPrimitive?.content ?: return
        Repo.onPlayingAppChanged(newest)
    }
}
