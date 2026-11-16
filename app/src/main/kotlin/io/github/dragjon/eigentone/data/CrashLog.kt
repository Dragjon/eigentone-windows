package io.github.dragjon.eigentone.data

import io.github.dragjon.eigentone.platform.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the most recent crash on this PC. Nothing is ever sent automatically: the user chooses whether to
 * share it.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"
    const val VERSION = "0.1.0"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                file(context).writeText("${deviceSummary(context)}\n\n$trace")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    fun deviceSummary(@Suppress("UNUSED_PARAMETER") context: Context): String =
        "Eigentone $VERSION on ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"

    private fun file(context: Context) = File(context.filesDir, FILE)
}
