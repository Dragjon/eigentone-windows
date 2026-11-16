package io.github.dragjon.eigentone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.dragjon.eigentone.data.Repo
import io.github.dragjon.eigentone.engine.LevelCheck
import io.github.dragjon.eigentone.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.util.Locale
import kotlin.math.abs

/**
 * A level tool's Start button, which first checks that Windows plays a quiet tone at the level given (see
 * [LevelCheck]). If something before Eigentone changes it, this says what and how to turn it off, and the tool
 * can still be started anyway.
 */
@Composable
fun LevelCheckedStart(onStart: () -> Unit) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<LevelCheck.Result?>(null) }

    fun check() {
        if (LevelCheck.passedFor(Repo.output.value.key)) {
            onStart()
            return
        }
        checking = true
        problem = null
        scope.launch {
            val result = LevelCheck.run()
            checking = false
            if (result == null || result.ok) onStart() else problem = result
        }
    }

    val found = problem
    if (found != null) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Surface, PanelShape)
                .border(1.dp, Palette.Danger, PanelShape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.win_level_title), style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            fun db(v: Double) = String.format(Locale.US, "%.0f", abs(v))
            Text(
                when {
                    found.driftDb > DRIFT_SHOWN_DB -> stringResource(Res.string.win_level_drift, db(found.gainDb), db(found.driftDb))
                    found.gainDb > 0 -> stringResource(Res.string.win_level_louder, db(found.gainDb))
                    else -> stringResource(Res.string.win_level_quieter, db(found.gainDb))
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Text,
            )
            Text(stringResource(Res.string.win_level_fix), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { io.github.dragjon.eigentone.platform.Actions.openUrl("ms-settings:sound") }) {
                    Text(stringResource(Res.string.win_level_open_settings))
                }
                FilledTonalButton(onClick = ::check) { Text(stringResource(Res.string.win_level_again)) }
                TextButton(onClick = onStart) { Text(stringResource(Res.string.win_level_anyway)) }
            }
        }
    } else {
        Button(enabled = !checking, onClick = ::check, modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)) {
            Text(stringResource(if (checking) Res.string.win_level_checking else Res.string.ui_start))
        }
    }
}

private const val DRIFT_SHOWN_DB = 2.0
