package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.platform.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * The connection to Eigentone's background service, over its named pipe: one JSON message per line each way.
 * Reconnects by itself if the service restarts. Messages the service sends on its own (outputs changing,
 * meters) arrive on [messages].
 */
class ServiceClient(private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _messages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val messages: SharedFlow<JsonObject> = _messages.asSharedFlow()

    // Opened for overlapped use: on a pipe opened the plain way, Windows holds every write until the read
    // waiting on another thread finishes, and the reader always waits.
    @Volatile private var pipe: AsynchronousFileChannel? = null
    private val writeLock = Any()

    /** Called after each (re)connection, so whoever uses the client can send the current settings again. */
    var onConnected: (() -> Unit)? = null

    fun start() {
        scope.launch {
            while (isActive) {
                val opened = runCatching {
                    AsynchronousFileChannel.open(Paths.get(PIPE), StandardOpenOption.READ, StandardOpenOption.WRITE)
                }.getOrNull()
                if (opened == null) {
                    delay(RETRY_MS)
                    continue
                }
                pipe = opened
                _connected.value = true
                Log.i(TAG, "Connected to the service")
                onConnected?.invoke()
                readUntilClosed(opened)
                pipe = null
                _connected.value = false
                Log.w(TAG, "Lost the service; retrying")
                delay(RETRY_MS)
            }
        }
    }

    /** Sends a message. Returns false while the service isn't reachable; the message is then dropped. */
    fun send(message: JsonObject): Boolean {
        val p = pipe ?: return false
        val line = (json.encodeToString(JsonObject.serializer(), message) + "\n").encodeToByteArray()
        return synchronized(writeLock) {
            runCatching {
                val buf = ByteBuffer.wrap(line)
                while (buf.hasRemaining()) p.write(buf, 0).get()
            }.isSuccess
        }
    }

    private fun readUntilClosed(p: AsynchronousFileChannel) {
        val buf = StringBuilder()
        val chunk = ByteArray(16 * 1024)
        val pending = java.io.ByteArrayOutputStream()
        while (true) {
            // A pipe has no position; the offset is ignored.
            val n = runCatching { p.read(ByteBuffer.wrap(chunk), 0).get() }.getOrDefault(-1)
            if (n <= 0) break
            pending.write(chunk, 0, n)
            // Split complete lines only; a UTF-8 character can straddle two reads.
            val bytes = pending.toByteArray()
            var start = 0
            for (i in bytes.indices) {
                if (bytes[i] == '\n'.code.toByte()) {
                    buf.append(bytes.decodeToString(start, i))
                    deliver(buf.toString())
                    buf.setLength(0)
                    start = i + 1
                }
            }
            pending.reset()
            pending.write(bytes, start, bytes.size - start)
        }
        runCatching { p.close() }
    }

    private fun deliver(line: String) {
        if (line.isBlank()) return
        val message = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return
        if (message["op"]?.jsonPrimitive?.content == "error") Log.w(TAG, "Service: $line")
        _messages.tryEmit(message)
    }

    private companion object {
        const val TAG = "ServiceClient"
        /** The service's pipe. EIGENTONE_PIPE points a test copy elsewhere, so it can't change the real sound. */
        val PIPE = """\\.\pipe\""" + (System.getenv("EIGENTONE_PIPE") ?: "eigentone")
        const val RETRY_MS = 2000L
        val json = Json { ignoreUnknownKeys = true }
    }
}
