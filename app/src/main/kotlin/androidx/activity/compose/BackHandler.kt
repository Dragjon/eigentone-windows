package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Android's BackHandler, for the desktop, under the same name so the shared screens keep it. There's no back
 * button on a PC; Escape and the mouse's back button do the same job, through [BackDispatcher].
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val entry = remember { BackDispatcher.Entry() }
    SideEffect {
        entry.enabled = enabled
        entry.onBack = onBack
    }
    DisposableEffect(entry) {
        BackDispatcher.add(entry)
        onDispose { BackDispatcher.remove(entry) }
    }
}

/** The screens waiting to handle back, newest last. The newest one that's enabled gets it. */
object BackDispatcher {
    class Entry {
        @Volatile var enabled = true
        @Volatile var onBack: () -> Unit = {}
    }

    private val entries = mutableListOf<Entry>()

    @Synchronized fun add(entry: Entry) {
        entries += entry
    }

    @Synchronized fun remove(entry: Entry) {
        entries -= entry
    }

    /** Returns true if a screen took it. */
    fun back(): Boolean {
        val handler = synchronized(this) { entries.lastOrNull { it.enabled } } ?: return false
        handler.onBack()
        return true
    }
}
