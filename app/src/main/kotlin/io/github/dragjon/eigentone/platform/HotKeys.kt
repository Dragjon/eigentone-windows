package io.github.dragjon.eigentone.platform

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser

/**
 * Keys that work from any app, even with Eigentone in the tray, as media keys do. Windows delivers them to the
 * thread that registered them, so they live on a thread of their own with its own message loop. A combination
 * another app already holds is refused by Windows; that one is noted in the log and the rest still work.
 */
object HotKeys {
    private const val TAG = "EigentoneKeys"
    private const val MOD_ALT = 0x0001
    private const val MOD_CONTROL = 0x0002
    private const val MOD_NOREPEAT = 0x4000
    private const val WM_HOTKEY = 0x0312
    private const val VK_LEFT = 0x25
    private const val VK_RIGHT = 0x27

    /** One shortcut: what to call it, its key with Ctrl and Alt, and what it does. */
    class Shortcut(val keys: String, val virtualKey: Int, val action: () -> Unit)

    @Volatile var registered: List<String> = emptyList()
        private set

    fun start(shortcuts: List<Shortcut>) {
        Thread({
            val user32 = runCatching { User32.INSTANCE }.getOrNull() ?: return@Thread
            val held = mutableListOf<String>()
            shortcuts.forEachIndexed { i, s ->
                if (user32.RegisterHotKey(null, i + 1, MOD_CONTROL or MOD_ALT or MOD_NOREPEAT, s.virtualKey)) {
                    held += s.keys
                } else {
                    Log.w(TAG, "${s.keys} is taken by another app, so it isn't Eigentone's")
                }
            }
            registered = held
            val message = WinUser.MSG()
            while (user32.GetMessage(message, null, 0, 0) > 0) {
                if (message.message == WM_HOTKEY) {
                    val index = message.wParam.toInt() - 1
                    shortcuts.getOrNull(index)?.let { s -> runCatching { s.action() }.onFailure { Log.w(TAG, "${s.keys} failed", it) } }
                }
            }
        }, "HotKeys").apply { isDaemon = true }.start()
    }

    const val LEFT = VK_LEFT
    const val RIGHT = VK_RIGHT
}
