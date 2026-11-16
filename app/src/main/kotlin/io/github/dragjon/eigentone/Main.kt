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

/**
 * Eigentone for Windows. The shared state (Repo) loads as on Android, the engine host starts, and the
 * window shows the same app. Escape goes back, like Android's back gesture.
 */
fun main() {
    CrashLog.install(Context.default)
    Repo.init(Context.default)
    // The surround slider drives the plug-in's own head-model surround; Android's alternative, stereo width
    // drawn with the EQ, isn't needed here.
    if (!Repo.state.value.useSystemSurround) Repo.update { it.copy(useSystemSurround = true) }
    AudioService.start(Context.default)

    application {
        Window(
            onCloseRequest = ::exitApplication,
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
            AppRoot()
        }
    }
}

/** Most of the screen, so the pages have room for their columns, without covering it all. */
private fun startSize(): DpSize {
    // Java gives the screen in scaled units already, the same as dp.
    val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    return DpSize((screen.width * 0.85).coerceAtMost(1520.0).dp, (screen.height * 0.85).coerceAtMost(960.0).dp)
}

private const val MIN_WIDTH = 640
private const val MIN_HEIGHT = 520
