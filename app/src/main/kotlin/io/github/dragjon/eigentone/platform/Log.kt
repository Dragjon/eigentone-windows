package io.github.dragjon.eigentone.platform

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Android's Log, for the JVM: lines to the console, which the app also keeps for its debug view. */
object Log {
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private const val KEEP = 2000
    private val recent = ArrayDeque<String>()

    /** The latest lines, oldest first, for the debug console. */
    fun lines(): List<String> = synchronized(recent) { recent.toList() }

    private fun write(level: Char, tag: String, message: String, error: Throwable?) {
        val line = "${LocalTime.now().format(time)} $level/$tag: $message" + (error?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: "")
        System.err.println(line)
        error?.printStackTrace()
        synchronized(recent) {
            recent.addLast(line)
            while (recent.size > KEEP) recent.removeFirst()
        }
    }

    fun d(tag: String, message: String): Int = write('D', tag, message, null).let { 0 }
    fun i(tag: String, message: String): Int = write('I', tag, message, null).let { 0 }
    fun w(tag: String, message: String, error: Throwable? = null): Int = write('W', tag, message, error).let { 0 }
    fun e(tag: String, message: String, error: Throwable? = null): Int = write('E', tag, message, error).let { 0 }
}
