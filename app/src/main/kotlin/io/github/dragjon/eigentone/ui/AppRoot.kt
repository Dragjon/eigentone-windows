package io.github.dragjon.eigentone.ui

import org.jetbrains.compose.resources.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dragjon.eigentone.data.AppState
import io.github.dragjon.eigentone.data.Repo
import io.github.dragjon.eigentone.engine.AudioService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the Android app's MainActivity sets up, as the window's content: the theme the user picked, larger
 * text on top of Windows' own scaling, and the app. Android's system bars, notification permission and
 * shortcut intents have no counterpart here.
 */
@Composable
fun AppRoot() {
    val modes = remember { Repo.state.map { it.themeMode }.distinctUntilChanged() }
    val mode by modes.collectAsStateWithLifecycle(Repo.state.value.themeMode)
    val dark = isDarkTheme(mode)
    val seeds = remember { Repo.state.map { it.themeColor }.distinctUntilChanged() }
    val seed by seeds.collectAsStateWithLifecycle(Repo.state.value.themeColor)
    val access by remember {
        Repo.state.map { Access.of(it) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(Access.of(Repo.state.value))
    Haptics.enabled = access.haptics
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * access.textScale.toFloat()),
        // A mouse is precise, so controls aren't padded out to a finger's 48 dp.
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp,
    ) {
        EigentoneTheme(dark, ThemePreview.seed ?: seed, access.highContrast) { MenuHost { EigentoneApp(onSetEnabled = ::setEnabled) } }
    }
}

private fun setEnabled(on: Boolean) {
    Repo.update { it.copy(enabled = on) }
    if (on) AudioService.start()
}

/** The accessibility settings that change the whole app, read together so they change it once. */
private data class Access(val textScale: Double, val highContrast: Boolean, val haptics: Boolean) {
    companion object {
        fun of(s: AppState) = Access(s.textScale, s.highContrast, s.haptics)
    }
}
