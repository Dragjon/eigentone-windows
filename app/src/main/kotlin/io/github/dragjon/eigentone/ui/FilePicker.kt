package io.github.dragjon.eigentone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Windows' own open and save dialogs, in the shape of the Android launchers the screens use: `launch(...)`
 * opens the dialog, and the chosen file (or null if cancelled) arrives in the callback.
 */
class FileLauncher(private val save: Boolean, private val onResult: () -> (File?) -> Unit) {
    /** For opening, the types Android was given; for saving, the suggested file name. */
    fun launch(input: Any) {
        val dialog = FileDialog(null as Frame?, if (save) "Save" else "Open", if (save) FileDialog.SAVE else FileDialog.LOAD)
        if (save && input is String) dialog.file = input
        dialog.isVisible = true
        val file = dialog.files.firstOrNull()
        onResult()(file)
    }
}

@Composable
fun rememberOpenFile(onResult: (File?) -> Unit): FileLauncher {
    val latest by rememberUpdatedState(onResult)
    return remember { FileLauncher(save = false) { latest } }
}

@Composable
fun rememberSaveFile(onResult: (File?) -> Unit): FileLauncher {
    val latest by rememberUpdatedState(onResult)
    return remember { FileLauncher(save = true) { latest } }
}
