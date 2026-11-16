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
        val tray = rememberTrayState()
        var toldAboutTray by remember { mutableStateOf(false) }
        val stillRunningTitle = stringResource(Res.string.win_tray_still_running)
        val stillRunningBody = stringResource(Res.string.win_tray_still_running_body)

        EigentoneTray(tray, onOpen = ::show, onQuit = ::exitApplication)

        Window(
            // Closing the window leaves Eigentone in the tray, where quiet listening and the tray menu keep
            // working. Quit is in the tray menu.
            onCloseRequest = {
                visible = false
                if (!toldAboutTray) {
                    toldAboutTray = true
                    tray.sendNotification(Notification(stillRunningTitle, stillRunningBody, Notification.Type.Info))
                }
            },
            visible = visible,
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
        }
    }
}

/**
 * The tray icon, which does what Android's Quick Settings tile does and more: on and off, Bypass and the
 * presets, without opening the window. Clicking the icon opens it. The tooltip says what Eigentone is doing.
 */
@Composable
private fun ApplicationScope.EigentoneTray(state: TrayState, onOpen: () -> Unit, onQuit: () -> Unit) {
    val app by Repo.state.collectAsState()
    val names by io.github.dragjon.eigentone.engine.OutputDetector.names.collectAsState()
    val presets = remember(app.presets, app.favorites, names) {
        (BuiltIns.availableFor(names) + app.presets)
            .sortedByDescending { it.id in app.favorites }
            .take(TRAY_PRESETS)
    }
    Tray(
        icon = painterResource(Res.drawable.ic_logo),
        state = state,
        tooltip = if (app.enabled) {
            stringResource(if (app.bypass) Res.string.win_tray_tooltip_bypass else Res.string.win_tray_tooltip_on, app.current.name)
        } else {
            stringResource(Res.string.win_tray_tooltip_off)
        },
        onAction = onOpen,
        menu = {
            Item(stringResource(Res.string.win_tray_open), onClick = onOpen)
            Separator()
            CheckboxItem(stringResource(Res.string.win_tray_on), checked = app.enabled) { on ->
                Repo.update { it.copy(enabled = on) }
                if (on) AudioService.start()
            }
            CheckboxItem(stringResource(Res.string.win_tray_bypass), checked = app.bypass, enabled = app.enabled) { on ->
                Repo.update { it.copy(bypass = on) }
            }
            Menu(stringResource(Res.string.win_tray_presets)) {
                presets.forEach { preset ->
                    // The tray's menu is Windows' own, which has ticks but no radio buttons.
                    CheckboxItem(preset.name, checked = preset.id == app.current.id) { Repo.loadPreset(preset) }
                }
            }
            Separator()
            Item(stringResource(Res.string.win_tray_quit), onClick = onQuit)
        },
    )
}

/** The presets the tray lists, favourites first: enough to choose from, few enough to fit a menu. */
private const val TRAY_PRESETS = 15

/** Most of the screen, so the pages have room for their columns, without covering it all. */
private fun startSize(): DpSize {
    // Java gives the screen in scaled units already, the same as dp.
    val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    return DpSize((screen.width * 0.85).coerceAtMost(1520.0).dp, (screen.height * 0.85).coerceAtMost(960.0).dp)
}

private const val MIN_WIDTH = 640
private const val MIN_HEIGHT = 520
