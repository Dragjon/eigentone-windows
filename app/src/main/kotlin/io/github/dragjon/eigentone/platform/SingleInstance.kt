package io.github.dragjon.eigentone.platform

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.FileSystems
import java.nio.file.StandardWatchEventKinds

/**
 * One Eigentone at a time. Opening it again while it runs in the tray brings the running one's window forward,
 * as Windows apps with a tray icon do, rather than starting a second copy that would fight over the settings.
 *
 * The first copy holds a lock on a file in Eigentone's folder. A second copy finds it held, leaves a note
 * beside it asking to be shown, and quits; the first watches the folder and shows its window.
 */
object SingleInstance {
    private val dir: File get() = Context.default.filesDir
    private val lockFile get() = File(dir, "instance.lock")
    private const val SHOW_NOTE = "show-window"

    @Suppress("unused") private var held: FileLock? = null

    /** True if this is the only copy running. If not, it has asked the running one to show itself. */
    fun claim(): Boolean {
        dir.mkdirs()
        val lock = runCatching { RandomAccessFile(lockFile, "rw").channel.tryLock() }.getOrNull()
        if (lock != null) {
            held = lock
            File(dir, SHOW_NOTE).delete()
            return true
        }
        runCatching { File(dir, SHOW_NOTE).writeText(System.currentTimeMillis().toString()) }
        return false
    }

    /** Calls [onShow] whenever another copy asks for the window, on a thread of its own. */
    fun listen(onShow: () -> Unit) {
        Thread({
            runCatching {
                val watcher = FileSystems.getDefault().newWatchService()
                dir.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY)
                while (true) {
                    val key = watcher.take()
                    val asked = key.pollEvents().any { it.context().toString() == SHOW_NOTE }
                    key.reset()
                    if (asked) {
                        File(dir, SHOW_NOTE).delete()
                        onShow()
                    }
                }
            }
        }, "SingleInstance").apply { isDaemon = true }.start()
    }
}
