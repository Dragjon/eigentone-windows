package io.github.dragjon.eigentone.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dragjon.eigentone.data.BuiltIns
import io.github.dragjon.eigentone.data.Repo
import io.github.dragjon.eigentone.engine.AudioService
import io.github.dragjon.eigentone.engine.OutputDetector
import io.github.dragjon.eigentone.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The flyout's size; the window is a little larger, to leave room for its shadow. */
const val TRAY_FLYOUT_WIDTH_DP = 300
const val TRAY_FLYOUT_HEIGHT_DP = 440
const val TRAY_FLYOUT_SHADOW_DP = 12

/**
 * What the tray icon opens on a right-click: the studio in miniature rather than Windows' plain white menu.
 * Power and Bypass as switches, what's playing, the presets to pick from, and the way into the window or out
 * of Eigentone. It takes the app's theme and accent, and closes when anything else is clicked.
 */
@Composable
fun TrayFlyout(onOpen: () -> Unit, onQuit: () -> Unit, onClose: () -> Unit) {
    val state by Repo.state.collectAsState()
    val names by OutputDetector.names.collectAsState()
    val output by Repo.output.collectAsState()
    EigentoneTheme(isDarkTheme(state.themeMode), state.themeColor, state.highContrast) {
        val presets = remember(state.presets, state.favorites, names) {
            (BuiltIns.availableFor(names) + state.presets).sortedByDescending { it.id in state.favorites }
        }
        Box(Modifier.fillMaxSize().padding(TRAY_FLYOUT_SHADOW_DP.dp)) {
            val shape = RoundedCornerShape(8.dp)
            Column(
                Modifier
                    .fillMaxSize()
                    .shadow(10.dp, shape)
                    .clip(shape)
                    .background(Palette.Bar)
                    .border(1.dp, Palette.Line, shape),
            ) {
                // Who and what: the logo and name, with power beside them, and what's playing beneath.
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(Res.drawable.app_icon_small), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Eigentone", style = MaterialTheme.typography.titleSmall, color = Palette.Text, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Box(
                        Modifier
                            .handCursor()
                            .clickable {
                                val on = !state.enabled
                                Repo.update { it.copy(enabled = on) }
                                if (on) AudioService.start()
                            }
                            .padding(4.dp),
                    ) { StudioToggle(state.enabled) }
                }
                Text(
                    if (state.enabled) "${state.current.name} · ${output.label}" else stringResource(Res.string.win_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 44.dp, end = 14.dp, bottom = 10.dp),
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
                FlyoutRow(stringResource(Res.string.win_tray_bypass), enabled = state.enabled, onClick = { Repo.update { it.copy(bypass = !it.bypass) } }) {
                    StudioToggle(state.bypass && state.enabled)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
                Text(
                    stringResource(Res.string.win_tray_presets).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextDim,
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 4.dp),
                )
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    presets.forEach { preset ->
                        val current = preset.id == state.current.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 28.dp)
                                .background(if (current) Palette.SurfaceHigh else Color.Transparent)
                                .handCursor()
                                .clickable { Repo.loadPreset(preset) }
                                .padding(end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(3.dp).height(16.dp).background(if (current) Palette.Lit else Color.Transparent))
                            Text(
                                preset.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (current) Palette.Text else Palette.TextDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 11.dp).weight(1f),
                            )
                            if (preset.id in state.favorites) {
                                Icon(painterResource(Res.drawable.ic_star), contentDescription = null, tint = Palette.Lit, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { onClose(); onOpen() }) { Text(stringResource(Res.string.win_tray_open)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onQuit) { Text(stringResource(Res.string.win_tray_quit)) }
                }
            }
        }
    }
}

@Composable
private fun FlyoutRow(title: String, enabled: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .then(if (enabled) Modifier.handCursor().clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = if (enabled) Palette.Text else Palette.TextDim, modifier = Modifier.weight(1f))
        trailing()
    }
}
