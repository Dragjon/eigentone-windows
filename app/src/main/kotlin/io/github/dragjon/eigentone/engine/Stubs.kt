package io.github.dragjon.eigentone.engine

import io.github.dragjon.eigentone.data.Preset
import io.github.dragjon.eigentone.platform.Context

/**
 * Android features that Windows does differently, kept under their Android names so the screens build.
 * Each says what replaces it.
 */

/**
 * Android reads the playing app from media notifications, which needs the user's permission. On Windows the
 * service lists the apps playing on the output (see AudioService), which needs none.
 */
class MediaWatcher(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") onApp: (String?) -> Unit) {
    fun attachIfAllowed() = Unit
    fun release() = Unit

    companion object {
        fun isAllowed(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = true
    }
}

/** Android's app-icon shortcuts. On Windows these become the taskbar menu, in M6. */
object PresetShortcuts {
    const val ACTION_LOAD_PRESET = "io.github.dragjon.eigentone.LOAD_PRESET"
    const val EXTRA_PRESET_ID = "preset"
    fun update(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") presets: List<Preset>) = Unit
}

/** Reads at most [limit] bytes, so opening the wrong, huge file can't exhaust memory. */
fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
