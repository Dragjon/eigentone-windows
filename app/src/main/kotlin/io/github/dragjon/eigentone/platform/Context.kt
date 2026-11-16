package io.github.dragjon.eigentone.platform

import java.io.File

/**
 * What the shared logic needs from Android's Context: only where the app keeps its files. On Windows that's
 * %APPDATA%\Eigentone, created on first use.
 */
class Context(val filesDir: File) {
    val applicationContext: Context get() = this

    /** Downloads that can be fetched again, such as AutoEq's index. */
    val cacheDir: File get() = File(filesDir, "cache").apply { mkdirs() }

    companion object {
        val default: Context by lazy {
            val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".eigentone")
            Context(File(base, "Eigentone").apply { mkdirs() })
        }
    }
}
