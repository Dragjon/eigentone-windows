package io.github.dragjon.eigentone.platform

import io.github.dragjon.eigentone.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BaseMultiResolutionImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * The tray icon. A click opens the window, and a right click opens the app's flyout (TrayFlyout): on and off,
 * Bypass and the presets, as Android's Quick Settings tile has.
 *
 * Built on Java's tray directly rather than through Compose, which loads its images in the background and so
 * gave Windows a blank, white icon. The logo is drawn for each size Windows uses (tools/make_icon.py) and
 * handed over together, so Windows picks the one for the display's scaling instead of stretching one.
 */
object AppTray {
    private const val TAG = "EigentoneTray"
    private val SIZES = listOf(16, 20, 24, 32, 40, 48)

    private var icon: TrayIcon? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Words for the menu, read once from the app's strings, so the tray needs nothing from a window. */
    class Words(
        val open: String,
        val on: String,
        val bypass: String,
        val presets: String,
        val quit: String,
        val tooltipOn: (String) -> String,
        val tooltipBypass: (String) -> String,
        val tooltipOff: String,
    )

    /**
     * Shows the icon. A left click opens the window; a right click calls [onMenu] with where the pointer is on
     * screen, for the app's own flyout, rather than Windows' plain menu.
     */
    fun install(words: Words, onOpen: () -> Unit, onMenu: (java.awt.Point) -> Unit) {
        if (icon != null || !SystemTray.isSupported()) return
        val image = runCatching {
            BaseMultiResolutionImage(
                *SIZES.map { size -> ImageIO.read(AppTray::class.java.getResourceAsStream("/tray/$size.png")) }.toTypedArray(),
            )
        }.getOrElse {
            Log.w(TAG, "The tray icon's images are missing", it)
            return
        }
        val tray = TrayIcon(image, "Eigentone").apply {
            isImageAutoSize = true
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseReleased(e: java.awt.event.MouseEvent) {
                    when {
                        e.isPopupTrigger || e.button == java.awt.event.MouseEvent.BUTTON3 -> onMenu(e.locationOnScreen)
                        e.button == java.awt.event.MouseEvent.BUTTON1 -> onOpen()
                    }
                }
            })
        }
        runCatching { SystemTray.getSystemTray().add(tray) }.onFailure {
            Log.w(TAG, "Windows wouldn't add the tray icon", it)
            return
        }
        icon = tray
        // The tooltip says what Eigentone is doing.
        scope.launch {
            Repo.state.collect { s ->
                SwingUtilities.invokeLater {
                    tray.toolTip = when {
                        !s.enabled -> words.tooltipOff
                        s.bypass -> words.tooltipBypass(s.current.name)
                        else -> words.tooltipOn(s.current.name)
                    }
                }
            }
        }
    }

    /** A notification from the tray, such as Eigentone still running after the window is closed. */
    fun notify(title: String, message: String) {
        icon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
    }

    /** Takes the icon away, so it doesn't linger after quitting. */
    fun remove() {
        icon?.let { tray -> runCatching { SystemTray.getSystemTray().remove(tray) } }
        icon = null
    }
}
