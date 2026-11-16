package io.github.dragjon.eigentone.platform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

/**
 * Windows' own title bar, in Eigentone's colours rather than plain white. It stays Windows' bar, so snapping,
 * Snap layouts on the maximise key and the rest work as in any app; only its colours change. Windows 11 does
 * the colours; Windows 10 takes only the dark setting.
 */
object TitleBar {
    private interface Dwm : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: Dwm? by lazy { runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    /** What the bar draws besides its keys. */
    class Options : com.sun.jna.Structure() {
        @JvmField var flags: Int = 0
        @JvmField var mask: Int = 0
        override fun getFieldOrder() = listOf("flags", "mask")
    }

    private interface UxTheme : StdCallLibrary {
        fun SetWindowThemeAttribute(hwnd: WinDef.HWND, attribute: Int, options: Options, size: Int): Int
    }

    private val uxTheme: UxTheme? by lazy { runCatching { Native.load("uxtheme", UxTheme::class.java) }.getOrNull() }
    private const val NON_CLIENT = 1
    private const val NO_CAPTION = 0x1
    private const val NO_ICON = 0x2

    private const val USE_DARK_MODE = 20
    private const val BORDER_COLOR = 34
    private const val CAPTION_COLOR = 35
    private const val TEXT_COLOR = 36

    /** Colours [window]'s title bar: its background, its text, and the window's thin border. */
    fun style(window: java.awt.Window, dark: Boolean, background: Color, text: Color, border: Color) {
        val api = dwm ?: return
        val hwnd = runCatching { WinDef.HWND(Native.getComponentPointer(window)) }.getOrNull() ?: return
        if (hwnd.pointer == Pointer.NULL) return
        fun set(attribute: Int, value: Int) = api.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), 4)
        set(USE_DARK_MODE, if (dark) 1 else 0)
        set(CAPTION_COLOR, colorRef(background))
        set(TEXT_COLOR, colorRef(text))
        set(BORDER_COLOR, colorRef(border))
        // The app's own header carries the name and logo just below, so the bar doesn't repeat them. The taskbar
        // and Alt+Tab still show both.
        uxTheme?.let { ux ->
            val options = Options().apply { flags = NO_CAPTION or NO_ICON; mask = NO_CAPTION or NO_ICON }
            ux.SetWindowThemeAttribute(hwnd, NON_CLIENT, options, options.size())
        }
    }

    /** Windows takes colours as 0x00BBGGRR. */
    private fun colorRef(c: Color): Int {
        val argb = c.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }
}
