package io.github.dragjon.eigentone.platform

/**
 * Opening Eigentone in the tray when you sign in to Windows, through the per-user Run key, as most tray apps
 * do. It needs no administrator rights, and Settings, Apps, Startup lists it, where it can also be turned off.
 *
 * Only an installed copy knows its own program file, from the `jpackage.app-path` property its launcher sets.
 * Run from a build, there's nothing to register, so this leaves the key alone.
 */
object Startup {
    private const val TAG = "EigentoneStartup"
    private const val RUN_KEY = """HKCU\Software\Microsoft\Windows\CurrentVersion\Run"""
    private const val VALUE = "Eigentone"

    /** The argument the sign-in entry passes, so Eigentone starts in the tray rather than in a window. */
    const val TRAY_ARG = "--tray"

    /** Eigentone's program file, or null when it's running from a build. */
    val program: String? get() = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    /** Adds or removes the sign-in entry. Called with the setting at start and whenever it changes. */
    fun apply(on: Boolean) {
        val exe = program ?: return
        val command = if (on) {
            listOf("reg", "add", RUN_KEY, "/v", VALUE, "/t", "REG_SZ", "/d", "\"$exe\" $TRAY_ARG", "/f")
        } else {
            listOf("reg", "delete", RUN_KEY, "/v", VALUE, "/f")
        }
        runCatching {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            process.inputStream.readBytes()
            val code = process.waitFor()
            // Deleting an entry that isn't there fails harmlessly.
            if (code != 0 && on) Log.w(TAG, "Couldn't add Eigentone to sign-in apps ($code)")
        }.onFailure { Log.w(TAG, "Couldn't change sign-in apps", it) }
    }
}
