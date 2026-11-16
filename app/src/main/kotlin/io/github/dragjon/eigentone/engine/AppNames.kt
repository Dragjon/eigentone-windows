package io.github.dragjon.eigentone.engine

/**
 * What apps are called, such as "Spotify" for spotify.exe, from their program files' descriptions as the
 * service reads them. Presets are assigned to the program, which stays the same, while this is only shown.
 */
object AppNames {
    private val names = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun remember(exe: String, name: String) {
        names[exe] = name
    }

    /** The app's name, or its program's name without ".exe" before it's been seen playing. */
    fun of(exe: String): String = names[exe] ?: exe.removeSuffix(".exe")
}
