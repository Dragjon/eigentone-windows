package io.github.dragjon.eigentone.ui

import org.jetbrains.compose.resources.*

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.dragjon.eigentone.resources.*
import io.github.dragjon.eigentone.data.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Eigentone's own log, read live, for working out what went wrong. Android lets an app read only what its own
 * process wrote, so this never shows anything from other apps. Lines wrap rather than scroll sideways.
 */
@Composable
fun DebugConsoleScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var live by remember { mutableStateOf(true) }
    var everything by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    BackHandler(onBack = onClose)

    LaunchedEffect(live, everything) {
        do {
            val read = withContext(Dispatchers.IO) { readOwnLog(everything) }
            val atEnd = !list.canScrollForward
            if (read != lines) {
                lines = read
                // Follows new lines only while already at the bottom, so reading further up isn't interrupted.
                if (atEnd && read.isNotEmpty()) list.scrollToItem(read.lastIndex)
            }
            if (live) delay(REFRESH_MS)
        } while (live && isActive)
    }

    fun asText() = (listOf(CrashLog.deviceSummary(context), "") + lines).joinToString("\n")

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Bar)
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = 32.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.ui_back), tint = Palette.Text)
            }
            Text(stringResource(Res.string.ui_debug_console), style = MaterialTheme.typography.titleLarge, color = Palette.Text, modifier = Modifier.weight(1f))
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SlidingToggle(
                options = listOf(
                    Res.drawable.ic_eigentone to stringResource(Res.string.ui_console_eigentone),
                    Res.drawable.ic_spectrum to stringResource(Res.string.ui_console_everything),
                ),
                selected = if (everything) 1 else 0,
                onSelect = { everything = it == 1 },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(Res.string.ui_console_note),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(Palette.Surface, PanelShape),
        ) {
            if (lines.isEmpty()) {
                Text(
                    stringResource(Res.string.ui_console_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    itemsIndexed(lines) { _, line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = when (levelOf(line)) {
                                'E', 'F' -> Palette.Danger
                                'W' -> Palette.Lit
                                else -> Palette.TextDim
                            },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Bar)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = { live = !live }, modifier = Modifier.weight(1f).heightIn(min = 28.dp)) {
                Text(stringResource(if (live) Res.string.ui_console_pause else Res.string.ui_console_resume), maxLines = 1)
            }
            FilledTonalButton(
                onClick = {
                    io.github.dragjon.eigentone.platform.Actions.copy(asText())
                    copied = true
                },
                modifier = Modifier.weight(1f).heightIn(min = 28.dp),
            ) { Text(stringResource(if (copied) Res.string.ui_copied else Res.string.ui_console_copy), maxLines = 1) }
            FilledTonalButton(
                onClick = {
                    io.github.dragjon.eigentone.platform.Actions.share("Eigentone log", asText())
                },
                modifier = Modifier.weight(1f).heightIn(min = 28.dp),
            ) { Text(stringResource(Res.string.ui_console_share), maxLines = 1) }
        }
    }
}

/** The latest lines Eigentone logged. "Everything" also includes the service's own log, once it shares one. */
private fun readOwnLog(@Suppress("UNUSED_PARAMETER") everything: Boolean): List<String> =
    io.github.dragjon.eigentone.platform.Log.lines().takeLast(MAX_LINES)

/** The level letter in a log line, such as the W in "09:00:01.234 W/Tag: text". */
private fun levelOf(line: String): Char? {
    val slash = line.indexOf('/')
    return if (slash > 0) line[slash - 1] else null
}

private const val MAX_LINES = 800
private const val REFRESH_MS = 1500L
