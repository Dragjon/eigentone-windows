package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.AppState
import io.github.dragjon.eigentone.data.EngineStatus
import io.github.dragjon.eigentone.data.OutputInfo
import io.github.dragjon.eigentone.data.OutputKind
import io.github.dragjon.eigentone.data.Profiles
import io.github.dragjon.eigentone.data.Repo
import io.github.dragjon.eigentone.platform.Context
import io.github.dragjon.eigentone.platform.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the Android app's AudioService does, inside the Windows app: follows what's playing, loads each
 * output's preset, keeps the night schedule, and sends every change in the settings on as sound. Android
 * needs a separate service for this; on Windows the background service already keeps the sound going when
 * the app is closed, so this lives with the app.
 */
object AudioService {
    private const val TAG = "EigentoneAudio"
    private const val APPLY_INTERVAL_MS = 30L
    private const val SCHEDULE_POLL_MS = 30_000L
    private const val SILENCE_DB = -80.0
    private const val STATUS_POLL_MS = 2000L
    private const val QUIET_FOR_MS = 1500L

    private data class Inputs(
        val state: AppState,
        val attenuation: Double,
        val night: Boolean,
        val suspended: Boolean,
        val comparing: Boolean,
        val outputKey: String,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    val client = ServiceClient()
    private val engine = EffectEngine(client)
    @Volatile private var started = false

    /** The sound engine on the output that's playing: its sample rate and delay, or null when it isn't running. */
    data class EngineInfo(val sampleRate: Int, val latencyMs: Double)
    val engineInfo = kotlinx.coroutines.flow.MutableStateFlow<EngineInfo?>(null)

    /** Whether anything is playing through Eigentone right now, from the service's level readings. */
    val musicActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    @Volatile private var lastSoundAt = 0L

    /** Starts once; later calls do nothing. Named as on Android, where the screens call it. */
    @Synchronized
    fun start(@Suppress("UNUSED_PARAMETER") context: Context = Context.default) {
        if (started) return
        started = true

        scope.launch {
            client.connected.collect { connected ->
                Repo.status.value = Repo.status.value.copy(
                    running = connected,
                    mode = if (connected) "Windows audio engine" else "Off",
                    error = if (connected) null else "Eigentone's background service isn't running.",
                )
                if (connected) {
                    client.send(op("outputs"))
                    client.send(op("volume"))
                    // Level readings keep "is anything playing" current, and feed the spectrum and meter.
                    client.send(buildJsonObject { put("op", JsonPrimitive("meters")); put("on", JsonPrimitive(true)) })
                }
            }
        }
        scope.launch {
            // The engine's format, for the status bar, checked every couple of seconds.
            while (true) {
                if (client.connected.value) {
                    client.send(op("status"))
                    // The service only announces the volume when it moves, so it's asked for too.
                    client.send(op("volume"))
                }
                delay(STATUS_POLL_MS)
            }
        }
        scope.launch {
            client.messages.collect { m ->
                if (m["op"]?.jsonPrimitive?.content != "status") return@collect
                val engines = m["engines"]?.jsonArray?.map { it.jsonObject } ?: return@collect
                val playing = OutputDetector.playingGuid()
                val engine = engines.firstOrNull { it["output"]?.jsonPrimitive?.content == playing } ?: engines.firstOrNull()
                engineInfo.value = engine?.let {
                    EngineInfo(
                        it["sampleRate"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        it["latencyMs"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    )
                }
                // Settings' Status, as Android fills it from its effect: the bands the plug-in builds its filter
                // from while it runs on the playing output, and whether it has taken the latest settings. It takes
                // them as sound plays, so until then the last answer stands.
                val playingEngine = engines.firstOrNull { it["output"]?.jsonPrimitive?.content == playing }
                val seq = m["seq"]?.jsonPrimitive?.content?.toLongOrNull()
                val applied = playingEngine?.get("appliedSeq")?.jsonPrimitive?.content?.toLongOrNull()
                val status = Repo.status.value
                Repo.status.value = status.copy(
                    bandCount = if (playingEngine != null) EffectEngine.BANDS else 0,
                    readback = when {
                        playingEngine == null -> null
                        seq != null && applied == seq -> "${EffectEngine.BANDS}/${EffectEngine.BANDS}"
                        else -> status.readback
                    },
                    hasControl = OutputDetector.playingHooked() ?: true,
                )
            }
        }
        scope.launch {
            // Windows' volume for the playing output, for quiet listening, as Android reads the phone's volume.
            // Muted keeps the last reading, so unmuting doesn't jump.
            client.messages.collect { m ->
                if (m["op"]?.jsonPrimitive?.content != "volume") return@collect
                val db = m["db"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@collect
                val maxDb = m["maxDb"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                if (m["muted"]?.jsonPrimitive?.booleanOrNull == true) return@collect
                val below = volumeAttenuation(db, maxDb)
                if (below != Repo.attenuationDb.value) Log.i(TAG, "Volume: $below dB below full")
                Repo.attenuationDb.value = below
            }
        }
        scope.launch {
            client.messages.collect { m ->
                if (m["op"]?.jsonPrimitive?.content != "meters") return@collect
                val now = System.currentTimeMillis()
                val peak = m["peakDb"]?.jsonPrimitive?.content?.toDoubleOrNull()
                if (m["playing"]?.jsonPrimitive?.booleanOrNull == true && peak != null && peak > SILENCE_DB) lastSoundAt = now
                musicActive.value = now - lastSoundAt < QUIET_FOR_MS
            }
        }
        scope.launch {
            client.messages.collect { message ->
                if (message["op"]?.jsonPrimitive?.content == "outputs") {
                    message["outputs"]?.jsonArray?.let { OutputDetector.update(it.map { o -> o.jsonObject }) }
                    onOutputChanged()
                }
            }
        }
        scope.launch {
            combine(Repo.comparing, Repo.hearingCheckRunning) { comparing, checking -> comparing || checking }
                .distinctUntilChanged()
                .collect { busy -> if (!busy) onOutputChanged() }
        }
        scope.launch {
            // Restarts when the schedule is edited, so a change is heard at once rather than at the next check.
            Repo.state.map { it.nightSchedule }.distinctUntilChanged().collectLatest { schedule ->
                while (true) {
                    val now = java.time.LocalTime.now()
                    Repo.scheduledNight.value = schedule.isActive(now.hour * 60 + now.minute)
                    delay(SCHEDULE_POLL_MS)
                }
            }
        }
        scope.launch {
            combine(
                Repo.state, Repo.attenuationDb, Repo.scheduledNight, Repo.hearingCheckRunning,
                combine(Repo.comparing, Repo.output) { comparing, output -> comparing to output.key },
            ) { state, attenuation, night, suspended, (comparing, outputKey) ->
                Inputs(state, attenuation, night, suspended, comparing, outputKey)
            }
                .conflate()
                .collect { (state, attenuation, night, suspended, comparing, outputKey) ->
                    engine.attenuationDb = attenuation
                    engine.scheduledNight = night
                    engine.suspended = suspended || !state.enabled
                    engine.comparing = comparing
                    engine.outputKey = outputKey
                    engine.apply(state)
                    delay(APPLY_INTERVAL_MS)
                }
        }
        // Connects last, once every listener above is in place, so the service's first replies aren't missed.
        scope.launch { client.start() }
    }

    /** How far below full the volume is, from Windows' level and the top of its range, in whole tenths. */
    internal fun volumeAttenuation(db: Double, maxDb: Double): Double =
        (kotlin.math.round((maxDb - db) * 10) / 10).coerceAtLeast(0.0)

    /** Android refreshes its Quick Settings tile here. The tray icon will do the same. */
    fun requestTileRefresh(@Suppress("UNUSED_PARAMETER") context: Context) = Unit

    /**
     * As on Android: compares with the output last acted on, which is saved, so a preset picked by hand
     * stays, while headphones changed in the meantime are still noticed. Waits while a tool is using the sound.
     */
    @Synchronized
    private fun onOutputChanged() {
        val info = OutputDetector.current(Context.default).info
        Repo.output.value = info
        Repo.update { it.withLegacyEarsMovedTo(info) }
        if (Repo.toolBusy) return
        val state = Repo.state.value
        if (state.lastOutputKey == info.key) return
        Repo.update { it.copy(lastOutputKey = info.key) }
        Log.i(TAG, "Now playing on ${info.label} (${info.kind})")
        if (!state.autoSwitch) return
        Profiles.presetForNewOutput(state, state.lastOutputKey, info.key, Repo::findPreset)?.let(Repo::loadPreset)
    }

    internal fun op(name: String): JsonObject = buildJsonObject { put("op", JsonPrimitive(name)) }
}

/**
 * Which output is playing, from the service's list. Keys follow the Android app's ("family:name"), and the
 * computer's own speakers are "speaker", as a phone's are, so the same per-output logic applies.
 */
object OutputDetector {
    class Detected(val info: OutputInfo, val deviceType: Int)

    @Volatile private var playing: JsonObject? = null

    /** The names of the outputs that are connected, kept current as devices come and go. */
    val names = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())

    /** Whether Eigentone is in the playing output's effects, or null before the service has said. */
    fun playingHooked(): Boolean? = playing?.get("hooked")?.jsonPrimitive?.booleanOrNull

    /** The playing output's ID, as the service names engines. */
    fun playingGuid(): String? = playing?.get("guid")?.jsonPrimitive?.content

    /** One connected output, for listing them all: what it is, Windows' name for it, and whether it's playing. */
    class Listed(val info: OutputInfo, val name: String, val playing: Boolean)

    /** Every connected output, the one playing first. */
    val all = kotlinx.coroutines.flow.MutableStateFlow<List<Listed>>(emptyList())

    fun update(outputs: List<JsonObject>) {
        playing = outputs.firstOrNull { it["default"]?.jsonPrimitive?.booleanOrNull == true }
        val active = outputs.filter { it["active"]?.jsonPrimitive?.booleanOrNull == true }
        names.value = active.map { it["name"]?.jsonPrimitive?.content.orEmpty() }
        all.value = active
            .map { o -> Listed(infoOf(o), o["name"]?.jsonPrimitive?.content.orEmpty(), o === playing) }
            .sortedByDescending { it.playing }
    }

    fun current(@Suppress("UNUSED_PARAMETER") context: Context): Detected {
        val o = playing ?: return Detected(OutputInfo("speaker", "This PC"), 0)
        return Detected(infoOf(o), 0)
    }

    private fun infoOf(o: JsonObject): OutputInfo {
        val name = o["name"]?.jsonPrimitive?.content.orEmpty()
        val device = o["device"]?.jsonPrimitive?.content.orEmpty()
        val bus = o["bus"]?.jsonPrimitive?.content.orEmpty()
        // The name inside the brackets is the hardware's, e.g. "Headphones (WH-1000XM5)".
        val product = Regex("""\((.+)\)\s*$""").find(name)?.groupValues?.get(1) ?: name
        val family = when {
            bus.startsWith("BTH") -> "Bluetooth"
            bus.startsWith("USB") -> "USB"
            else -> "Wired"
        }
        val info = when (o["kind"]?.jsonPrimitive?.content) {
            "Built-in speakers" -> OutputInfo("speaker", "This PC", OutputKind.PHONE)
            "Headphones" -> OutputInfo("$family:${if (family == "Wired") device else product}", if (family == "Wired") "Headphones" else product, OutputKind.HEADPHONES)
            "Earbuds" -> OutputInfo("$family:$product", product, OutputKind.EARBUDS)
            "Car" -> OutputInfo("$family:$product", product, OutputKind.CAR)
            "TV or monitor" -> OutputInfo("Display:$product", product, OutputKind.SPEAKER)
            else -> OutputInfo("$family:$product", product, OutputKind.SPEAKER)
        }
        return info
    }
}
