package io.github.dragjon.eigentone

import androidx.activity.compose.BackDispatcher
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.dragjon.eigentone.data.CrashLog
import io.github.dragjon.eigentone.data.Repo
import io.github.dragjon.eigentone.engine.AudioService
import io.github.dragjon.eigentone.platform.Context
import io.github.dragjon.eigentone.ui.AppRoot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.rememberTrayState
import io.github.dragjon.eigentone.data.BuiltIns
import io.github.dragjon.eigentone.platform.SingleInstance
import io.github.dragjon.eigentone.platform.Startup
import io.github.dragjon.eigentone.resources.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Eigentone for Windows. The shared state (Repo) loads as on Android, the engine host starts, and the
 * window shows the same app. Escape goes back, like Android's back gesture.
 */
fun main(args: Array<String>) {
    // Opened again while it's running, the running one comes forward instead.
    if (!SingleInstance.claim()) return
    CrashLog.install(Context.default)
    Repo.init(Context.default)
    // The surround slider drives the plug-in's own head-model surround; Android's alternative, stereo width
    // drawn with the EQ, isn't needed here.
    if (!Repo.state.value.useSystemSurround) Repo.update { it.copy(useSystemSurround = true) }
    AudioService.start(Context.default)
    when (System.getenv("EIGENTONE_SELFTEST")) {
        "tone" -> io.github.dragjon.eigentone.engine.SelfTest.tone()
        "level" -> io.github.dragjon.eigentone.engine.SelfTest.level()
    }

    // Keys that work from any app: Bypass, on and off, and the presets.
    io.github.dragjon.eigentone.platform.HotKeys.start(
        listOf(
            io.github.dragjon.eigentone.platform.HotKeys.Shortcut("Ctrl+Alt+B", 'B'.code) {
                if (Repo.state.value.enabled) Repo.update { it.copy(bypass = !it.bypass) }
            },
            io.github.dragjon.eigentone.platform.HotKeys.Shortcut("Ctrl+Alt+E", 'E'.code) {
                val on = !Repo.state.value.enabled
                Repo.update { it.copy(enabled = on) }
                if (on) AudioService.start()
            },
            io.github.dragjon.eigentone.platform.HotKeys.Shortcut("Ctrl+Alt+Right", io.github.dragjon.eigentone.platform.HotKeys.RIGHT) { Repo.nextPreset() },
            io.github.dragjon.eigentone.platform.HotKeys.Shortcut("Ctrl+Alt+Left", io.github.dragjon.eigentone.platform.HotKeys.LEFT) { previousPreset() },
        ),
    )

    // Signed in to Windows, it starts in the tray; opened by hand, in its window.
    val startInTray = Startup.TRAY_ARG in args
    // The sign-in entry follows the setting, and points at wherever Eigentone is installed now.
    Thread({
        kotlinx.coroutines.runBlocking {
            Repo.state.map { it.startOnBoot }.distinctUntilChanged().collect { Startup.apply(it) }
        }
    }, "Startup").apply { isDaemon = true }.start()

    application {
        var visible by remember { mutableStateOf(!startInTray) }
        // Counts requests to come forward, so asking twice brings it forward twice.
        var raised by remember { mutableStateOf(0) }
        fun show() {
            visible = true
            raised++
        }
        remember { SingleInstance.listen { javax.swing.SwingUtilities.invokeLater { show() } } }
        var toldAboutTray by remember { mutableStateOf(false) }
        val stillRunningTitle = stringResource(Res.string.win_tray_still_running)
        val stillRunningBody = stringResource(Res.string.win_tray_still_running_body)
        fun quit() {
            io.github.dragjon.eigentone.platform.AppTray.remove()
            exitApplication()
        }
        val words = io.github.dragjon.eigentone.platform.AppTray.Words(
            open = stringResource(Res.string.win_tray_open),
            on = stringResource(Res.string.win_tray_on),
            bypass = stringResource(Res.string.win_tray_bypass),
            presets = stringResource(Res.string.win_tray_presets),
            quit = stringResource(Res.string.win_tray_quit),
            tooltipOn = { name -> io.github.dragjon.eigentone.ui.appText(Res.string.win_tray_tooltip_on, name) },
            tooltipBypass = { name -> io.github.dragjon.eigentone.ui.appText(Res.string.win_tray_tooltip_bypass, name) },
            tooltipOff = stringResource(Res.string.win_tray_tooltip_off),
        )
        // Where the tray's flyout opens, while it's open: by the pointer, just above the taskbar.
        // EIGENTONE_FLYOUT opens it at start, by the clock, for looking at it without the tray.
        var flyoutAt by remember { mutableStateOf<java.awt.Point?>(null) }
        if (System.getenv("EIGENTONE_FLYOUT") != null) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(FLYOUT_TEST_DELAY_MS)
                val s = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
                flyoutAt = java.awt.Point(s.x + s.width - 40, s.y + s.height)
            }
        }
        LaunchedEffect(Unit) {
            javax.swing.SwingUtilities.invokeLater {
                io.github.dragjon.eigentone.platform.AppTray.install(words, onOpen = { show() }, onMenu = { at -> flyoutAt = if (flyoutAt == null) at else null })
            }
        }
        flyoutAt?.let { at -> TrayFlyoutWindow(at, onOpen = ::show, onQuit = ::quit, onClose = { flyoutAt = null }) }

        Window(
            // Closing the window leaves Eigentone in the tray, where quiet listening and the tray menu keep
            // working. Quit is in the tray menu.
            onCloseRequest = {
                visible = false
                if (!toldAboutTray) {
                    toldAboutTray = true
                    io.github.dragjon.eigentone.platform.AppTray.notify(stillRunningTitle, stillRunningBody)
                }
            },
            visible = visible,
            icon = painterResource(Res.drawable.app_icon_small),
            title = "Eigentone",
            state = rememberWindowState(size = startSize(), position = androidx.compose.ui.window.WindowPosition(androidx.compose.ui.Alignment.Center)),
            onPreviewKeyEvent = { e ->
                e.type == KeyEventType.KeyDown && e.key == Key.Escape && BackDispatcher.back()
            },
            // Undo and redo, as in any Windows app. Text fields see these keys first and keep their own.
            onKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return@Window false
                when {
                    e.key == Key.Z && !e.isShiftPressed -> Repo.undo()
                    e.key == Key.Y || (e.key == Key.Z && e.isShiftPressed) -> Repo.redo()
                    else -> return@Window false
                }
                true
            },
        ) {
            window.minimumSize = java.awt.Dimension(MIN_WIDTH, MIN_HEIGHT)
            LaunchedEffect(raised) {
                if (raised == 0) return@LaunchedEffect
                if (window.extendedState and java.awt.Frame.ICONIFIED != 0) window.extendedState = java.awt.Frame.NORMAL
                window.toFront()
                window.requestFocus()
            }
            AppRoot()
            // Windows' title bar in the studio's colours, following the theme as it changes.
            val colors = io.github.dragjon.eigentone.ui.Palette.colors
            val dark = io.github.dragjon.eigentone.ui.Palette.dark
            LaunchedEffect(colors, dark) {
                io.github.dragjon.eigentone.platform.TitleBar.style(window, dark, colors.bar, colors.text, colors.line)
            }
        }
    }
}

/** Back through favourites, or every preset when there are none: Repo.nextPreset the other way. */
private fun previousPreset() {
    if (Repo.toolBusy) return
    val state = Repo.state.value
    val all = BuiltIns.availableFor(listOf(Repo.output.value.key)) + state.presets
    val cycle = all.filter { it.id in state.favorites }.ifEmpty { all }
    val index = cycle.indexOfFirst { it.id == state.current.id }
    Repo.loadPreset(cycle[(if (index < 0) 0 else index - 1 + cycle.size) % cycle.size])
}



/** Most of the screen, so the pages have room for their columns, without covering it all. */
private fun startSize(): DpSize {
    // Java gives the screen in scaled units already, the same as dp.
    val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    return DpSize((screen.width * 0.85).coerceAtMost(1520.0).dp, (screen.height * 0.85).coerceAtMost(960.0).dp)
}

private const val MIN_WIDTH = 640
private const val MIN_HEIGHT = 520

/**
 * The tray's flyout, in a borderless window of its own that floats above everything, placed so its corner sits
 * by the pointer and it stays on screen above the taskbar. Clicking anywhere else closes it.
 */
@Composable
private fun TrayFlyoutWindow(at: java.awt.Point, onOpen: () -> Unit, onQuit: () -> Unit, onClose: () -> Unit) {
    val shadow = io.github.dragjon.eigentone.ui.TRAY_FLYOUT_SHADOW_DP
    val width = io.github.dragjon.eigentone.ui.TRAY_FLYOUT_WIDTH_DP + shadow * 2
    val height = io.github.dragjon.eigentone.ui.TRAY_FLYOUT_HEIGHT_DP + shadow * 2
    // Java gives the screen and pointer in the same scaled units as dp.
    val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val x = (at.x - width + shadow).coerceIn(screen.x, screen.x + screen.width - width)
    val y = (minOf(at.y, screen.y + screen.height) - height + shadow).coerceIn(screen.y, screen.y + screen.height - height)
    androidx.compose.runtime.key(at) {
        Window(
            onCloseRequest = onClose,
            title = "Eigentone menu",
            undecorated = true,
            transparent = true,
            // Never the active window, as Windows' own menus aren't, so the tray's overflow it opened from stays
            // open behind it. Its buttons still work.
            focusable = false,
            resizable = false,
            alwaysOnTop = true,
            state = rememberWindowState(
                size = DpSize(width.dp, height.dp),
                position = androidx.compose.ui.window.WindowPosition(x.dp, y.dp),
            ),
        ) {
            LaunchedEffect(Unit) {
                window.toFront()
                // It never has the focus to lose, so it closes when the active window changes instead: a click
                // anywhere else, or the overflow closing.
                val user32 = com.sun.jna.platform.win32.User32.INSTANCE
                val opener = user32.GetForegroundWindow()
                while (true) {
                    kotlinx.coroutines.delay(FLYOUT_WATCH_MS)
                    if (user32.GetForegroundWindow() != opener) {
                        onClose()
                        break
                    }
                }
            }
            io.github.dragjon.eigentone.ui.TrayFlyout(onOpen = onOpen, onQuit = onQuit, onClose = onClose)
        }
    }
}

/** How long after starting EIGENTONE_FLYOUT waits, so the window has opened and settled first. */
private const val FLYOUT_TEST_DELAY_MS = 6000L

/** How often the flyout checks whether something else has become the active window. */
private const val FLYOUT_WATCH_MS = 150L
