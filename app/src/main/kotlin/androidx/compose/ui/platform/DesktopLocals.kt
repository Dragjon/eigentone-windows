package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.dragjon.eigentone.platform.Context

/**
 * Android's LocalContext and LocalView, for the desktop, under the same names so the shared screens keep
 * them. The context is where the app keeps its files; a view is only ever handed to Haptics, which does
 * nothing on a PC.
 */
val LocalContext = staticCompositionLocalOf { Context.default }

val LocalView = staticCompositionLocalOf<Any?> { null }
