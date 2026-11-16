package io.github.dragjon.eigentone.ui

import org.jetbrains.compose.resources.*

import androidx.compose.material.icons.filled.Settings

import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.layout.positionInRoot

import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.ui.draw.drawBehind

import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import io.github.dragjon.eigentone.data.NightSchedule
import androidx.compose.ui.graphics.graphicsLayer
import io.github.dragjon.eigentone.data.ClipRisk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import io.github.dragjon.eigentone.data.PresetOrganiser
import io.github.dragjon.eigentone.engine.PresetShortcuts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.drawText
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dragjon.eigentone.resources.*
import io.github.dragjon.eigentone.data.AppState
import io.github.dragjon.eigentone.data.DeviceSettings
import io.github.dragjon.eigentone.data.Disclosure
import io.github.dragjon.eigentone.data.AutoEqCatalog
import io.github.dragjon.eigentone.data.BackupCodec
import io.github.dragjon.eigentone.data.BuiltIns
import io.github.dragjon.eigentone.data.CatalogEntry
import io.github.dragjon.eigentone.data.EngineMode
import io.github.dragjon.eigentone.data.EngineStatus
import io.github.dragjon.eigentone.data.ListeningMode
import io.github.dragjon.eigentone.data.Preset
import io.github.dragjon.eigentone.data.Processing
import io.github.dragjon.eigentone.data.Profiles
import io.github.dragjon.eigentone.data.Repo
import io.github.dragjon.eigentone.data.ReverbRoom
import io.github.dragjon.eigentone.data.EqualLoudness
import io.github.dragjon.eigentone.dsp.GraphicEq
import io.github.dragjon.eigentone.data.LimiterSettings
import io.github.dragjon.eigentone.data.ShapeText
import io.github.dragjon.eigentone.data.ThemeMode
import io.github.dragjon.eigentone.data.Tone
import io.github.dragjon.eigentone.dsp.AutoEqParser
import io.github.dragjon.eigentone.dsp.Eq
import io.github.dragjon.eigentone.dsp.Filter
import io.github.dragjon.eigentone.dsp.FilterType
import io.github.dragjon.eigentone.engine.SpectrumAnalyser
import io.github.dragjon.eigentone.engine.readAtMost
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.util.Locale
import io.github.dragjon.eigentone.data.PresetChanges
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxHeight
import io.github.dragjon.eigentone.data.OutputKind
import io.github.dragjon.eigentone.data.OutputLevel
import androidx.compose.animation.togetherWith
import kotlin.math.log10
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.pow

/** Each tab's icon comes outlined, and filled for the tab in view, as Material's own tab bars do (user decision). */
private enum class Tab(val label: StringResource, val icon: DrawableResource, val filledIcon: DrawableResource) {
    EQ(Res.string.ui_tab_eq, Res.drawable.ic_tab_eq, Res.drawable.ic_tab_eq_filled),
    SOUND(Res.string.ui_tab_sound, Res.drawable.ic_tab_sound, Res.drawable.ic_tab_sound_filled),
    PRESETS(Res.string.ui_tab_presets, Res.drawable.ic_tab_presets, Res.drawable.ic_tab_presets_filled),
    HEADPHONES(Res.string.ui_tab_headphones, Res.drawable.ic_tab_headphones, Res.drawable.ic_tab_headphones_filled),
    TOOLS(Res.string.win_tab_tools, Res.drawable.ic_tab_tune, Res.drawable.ic_tab_tune),
    SYSTEM(Res.string.ui_tab_system, Res.drawable.ic_tab_system, Res.drawable.ic_tab_system),
}

@Composable
private fun listeningModeLabel(mode: ListeningMode) = stringResource(
    when (mode) {
        ListeningMode.NOISE_CANCELLING -> Res.string.ui_mode_cancelling
        ListeningMode.TRANSPARENCY -> Res.string.ui_mode_transparency
        ListeningMode.NORMAL -> Res.string.ui_mode_off
    },
)

@Composable
private fun engineModeLabel(mode: EngineMode) = stringResource(
    when (mode) {
        EngineMode.AUTO -> Res.string.ui_route_auto
        EngineMode.GLOBAL -> Res.string.ui_route_global
        EngineMode.SESSIONS -> Res.string.ui_route_per_app
    },
)

private const val MAX_FILTERS = 20
private const val MAX_IMPORT_BYTES = 256 * 1024
private const val MAX_BACKUP_BYTES = 1024 * 1024

/** Brief feedback for actions, shown by the screen's snackbar. */
private val LocalSnackbar = compositionLocalOf<SnackbarHostState> { error("No snackbar host") }

/** Messages run on the app's scope, so one shown by a bar that then goes away, such as Undo after Discard, stays. */
private val LocalSnackScope = compositionLocalOf<CoroutineScope> { error("No snackbar scope") }

/** Opens the guided listening test from anywhere in the main screens. */
private val LocalOpenFinder = compositionLocalOf<() -> Unit> { error("No finder") }

private val LocalOpenHearing = compositionLocalOf<() -> Unit> { error("No hearing check") }

private val LocalOpenRefine = compositionLocalOf<() -> Unit> { error("No refine") }

private const val SEARCH_FROM_PRESETS = 6

/** Just after the theme toggle's thumb has finished sliding, so recolouring the app never stalls it midway. */
private const val THEME_SWITCH_AFTER_MS = 260L
private const val TAB_SLIDE_MS = 240
/** How long a tab's icon takes to turn from outlined to filled. */
private const val TAB_ICON_FADE_MS = 100
/** The lit pill settles with barely any overshoot as it grows (calmed, user decision), and shrinks back quickly when left. */
private const val PILL_DAMPING = 0.85f
private const val PILL_SHRINK_MS = 120
/** How far a tab's icon dips as it fills in, and how much it bounces back. */
private const val ICON_DIP = 0.92f
private const val ICON_DAMPING = 0.7f
/** In a fade-through, how much of the time the old page takes to fade out before the new one fades in. */
private const val FADE_THROUGH_SPLIT = 0.35f
/** The size the new page grows from as it fades in. */
private const val FADE_THROUGH_SCALE = 0.96f

/** How long after the app appears the tabs not yet visited are built, so the opening isn't slowed by them. */
private const val TAB_PREBUILD_DELAY_MS = 400L
/** Short enough that the app is in reach soon after the welcome closes (user decision). */
internal const val OPEN_MS = 1000
/** When, as a share of the opening, the app starts to fade in behind the line. */
private const val OPEN_UNFOLD_AT = 0.52f
private const val OPEN_BARS = 41

/** How far through the window from [start] to [end] the progress is, from 0 to 1. */
private fun phase(progress: Float, start: Float, end: Float) = ((progress - start) / (end - start)).coerceIn(0f, 1f)

/**
 * The power-on drawn over the app as it opens: the lit line, two pulses ringing out from its middle, and a
 * spectrum that bursts out of the line from the centre outward, moves like live sound, then settles back
 * into the line, edges first, as the app fades in.
 */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPowerOn(p: Float) {
    val centre = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
    val reach = kotlin.math.hypot(size.width, size.height) / 2f
    // Pulses.
    for (k in 0..1) {
        val r = phase(p, 0.02f + k * 0.13f, 0.6f + k * 0.13f)
        if (r > 0f && r < 1f) {
            drawCircle(
                Palette.Lit.copy(alpha = (1f - r) * 0.55f),
                radius = r * reach * 1.1f,
                center = centre,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = (1f + 3f * (1f - r)) * density),
            )
        }
    }
    // The line, brightest at the start and gone once the app is open.
    val lineAlpha = 1f - phase(p, OPEN_UNFOLD_AT, OPEN_UNFOLD_AT + 0.25f)
    drawLine(
        Palette.Lit.copy(alpha = lineAlpha),
        androidx.compose.ui.geometry.Offset(0f, centre.y),
        androidx.compose.ui.geometry.Offset(size.width, centre.y),
        strokeWidth = 3f * density,
    )
    // The spectrum.
    val rise = phase(p, 0.06f, 0.42f)
    val settle = phase(p, OPEN_UNFOLD_AT - 0.06f, 0.9f)
    val slot = size.width / OPEN_BARS
    val barWidth = slot * 0.55f
    val tallest = size.height * 0.26f
    for (i in 0 until OPEN_BARS) {
        // 0 in the middle, 1 at the edges.
        val distance = kotlin.math.abs(i - (OPEN_BARS - 1) / 2f) / ((OPEN_BARS - 1) / 2f)
        val up = ((rise * 1.5f - distance * 0.5f)).coerceIn(0f, 1f)
        val down = ((settle * 1.5f - (1f - distance) * 0.5f)).coerceIn(0f, 1f)
        val upEased = 1f - (1f - up) * (1f - up)
        // Moves like music: two waves at different speeds, louder in the middle, as bass and mids are.
        val motion = 0.55f + 0.45f * kotlin.math.sin(i * 0.9f + p * 22f) * kotlin.math.sin(i * 0.31f - p * 9f)
        val height = tallest * upEased * (1f - down) * motion * (1f - 0.55f * distance)
        if (height < 0.5f) continue
        val x = slot * i + (slot - barWidth) / 2f
        drawRoundRect(
            Palette.Lit.copy(alpha = 0.9f * (1f - down * 0.5f)),
            topLeft = androidx.compose.ui.geometry.Offset(x, centre.y - height),
            size = androidx.compose.ui.geometry.Size(barWidth, height * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
        )
    }
}
private const val UNSAVED_BAR_ROOM_DP = 80
private const val NAV_EDGE_DP = 8

private val LocalOpenPresets = compositionLocalOf<() -> Unit> { error("No presets tab") }

private val LocalOpenPeaks = compositionLocalOf<() -> Unit> { error("No peak finder") }
private val LocalOpenCurves = compositionLocalOf<() -> Unit> { error("No curve comparison") }

private val LocalOpenConsole = compositionLocalOf<() -> Unit> { error("No debug console") }

private const val SOURCE_URL = "https://github.com/Dragjon/eigentone-windows"

private val LocalOpenSweep = compositionLocalOf<() -> Unit> { error("No sweep") }

private val LocalOpenCompare = compositionLocalOf<() -> Unit> { error("No comparison") }

/** Opens the faders across the whole screen. */
private val LocalOpenFaders = compositionLocalOf<() -> Unit> { error("No expanded faders") }

/** Opens the shape library from the graphic EQ. */
private val LocalOpenShapes =compositionLocalOf<() -> Unit> { error("No shape library") }

@Composable
fun EigentoneApp(onSetEnabled: (Boolean) -> Unit) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val status by Repo.status.collectAsStateWithLifecycle()
    // EIGENTONE_MODULE opens another module first, for looking at a layout without clicking to it.
    var tab by rememberSaveable { mutableStateOf(System.getenv("EIGENTONE_MODULE")?.let { m -> Tab.entries.firstOrNull { it.name == m } } ?: Tab.EQ) }
    // Settings opens from the top bar, so Back returns to whichever tab it was opened from.
    var beforeSettings by rememberSaveable { mutableStateOf(Tab.EQ) }
    // Each tab has its own position. Moving to one starts it at the top; Back from Settings returns to where you were.
    val scrolls: Map<Tab, ScrollState> = Tab.entries.associateWith { rememberScrollState() }
    val tabScope = rememberCoroutineScope()
    val toggleSettings = {
        if (tab == Tab.SYSTEM) {
            tab = beforeSettings
        } else {
            beforeSettings = tab
            tabScope.launch { scrolls.getValue(Tab.SYSTEM).scrollTo(0) }
            tab = Tab.SYSTEM
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val appContext = LocalContext.current
    LaunchedEffect(state.favorites, state.presets) {
        val available = BuiltIns.all + state.presets
        withContext(Dispatchers.Default) {
            PresetShortcuts.update(appContext, PresetOrganiser.shortcuts(state, available))
        }
    }
    // The tools open in the Tools module's pane, not over the whole window. Opened from another module, closing
    // one goes back there.
    var tool by rememberSaveable { mutableStateOf<StudioTool?>(null) }
    var toolReturn by rememberSaveable { mutableStateOf<Tab?>(null) }
    fun openTool(t: StudioTool) {
        if (tab != Tab.TOOLS) toolReturn = tab
        tool = t
        tab = Tab.TOOLS
    }
    fun closeTool() {
        tool = null
        toolReturn?.let { tab = it }
        toolReturn = null
    }
    var expandingFaders by rememberSaveable { mutableStateOf(false) }

    // Which full screen is open over the app, if any. Each slides in from the right and back out the way it came.
    val screen = when {
        expandingFaders -> Overlay.FADERS
        // Find your sound opens over the welcome and returns to it.
        !state.welcomeDone && tool == StudioTool.FINDER -> Overlay.FINDER
        !state.welcomeDone -> Overlay.WELCOME
        else -> null
    }
    AnimatedContent(
        targetState = screen,
        // The app's own page behind every change of screen, so a cross-fade never shows the window beneath, which
        // follows the phone's light or dark setting rather than Eigentone's and flashed through (user decision).
        modifier = Modifier.background(Palette.Background),
        transitionSpec = {
            // The landscape faders turn the phone, which a slide would only make look broken. The welcome closes
            // into the line the app then opens out of, so that change needs no transition of its own either.
            if (initialState == Overlay.FADERS || targetState == Overlay.FADERS ||
                (initialState == Overlay.WELCOME && targetState == null)
            ) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (targetState != null && initialState == null || targetState == Overlay.WELCOME) {
                (slideInHorizontally(screenSlide()) { it } + fadeIn(screenFade())) togetherWith
                    (slideOutHorizontally(screenSlide()) { -it / 4 } + fadeOut(screenFade()))
            } else {
                (slideInHorizontally(screenSlide()) { -it / 4 } + fadeIn(screenFade())) togetherWith
                    (slideOutHorizontally(screenSlide()) { it } + fadeOut(screenFade()))
            }
        },
        label = "screen",
    ) { shown ->
    when (shown) {
        Overlay.FADERS -> ExpandedFaders(onClose = { expandingFaders = false })
        Overlay.FINDER -> FinderScreen(onSetEnabled = onSetEnabled, onClose = { tool = null })
        Overlay.WELCOME -> WelcomeScreen(onSetEnabled = onSetEnabled, onFindSound = { tool = StudioTool.FINDER })
        else -> AppScreen(state, status, tab, { t -> if (t != Tab.TOOLS) toolReturn = null; tab = t }, beforeSettings, { beforeSettings = it }, scrolls, tabScope, toggleSettings, snackbar, onSetEnabled,
            open = OverlayOpeners(
                finder = { openTool(StudioTool.FINDER) },
                refine = { openTool(StudioTool.REFINE) },
                shapes = { openTool(StudioTool.SHAPES) },
                hearing = { openTool(StudioTool.HEARING) },
                compare = { openTool(StudioTool.COMPARE) },
                sweep = { openTool(StudioTool.SWEEP) },
                peaks = { openTool(StudioTool.PEAKS) },
                console = { openTool(StudioTool.CONSOLE) },
                faders = { expandingFaders = true },
                curves = { openTool(StudioTool.CURVES) },
            ),
            tool = tool,
            setTool = { t -> toolReturn = null; tool = t },
            closeTool = ::closeTool,
        )
    }
    }
}

private enum class Overlay { FADERS, FINDER, WELCOME }

/** Opens each full screen; passed down, since the screens are chosen above the app's own content. */
private class OverlayOpeners(
    val finder: () -> Unit,
    val refine: () -> Unit,
    val shapes: () -> Unit,
    val hearing: () -> Unit,
    val compare: () -> Unit,
    val sweep: () -> Unit,
    val peaks: () -> Unit,
    val console: () -> Unit,
    val faders: () -> Unit,
    val curves: () -> Unit,
)

// Decelerating from full speed, so a page moves on the very first frame after the tap rather than easing in.
private fun <T> screenSlide() = tween<T>(SCREEN_MS, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
private fun <T> screenFade() = tween<T>(SCREEN_MS / 2)
private const val SCREEN_MS = 300

/**
 * The app as an audio studio, laid out like a professional photo editor: the modules across the top, and in
 * Tune the curve as the canvas in the middle, presets, history and the output on the left, every processing
 * panel stacked on the right, and the presets as a filmstrip of their curves along the bottom. The panels are
 * the Android app's own sections, so every feature is here.
 */
@Composable
private fun AppScreen(
    state: AppState,
    status: EngineStatus,
    tab: Tab,
    setTab: (Tab) -> Unit,
    @Suppress("UNUSED_PARAMETER") beforeSettings: Tab,
    @Suppress("UNUSED_PARAMETER") setBeforeSettings: (Tab) -> Unit,
    scrolls: Map<Tab, ScrollState>,
    tabScope: CoroutineScope,
    @Suppress("UNUSED_PARAMETER") toggleSettings: () -> Unit,
    snackbar: SnackbarHostState,
    onSetEnabled: (Boolean) -> Unit,
    open: OverlayOpeners,
    tool: StudioTool?,
    setTool: (StudioTool?) -> Unit,
    closeTool: () -> Unit,
) {
    AnalyserEffect(state.analyser)
    // Effects live in Tune's right-hand panels, so anything asking for that page goes to Tune.
    val module = if (tab == Tab.SOUND) Tab.EQ else tab
    val goTo: (Tab) -> Unit = { t -> if (t != module) setTab(if (t == Tab.SOUND) Tab.EQ else t) }
    History.Recorder(state)
    CompositionLocalProvider(
        LocalSnackbar provides snackbar,
        LocalSnackScope provides tabScope,
        LocalOpenFinder provides open.finder,
        LocalOpenRefine provides open.refine,
        LocalOpenShapes provides open.shapes,
        LocalOpenHearing provides open.hearing,
        LocalOpenCompare provides open.compare,
        LocalOpenSweep provides open.sweep,
        LocalOpenPeaks provides open.peaks,
        LocalOpenConsole provides open.console,
        LocalOpenFaders provides open.faders,
        LocalOpenCurves provides open.curves,
        LocalOpenPresets provides { goTo(Tab.PRESETS) },
        LocalOpenEffect provides { name ->
            val (target, section) = when (name) {
                "Night", "Level" -> Tab.EQ to Res.string.ui_dynamics
                "Surround" -> Tab.EQ to Res.string.ui_stereo
                "Ears" -> Tab.HEADPHONES to Res.string.ui_your_ears
                "Comp" -> Tab.EQ to Res.string.ui_compressor
                "Tame" -> Tab.EQ to Res.string.ui_dynamic_eq
                else -> Tab.EQ to Res.string.ui_space
            }
            goTo(target)
            SectionFocus.request = appText(section)
        },
        LocalPageShown provides { true },
        LocalTopCover provides 0.dp,
        LocalBottomCover provides 0.dp,
    ) {
        Column(Modifier.fillMaxSize().background(Palette.Bar)) {
            StudioTopBar(state, status, module, goTo, onSetEnabled)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (module) {
                    Tab.EQ -> TuneWorkspace(state, scrolls)
                    Tab.TOOLS -> ToolsWorkspace(tool, setTool, closeTool, onSetEnabled)
                    Tab.PRESETS -> PresetsWorkspace(state, scrolls) { goTo(Tab.EQ) }
                    Tab.HEADPHONES -> DevicesWorkspace(state, scrolls)
                    else ->
                    ModulePage(state, status, module, scrolls.getValue(module))
                }
                SaveBarsAndNotices(state, snackbar)
            }
            // The filmstrip carries the output and its meter, so there's no separate, mostly empty status bar.
            Filmstrip(state, compact = module != Tab.EQ && module != Tab.PRESETS)
        }
        TourOverlay()
    }
}

/** Across the top: who we are, the sound in use and whether it's saved, power and Bypass, then the modules. */
@Composable
private fun StudioTopBar(state: AppState, status: EngineStatus, module: Tab, goTo: (Tab) -> Unit, onSetEnabled: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(TOP_BAR_DP.dp).background(Palette.Bar).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.ic_eigentone), contentDescription = null, tint = Palette.Lit, modifier = Modifier.size(18.dp))
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
            color = Palette.Text,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(io.github.dragjon.eigentone.data.CrashLog.VERSION, style = Readout, color = Palette.TextDim, modifier = Modifier.padding(start = 6.dp, end = 28.dp))
        val savedVersion = Repo.allPresets(state).firstOrNull { it.id == state.current.id }
        val deviceSaved by Repo.deviceSaved.collectAsStateWithLifecycle()
        Row(Modifier.padding(start = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // In one place in every module, where there's room for it, rather than crowding the canvas toolbar.
            if (savedVersion != null && savedVersion != state.current) UnsavedBar(state, savedVersion, inline = true)
            deviceSaved?.let { DeviceUnsavedBar(state, it, inline = true) }
        }
        Spacer(Modifier.weight(1f))
        if (state.enabled) BypassButton(state.bypass)
        Row(
            Modifier
                .padding(start = 12.dp)
                .clip(RoundedCornerShape(3.dp))
                .toggleableRow(state.enabled, onSetEnabled)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudioToggle(state.enabled)
            Text(
                stringResource(if (state.enabled) Res.string.win_on else Res.string.win_off),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        // The modules, as in a photo editor: words, the open one bright, divided by hairlines.
        Row(Modifier.padding(start = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            val output by Repo.output.collectAsStateWithLifecycle()
            STUDIO_MODULES.forEachIndexed { i, t ->
                if (i > 0) Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(14.dp).background(Palette.Track))
                val name = when (t) {
                    Tab.EQ -> stringResource(Res.string.win_module_tune)
                    Tab.HEADPHONES -> stringResource(Res.string.win_module_devices)
                    else -> stringResource(t.label)
                }
                val selected = module == t
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                    ),
                    color = if (selected) Palette.Text else Palette.TextDim,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .selectable(selected = selected, role = Role.Tab) { goTo(t) }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .semantics { if (t == Tab.HEADPHONES) contentDescription = name + ", " + output.label },
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
}

private val STUDIO_MODULES = listOf(Tab.EQ, Tab.PRESETS, Tab.HEADPHONES, Tab.TOOLS, Tab.SYSTEM)

private fun Modifier.toggleableRow(value: Boolean, onChange: (Boolean) -> Unit): Modifier =
    this.then(Modifier.toggleable(value = value, role = Role.Switch, onValueChange = onChange))

/** A column of panels down one side, as a studio's left and right panel tracks. */
@Composable
private fun PanelTrack(width: androidx.compose.ui.unit.Dp, scroll: ScrollState, background: Color = Palette.Bar, content: @Composable () -> Unit) {
    Box(Modifier.width(width).fillMaxHeight().background(background)) {
        CompositionLocalProvider(LocalPageWidth provides width) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(PANEL_GAP_DP.dp),
            ) { content() }
        }
        androidx.compose.foundation.VerticalScrollbar(
            adapter = androidx.compose.foundation.rememberScrollbarAdapter(scroll),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

/** Tune: the curve in the middle, with panels either side. */
@Composable
private fun TuneWorkspace(state: AppState, scrolls: Map<Tab, ScrollState>) {
    val leftScroll = rememberScrollState()
    Row(Modifier.fillMaxSize()) {
        // The presets take the height the history leaves, and scroll within it, so the track has no empty end.
        // The presets take only the height they need (and scroll past it), and History the rest, so folding a
        // preset group hands its room to History rather than leaving an empty panel behind.
        CompositionLocalProvider(LocalPageWidth provides LEFT_PANEL_DP.dp) {
            androidx.compose.ui.layout.SubcomposeLayout(Modifier.width(LEFT_PANEL_DP.dp).fillMaxHeight().background(Palette.Bar)) { c ->
                val gap = PANEL_GAP_DP.dp.roundToPx()
                val historyMin = History.HISTORY_MIN_DP.dp.roundToPx()
                val loose = c.copy(minHeight = 0)
                val presets = subcompose("presets") {
                    Box(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().verticalScroll(leftScroll), verticalArrangement = Arrangement.spacedBy(PANEL_GAP_DP.dp)) {
                            PresetListSection(state)
                        }
                        if (leftScroll.maxValue > 0) {
                            androidx.compose.foundation.VerticalScrollbar(
                                adapter = androidx.compose.foundation.rememberScrollbarAdapter(leftScroll),
                                modifier = Modifier.align(Alignment.CenterEnd).matchParentSize(),
                            )
                        }
                    }
                }.map { it.measure(loose.copy(maxHeight = (c.maxHeight - historyMin - gap).coerceAtLeast(0))) }
                val top = presets.maxOfOrNull { it.height } ?: 0
                val rest = (c.maxHeight - top - gap).coerceAtLeast(0)
                val history = subcompose("history") {
                    Box(Modifier.fillMaxSize().background(Palette.Surface)) {
                        History.Panel(listMax = (rest.toDp() - History.HISTORY_CHROME_DP.dp).coerceAtLeast(40.dp))
                    }
                }.map { it.measure(c.copy(minHeight = rest, maxHeight = rest)) }
                layout(c.maxWidth, c.maxHeight) {
                    presets.forEach { it.place(0, 0) }
                    history.forEach { it.place(0, top + gap) }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(Palette.Background)) {
            // The canvas's height, less its padding: the graph fills what the toolbar and faders leave of it.
            val graph = maxHeight - 24.dp
            val scroll = scrolls.getValue(Tab.EQ)
            var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
            val pageScroll = remember { PageScroll(scroll) { androidx.compose.ui.geometry.Rect(origin, size) } }
            CompositionLocalProvider(
                LocalPageWidth provides maxWidth,
                LocalCanvasGraph provides graph,
                LocalPageScroll provides pageScroll,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            origin = it.positionInRoot()
                            size = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                FaderFocus.touched(down.position + origin)
                            }
                        },
                ) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!state.enabled) OffBar(onTurnOn = { Repo.update { it.copy(enabled = true) }; io.github.dragjon.eigentone.engine.AudioService.start() })
                        EqTab(state, canvasOnly = true)
                    }
                    androidx.compose.foundation.VerticalScrollbar(
                        adapter = androidx.compose.foundation.rememberScrollbarAdapter(scroll),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
        PanelTrack(RIGHT_PANEL_DP.dp, scrolls.getValue(Tab.SOUND)) {
            ShapesPanel(LocalOpenShapes.current)
            SoundTab(state)
            EveryPresetSections(state)
        }
    }
}

/** How tall the canvas makes the graph, set by Tune from the room it has. */
private val LocalCanvasGraph = compositionLocalOf { 0.dp }

/** Any other module: its panels in columns across the window, on the studio's grey. */
@Composable
private fun ModulePage(state: AppState, status: EngineStatus, module: Tab, scroll: ScrollState) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Background)) {
        val contentWidth = (maxWidth - MODULE_SIDE_DP.dp * 2).coerceAtLeast(0.dp)
        val columns = columnsFor(contentWidth)
        var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
        val pageScroll = remember(module) { PageScroll(scroll) { androidx.compose.ui.geometry.Rect(origin, size) } }
        CompositionLocalProvider(LocalPageWidth provides contentWidth, LocalPageScroll provides pageScroll) {
            Box(
                Modifier.fillMaxSize().onGloballyPositioned {
                    origin = it.positionInRoot()
                    size = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())
                },
            ) {
                Box(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = MODULE_SIDE_DP.dp, vertical = 16.dp)) {
                    SectionColumns(columns, gap = PANEL_GAP_DP.dp * 6) {
                        when (module) {
                            Tab.PRESETS -> PresetsTab(state)
                            Tab.HEADPHONES -> HeadphonesTab(state)
                            Tab.TOOLS -> ToolsTab(state)
                            Tab.SYSTEM -> SystemTab(state, status)
                            else -> SoundTab(state)
                        }
                    }
                }
                androidx.compose.foundation.VerticalScrollbar(
                    adapter = androidx.compose.foundation.rememberScrollbarAdapter(scroll),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

/** The output in use, at a glance: what it is, the engine's format and delay, and where its settings are. */
@Composable
private fun OutputPanel() {
    val output by Repo.output.collectAsStateWithLifecycle()
    val engine by io.github.dragjon.eigentone.engine.AudioService.engineInfo.collectAsStateWithLifecycle()
    SettingsSection(stringResource(Res.string.win_panel_output)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { DeviceTabIcon(output.kind, tint = Palette.Text, ring = Palette.Lit) }
            Column(Modifier.padding(start = 10.dp)) {
                Text(output.label, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    engine?.let { e -> stringResource(Res.string.win_status_engine, formatKhz(e.sampleRate), String.format(Locale.US, "%.1f", e.latencyMs)) }
                        ?: stringResource(if (io.github.dragjon.eigentone.engine.AudioService.client.connected.collectAsStateWithLifecycle().value) Res.string.win_status_idle else Res.string.win_status_no_engine),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                )
            }
        }
    }
}

/**
 * Along the bottom, as a photo editor's filmstrip: every preset as a small picture of its own curve, the
 * favourites first. Clicking one plays it.
 */
@Composable
private fun Filmstrip(state: AppState, compact: Boolean) {
    val output by Repo.output.collectAsStateWithLifecycle()
    val connectedNames = rememberConnectedOutputNames()
    var filter by remember { mutableStateOf(0) }
    val all = remember(state.presets, state.favorites, connectedNames) {
        (BuiltIns.availableFor(connectedNames) + state.presets).sortedByDescending { it.id in state.favorites }
    }
    val presets = when (filter) {
        1 -> all.filter { it.id in state.favorites }
        2 -> all.filter { p -> state.presets.any { it.id == p.id } }
        else -> all
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
    Column(Modifier.fillMaxWidth().background(Palette.Bar)) {
        // The header: which presets, then the output and its level, as a studio's filmstrip bar carries its status.
        Row(
            Modifier.fillMaxWidth().height(FILM_HEADER_DP.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact) {
                SlidingToggle(
                    options = listOf(
                        ToggleOption(stringResource(Res.string.win_film_all)),
                        ToggleOption(stringResource(Res.string.win_film_favourites)),
                        ToggleOption(stringResource(Res.string.win_film_mine)),
                    ),
                    selected = filter,
                    onSelect = { filter = it },
                    modifier = Modifier.width(240.dp),
                    height = 22.dp,
                )
                Text(
                    stringResource(Res.string.win_film_count, presets.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            OutputStatus()
        }
        if (!compact) {
            val curves = remember(presets, output.key) {
                presets.associate { p ->
                    val filters = Processing.effectiveFilters(state.copy(current = p, bypass = false, loudness = false), 0.0, output.key)
                    p.id to FloatArray(FILM_POINTS) { i -> Eq.curveDb(filters, Eq.logFreq(i / (FILM_POINTS - 1.0))).toFloat() }
                }
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(FILMSTRIP_DP.dp)) {
                // Cells widen to fill the strip when there are few, so it never ends in an empty stretch.
                val gap = 8.dp
                val room = maxWidth - 24.dp
                val n = presets.size.coerceAtLeast(1)
                val cell = ((room - gap * (n - 1)) / n).coerceIn(FILM_CELL_DP.dp, FILM_CELL_MAX_DP.dp)
                androidx.compose.foundation.lazy.LazyRow(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items(presets, key = { it.id }) { preset ->
                        FilmCell(preset, cell, selected = preset.id == state.current.id, favourite = preset.id in state.favorites, curve = curves[preset.id])
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmCell(preset: Preset, width: androidx.compose.ui.unit.Dp, selected: Boolean, favourite: Boolean, curve: FloatArray?) {
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(2.dp))
            .clickable { Repo.loadPreset(preset) }
            .background(if (selected) Palette.SurfaceHigh else Palette.Surface)
            .border(1.dp, if (selected) Palette.Lit else Color.Transparent, RoundedCornerShape(2.dp))
            .padding(6.dp),
    ) {
        val lineColour = if (selected) Palette.Lit else Palette.Text
        val grid = Palette.Track
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().weight(1f)) {
            drawLine(grid, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), 1f)
            if (curve != null) {
                val path = androidx.compose.ui.graphics.Path()
                curve.forEachIndexed { i, db ->
                    val x = size.width * i / (curve.size - 1)
                    val y = size.height / 2 - (db / FILM_RANGE_DB).coerceIn(-1f, 1f) * size.height / 2
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColour, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            if (favourite) {
                Icon(painterResource(Res.drawable.ic_star), contentDescription = null, tint = Palette.Lit, modifier = Modifier.size(11.dp).padding(end = 3.dp))
            }
            Text(preset.name, style = MaterialTheme.typography.bodySmall, color = if (selected) Palette.Text else Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The output, the engine's format and delay, and a level meter, at the right of the filmstrip bar. */
@Composable
private fun OutputStatus() {
    val output by Repo.output.collectAsStateWithLifecycle()
    val engine by io.github.dragjon.eigentone.engine.AudioService.engineInfo.collectAsStateWithLifecycle()
    val connected by io.github.dragjon.eigentone.engine.AudioService.client.connected.collectAsStateWithLifecycle()
    val level by Repo.level.collectAsStateWithLifecycle()
    val playing by io.github.dragjon.eigentone.engine.AudioService.musicActive.collectAsStateWithLifecycle()
    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { DeviceTabIcon(output.kind, tint = Palette.TextDim, ring = Palette.Lit) }
    Text(output.label, style = MaterialTheme.typography.bodySmall, color = Palette.Text, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
    Text(
        engine?.let { e -> stringResource(Res.string.win_status_engine, formatKhz(e.sampleRate), String.format(Locale.US, "%.1f", e.latencyMs)) }
            ?: stringResource(if (connected) Res.string.win_status_idle else Res.string.win_status_no_engine),
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextDim,
        maxLines = 1,
        modifier = Modifier.padding(start = 12.dp, end = 14.dp),
    )
    val peak = level?.heldPeakDb?.takeIf { playing }
    Box(
        Modifier
            .width(LEVEL_METER_DP.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Palette.Track)
            .drawBehind {
                val p = peak ?: return@drawBehind
                val fill = ((p + 60f) / 60f).coerceIn(0f, 1f)
                drawRect(if (p > -0.1f) Palette.Danger else Palette.Lit, size = androidx.compose.ui.geometry.Size(size.width * fill, size.height))
            },
    )
    Text(
        peak?.let { String.format(Locale.US, "%.0f dB", it) } ?: "-- dB",
        style = Readout,
        color = if (peak != null && peak > -0.1f) Palette.Danger else Palette.TextDim,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
        modifier = Modifier.width(44.dp),
    )
}

private const val TOP_BAR_DP = 44
private const val LEFT_PANEL_DP = 272
private const val RIGHT_PANEL_DP = 340
private const val PANEL_GAP_DP = 2
private const val MODULE_SIDE_DP = 24
private const val FILMSTRIP_DP = 84
private const val FILM_CELL_DP = 132
private const val FILM_CELL_MAX_DP = 260
private const val FILM_HEADER_DP = 30
private const val FILM_POINTS = 48
private const val FILM_RANGE_DB = 12f

/** Shown while Eigentone is off, so a fader that changes nothing never looks broken. */
@Composable
private fun OffBar(onTurnOn: () -> Unit) {
    Row(
        Modifier
            .padding(start = PAGE_SIDE_DP.dp, end = PAGE_SIDE_DP.dp, bottom = 12.dp)
            .widthIn(max = PAGE_MAX_DP.dp - PAGE_SIDE_DP.dp * 2)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Line, RoundedCornerShape(4.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = Palette.Lit, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(stringResource(Res.string.win_off_title), style = MaterialTheme.typography.titleSmall, color = Palette.Text)
            Text(stringResource(Res.string.win_off_body), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        }
        Button(onClick = onTurnOn) { Text(stringResource(Res.string.win_turn_on)) }
    }
}

/** Along the bottom: what's playing, the engine's format and delay, and the output level. */
@Composable
private fun StatusBar(@Suppress("UNUSED_PARAMETER") state: AppState) {
    val output by Repo.output.collectAsStateWithLifecycle()
    val engine by io.github.dragjon.eigentone.engine.AudioService.engineInfo.collectAsStateWithLifecycle()
    val level by Repo.level.collectAsStateWithLifecycle()
    val playing by io.github.dragjon.eigentone.engine.AudioService.musicActive.collectAsStateWithLifecycle()
    Row(
        Modifier
            .fillMaxWidth()
            .height(STATUS_BAR_DP.dp)
            .background(Palette.Bar)
            .padding(horizontal = PAGE_SIDE_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            DeviceTabIcon(output.kind, tint = Palette.TextDim, ring = Palette.Lit)
        }
        Text(output.label, style = MaterialTheme.typography.bodySmall, color = Palette.Text, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        Text(
            engine?.let { e -> stringResource(Res.string.win_status_engine, formatKhz(e.sampleRate), String.format(Locale.US, "%.1f", e.latencyMs)) }
                ?: stringResource(if (io.github.dragjon.eigentone.engine.AudioService.client.connected.collectAsStateWithLifecycle().value) Res.string.win_status_idle else Res.string.win_status_no_engine),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextDim,
            maxLines = 1,
            modifier = Modifier.padding(start = 16.dp),
        )
        Spacer(Modifier.weight(1f))
        val peak = level?.heldPeakDb
        Text(
            if (!playing || peak == null) stringResource(Res.string.win_status_nothing_playing) else String.format(Locale.US, "%.0f dB", peak),
            style = Readout.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = if (peak != null && peak > -0.1f) Palette.Danger else Palette.TextDim,
            modifier = Modifier.padding(end = 8.dp),
        )
        // A slim meter, red when peaks reach full scale.
        Box(
            Modifier
                .width(LEVEL_METER_DP.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Palette.Track)
                .drawBehind {
                    val p = peak ?: return@drawBehind
                    if (!playing) return@drawBehind
                    val fill = ((p + 60f) / 60f).coerceIn(0f, 1f)
                    drawRect(if (p > -0.1f) Palette.Danger else Palette.Lit, size = androidx.compose.ui.geometry.Size(size.width * fill, size.height))
                },
        )
    }
}

private fun formatKhz(rate: Int): String =
    if (rate % 1000 == 0) "${rate / 1000}" else String.format(Locale.US, "%.1f", rate / 1000.0)

/** Messages, over the bottom of the page. Unsaved changes live in the command bar, so only these float. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SaveBarsAndNotices(state: AppState, snackbar: SnackbarHostState) {
    NoticeHost(
        snackbar,
        longer = state.longNotices,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = PAGE_SIDE_DP.dp - 12.dp)
            .widthIn(max = NOTICE_MAX_DP.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

/**
 * Every listening tool in one place. On the phone they sit in the tabs they belong to, which stay; on a PC
 * there's room to gather them, as Windows apps gather their tools.
 */
@Composable
private fun ToolsTab(state: AppState) {
    val output by Repo.output.collectAsStateWithLifecycle()
    var measuring by remember { mutableStateOf(false) }
    SettingsSection(stringResource(Res.string.win_tools_fit)) {
        ActionRow(stringResource(Res.string.ui_find_my_sound), Help.finder, detail = stringResource(Res.string.ui_a_short_guided_listening_test), onClick = LocalOpenFinder.current)
        RowDivider()
        ActionRow(stringResource(Res.string.ui_refine), Help.refine, detail = stringResource(Res.string.ui_refine_detail), onClick = LocalOpenRefine.current)
        RowDivider()
        ActionRow(stringResource(Res.string.ui_hearing_check), Help.hearing, detail = stringResource(Res.string.ui_builds_a_curve_for_how_you_hear), onClick = LocalOpenHearing.current)
    }
    SettingsSection(stringResource(Res.string.win_tools_problems)) {
        ActionRow(stringResource(Res.string.ui_find_peaks_for_me), Help.peakFinder, detail = stringResource(Res.string.ui_a_guided_test_that_finds_peaks_and_dips), onClick = LocalOpenPeaks.current)
        RowDivider()
        ActionRow(stringResource(Res.string.ui_frequency_sweep), Help.sweep, detail = stringResource(Res.string.ui_find_and_fix_peaks_in_your_headphones_by), onClick = LocalOpenSweep.current)
        RowDivider()
        ActionRow(stringResource(Res.string.ui_measure_action), Help.measure, detail = stringResource(Res.string.ui_measure_action_detail)) { measuring = true }
    }
    SettingsSection(stringResource(Res.string.win_tools_compare)) {
        ActionRow(stringResource(Res.string.ui_compare_two_sounds), Help.compare, detail = stringResource(Res.string.ui_switch_instantly_with_an_optional_blind), onClick = LocalOpenCompare.current)
        RowDivider()
        ActionRow(stringResource(Res.string.ui_compare_curves), Help.compareCurves, detail = stringResource(Res.string.ui_compare_curves_detail), onClick = LocalOpenCurves.current)
    }
    SettingsSection(stringResource(Res.string.win_tools_shapes)) {
        ActionRow(stringResource(Res.string.help_shape_library_title), Help.shapeLibrary, detail = stringResource(Res.string.win_tools_shapes_detail), onClick = LocalOpenShapes.current)
    }
    SettingsSection(stringResource(Res.string.win_tools_trouble)) {
        ActionRow(stringResource(Res.string.ui_debug_console), Help.console, detail = stringResource(Res.string.ui_debug_console_detail), onClick = LocalOpenConsole.current)
    }
    if (measuring) {
        val snackbar = LocalSnackbar.current
        val scope = LocalSnackScope.current
        RoomMeasureDialog(output, onDismiss = { measuring = false }) { name ->
            measuring = false
            scope.notify(snackbar, appText(Res.string.ui_added, name))
        }
    }
}

private const val WINDOWS_TOUR_READY = false
private const val STUDIO = true

/** Settings that only mean something on Android, such as earbud noise modes and vibration. Kept, hidden. */
private const val ANDROID_ONLY = false
private const val NAV_WIDTH_DP = 240
private const val NAV_COMPACT_DP = 56
private const val NAV_COMPACT_BELOW_DP = 900
private const val COMMAND_BAR_DP = 72
private const val STATUS_BAR_DP = 30
private const val PAGE_SIDE_DP = 32
private const val PAGE_MAX_DP = 1040
private const val PAGE_GAP_DP = 16
private const val NOTICE_MAX_DP = 560
private const val EQ_SIDE_PANEL_FROM_DP = 1100
private const val CANVAS_GRAPH_MIN_DP = 180
private const val EQ_SIDE_PANEL_DP = 360
private const val PAGE_MS = 250
private const val PRESET_SWITCHER_MAX_DP = 320
private const val LEVEL_METER_DP = 120

/** One bottom tab: a lit pill behind the icon when chosen, and the name below. */
@Composable
private fun NavItem(tab: Tab, selected: Boolean, device: OutputKind? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // The whole tab takes the touch, but the press ripples inside the rounded pill, as Android's own tabs do,
    // rather than as a tight box around the narrow tab.
    val presses = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier
            .selectable(selected = selected, interactionSource = presses, indication = null, role = Role.Tab, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val tint = if (selected) Palette.OnLit else Palette.TextDim
        // Chosen, the lit pill grows out from the middle with a little give, and shrinks back when left, as the tab
        // bars in WhatsApp and Telegram do (user decision).
        val grow by androidx.compose.animation.core.animateFloatAsState(
            if (selected) 1f else 0f,
            if (selected) {
                androidx.compose.animation.core.spring(dampingRatio = PILL_DAMPING, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium)
            } else {
                tween(PILL_SHRINK_MS)
            },
            label = "tabPill",
        )
        // The icon dips a little as it fills in and springs back, so the change reads as a press.
        val bump = remember { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(selected) {
            if (selected) {
                bump.snapTo(ICON_DIP)
                bump.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = ICON_DAMPING, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))
            }
        }
        val pillColour = Palette.LitFill
        // The device tab's icon sits over the pill rather than in it, so its change animation isn't clipped.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = bump.value
                scaleY = bump.value
            },
        ) {
            Box(
                Modifier
                    .size(width = 64.dp, height = 32.dp)
                    .drawBehind {
                        val w = size.width * grow.coerceAtLeast(0f)
                        if (w > 0.5f) {
                            drawRoundRect(
                                pillColour,
                                topLeft = androidx.compose.ui.geometry.Offset((size.width - w) / 2f, 0f),
                                size = androidx.compose.ui.geometry.Size(w, size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                                alpha = grow.coerceIn(0f, 1f),
                            )
                        }
                    }
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .indication(presses, androidx.compose.material3.ripple()),
                contentAlignment = Alignment.Center,
            ) {
                // The label below already names the tab for screen readers. Filled while the tab is in view.
                if (device == null) {
                    androidx.compose.animation.Crossfade(selected, animationSpec = tween(TAB_ICON_FADE_MS), label = "tabIcon") { on ->
                        Icon(painterResource(if (on) tab.filledIcon else tab.icon), contentDescription = null, tint = tint)
                    }
                }
            }
            if (device != null) DeviceTabIcon(device, tint = tint, ring = Palette.Lit, filled = selected)
        }
        val label = stringResource(device?.tabLabel() ?: tab.label)
        androidx.compose.animation.AnimatedContent(
            targetState = label,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(tween(220, delayMillis = 200)) + androidx.compose.animation.slideInVertically(tween(260, delayMillis = 200)) { it / 2 })
                    .togetherWith(androidx.compose.animation.fadeOut(tween(180)) + androidx.compose.animation.slideOutVertically(tween(200)) { -it / 2 })
            },
            label = "deviceLabel",
        ) { text ->
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Palette.Text else Palette.TextDim,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 4.dp),
        )
        }
    }
}

/** Stays on screen, so power and Bypass are always one tap away. */
@Composable
private fun TopBar(
    state: AppState,
    status: EngineStatus,
    showCurve: Boolean,
    settingsOpen: Boolean,
    onSettings: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    // Here because the strip below always shows the live spectrum. It still stops when the app is closed.
    AnalyserEffect(state.analyser)
    // The strip floats on the page below the bar rather than sitting in it (tester feedback).
    Column(Modifier.fillMaxWidth()) {
    val barColour = Palette.Bar
    Column(
        Modifier
            .fillMaxWidth()
            // Solid behind the controls, so they stay readable over a busy page. The strip's band below continues it
            // as a fade, so the page scrolling up beneath eases into the bar rather than stopping at an edge (user decision).
            .background(barColour)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val connectedNames = rememberConnectedOutputNames()
        val quickPresets = remember(state.current.id, state.favorites, state.presets, connectedNames) {
            PresetOrganiser.quickKeys(state, BuiltIns.availableFor(connectedNames) + state.presets)
        }
        PresetSwitcher(
            label = statusLine(state, status),
            problem = state.enabled && (status.error != null || !status.hasControl),
            presets = quickPresets,
            currentId = state.current.id,
            favorites = state.favorites,
            onPick = Repo::loadPreset,
            onAll = LocalOpenPresets.current,
            modifier = Modifier
                .weight(1f)
                .tourTarget(TourStop.PRESETS),
        ) {
            // The version beside the name, small and quiet, so which build is running is plain at a glance.
            val context = LocalContext.current
            val version = remember {
                io.github.dragjon.eigentone.data.CrashLog.VERSION
            }
            // Neither wraps: when the bar is tight the version is clipped rather than pushed into the name.
            Row {
                Text(
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Text,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                // Left out with Large or Larger text, where there's no room for it beside the name (tester feedback).
                if (version.isNotEmpty() && state.textScale <= 1.0) {
                    Text(
                        version,
                        style = Readout.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                        color = Palette.TextDim,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .alignByBaseline()
                            .weight(1f, fill = false)
                            .padding(start = 6.dp),
                    )
                }
            }
        }
        // Even gaps: version, Bypass, switch and settings sit 12 dp apart (the settings icon carries its own 12 dp).
        Row(Modifier.padding(start = 8.dp).tourTarget(TourStop.POWER), verticalAlignment = Alignment.CenterVertically) {
            if (state.enabled) BypassButton(state.bypass)
            val view = androidx.compose.ui.platform.LocalView.current
            Switch(
                colors = switchColors(),
                checked = state.enabled,
                // Felt as well as seen, since it's the switch that matters most.
                onCheckedChange = { on ->
                    Haptics.toggle(view, on)
                    onSetEnabled(on)
                },
                modifier = Modifier
                    .litGlow(state.enabled && !state.bypass)
                    .padding(start = 8.dp)
                    .semantics { contentDescription = "Eigentone" },
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                // Filled while Settings is open, as the tabs' icons are while they're in view.
                if (settingsOpen) Icons.Filled.Settings else Icons.Outlined.Settings,
                contentDescription = stringResource(Res.string.ui_tab_system),
                tint = if (settingsOpen) Palette.Lit else Palette.TextDim,
                // The tour outlines the gear itself rather than its whole touch area, which would reach the switch.
                modifier = Modifier.tourTarget(TourStop.SETTINGS),
            )
        }
    }
    }
    }
}

// Room for "Bypassed" clear of the pill's rounded ends.
private val BypassPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)

/** Distinct from the power switch: neutral until engaged, when it's clearly a held state. */
@Composable
private fun BypassButton(bypassed: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    val toggle = {
        Haptics.toggle(view, !bypassed)
        Repo.update { it.copy(bypass = !it.bypass) }
    }
    // Both words are laid out and only one shows, so the button keeps one width and nothing beside it moves.
    val label: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.ui_bypassed), maxLines = 1, modifier = Modifier.alpha(if (bypassed) 1f else 0f))
            Text(stringResource(Res.string.ui_bypass), maxLines = 1, modifier = Modifier.alpha(if (bypassed) 0f else 1f))
        }
    }
    if (bypassed) {
        // Engaged, it's a warning: the EQ is silently off. Orange, since red belongs to the right ear.
        androidx.compose.material3.Button(
            onClick = { toggle() },
            shape = KeyShape,
            contentPadding = BypassPadding,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = androidx.compose.ui.graphics.lerp(Palette.Surface, Palette.Danger, 0.3f),
                contentColor = Palette.Danger,
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Palette.Danger.copy(alpha = 0.6f)),
        ) { label() }
    } else {
        FilledTonalButton(onClick = { toggle() }, contentPadding = BypassPadding) { label() }
    }
}

private fun statusLine(state: AppState, status: EngineStatus): String {
    val saved = Repo.allPresets(state).firstOrNull { it.id == state.current.id }
    // A preset deleted while in use isn't "edited": there's nothing left to compare it with.
    val name = when (saved) {
        null -> appText(Res.string.ui_not_saved, state.current.name)
        state.current -> state.current.name
        else -> appText(Res.string.ui_edited, state.current.name)
    }
    return when {
        !state.enabled -> appText(Res.string.ui_mode_off)
        status.error != null -> appText(Res.string.ui_problem_see_the_system_tab)
        !status.running -> appText(Res.string.ui_starting)
        !status.hasControl -> appText(Res.string.ui_another_audio_app_has_taken_control)
        else -> name
    }
}

@Composable
private fun EqTab(state: AppState, canvasOnly: Boolean = false) {
    val filters = state.current.filters
    var selectedId by rememberSaveable { mutableStateOf<Int?>(filters.firstOrNull()?.id) }
    var graphicMode by rememberSaveable { mutableStateOf(true) }
    val openFinder = LocalOpenFinder.current
    // The chosen filter can stop existing without a tap here, as when Discard, undo or switching preset
    // removes it. Falling back to the first filter keeps the editor open rather than leaving nothing chosen.
    val selected = filters.firstOrNull { it.id == selectedId } ?: filters.firstOrNull()
    LaunchedEffect(selected?.id) { if (selected?.id != selectedId) selectedId = selected?.id }
    val showParametric = Disclosure.parametric(state)
    val inGraphic = graphicMode || !showParametric
    // The graph grows for parametric editing and shrinks back above the faders, rather than jumping.
    val roomy = canvasOnly || LocalPageWidth.current >= EQ_SIDE_PANEL_FROM_DP.dp
    // On the canvas the graph takes the height the rest leaves: the canvas's height, less everything else in it.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val canvasOtherPx = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val canvasHeight = LocalCanvasGraph.current
    val canvasFill = remember { mutableStateOf(CANVAS_GRAPH_MIN_DP.dp) }
    if (canvasOnly) {
        val wanted = (canvasHeight - with(density) { canvasOtherPx.intValue.toDp() }).coerceAtLeast(CANVAS_GRAPH_MIN_DP.dp)
        if (kotlin.math.abs((wanted - canvasFill.value).value) > 1f) canvasFill.value = wanted
    }
    val canvasGraph = LocalCanvasGraph.current
    val graphHeight = androidx.compose.animation.core.animateDpAsState(
        when {
            canvasOnly && inGraphic -> canvasGraph
            canvasOnly -> canvasGraph * 1.3f
            roomy && inGraphic -> 260.dp
            roomy -> 380.dp
            inGraphic -> 170.dp
            else -> 260.dp
        },
        tween(TAB_SLIDE_MS, easing = LinearOutSlowInEasing),
        label = "graphHeight",
    )
    val attenuation by Repo.attenuationDb.collectAsStateWithLifecycle()
    val curveFilters = remember(state, attenuation, Repo.output.value.key) { Processing.effectiveFilters(state, attenuation, Repo.output.value.key) }
    val graphOutput by Repo.output.collectAsStateWithLifecycle()
    val earCurves = remember(curveFilters, state.current.filters, state.earCorrections, graphOutput.key, state.useSystemSurround, state.bypass) {
        if (state.bypass) null else Processing.channelFilters(state, graphOutput.key, curveFilters)
    }
    // The measured points of the ear correction, sitting on each ear's own curve.
    val earMarkers = remember(earCurves, state.earCorrections, graphOutput.key) {
        val ears = state.earsFor(graphOutput.key)?.takeIf { it.enabled }
        if (ears == null || earCurves == null) {
            null
        } else {
            io.github.dragjon.eigentone.data.EarCorrection(
                left = ears.left.map { io.github.dragjon.eigentone.data.ShapePoint(it.hz, Eq.curveDb(earCurves.first, it.hz)) },
                right = ears.right.map { io.github.dragjon.eigentone.data.ShapePoint(it.hz, Eq.curveDb(earCurves.second, it.hz)) },
            )
        }
    }
    val savedPreset = Repo.allPresets(state).firstOrNull { it.id == state.current.id }
    val savedCurve = remember(savedPreset, state.current, state.loudness, state.loudnessStrength, attenuation, state.bypass) {
        savedPreset?.takeIf { it != state.current && !state.bypass }
            ?.let { Processing.effectiveFilters(state.copy(current = it), attenuation, Repo.output.value.key) }
    }
    val spectrumState = Repo.spectrum.collectAsStateWithLifecycle()
    val pageShown = LocalPageShown.current

    // On a wide screen the device prompts, tone and Refine sit in a panel beside the graph and faders,
    // which then get the rest of the width. In a narrow window everything stacks, as on the phone.
    val wide = !canvasOnly && LocalPageWidth.current >= EQ_SIDE_PANEL_FROM_DP.dp
    val prompts: @Composable () -> Unit = {
        // The tour points at the phone layout's tabs and strip; it comes back once it's written for this one.
        if (WINDOWS_TOUR_READY && !state.tourSeen && Tour.stop == null) {
            TourOffer()
        } else if (!state.guidedSetupSeen) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(Palette.SurfaceHigh)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(Res.string.ui_new_here_find_your_sound), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Text(
                    stringResource(Res.string.ui_compare_two_versions_of_a_song_a_few_tim),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = openFinder) { Text(stringResource(Res.string.ui_start)) }
                    TextButton(onClick = { Repo.update { it.copy(guidedSetupSeen = true) } }) { Text(stringResource(Res.string.ui_not_now)) }
                }
            }
        } else {
            OutputPrompts(state)
        }
    }
    val main: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(PanelShape)
                .background(Palette.Surface)
                // The graph sits inset in its rounded panel, so its labels and the info icon keep clear of the corners.
                .padding(start = GRAPH_INSET_DP.dp, top = GRAPH_INSET_DP.dp, bottom = GRAPH_INSET_DP.dp)
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        ) {
            Box(Modifier.weight(1f)) {
            EqGraph(
                legendInsetDp = if (state.analyser) SPECTRUM_INFO_INSET_DP else 0,
                // Graphic mode has its own faders, so the parametric nodes would only get in the way.
                filters = if (inGraphic) emptyList() else filters,
                height = { if (canvasOnly) canvasFill.value else graphHeight.value },
                curveFilters = earCurves?.first ?: curveFilters,
                rightCurveFilters = earCurves?.second,
                ghostFilters = savedCurve,
                morphKey = state.current.id,
                earMarkers = earMarkers,
                // Not read while the page is out of view, so the graph isn't redrawn off screen with every reading.
                spectrum = { if (pageShown() && state.analyser) spectrumState.value else null },
                selectedId = selectedId,
                dimmed = !state.enabled || state.bypass,
                onSelect = { selectedId = it },
                onDrag = { id, freq, gain ->
                    updateFilter(id) { f -> f.copy(freq = freq, gainDb = if (f.type.hasGain) gain else f.gainDb) }
                },
                onDragEnd = Repo::commit,
            )
                // Explains the spectrum while it shows, faint so it doesn't compete with the curve.
                if (state.analyser) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            // Centres the icon over the column of dB labels below it, clear of the panel's edge,
                            // and in line with the ear legend's row.
                            .offset(x = (-10).dp, y = (-10).dp)
                            .alpha(SPECTRUM_INFO_ALPHA),
                    ) { InfoButton(Help.analyser) }
                }
            }
            // The output level beside the spectrum it comes from, at the graph's right edge.
            // Tapping it explains it. The touch area is wider than the bar, so it's easy to hit.
            if (state.analyser) TipOnTap(Help.meter, Modifier.requiredWidth(28.dp).fillMaxHeight()) {
                OutputMeterBar(Modifier.padding(top = 12.dp, bottom = 12.dp))
            }
        }
        if (state.analyser && analyserUnavailable.value) {
            Text(stringResource(Res.string.ui_this_phone_refused_the_analyser), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }

        if (!canvasOnly) Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Without the mode switch beside it, the analyser is a labelled chip, so it doesn't sit alone as a bare icon.
            if (!showParametric) {
                AnalyserChip(state)
            } else SlidingToggle(
                options = listOf(ToggleOption(stringResource(Res.string.ui_graphic)), ToggleOption(stringResource(Res.string.ui_parametric))),
                selected = if (graphicMode) 0 else 1,
                onSelect = { graphicMode = it == 0 },
                modifier = Modifier.weight(1f),
            )
            // Beside the mode rather than on the graph, where the ear legends sit.
            if (showParametric) AnalyserToggle(state, Modifier)
            if (showParametric) InfoButton(Help.eqMode)
        }

        // The faders and the filter list swap with a slide that follows the switch above: Graphic is on the
        // left, so it comes in from the left, and Parametric from the right.
        AnimatedContent(
            targetState = inGraphic,
            transitionSpec = {
                val sign = if (targetState) -1 else 1
                (slideInHorizontally(tween(TAB_SLIDE_MS, easing = LinearOutSlowInEasing)) { sign * it / 4 } + fadeIn(tween(TAB_SLIDE_MS / 2))) togetherWith
                    (slideOutHorizontally(tween(TAB_SLIDE_MS, easing = LinearOutSlowInEasing)) { -sign * it / 4 } + fadeOut(tween(TAB_SLIDE_MS / 2))) using
                    // The panel's height changes on the same curve and time as the slide, so everything moves together.
                    androidx.compose.animation.SizeTransform(clip = false) { _, _ -> tween(TAB_SLIDE_MS, easing = LinearOutSlowInEasing) }
            },
            label = "eqMode",
        ) { graphic ->
        Column(verticalArrangement = SectionSpacing) {
        if (graphic) {
            GraphicEqConsole(state.current.tone, state.current.id, onBrowseShapes = LocalOpenShapes.current, onExpand = LocalOpenFaders.current, canvas = canvasOnly)
            // A shortcut to Refine, which is also on the device tab with the other listening tools. On a wide screen
            // the side panel has it instead.
            if (!roomy) SettingsSection(null) {
                ActionRow(stringResource(Res.string.ui_refine_this), Help.refine, detail = stringResource(Res.string.ui_refine_this_detail), onClick = LocalOpenRefine.current)
            }
            return@Column
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                enabled = filters.size < MAX_FILTERS,
                onClick = {
                    // The id comes from the filters at the moment of adding, so two quick taps can't share one.
                    var id = 0
                    Repo.edit { p ->
                        id = (p.filters.maxOfOrNull { it.id } ?: 0) + 1
                        p.copy(filters = p.filters + Filter(id))
                    }
                    Repo.commit()
                    selectedId = id
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.ui_add_filter))
            }
            UndoRedoButtons()
        }

        if (selected == null) {
            Text(
                if (filters.isEmpty()) stringResource(Res.string.ui_no_filters_yet_add_one_to)
                else stringResource(Res.string.ui_tap_a_point_on_the_curve),
                color = Palette.TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            val snackbar = LocalSnackbar.current
            val snackScope = LocalSnackScope.current
            val filterDeleted = stringResource(Res.string.ui_filter_deleted)
            val dynamicPlaced = remember(state) {
                val target = Processing.target(state.copy(bypass = false), 0.0)
                io.github.dragjon.eigentone.data.Dynamics.placedIds(target.dynamics, target.tamers)
            }
            // Moving to another filter slides its editor in from the side it sits on, by its place in the list.
            AnimatedContent(
                targetState = selected.id,
                transitionSpec = {
                    val forward = filters.indexOfFirst { it.id == targetState } >= filters.indexOfFirst { it.id == initialState }
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally(tween(TAB_SLIDE_MS, easing = LinearOutSlowInEasing)) { sign * it / 5 } + fadeIn(tween(TAB_SLIDE_MS / 2))) togetherWith
                        (slideOutHorizontally(tween(TAB_SLIDE_MS, easing = LinearOutSlowInEasing)) { -sign * it / 5 } + fadeOut(tween(TAB_SLIDE_MS / 2))) using
                        androidx.compose.animation.SizeTransform(clip = false)
                },
                label = "filter",
            ) { shownId ->
            val shown = filters.firstOrNull { it.id == shownId } ?: return@AnimatedContent
            FilterEditor(
                filter = shown,
                filters = filters,
                dynamicPlaced = dynamicPlaced,
                onSelect = { selectedId = it },
                onDelete = {
                    val index = filters.indexOf(shown)
                    val deletedId = shown.id
                    Repo.commit()
                    Repo.edit { p -> p.copy(filters = p.filters.filterNot { it.id == deletedId }) }
                    Repo.commit()
                    val remaining = Repo.state.value.current.filters
                    selectedId = remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.id
                    // Deleting a preset offers Undo, and so does deleting a filter.
                    snackScope.offerUndo(
                        snackbar,
                        filterDeleted,
                        undo = {
                            Repo.undo()
                            selectedId = deletedId
                        },
                        redo = {
                            Repo.redo()
                            val left = Repo.state.value.current.filters
                            selectedId = left.getOrNull(index.coerceAtMost(left.lastIndex))?.id
                        },
                    )
                },
            )
            }
        }
        }
        }
    }
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(PAGE_GAP_DP.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = SectionSpacing) { main() }
            Column(Modifier.width(EQ_SIDE_PANEL_DP.dp), verticalArrangement = SectionSpacing) {
                prompts()
                ToneSection(state, savedPreset(state)?.tone)
                SettingsSection(null) {
                    ActionRow(stringResource(Res.string.ui_refine_this), Help.refine, detail = stringResource(Res.string.ui_refine_this_detail), onClick = LocalOpenRefine.current)
                }
            }
        }
    } else if (canvasOnly) {
        // The studio's canvas: a toolbar, then the graph filling whatever height the faders leave, then the faders.
        // Tone, shapes and Refine are in the panels on the right.
        Column(
            Modifier.onSizeChanged { canvasOtherPx.intValue = it.height - with(density) { canvasFill.value.roundToPx() } },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CanvasToolbar(state, inGraphic, showParametric, graphicMode) { graphicMode = it }
            prompts()
            main()
        }
    } else {
        prompts()
        main()
    }
}

/** The unsaved changes in a few words, such as "1 added filter, faders and bass". */
@Composable
private fun changeSummary(parts: List<Pair<PresetChanges.Part, Int>>): String {
    val words = parts.map { (part, count) ->
        when (part) {
            PresetChanges.Part.ADDED_FILTERS -> pluralStringResource(Res.plurals.ui_changes_added_filters, count, count)
            PresetChanges.Part.REMOVED_FILTERS -> pluralStringResource(Res.plurals.ui_changes_removed_filters, count, count)
            PresetChanges.Part.CHANGED_FILTERS -> pluralStringResource(Res.plurals.ui_changes_changed_filters, count, count)
            PresetChanges.Part.PREAMP -> stringResource(Res.string.ui_changes_preamp)
            PresetChanges.Part.FADERS -> stringResource(Res.string.ui_changes_faders)
            PresetChanges.Part.BASS -> stringResource(Res.string.ui_changes_bass)
            PresetChanges.Part.TREBLE -> stringResource(Res.string.ui_changes_treble)
            PresetChanges.Part.VOCALS -> stringResource(Res.string.ui_changes_vocals)
            PresetChanges.Part.STRENGTH -> stringResource(Res.string.ui_changes_strength)
            PresetChanges.Part.CORRECTION -> stringResource(Res.string.ui_changes_correction)
            PresetChanges.Part.WIDTH -> stringResource(Res.string.ui_changes_width)
            PresetChanges.Part.COMPRESSOR -> stringResource(Res.string.ui_changes_compressor)
            PresetChanges.Part.CLARITY -> stringResource(Res.string.ui_changes_clarity)
            PresetChanges.Part.REVERB -> stringResource(Res.string.ui_changes_reverb)
            PresetChanges.Part.OTHER -> stringResource(Res.string.ui_changes_other)
        }
    }
    return when (words.size) {
        0 -> ""
        1 -> words[0]
        else -> stringResource(Res.string.ui_changes_and, words.dropLast(1).joinToString(", "), words.last())
    }
}

/** Shown while the preset has changes, so they can be kept or dropped without leaving the EQ. */
/**
 * Unsaved changes, the Windows way: a dot and a few words in the command bar, with what changed in a tooltip,
 * and Discard and Save beside them. Nothing floats over the page.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun InlineUnsaved(title: String, detail: String, actions: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.TooltipArea(
            tooltip = {
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Text,
                        modifier = Modifier.background(Palette.Tooltip, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Palette.Lit))
                Text(title, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            }
        }
        actions()
    }
}

@Composable
private fun UnsavedBar(state: AppState, saved: Preset, modifier: Modifier = Modifier, inline: Boolean = false) {
    val scope = LocalSnackScope.current
    val snackbar = LocalSnackbar.current
    var naming by remember { mutableStateOf(false) }
    val own = state.presets.any { it.id == saved.id }
    val changes = changeSummary(remember(saved, state.current) { PresetChanges.of(saved, state.current) })
    val view = androidx.compose.ui.platform.LocalView.current
    val discard = {
        Repo.discardEdits()
        // Discard is a step in the undo history, so the message can offer it straight back.
        scope.offerUndo(snackbar, appText(Res.string.ui_edits_discarded, saved.name), undo = Repo::undo, redo = Repo::redo)
    }
    val save = {
        if (own) {
            Haptics.confirm(view)
            Repo.overwriteCurrent()
            // Saving over a preset can be taken back, which leaves the edits waiting to be saved again.
            scope.offerUndo(
                snackbar,
                appText(Res.string.ui_updated, saved.name),
                undo = { Repo.update { s -> s.copy(presets = s.presets.map { if (it.id == saved.id) saved else it }) } },
                redo = { if (Repo.state.value.current.id == saved.id) Repo.overwriteCurrent() },
            )
        } else {
            naming = true
        }
    }
    if (inline) {
        InlineUnsaved(stringResource(Res.string.win_unsaved), changes) {
            TextButton(onClick = discard) { Text(stringResource(Res.string.ui_discard)) }
            Button(onClick = save) {
                Text(stringResource(if (own) Res.string.ui_save else Res.string.ui_save_as_new), maxLines = 1)
            }
        }
    } else
    Row(
        modifier
            .fillMaxWidth()
            .shadow(6.dp, PanelShape)
            .clip(PanelShape)
            .background(Palette.Note)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Says what's unsaved, so it's plain what Discard would drop before it's tapped.
        val changes = changeSummary(remember(saved, state.current) { PresetChanges.of(saved, state.current) })
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.ui_unsaved_changes, saved.name),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
                // Two lines, so the preset's name isn't cut to its first letter beside the two keys.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (changes.isNotEmpty()) {
                Text(
                    changes,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = {
            Repo.discardEdits()
            // Discard is a step in the undo history, so the message can offer it straight back.
            scope.offerUndo(snackbar, appText(Res.string.ui_edits_discarded, saved.name), undo = Repo::undo, redo = Repo::redo)
        }) { Text(stringResource(Res.string.ui_discard)) }
        val view = androidx.compose.ui.platform.LocalView.current
        Button(onClick = {
            if (own) {
                Haptics.confirm(view)
                Repo.overwriteCurrent()
                // Saving over a preset can be taken back, which leaves the edits waiting to be saved again.
                scope.offerUndo(
                    snackbar,
                    appText(Res.string.ui_updated, saved.name),
                    undo = { Repo.update { s -> s.copy(presets = s.presets.map { if (it.id == saved.id) saved else it }) } },
                    redo = { if (Repo.state.value.current.id == saved.id) Repo.overwriteCurrent() },
                )
            } else {
                naming = true
            }
        }) { Text(stringResource(if (own) Res.string.ui_save else Res.string.ui_save_as_new), maxLines = 1) }
    }
    if (naming) {
        SaveDialog(initial = saved.name, onDismiss = { naming = false }) { name ->
            Repo.saveCurrentAs(name)
            naming = false
            scope.notify(snackbar, appText(Res.string.ui_saved_as, name))
        }
    }
}

/**
 * [value], but only once it has held for [settleMs], so something that flips quickly back and forth, such
 * as a slider swept through zero, changes what's shown once rather than on every flip.
 */
@Composable
private fun rememberSettled(value: Boolean, settleMs: Long = SHOW_SETTLE_MS): Boolean {
    var settled by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value == settled) return@LaunchedEffect
        kotlinx.coroutines.delay(settleMs)
        settled = value
    }
    return settled
}

private const val SHOW_SETTLE_MS = 500L
private const val BAR_GAP_DP = 8
/** Space between the strip and the page's first panel, matching the space above the strip. */
private const val PAGE_TOP_DP = 8
/** How solid the fades beside the bars are partway through, where the page starts to show. */
private const val BAR_EDGE_ALPHA = 0.85f
/** How solid the tab bar's even tint is where the phone can't blur, before Android 12. */
private const val TAB_BAR_ALPHA = 0.94f
/** Over the blur, the tint can be lighter, since the blur already softens what's behind. */
private const val TAB_BAR_BLUR_TINT = 0.75f
private const val TAB_BAR_BLUR_DP = 24
/** How far above the pill the band starts, so the page has room to fade into it. */
private const val TAB_BAND_FADE_DP = 24
/** The pill's inset from the sides, matching the strip's at the top. */
private const val TAB_PILL_INSET_DP = 12
/** Space between the pill and the gesture line. */
private const val TAB_PILL_GAP_DP = 8
private const val TAB_PILL_SHADOW_DP = 6
// Concentric with the selected tab's highlight inside it: its 16 dp corners plus the 8 dp between them.
private val TabPillShape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)

/**
 * The colour the app is made from: the phone's own wallpaper colours where Android has them, one of a few
 * swatches, or any hue of your own.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ThemeColourRow(state: AppState) {
    val context = LocalContext.current
    // Windows has no wallpaper colours to offer, as Android 11 and older have none.
    val wallpaperAvailable = false
    val seed = state.themeColor
    val custom = seed != null && seed !in SeedScheme.SWATCHES
    // Kept apart from the saved colour while the hue is dragged, so the whole app isn't recoloured every frame.
    val lastPreview = remember { longArrayOf(0L) }
    var hue by remember(seed) { mutableStateOf(seed?.let { SeedScheme.hueOf(Color(it.toInt())).toFloat() } ?: 220f) }
    val names = listOf(
        Res.string.ui_colour_blue, Res.string.ui_colour_teal, Res.string.ui_colour_green, Res.string.ui_colour_amber,
        Res.string.ui_colour_orange, Res.string.ui_colour_red, Res.string.ui_colour_pink, Res.string.ui_colour_purple,
    )
    fun pick(value: Long?) = Repo.update { it.copy(themeColor = value) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.ui_theme_colour), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
            InfoButton(Help.themeColour)
        }
        androidx.compose.foundation.layout.FlowRow(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // No choice means Windows' own accent colour, so it has a swatch of its own, and blue is just blue.
            val windowsAccent = io.github.dragjon.eigentone.platform.WindowsTheme.accent
            if (windowsAccent != null) {
                ColourSwatch(Color(windowsAccent.toInt()), stringResource(Res.string.win_colour_windows), selected = seed == null, wallpaper = true) { pick(null) }
            }
            SeedScheme.SWATCHES.forEachIndexed { i, colour ->
                val chosen = seed == colour || (windowsAccent == null && seed == null && colour == SeedScheme.DEFAULT_SEED)
                ColourSwatch(Color(colour.toInt()), stringResource(names[i]), selected = chosen) {
                    pick(if (windowsAccent == null && colour == SeedScheme.DEFAULT_SEED) null else colour)
                }
            }
            ColourSwatch(Color(SeedScheme.fromHue(hue).toInt()), stringResource(Res.string.ui_colour_custom), selected = custom, rainbow = true) {
                pick(SeedScheme.fromHue(hue))
            }
        }
        if (custom) {
            ValueTrack(
                label = stringResource(Res.string.ui_colour_hue),
                readout = "${hue.toInt()}°",
                value = hue,
                range = 0f..359f,
                origin = 0f,
                default = hue,
                onChange = {
                    hue = it
                    // Recolours the app as the finger slides. Recolouring redraws the whole app, so it's done at most
                    // ten times a second, and the handle itself still follows the finger every frame.
                    val now = io.github.dragjon.eigentone.platform.SystemClock.uptimeMillis()
                    if (now - lastPreview[0] >= HUE_PREVIEW_MS) {
                        lastPreview[0] = now
                        ThemePreview.seed = SeedScheme.fromHue(it)
                    }
                },
                onFinished = {
                    pick(SeedScheme.fromHue(hue))
                    ThemePreview.seed = null
                },
                trackColours = RAINBOW,
            )
        }
    }
}

/** One round colour to choose, ringed while chosen. */
@Composable
private fun ColourSwatch(colour: Color, name: String, selected: Boolean, wallpaper: Boolean = false, rainbow: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
            val ring = 2.dp.toPx()
            if (selected) drawCircle(Palette.Text, radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(ring))
            val inner = size.minDimension / 2 - ring * 2
            if (rainbow) {
                drawCircle(
                    androidx.compose.ui.graphics.Brush.sweepGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                    ),
                    radius = inner,
                )
                drawCircle(colour, radius = inner * 0.5f)
            } else {
                drawCircle(colour, radius = inner)
            }
            // The phone's colours are marked with a small dot of the page colour, so it's told apart from a swatch.
            if (wallpaper) drawCircle(Palette.Surface, radius = inner * 0.3f)
        }
    }
}

/** Settings that make the app easier to read, feel and keep up with. */
@Composable
private fun AccessibilitySection(state: AppState) {
    SettingsSection(stringResource(Res.string.ui_accessibility)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.ui_text_size), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                InfoButton(Help.textSize)
            }
            val scales = TEXT_SCALES
            SlidingToggle(
                options = listOf(
                    ToggleOption(stringResource(Res.string.ui_text_size_phone)),
                    ToggleOption(stringResource(Res.string.ui_text_size_large)),
                    ToggleOption(stringResource(Res.string.ui_text_size_larger)),
                ),
                selected = scales.indexOfFirst { kotlin.math.abs(it - state.textScale) < 0.01 }.coerceAtLeast(0),
                onSelect = { i -> Repo.update { it.copy(textScale = scales[i]) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RowDivider()
        SwitchSetting(stringResource(Res.string.ui_high_contrast), Help.highContrast, state.highContrast) { on ->
            Repo.update { it.copy(highContrast = on) }
        }
        if (ANDROID_ONLY) {
            RowDivider()
            SwitchSetting(stringResource(Res.string.ui_haptics), Help.haptics, state.haptics) { on ->
                Repo.update { it.copy(haptics = on) }
            }
        }
        RowDivider()
        SwitchSetting(stringResource(Res.string.ui_long_notices), Help.longNotices, state.longNotices) { on ->
            Repo.update { it.copy(longNotices = on) }
        }
    }
}

private val TEXT_SCALES = listOf(1.0, 1.15, 1.3)
private const val HUE_PREVIEW_MS = 100L

/** The hues round the wheel, as the theme would make them, for the hue slider's track. */
private val RAINBOW: List<Color> = (0..12).map { Color(SeedScheme.fromHue(it * 30f).toInt()) }

/** Shown while the device tab has changes waiting, so they can be kept or put back as a preset's can. */
@Composable
private fun DeviceUnsavedBar(state: AppState, saved: DeviceSettings, inline: Boolean = false) {
    val scope = LocalSnackScope.current
    val snackbar = LocalSnackbar.current
    val output by Repo.output.collectAsStateWithLifecycle()
    val parts = remember(saved, state) { DeviceSettings.of(state).changedParts(saved) }
    val words = parts.map {
        when (it) {
            DeviceSettings.Part.PRESET -> stringResource(Res.string.ui_device_changes_preset)
            DeviceSettings.Part.MODE -> stringResource(Res.string.ui_device_changes_mode)
            DeviceSettings.Part.AUTO_SWITCH -> stringResource(Res.string.ui_device_changes_auto)
            DeviceSettings.Part.EARS -> stringResource(Res.string.ui_device_changes_ears)
            DeviceSettings.Part.QUIET -> stringResource(Res.string.ui_device_changes_quiet)
        }
    }
    val summary = when (words.size) {
        0 -> ""
        1 -> words[0]
        else -> stringResource(Res.string.ui_changes_and, words.dropLast(1).joinToString(", "), words.last())
    }
    if (inline) {
        val view = androidx.compose.ui.platform.LocalView.current
        InlineUnsaved(stringResource(Res.string.win_unsaved_device, output.label), summary) {
            TextButton(onClick = {
                val dropped = Repo.discardDevice() ?: return@TextButton
                scope.offerUndo(
                    snackbar,
                    appText(Res.string.ui_device_discarded),
                    undo = { Repo.restoreDevice(dropped) },
                    redo = { Repo.discardDevice() },
                )
            }) { Text(stringResource(Res.string.ui_discard)) }
            Button(onClick = {
                Haptics.confirm(view)
                Repo.saveDevice()
                scope.notify(snackbar, appText(Res.string.ui_device_saved))
            }) { Text(stringResource(Res.string.ui_save), maxLines = 1) }
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, PanelShape)
            .clip(PanelShape)
            .background(Palette.Note)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(Res.string.ui_device_unsaved, output.label),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.isNotEmpty()) {
                Text(summary, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = {
            val dropped = Repo.discardDevice() ?: return@TextButton
            scope.offerUndo(
                snackbar,
                appText(Res.string.ui_device_discarded),
                undo = { Repo.restoreDevice(dropped) },
                redo = { Repo.discardDevice() },
            )
        }) { Text(stringResource(Res.string.ui_discard)) }
        val view = androidx.compose.ui.platform.LocalView.current
        Button(onClick = {
            Haptics.confirm(view)
            Repo.saveDevice()
            scope.notify(snackbar, appText(Res.string.ui_device_saved))
        }) { Text(stringResource(Res.string.ui_save), maxLines = 1) }
    }
}

/** Runs the analyser only while the EQ tab is visible, and only if it has been turned on. */
@Composable
private fun AnalyserEffect(enabled: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(enabled, lifecycle) {
        // A reading already on its way when the analyser stops is dropped, so turning it off always clears the
        // bars rather than sometimes leaving the last ones behind.
        val live = java.util.concurrent.atomic.AtomicBoolean(true)
        val analyser = SpectrumAnalyser(
            onBands = { if (live.get()) Repo.spectrum.value = it },
            onLevel = { peak, rms -> if (live.get()) Repo.updateLevel(peak, rms) },
        )
        val observer = LifecycleEventObserver { _, event ->
            // The plug-in measures what's playing itself, so Windows needs no microphone permission for this.
            val allowed = enabled
            when (event) {
                Lifecycle.Event.ON_START -> if (allowed) {
                    live.set(true)
                    analyserUnavailable.value = !analyser.start()
                }
                Lifecycle.Event.ON_STOP -> {
                    live.set(false)
                    analyser.stop()
                    Repo.spectrum.value = null
                    Repo.level.value = null
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            live.set(false)
            lifecycle.removeObserver(observer)
            analyser.stop()
            Repo.spectrum.value = null
            Repo.level.value = null
        }
    }
}

private val analyserUnavailable = mutableStateOf(false)

/** Turns the live spectrum on or off, first asking for the microphone permission Android requires to read it. */
@Composable
private fun rememberAnalyserSwitch(): (Boolean) -> Unit {
    return { on -> Repo.update { it.copy(analyser = on) } }
}

/** The analyser as a labelled chip, for when it stands on its own row. */
@Composable
private fun AnalyserChip(state: AppState) {
    val switch = rememberAnalyserSwitch()
    FilterChip(
        selected = state.analyser,
        onClick = { switch(!state.analyser) },
        label = { Text(stringResource(Res.string.ui_live_spectrum), maxLines = 1) },
        leadingIcon = { Icon(painterResource(Res.drawable.ic_spectrum), contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Palette.LitFill,
            selectedLabelColor = Palette.OnLit,
            selectedLeadingIconColor = Palette.OnLit,
            labelColor = Palette.Text,
            iconColor = Palette.TextDim,
        ),
    )
}

/** Shows the live spectrum behind the curve, as an icon beside the EQ mode switch. */
@Composable
private fun AnalyserToggle(state: AppState, modifier: Modifier) {
    val switch = rememberAnalyserSwitch()
    androidx.compose.material3.IconToggleButton(
        checked = state.analyser,
        onCheckedChange = { on -> switch(on) },
        modifier = modifier,
    ) {
        Icon(
            painterResource(Res.drawable.ic_spectrum),
            contentDescription = stringResource(Res.string.ui_spectrum_analyser),
            tint = if (state.analyser) Palette.Lit else Palette.TextDim,
        )
    }
}

private const val SPECTRUM_INFO_INSET_DP = 24

/** The graph's room inside its rounded panel. */
private const val GRAPH_INSET_DP = 8
private const val SPECTRUM_INFO_ALPHA = 0.7f

private fun updateFilter(id: Int, transform: (Filter) -> Filter) {
    Repo.edit { p -> p.copy(filters = p.filters.map { if (it.id == id) transform(it) else it }) }
}

@Composable
private fun FilterEditor(filter: Filter, filters: List<Filter>, dynamicPlaced: Set<Int>, onSelect: (Int) -> Unit, onDelete: () -> Unit) {
    val index = filters.indexOf(filter)
    SettingsSection(
        stringResource(Res.string.ui_filter_of, index + 1, filters.size),
        trailing = {
            // Nodes can overlap on the graph, so every filter is also reachable from here.
            IconButton(enabled = index > 0, onClick = { onSelect(filters[index - 1].id) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = stringResource(Res.string.ui_previous_filter))
            }
            IconButton(enabled = index < filters.lastIndex, onClick = { onSelect(filters[index + 1].id) }) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.ui_next_filter))
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(Res.drawable.ic_delete), contentDescription = stringResource(Res.string.ui_delete_filter), tint = Palette.TextDim)
            }
        },
    ) {
        SwitchSetting(stringResource(Res.string.ui_enabled), Help.filterEnabled, filter.enabled) { on ->
            updateFilter(filter.id) { it.copy(enabled = on) }
            Repo.commit()
        }
        RowDivider()
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.ui_type), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                InfoButton(Help.filterType)
            }
            // Each chip takes the width its name needs and the rows wrap, so no name is ever cut short.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TypeChip(filter, FilterType.BELL, stringResource(Res.string.ui_bell), Modifier)
                TypeChip(filter, FilterType.LOW_SHELF, stringResource(Res.string.ui_low_shelf), Modifier)
                TypeChip(filter, FilterType.HIGH_SHELF, stringResource(Res.string.ui_high_shelf), Modifier)
                TypeChip(filter, FilterType.TILT, stringResource(Res.string.ui_tilt), Modifier)
                TypeChip(filter, FilterType.HIGH_PASS, stringResource(Res.string.ui_high_pass), Modifier)
                TypeChip(filter, FilterType.LOW_PASS, stringResource(Res.string.ui_low_pass), Modifier)
                TypeChip(filter, FilterType.NOTCH, stringResource(Res.string.ui_notch), Modifier)
                TypeChip(filter, FilterType.BAND_PASS, stringResource(Res.string.ui_band_pass), Modifier)
            }
        }
        RowDivider()
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.ui_channel), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                InfoButton(Help.channel)
            }
            val channels = listOf(
                io.github.dragjon.eigentone.dsp.FilterChannel.BOTH to stringResource(Res.string.ui_channel_both),
                io.github.dragjon.eigentone.dsp.FilterChannel.LEFT to stringResource(Res.string.ui_channel_left),
                io.github.dragjon.eigentone.dsp.FilterChannel.RIGHT to stringResource(Res.string.ui_channel_right),
            )
            SlidingToggle(
                options = channels.map { (_, name) -> ToggleOption(name) },
                selected = channels.indexOfFirst { it.first == filter.channel }.coerceAtLeast(0),
                onSelect = { i ->
                    val channel = channels[i].first
                    if (filter.channel != channel) {
                        updateFilter(filter.id) { it.copy(channel = channel) }
                        Repo.commit()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (filter.type.hasSlope) {
            RowDivider()
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.ui_slope), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                    InfoButton(Help.slope)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(Res.string.ui_db_per_octave), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
                val current = Eq.slopeOf(filter)
                SlidingToggle(
                    options = Eq.SLOPES.map { ToggleOption("$it", description = appText(Res.string.ui_slope_option, it)) },
                    selected = Eq.SLOPES.indexOf(current).coerceAtLeast(0),
                    onSelect = { i ->
                        val option = Eq.SLOPES[i]
                        if (current != option) {
                            updateFilter(filter.id) { it.copy(slope = option) }
                            Repo.commit()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        RowDivider()
        SliderSetting(
            title = stringResource(Res.string.ui_frequency),
            tip = Help.frequency,
            readout = formatHz(filter.freq),
            value = Eq.logPosition(filter.freq).toFloat(),
            range = 0f..1f,
            // Frequency has no "off" value, so every position reads as a plain value.
            default = Eq.logPosition(filter.freq).toFloat(),
            onChange = { t -> updateFilter(filter.id) { it.copy(freq = Eq.logFreq(t.toDouble())) } },
            onFinished = Repo::commit,
        )
        if (filter.type.hasGain) {
            RowDivider()
            SliderSetting(
                title = stringResource(Res.string.ui_gain),
                tip = Help.gain,
                readout = formatDb(filter.gainDb),
                value = filter.gainDb.toFloat(),
                range = -Eq.MAX_GAIN.toFloat()..Eq.MAX_GAIN.toFloat(),
                default = 0f,
                origin = 0f,
                onChange = { g -> updateFilter(filter.id) { it.copy(gainDb = g.toDouble()) } },
                onFinished = Repo::commit,
            )
        }
        // A 6 dB slope has a single gentle bend, so there's no Q to set.
        if (!(filter.type.hasSlope && Eq.slopeOf(filter) == 6)) {
        RowDivider()
        SliderSetting(
            title = stringResource(Res.string.ui_q),
            tip = Help.q,
            readout = String.format(Locale.US, "%.2f", filter.q),
            value = qPosition(filter.q),
            range = 0f..1f,
            default = qPosition(1.0),
            onChange = { t -> updateFilter(filter.id) { it.copy(q = qFromPosition(t)) } },
            onFinished = Repo::commit,
        )
        }
        if (filter.canBeDynamic) DynamicFilterRows(filter, placed = filter.id in dynamicPlaced)
    }
}

/**
 * Turns a bell or shelf into a dynamic one, which also turns its range down while that range is loud. It
 * uses a band of Android's multiband compressor, which the compressor and Clarity share, so it says when
 * there's no band left for it.
 */
@Composable
private fun DynamicFilterRows(filter: Filter, placed: Boolean) {
    val dynamic = filter.dynamic
    RowDivider()
    SwitchSetting(
        stringResource(Res.string.ui_dynamic),
        Help.dynamicFilter,
        dynamic != null,
        detail = when {
            dynamic == null -> stringResource(Res.string.ui_dynamic_detail)
            placed -> null
            else -> stringResource(Res.string.ui_dynamic_no_room)
        },
    ) { on ->
        updateFilter(filter.id) { it.copy(dynamic = if (on) it.dynamic ?: io.github.dragjon.eigentone.dsp.FilterDynamics() else null) }
        Repo.commit()
    }
    if (dynamic == null) return
    RowDivider()
    SliderSetting(
        title = stringResource(Res.string.ui_threshold),
        tip = Help.dynamicFilter,
        readout = String.format(Locale.US, "%.0f dB", dynamic.thresholdDb),
        value = dynamic.thresholdDb.toFloat(),
        range = -60f..0f,
        default = -30f,
        onChange = { v -> updateFilter(filter.id) { it.copy(dynamic = dynamic.copy(thresholdDb = kotlin.math.round(v.toDouble()))) } },
        onFinished = Repo::commit,
    )
    RowDivider()
    SliderSetting(
        title = stringResource(Res.string.ui_ratio),
        tip = Help.dynamicFilter,
        readout = String.format(Locale.US, "%.1f:1", dynamic.ratio),
        value = dynamic.ratio.toFloat(),
        range = 1f..10f,
        default = 3f,
        onChange = { v -> updateFilter(filter.id) { it.copy(dynamic = dynamic.copy(ratio = kotlin.math.round(v * 2.0) / 2.0)) } },
        onFinished = Repo::commit,
    )
}

@Composable
private fun TypeChip(filter: Filter, type: FilterType, label: String, modifier: Modifier) {
    FilterChip(
        modifier = modifier,
        selected = filter.type == type,
        onClick = {
            updateFilter(filter.id) { it.copy(type = type) }
            Repo.commit()
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Palette.LitFill,
            selectedLabelColor = Palette.OnLit,
            labelColor = Palette.Text,
        ),
    )
}

private fun qPosition(q: Double): Float =
    (log10(q / Eq.MIN_Q) / log10(Eq.MAX_Q / Eq.MIN_Q)).toFloat()

private fun qFromPosition(t: Float): Double = Eq.MIN_Q * (Eq.MAX_Q / Eq.MIN_Q).pow(t.toDouble())

private fun formatHz(hz: Double): String =
    if (hz >= 1000) String.format(Locale.US, "%.2f kHz", hz / 1000) else String.format(Locale.US, "%.0f Hz", hz)

private fun formatDb(db: Double): String = String.format(Locale.US, "%+.1f dB", db)

private fun formatPercent(amount: Double): String = "${(amount * 100).roundToInt()}%"

/** Half-dB steps, which also makes it easy to land on exactly zero. */
private fun snapTone(value: Float): Double = kotlin.math.round(value * 2.0) / 2.0

private val toneRange = -Processing.MAX_TONE_DB.toFloat()..Processing.MAX_TONE_DB.toFloat()

/** The preset as last saved, where a double tap on one of its settings returns it. */
@Composable
private fun savedPreset(state: AppState): Preset? =
    remember(state.presets, state.current.id) { Repo.allPresets(state).firstOrNull { it.id == state.current.id } }

/** Says which settings a tab changes: the preset in use, or every preset. */
@Composable
private fun ScopeNote(text: String) {
    // In the studio every panel is in view, so these phone-tab notes are left out.
    if (STUDIO) return
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextDim,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Everything here is part of the preset in use, saved and shared with it. */
@Composable
private fun SoundTab(state: AppState) {
    val saved = savedPreset(state)?.tone
    Box(Modifier.fullSpan()) { ScopeNote(stringResource(Res.string.ui_sound_scope, state.current.name)) }
    // The note above already names the preset, so the legend says what the group is.
    if (Disclosure.presetLevel(state)) SettingsSection(stringResource(Res.string.ui_preset_level)) {
        SliderSetting(
            title = stringResource(Res.string.ui_preset_strength),
            tip = Help.strength,
            readout = formatPercent(state.current.tone.strength),
            value = state.current.tone.strength.toFloat(),
            range = 0f..1.5f,
            default = 1f,
            origin = 1f,
            onChange = { v -> Repo.editTone { it.copy(strength = kotlin.math.round(v * 20.0) / 20.0) } },
            onFinished = Repo::commit,
            resetTo = (saved?.strength ?: 1.0).toFloat(),
        )
        RowDivider()
        SliderSetting(
            title = stringResource(Res.string.ui_preamp),
            tip = Help.preamp,
            readout = formatDb(state.current.preampDb),
            value = state.current.preampDb.toFloat(),
            range = -24f..12f,
            default = 0f,
            origin = 0f,
            onChange = { v -> Repo.edit { it.copy(preampDb = v.toDouble()) } },
            onFinished = Repo::commit,
            resetTo = (savedPreset(state)?.preampDb ?: 0.0).toFloat(),
        )
    }
    ToneSection(state, saved)
    SpaceSection(state, saved)
    CompressorSection(state)
}

/** What applies whichever preset is in use, at the top of Settings. */
@Composable
private fun EveryPresetSections(state: AppState) {
    val attenuation by Repo.attenuationDb.collectAsStateWithLifecycle()
    ScopeNote(stringResource(Res.string.ui_settings_scope))
    DynamicsSection(state, attenuation)
    LevelSection(state, attenuation)
    StereoSection(state)
}

@Composable
private fun ToneSection(state: AppState, saved: Tone?) {
    val tone = state.current.tone
    val modified = tone.bassDb != 0.0 || tone.trebleDb != 0.0 || tone.vocalsDb != 0.0 || tone.bassTunerDb != 0.0 ||
        tone.bassHz != Tone.DEFAULT_BASS_HZ
    SettingsSection(
        stringResource(Res.string.ui_tone),
        trailing = {
            if (modified) {
                val snackbar = LocalSnackbar.current
                val snackScope = LocalSnackScope.current
                TextButton(onClick = {
                    Repo.commit()
                    Repo.editTone {
                        it.copy(
                            bassDb = 0.0,
                            bassHz = Tone.DEFAULT_BASS_HZ,
                            trebleDb = 0.0,
                            vocalsDb = 0.0,
                            bassTunerDb = 0.0,
                            bassCharacter = Tone().bassCharacter,
                        )
                    }
                    Repo.commit()
                    // This tab has no undo key of its own, so the reset offers one.
                    snackScope.offerUndo(snackbar, appText(Res.string.ui_tone_reset), undo = Repo::undo, redo = Repo::redo)
                }) { Text(stringResource(Res.string.ui_reset_tone)) }
            }
        },
    ) {
        ToneSlider(stringResource(Res.string.ui_bass), Help.bass, tone.bassDb, saved?.bassDb ?: 0.0) { v -> Repo.editTone { it.copy(bassDb = v) } }
        // Only means something once there's a bass change to place. They come and go half a second after the
        // bass settles, so sliding it through zero doesn't flick them in and out and jolt the page (tester feedback).
        val bassPlaced = rememberSettled(tone.bassDb != 0.0)
        val bassAdded = rememberSettled(tone.bassDb > 0.0)
        AnimatedVisibility(
            visible = bassPlaced && Disclosure.bassDetail(state),
            enter = expandVertically(tween(SCREEN_MS, easing = FastOutSlowInEasing)) + fadeIn(tween(SCREEN_MS)),
            exit = shrinkVertically(tween(SCREEN_MS, easing = FastOutSlowInEasing)) + fadeOut(tween(SCREEN_MS)),
        ) {
          Column {
            RowDivider()
            SliderSetting(
                title = stringResource(Res.string.ui_bass_frequency),
                tip = Help.bassFrequency,
                readout = formatHz(tone.bassHz),
                value = tone.bassHz.toFloat(),
                range = 30f..250f,
                default = Tone.DEFAULT_BASS_HZ.toFloat(),
                onChange = { v -> Repo.editTone { it.copy(bassHz = v.toDouble()) } },
                onFinished = Repo::commit,
                resetTo = (saved?.bassHz ?: Tone.DEFAULT_BASS_HZ).toFloat(),
            )
          }
        }
        // Only shapes bass that's been added, so it only shows then.
        AnimatedVisibility(
            visible = bassAdded && Disclosure.bassDetail(state),
            enter = expandVertically(tween(SCREEN_MS, easing = FastOutSlowInEasing)) + fadeIn(tween(SCREEN_MS)),
            exit = shrinkVertically(tween(SCREEN_MS, easing = FastOutSlowInEasing)) + fadeOut(tween(SCREEN_MS)),
        ) {
          Column {
            RowDivider()
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.ui_bass_character), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                    InfoButton(Help.bassCharacter)
                }
                WordSegments(
                    labels = listOf(
                        stringResource(Res.string.ui_bass_natural),
                        stringResource(Res.string.ui_bass_punch),
                        stringResource(Res.string.ui_bass_sustain),
                    ),
                    selected = io.github.dragjon.eigentone.data.BassCharacter.entries.indexOf(tone.bassCharacter),
                    onSelect = { i ->
                        Repo.editTone { it.copy(bassCharacter = io.github.dragjon.eigentone.data.BassCharacter.entries[i]) }
                        Repo.commit()
                    },
                )
            }
          }
        }
        RowDivider()
        ToneSlider(stringResource(Res.string.help_vocals_title), Help.vocals, tone.vocalsDb, saved?.vocalsDb ?: 0.0) { v -> Repo.editTone { it.copy(vocalsDb = v) } }
        RowDivider()
        ToneSlider(stringResource(Res.string.ui_treble), Help.treble, tone.trebleDb, saved?.trebleDb ?: 0.0) { v -> Repo.editTone { it.copy(trebleDb = v) } }
        // Only on a sound from an earlier version that used both bass controls. Setting it to zero retires it.
        if (tone.bassTunerDb != 0.0) {
            RowDivider()
            ToneSlider(stringResource(Res.string.ui_extra_bass, formatHz(tone.bassTunerHz)), Help.extraBass, tone.bassTunerDb) { v ->
                Repo.editTone { it.copy(bassTunerDb = v) }
            }
        }
    }
}

@Composable
private fun ToneSlider(title: String, tip: Tip, value: Double, saved: Double = 0.0, onChange: (Double) -> Unit) {
    SliderSetting(
        title = title,
        tip = tip,
        readout = formatDb(value),
        value = value.toFloat(),
        range = toneRange,
        default = 0f,
        origin = 0f,
        onChange = { onChange(snapTone(it)) },
        onFinished = Repo::commit,
        resetTo = saved.toFloat(),
    )
}

@Composable
private fun SpaceSection(state: AppState, saved: Tone?) {
    val status by Repo.status.collectAsStateWithLifecycle()
    val systemSpatialAudio = rememberSystemSpatialAudio()
    val spaceParts = buildList {
        val surround = state.current.tone.surround
        if (surround > 0.0) {
            add(stringResource(if (state.useSystemSurround) Res.string.help_surround_title else Res.string.help_stereo_width_title) + " " + formatPercent(surround))
        }
        if (state.current.tone.reverb != ReverbRoom.OFF) add(stringResource(Res.string.ui_reverb) + " " + stringResource(reverbName(state.current.tone.reverb)).lowercase())
    }
    SettingsSection(stringResource(Res.string.ui_space), summary = foldSummary(spaceParts)) {
        SliderSetting(
            title = if (state.useSystemSurround) stringResource(Res.string.help_surround_title) else stringResource(Res.string.help_stereo_width_title),
            tip = if (state.useSystemSurround) Help.surround else Help.stereoWidth,
            readout = if (state.current.tone.surround <= 0.0) stringResource(Res.string.ui_mode_off) else formatPercent(state.current.tone.surround),
            value = state.current.tone.surround.toFloat(),
            range = 0f..1f,
            default = 0f,
            detail = when {
                state.useSystemSurround && status.spatialError != null -> status.spatialError
                state.current.tone.surround > 0.0 && systemSpatialAudio ->
                    stringResource(Res.string.ui_android_s_own_spatial_audio_is)
                !STUDIO && state.useSystemSurround -> stringResource(Res.string.ui_surround_from_settings)
                else -> null
            },
            onChange = { v -> Repo.editTone { it.copy(surround = if (v < 0.02f) 0.0 else v.toDouble()) } },
            onFinished = Repo::commit,
            resetTo = (saved?.surround ?: 0.0).toFloat(),
        )
        RowDivider()
        ReverbRow(state, status, saved)
    }
}

/** Stereo settings, which belong to the phone rather than to a preset. */
@Composable
private fun StereoSection(state: AppState) {
    val parts = buildList {
        if (kotlin.math.abs(state.balance) > 0.005) add(stringResource(Res.string.ui_balance))
    }
    SettingsSection(stringResource(Res.string.ui_stereo), summary = foldSummary(parts)) {
        // Android can hand surround to the phone's own virtualiser. Windows has none: Eigentone's plug-in does
        // the surround itself, always, so there's no choice to offer here.
        SliderSetting(
            title = stringResource(Res.string.ui_balance),
            tip = Help.balance,
            readout = when {
                state.balance < -0.005 -> stringResource(Res.string.ui_balance_left, formatPercent(-state.balance))
                state.balance > 0.005 -> stringResource(Res.string.ui_balance_right, formatPercent(state.balance))
                else -> stringResource(Res.string.ui_centre)
            },
            value = state.balance.toFloat(),
            range = -1f..1f,
            default = 0f,
            origin = 0f,
            onChange = { v -> Repo.update { it.copy(balance = v.toDouble()) } },
        )
    }
}

/** Says which apps it really reaches, because Android only allows a reverb on apps that announce their audio. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReverbRow(state: AppState, @Suppress("UNUSED_PARAMETER") status: EngineStatus, saved: Tone?) {
    val chosen = state.current.tone.reverb
    // A compact choice, as a studio's panels offer. On Windows the reverb reaches every app, so the Android
    // note about which apps announce their audio has no place here.
    DropdownSetting(
        title = stringResource(Res.string.ui_reverb),
        tip = Help.reverb,
        options = ReverbRoom.entries.map { stringResource(reverbName(it)) },
        selected = ReverbRoom.entries.indexOf(chosen),
        onSelect = { i ->
            Repo.editTone { it.copy(reverb = ReverbRoom.entries[i]) }
            Repo.commit()
        },
    )
    // Inset like every other slider, rather than twice over inside the chips' column.
    if (chosen != ReverbRoom.OFF) {
        RowDivider()
        SliderSetting(
            title = stringResource(Res.string.ui_reverb_amount),
            tip = Help.reverbAmount,
            readout = formatPercent(state.current.tone.reverbAmount),
            value = state.current.tone.reverbAmount.toFloat(),
            range = 0f..1f,
            default = 0.6f,
            onChange = { v -> Repo.editTone { it.copy(reverbAmount = v.toDouble()) } },
            onFinished = Repo::commit,
            resetTo = (saved?.takeIf { it.reverb != ReverbRoom.OFF }?.reverbAmount ?: 0.6).toFloat(),
        )
    }
}

private fun reverbName(room: ReverbRoom): StringResource = when (room) {
    ReverbRoom.OFF -> Res.string.ui_mode_off
    ReverbRoom.SMALL_ROOM -> Res.string.ui_reverb_small_room
    ReverbRoom.MEDIUM_ROOM -> Res.string.ui_reverb_medium_room
    ReverbRoom.LARGE_ROOM -> Res.string.ui_reverb_large_room
    ReverbRoom.MEDIUM_HALL -> Res.string.ui_reverb_medium_hall
    ReverbRoom.LARGE_HALL -> Res.string.ui_reverb_large_hall
    ReverbRoom.PLATE -> Res.string.ui_reverb_plate
}

/** This output's correction, or the offer to make one from its latest hearing check. */
@Composable
private fun EarCorrectionRows(state: AppState) {
    val output by Repo.output.collectAsStateWithLifecycle()
    val key = output.key
    val ears = state.earsFor(key)
    val record = state.hearings[key]
    val measured = remember(record) { record?.result() }
    fun setEars(new: io.github.dragjon.eigentone.data.EarCorrection?) = Repo.editDevice {
        it.copy(earCorrections = if (new == null) it.earCorrections - key else it.earCorrections + (key to new))
    }
    val snackbar = LocalSnackbar.current
    val snackScope = LocalSnackScope.current

    /** Replacing or removing a correction acts at once, so it can be taken back for a moment. */
    fun replaceEars(new: io.github.dragjon.eigentone.data.EarCorrection?, message: String) {
        val old = state.earCorrections[key]
        setEars(new)
        snackScope.offerUndo(snackbar, message, undo = { setEars(old) }, redo = { setEars(new) })
    }

    if (Profiles.supportsModes(key)) {
        if (ears != null) {
            RowDivider()
            SwitchSetting(stringResource(Res.string.ui_ear_correction), Help.earCorrection, ears.enabled, detail = stringResource(Res.string.ui_a_separate_eq_for_each_ear, output.label)) { on ->
                setEars(ears.copy(enabled = on))
            }
            EarCurvesGraph(ears, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), markers = true)
            // The latest check is always shown, so a new one replaces the old graph even with a correction in use.
            if (measured != null && record != null) {
                val latest = remember(measured) { measured.earCorrection() }
                val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(record.measuredAt))
                RowDivider()
                HearingGraph(measured, Modifier.padding(16.dp))
                if (latest.hasChanges && (latest.left != ears.left || latest.right != ears.right)) {
                    RowDivider()
                    ActionRow(
                        stringResource(Res.string.ui_use_latest_check),
                        Help.earCorrection,
                        detail = stringResource(Res.string.ui_correction_from_check_on, date),
                        opens = false,
                    ) { replaceEars(latest.copy(enabled = ears.enabled), appText(Res.string.ui_ear_correction_updated)) }
                }
            }
            RowDivider()
            ActionRow(stringResource(Res.string.ui_remove_ear_correction), Help.earCorrection, opens = false, danger = true) {
                replaceEars(null, appText(Res.string.ui_ear_correction_removed))
            }
        } else if (measured != null && record != null) {
            // Offered until it's used, so leaving the hearing check without choosing doesn't lose it.
            val correction = remember(measured) { measured.earCorrection() }
            val recommended = measured.earCorrectionIsRecommended()
            val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(record.measuredAt))
            RowDivider()
            HearingGraph(measured, Modifier.padding(16.dp))
            RowDivider()
            if (correction.hasChanges) {
                ActionRow(
                    if (recommended) stringResource(Res.string.ui_correct_each_ear) else stringResource(Res.string.ui_correct_each_ear_anyway),
                    Help.earCorrection,
                    detail = if (recommended) {
                        stringResource(Res.string.ui_for_from_your_hearing_check_on, output.label, date)
                    } else {
                        stringResource(Res.string.ui_for_from_your_check_on, output.label, date, earsCloseNote(measured))
                    },
                    opens = false,
                ) { setEars(correction) }
            } else {
                Text(
                    earsCloseNote(measured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
    val others = (state.earCorrections.keys - key).map { state.hearings[it]?.headphones ?: it.substringAfter(':') }
    if (others.isNotEmpty()) {
        RowDivider()
        Text(
            stringResource(Res.string.ui_ear_correction_is_also_saved_for, others.joinToString()),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextDim,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun DynamicsSection(state: AppState, attenuation: Double) {
    val scheduledNight by Repo.scheduledNight.collectAsStateWithLifecycle()
    val volumeParts = buildList {
        if (state.loudness) add(stringResource(Res.string.ui_adapt_to_volume))
        if (state.nightMode > 0.001) add(stringResource(Res.string.ui_night_mode) + " " + formatPercent(state.nightMode))
        if (state.leveller > 0.001) add(stringResource(Res.string.ui_volume_leveller) + " " + formatPercent(state.leveller))
        if (state.nightSchedule.enabled) add(stringResource(Res.string.ui_night_mode_schedule))
    }
    SettingsSection(stringResource(Res.string.ui_dynamics), summary = foldSummary(volumeParts)) {
        SwitchSetting(
            stringResource(Res.string.ui_adapt_to_volume),
            Help.loudness,
            state.loudness,
            detail = if (state.loudness) stringResource(Res.string.ui_loudness_tuned_per_output) else null,
        ) { on ->
            Repo.update { it.copy(loudness = on) }
        }
        RowDivider()
        SliderSetting(
            title = stringResource(Res.string.ui_night_mode),
            tip = Help.nightMode,
            readout = if (state.nightMode <= 0.001) stringResource(Res.string.ui_mode_off) else formatPercent(state.nightMode),
            value = state.nightMode.toFloat(),
            range = 0f..1f,
            default = 0f,
            onChange = { v -> Repo.update { it.copy(nightMode = v.toDouble()) } },
        )
        RowDivider()
        SliderSetting(
            title = stringResource(Res.string.ui_volume_leveller),
            tip = Help.leveller,
            readout = if (state.leveller <= 0.001) stringResource(Res.string.ui_mode_off) else formatPercent(state.leveller),
            value = state.leveller.toFloat(),
            range = 0f..1f,
            default = 0f,
            detail = when {
                state.leveller <= 0.001 -> null
                state.nightMode > 0.001 || scheduledNight -> stringResource(Res.string.ui_night_mode_is_on_so_it)
                state.current.tone.compressor.enabled -> stringResource(Res.string.ui_this_preset_s_compressor_is_on)
                else -> null
            },
            onChange = { v -> Repo.update { it.copy(leveller = if (v < 0.02f) 0.0 else v.toDouble()) } },
        )
        RowDivider()
        val schedule = state.nightSchedule
        fun updateSchedule(change: (io.github.dragjon.eigentone.data.NightSchedule) -> io.github.dragjon.eigentone.data.NightSchedule) =
            Repo.update { it.copy(nightSchedule = change(it.nightSchedule)) }
        SwitchSetting(stringResource(Res.string.ui_night_mode_schedule), Help.nightSchedule, schedule.enabled) { on -> updateSchedule { it.copy(enabled = on) } }
        if (schedule.enabled) {
            TimeSetting(stringResource(Res.string.help_night_schedule_start_title), Help.nightScheduleStart, schedule.startMinute) { m -> updateSchedule { it.copy(startMinute = m) } }
            TimeSetting(stringResource(Res.string.help_night_schedule_end_title), Help.nightScheduleEnd, schedule.endMinute) { m -> updateSchedule { it.copy(endMinute = m) } }
            if (schedule.startMinute == schedule.endMinute) {
                Text(
                    stringResource(Res.string.ui_the_start_and_end_are_the_same_so_night),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            SliderSetting(
                title = stringResource(Res.string.ui_scheduled_strength),
                tip = Help.nightScheduleAmount,
                readout = formatPercent(schedule.amount),
                value = schedule.amount.toFloat(),
                range = 0.1f..1f,
                default = 0.6f,
                onChange = { v -> updateSchedule { it.copy(amount = v.toDouble()) } },
            )
        }
    }
}

/**
 * Equal loudness: what's being added right now, drawn live, and the usual volume it works from, which each
 * pair of headphones keeps separately because they play at different levels.
 */
@Composable
private fun EqualLoudnessRows(state: AppState, attenuation: Double) {
    val output by Repo.output.collectAsStateWithLifecycle()
    val usual = state.usualVolumeDb(output.key)
    val quieter = EqualLoudness.quieter(attenuation, usual)
    val strength = state.loudnessStrengthFor(output.key)
    val added = remember(quieter, strength) { EqualLoudness.correction(quieter, strength) }
    fun at(hz: Double) = if (added.isEmpty()) 0.0 else GraphicEq.interpolate(added.map { it.hz to it.db }, hz)
    fun setUsual(db: Double) = Repo.editDevice { it.copy(loudnessUsual = it.loudnessUsual + (output.key to db.coerceIn(0.0, USUAL_MAX_DB))) }

    LoudnessGraph(added, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp))
    Text(
        if (added.isEmpty()) {
            stringResource(Res.string.ui_loudness_none)
        } else {
            stringResource(Res.string.ui_loudness_now, formatAmount(quieter), formatDb(at(LOUDNESS_LOW_HZ)), formatDb(at(LOUDNESS_HIGH_HZ)))
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (added.isEmpty()) Palette.TextDim else Palette.Text,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    RowDivider()
    SliderSetting(
        title = stringResource(Res.string.ui_usual_volume),
        tip = Help.usualVolume,
        readout = stringResource(Res.string.ui_db_below_full, formatAmount(usual)),
        value = usual.toFloat(),
        range = 0f..USUAL_MAX_DB.toFloat(),
        default = EqualLoudness.DEFAULT_USUAL_DB.toFloat(),
        detail = stringResource(Res.string.ui_usual_volume_for, output.label, formatAmount(attenuation)),
        onChange = { v -> setUsual(kotlin.math.round(v * 2.0) / 2.0) },
    )
    // A row like the others, and only usable when it would change something.
    RowDivider()
    ActionRow(
        stringResource(Res.string.ui_use_current_volume),
        Help.usualVolume,
        detail = stringResource(Res.string.ui_db_below_full, formatAmount(attenuation)),
        enabled = kotlin.math.abs(usual - attenuation) > 0.25,
        opens = false,
    ) { setUsual(kotlin.math.round(attenuation * 2.0) / 2.0) }
    RowDivider()
    SliderSetting(
        title = stringResource(Res.string.ui_strength),
        tip = Help.loudnessStrength,
        readout = formatPercent(strength),
        value = strength.toFloat(),
        range = 0f..1f,
        default = 1f,
        detail = stringResource(Res.string.ui_strength_for, output.label),
        onChange = { v -> Repo.editDevice { it.copy(loudnessStrengths = it.loudnessStrengths + (output.key to kotlin.math.round(v * 20.0) / 20.0)) } },
    )
}

/** The correction being added right now, from 20 Hz to 20 kHz, on a 0 to 15 dB scale. */
@Composable
private fun LoudnessGraph(points: List<io.github.dragjon.eigentone.data.ShapePoint>, modifier: Modifier) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(color = Palette.TextDim, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), fontFamily = ReadoutFamily)
    androidx.compose.foundation.Canvas(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(PanelShape)
            .background(Palette.SurfaceHigh)
            .semantics { contentDescription = appText(Res.string.ui_loudness_graph) },
    ) {
        val pad = 6.dp.toPx()
        val h = size.height - pad * 2
        fun x(hz: Double) = (Eq.logPosition(hz) * size.width).toFloat()
        fun y(db: Double) = (pad + (1 - (db + LOUDNESS_GRAPH_FLOOR) / (EqualLoudness.MAX_BOOST_DB + LOUDNESS_GRAPH_FLOOR)) * h).toFloat()
        for (hz in listOf(100.0, 1000.0, 10000.0)) {
            drawLine(Palette.Line, androidx.compose.ui.geometry.Offset(x(hz), 0f), androidx.compose.ui.geometry.Offset(x(hz), size.height), strokeWidth = 1f)
        }
        drawLine(Palette.Outline, androidx.compose.ui.geometry.Offset(0f, y(0.0)), androidx.compose.ui.geometry.Offset(size.width, y(0.0)), strokeWidth = 1f)
        val curve = androidx.compose.ui.graphics.Path()
        val samples = 64
        for (i in 0..samples) {
            val hz = Eq.logFreq(i.toDouble() / samples)
            val db = if (points.isEmpty()) 0.0 else GraphicEq.interpolate(points.map { it.hz to it.db }, hz)
            if (i == 0) curve.moveTo(x(hz), y(db)) else curve.lineTo(x(hz), y(db))
        }
        drawPath(curve, if (points.isEmpty()) Palette.TextDim else Palette.Lit, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        val top = measurer.measure("+${EqualLoudness.MAX_BOOST_DB.toInt()}", labelStyle)
        drawText(top, topLeft = androidx.compose.ui.geometry.Offset(4.dp.toPx(), y(EqualLoudness.MAX_BOOST_DB)))
        val zero = measurer.measure("0", labelStyle)
        drawText(zero, topLeft = androidx.compose.ui.geometry.Offset(4.dp.toPx(), y(0.0) - zero.size.height))
    }
}

private const val USUAL_MAX_DB = 45.0
private const val LOUDNESS_LOW_HZ = 50.0
private const val LOUDNESS_HIGH_HZ = 10000.0
private const val LOUDNESS_GRAPH_FLOOR = 3.0

private fun formatAmount(db: Double): String = String.format(Locale.US, "%.0f", db)

/** One-tap limiter settings. Each is a designed starting point, adjustable afterwards. */
private enum class LimiterStyle(val label: StringResource, val settings: LimiterSettings) {
    SAFE(Res.string.ui_limiter_safe, LimiterSettings()),
    TRANSPARENT(Res.string.ui_limiter_transparent, LimiterSettings(attackMs = 5.0, releaseMs = 250.0, ratio = 10.0, thresholdDb = -1.0)),
    BRICKWALL(Res.string.ui_limiter_brickwall, LimiterSettings(attackMs = 0.5, releaseMs = 40.0, ratio = 50.0, thresholdDb = -1.0)),
    LOUD(Res.string.ui_limiter_loud, LimiterSettings(attackMs = 1.0, releaseMs = 80.0, ratio = 20.0, thresholdDb = -6.0, postGainDb = 5.0)),
}

private fun AppState.withLimiter(l: LimiterSettings) = copy(
    limiterAttackMs = l.attackMs,
    limiterReleaseMs = l.releaseMs,
    limiterRatio = l.ratio,
    limiterThresholdDb = l.thresholdDb,
    limiterPostGainDb = l.postGainDb,
)

/** Styles, and the timing and gain controls tucked away until wanted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimiterDetails(state: AppState) {
    val current = state.limiterSettings()
    var open by rememberSaveable { mutableStateOf(false) }
    FlowRow(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LimiterStyle.entries.forEach { style ->
            FilterChip(
                selected = current == style.settings.clamped(),
                onClick = { Repo.update { it.withLimiter(style.settings) } },
                label = { Text(stringResource(style.label), maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Palette.LitFill,
                    selectedLabelColor = Palette.OnLit,
                    labelColor = Palette.Text,
                ),
            )
        }
    }
    Text(
        stringResource(Res.string.ui_limiter_ceiling, formatDb(current.ceilingDb)),
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextDim,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    DisclosureRow(stringResource(Res.string.ui_limiter_more), open) { open = !open }
    if (!open) return
    SliderSetting(
        title = stringResource(Res.string.ui_limiter_attack),
        tip = Help.limiterAttack,
        readout = String.format(Locale.US, "%.1f ms", current.attackMs),
        value = current.attackMs.toFloat(),
        range = 0.1f..50f,
        default = 1f,
        onChange = { v -> Repo.update { it.copy(limiterAttackMs = kotlin.math.round(v * 10.0) / 10.0) } },
    )
    SliderSetting(
        title = stringResource(Res.string.ui_limiter_release),
        tip = Help.limiterRelease,
        readout = String.format(Locale.US, "%.0f ms", current.releaseMs),
        value = current.releaseMs.toFloat(),
        range = 10f..1000f,
        default = 60f,
        onChange = { v -> Repo.update { it.copy(limiterReleaseMs = kotlin.math.round(v).toDouble()) } },
    )
    SliderSetting(
        title = stringResource(Res.string.ui_limiter_ratio),
        tip = Help.limiterRatio,
        readout = String.format(Locale.US, "%.0f : 1", current.ratio),
        value = current.ratio.toFloat(),
        range = 1f..50f,
        default = 10f,
        onChange = { v -> Repo.update { it.copy(limiterRatio = kotlin.math.round(v).toDouble()) } },
    )
    SliderSetting(
        title = stringResource(Res.string.ui_limiter_post_gain),
        tip = Help.limiterPostGain,
        readout = formatDb(current.postGainDb),
        value = current.postGainDb.toFloat(),
        range = 0f..12f,
        default = 0f,
        detail = if (state.limiterPostGainDb > current.postGainDb + 0.01) stringResource(Res.string.ui_limiter_post_gain_capped) else null,
        onChange = { v -> Repo.update { it.copy(limiterPostGainDb = snapTone(v)) } },
    )
}

/** Opens a clock, which follows the phone's 12 or 24 hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSetting(title: String, tip: Tip, minute: Int, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    // Follows the clock format in Windows' region settings, as Android follows the phone's.
    val is24Hour = remember {
        (java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) as? java.text.SimpleDateFormat)?.toPattern()?.contains('H') ?: true
    }
    var picking by remember { mutableStateOf(false) }
    ActionRow(title, tip, detail = NightSchedule.formatTime(minute, is24Hour)) { picking = true }
    if (picking) {
        val clock = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = is24Hour)
        MotionDialog(
            onDismissRequest = { picking = false },
            containerColor = Palette.Tooltip,
            title = { Text(title) },
            text = { TimePicker(state = clock) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(clock.hour * 60 + clock.minute)
                    picking = false
                }) { Text(stringResource(Res.string.ui_set)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(Res.string.ui_cancel)) } },
        )
    }
}

/** Says when these settings could push peaks over, which they can't do while automatic headroom has room. */
@Composable
private fun ClipWarning(state: AppState, attenuation: Double) {
    val output by Repo.output.collectAsStateWithLifecycle()
    val scheduledNight by Repo.scheduledNight.collectAsStateWithLifecycle()
    val estimate = remember(state, attenuation, output.key, scheduledNight) {
        Processing.peakEstimate(state, attenuation, output.key, scheduledNight)
    }
    if (estimate.risk == ClipRisk.NONE) return
    val peak = formatDb(estimate.peakDb)
    Text(
        if (estimate.risk == ClipRisk.CLIPPING) {
            stringResource(Res.string.ui_loud_music_could_reach_which_can, peak)
        } else {
            stringResource(Res.string.ui_loud_music_could_reach_the_limiter, peak)
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (estimate.risk == ClipRisk.CLIPPING) Palette.Danger else Palette.TextDim,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
}

@Composable
private fun LevelSection(state: AppState, attenuation: Double) {
    val effective = Processing.effectiveFilters(state, attenuation, Repo.output.value.key)
    val input = Processing.inputOf(state.current, effective, state.autoHeadroom)
    val levelParts = buildList {
        if (!state.autoHeadroom) add(stringResource(Res.string.ui_automatic_headroom) + " " + stringResource(Res.string.ui_mode_off).lowercase())
        add(
            if (state.limiter) stringResource(Res.string.ui_limiter) + " " + formatDb(state.limiterSettings().ceilingDb)
            else stringResource(Res.string.ui_limiter) + " " + stringResource(Res.string.ui_mode_off).lowercase(),
        )
    }
    SettingsSection(stringResource(Res.string.ui_level), summary = foldSummary(levelParts)) {
        SwitchSetting(
            stringResource(Res.string.ui_automatic_headroom),
            Help.headroom,
            state.autoHeadroom,
            detail = stringResource(Res.string.ui_level_going_into_the_eq, formatDb(input)),
        ) { on -> Repo.update { it.copy(autoHeadroom = on) } }
        RowDivider()
        ClipWarning(state, attenuation)
        SwitchSetting(stringResource(Res.string.ui_limiter), Help.limiter, state.limiter) { on -> Repo.update { it.copy(limiter = on) } }
        if (state.limiter) {
            SliderSetting(
                title = stringResource(Res.string.ui_limiter_threshold),
                tip = Help.limiterThreshold,
                readout = formatDb(state.limiterThresholdDb),
                value = state.limiterThresholdDb.toFloat(),
                range = -12f..0f,
                default = -1f,
                origin = 0f,
                onChange = { v -> Repo.update { it.copy(limiterThresholdDb = snapTone(v)) } },
            )
            if (Disclosure.limiterDetail(state)) LimiterDetails(state)
        }
        if (Disclosure.matchBypass(state)) {
            RowDivider()
            SwitchSetting(stringResource(Res.string.ui_level_matched_bypass), Help.matchBypass, state.matchBypass) { on ->
                Repo.update { it.copy(matchBypass = on) }
            }
        }
    }
}

/** Shows a message through the screen's snackbar. */
/** Why something failed, in words, never "null" when an error carries no message. */
private fun reason(error: Throwable?): String = error?.message ?: error?.javaClass?.simpleName ?: "unknown error"



/**
 * Everything about what's connected: its correction, the preset it switches to, your ears, and how it adapts
 * to quiet listening. Each output keeps its own, so this page follows whatever is plugged in.
 */
@Composable
private fun HeadphonesTab(state: AppState) {
    val scope = LocalSnackScope.current
    val snackbar = LocalSnackbar.current
    val output by Repo.output.collectAsStateWithLifecycle()
    val attenuation by Repo.attenuationDb.collectAsStateWithLifecycle()
    var finding by remember { mutableStateOf(false) }
    var tuning by remember { mutableStateOf(false) }
    var measuringRoom by remember { mutableStateOf(false) }
    val preset = state.current
    val saved = savedPreset(state)?.tone

    val kind = output.kind
    val hasCorrection = preset.filters.any { it.gainDb != 0.0 || !it.type.hasGain } || preset.tone.importedCurve.isNotEmpty()

    Row(Modifier.fullSpan().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(kind.icon()), contentDescription = null, tint = Palette.Lit, modifier = Modifier.padding(end = 12.dp).size(28.dp))
        Column {
            Text(output.label, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(
                    when (kind) {
                        OutputKind.PHONE -> Res.string.ui_device_scope_phone
                        OutputKind.SPEAKER -> Res.string.ui_device_scope_speaker
                        OutputKind.CAR -> Res.string.ui_device_scope_car
                        else -> Res.string.ui_hp_scope
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
            )
        }
    }

    // Headphone corrections are measured on heads, so finding one only makes sense for something worn.
    // Anything else still shows the strength of a correction its preset already has.
    // A speaker or a car can be measured where you listen. The phone's own speaker can't, since its
    // microphone sits right beside it and would hear the phone rather than the room.
    val canMeasure = kind == OutputKind.SPEAKER || kind == OutputKind.CAR
    if (kind.isWorn || hasCorrection || canMeasure) SettingsSection(stringResource(Res.string.ui_hp_correction)) {
        if (kind.isWorn) {
            ActionRow(stringResource(if (kind == OutputKind.EARBUDS) Res.string.ui_find_earbuds else Res.string.ui_find_headphones), Help.find) { finding = true }
        }
        if (canMeasure) {
            ActionRow(stringResource(Res.string.ui_measure_action), Help.measure, detail = stringResource(Res.string.ui_measure_action_detail)) { measuringRoom = true }
        }
        if (hasCorrection) {
            if (kind.isWorn || canMeasure) RowDivider()
            SliderSetting(
                title = stringResource(if (kind.isWorn) Res.string.ui_headphone_correction else Res.string.ui_hp_correction),
                tip = Help.correction,
                readout = if (preset.tone.correction <= 0.0) stringResource(Res.string.ui_mode_off) else formatPercent(preset.tone.correction),
                value = preset.tone.correction.toFloat(),
                range = 0f..1f,
                default = 1f,
                detail = stringResource(Res.string.ui_saved_with, preset.name),
                onChange = { v -> Repo.editTone { it.copy(correction = kotlin.math.round(v * 20.0) / 20.0) } },
                onFinished = Repo::commit,
                resetTo = (saved?.correction ?: 1.0).toFloat(),
            )
        }
        if (preset.filters.any { it.gainDb != 0.0 } || preset.tone.importedCurve.isNotEmpty()) {
            RowDivider()
            ActionRow(stringResource(Res.string.ui_tune_correction), Help.tuner, detail = stringResource(Res.string.ui_use_less_of_it_or_add_bass_or_treble_bac)) { tuning = true }
        }
    }

    OutputSection(state, output.key, output.label)

    SettingsSection(stringResource(Res.string.ui_your_ears)) {
        if (kind.isWorn) {
            ActionRow(stringResource(Res.string.ui_hearing_check), Help.hearing, detail = stringResource(Res.string.ui_builds_a_curve_for_how_you_hear), onClick = LocalOpenHearing.current)
            EarCorrectionRows(state)
        } else {
            // A speaker reaches both ears at once, and the room changes what each one hears.
            Text(
                stringResource(Res.string.ui_ears_need_headphones, output.label),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextDim,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    SettingsSection(stringResource(Res.string.ui_quiet_listening)) {
        if (state.loudness) {
            EqualLoudnessRows(state, attenuation)
        } else {
            // The switch itself, rather than directions to it in a folded section of Settings.
            SwitchSetting(
                stringResource(Res.string.ui_adapt_to_volume),
                Help.loudness,
                false,
                detail = stringResource(Res.string.ui_quiet_turn_on, output.label),
            ) { on -> Repo.editDevice { it.copy(loudness = on) } }
        }
    }

    SettingsSection(stringResource(Res.string.ui_listening_tools)) {
        ActionRow(stringResource(Res.string.ui_find_my_sound), Help.finder, detail = stringResource(Res.string.ui_a_short_guided_listening_test), onClick = LocalOpenFinder.current)
        RowDivider()
        ActionRow(stringResource(Res.string.ui_refine), Help.refine, detail = stringResource(Res.string.ui_refine_detail), onClick = LocalOpenRefine.current)
        // Both play tones into each ear and ask what you hear, so they're for headphones and earbuds.
        // A speaker's peaks are mostly the room's, which the microphone measurement above handles.
        if (kind.isWorn && Disclosure.measureTools(state)) {
            RowDivider()
            ActionRow(stringResource(Res.string.ui_find_peaks_for_me), Help.peakFinder, detail = stringResource(Res.string.ui_a_guided_test_that_finds_peaks_and_dips), onClick = LocalOpenPeaks.current)
            RowDivider()
            ActionRow(stringResource(Res.string.ui_frequency_sweep), Help.sweep, detail = stringResource(Res.string.ui_find_and_fix_peaks_in_your_headphones_by), onClick = LocalOpenSweep.current)
        }
    }

    if (finding) {
        AutoEqDialog(onDismiss = { finding = false }) { result ->
            scope.notify(snackbar, result)
            finding = false
            // A correction is the moment people find it too neutral, so offer this straight away, once the
            // search has finished closing rather than over it.
            scope.launch {
                kotlinx.coroutines.delay(DIALOG_GAP_MS)
                tuning = true
            }
        }
    }
    if (tuning) CorrectionTuner(onDismiss = { tuning = false })
    if (measuringRoom) {
        RoomMeasureDialog(output, onDismiss = { measuringRoom = false }) { name ->
            measuringRoom = false
            scope.notify(snackbar, appText(Res.string.ui_added, name))
        }
    }
}

@Composable
private fun PresetsTab(state: AppState) {
    PresetListSection(state)
    if (Disclosure.perApp(state)) PerAppSection(state)
    ManageSection(state)
}

@Composable
private fun OutputSection(state: AppState, outputKey: String, outputLabel: String) {
    val supportsModes = Profiles.supportsModes(outputKey)
    val assignKey = if (supportsModes) Profiles.modeKey(outputKey, state.listeningMode) else outputKey
    val assigned = Profiles.presetIdFor(state, outputKey)?.let(Repo::findPreset)
    val correction = BuiltIns.correctionFor(outputKey)
    val canAssign = Repo.findPreset(state.current.id) != null
    val alreadyAssigned = assigned?.id == state.current.id

    SettingsSection(stringResource(Res.string.ui_hp_preset)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.ui_assigned_preset), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                InfoButton(Help.outputPreset)
            }
            Text(
                assigned?.name ?: stringResource(Res.string.ui_none),
                style = MaterialTheme.typography.bodyMedium,
                color = if (assigned != null) Palette.Lit else Palette.TextDim,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            if (assigned == null && correction != null) {
                Button(
                    onClick = {
                        Repo.assignToOutput(outputKey, correction.id, draft = true)
                        Repo.loadPreset(correction)
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                ) { Text(stringResource(Res.string.ui_use_correction, correction.name)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (alreadyAssigned) {
                    // Already done, so said as a fact rather than shown as a button that does nothing (tester feedback).
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.Lit, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(Res.string.ui_current_preset_assigned),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else FilledTonalButton(
                    enabled = canAssign,
                    onClick = { Repo.assignToOutput(assignKey, state.current.id, draft = true) },
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        stringResource(Res.string.ui_assign, state.current.name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (assigned != null) {
                    // Clears the mode's own choice first, then the output's main one.
                    val clearKey = if (assignKey in state.outputProfiles) assignKey else outputKey
                    TextButton(onClick = { Repo.assignToOutput(clearKey, null, draft = true) }) { Text(stringResource(Res.string.ui_clear)) }
                }
            }
        }
        if (ANDROID_ONLY && supportsModes) {
            RowDivider()
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.ui_earbud_noise_mode), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                    InfoButton(Help.noiseMode)
                }
                val modes = ListeningMode.entries
                WordSegments(
                    labels = modes.map { listeningModeLabel(it) },
                    selected = modes.indexOf(state.listeningMode),
                    onSelect = { Repo.setListeningMode(modes[it]) },
                )
            }
        }
        RowDivider()
        SwitchSetting(stringResource(Res.string.ui_switch_automatically), Help.autoSwitch, state.autoSwitch) { on ->
            Repo.editDevice { it.copy(autoSwitch = on) }
        }
    }
}

@Composable
fun segmentColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = Palette.LitFill,
    activeContentColor = Palette.OnLit,
    activeBorderColor = Palette.LitFill,
    inactiveContainerColor = Palette.SurfaceHigh,
    inactiveContentColor = Palette.Text,
    inactiveBorderColor = Palette.Line,
)

@Composable
private fun PresetListSection(state: AppState) {
    val snackbar = LocalSnackbar.current
    val scope = LocalSnackScope.current
    val connected = rememberConnectedOutputNames()
    val editedIds by Repo.editedIds.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val allFolders = PresetOrganiser.folders(state.presets)
    fun shown(presets: List<Preset>) = presets.filter { PresetOrganiser.matches(it, query) }
    val (headphones, general) = shown(BuiltIns.availableFor(connected)).partition(BuiltIns::isForSpecificHeadphones)
    val own = shown(state.presets)

    // Only worth the space once there's a list to search.
    if (state.presets.size + general.size + headphones.size > SEARCH_FROM_PRESETS || query.isNotEmpty()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(Res.string.ui_search_presets)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            colors = fieldColors(unfocusedBorder = Palette.Track),
            trailingIcon = if (query.isEmpty()) null else ({
                IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.ui_clear_search)) }
            }),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    @Composable
    fun Group(title: String, presets: List<Preset>, deletable: Boolean, tip: Tip? = null) {
        if (presets.isEmpty()) return
        SettingsSection(title, trailing = tip?.let { { InfoButton(it) } }) {
            presets.forEachIndexed { i, preset ->
                if (i > 0) RowDivider()
                PresetRow(
                    preset = preset,
                    selected = preset.id == state.current.id,
                    // The list holds the saved version, so the one in use differs from it as soon as it's edited.
                    edited = preset.id in editedIds || (preset.id == state.current.id && preset != state.current),
                    favorite = preset.id in state.favorites,
                    folders = allFolders,
                    onDelete = if (deletable) {
                        {
                            val before = Repo.state.value
                            Repo.deletePreset(preset.id)
                            scope.offerUndo(
                                snackbar,
                                appText(Res.string.ui_deleted, preset.name),
                                undo = {
                                    Repo.update {
                                        it.copy(
                                            presets = before.presets,
                                            outputProfiles = before.outputProfiles,
                                            appProfiles = before.appProfiles,
                                            favorites = before.favorites,
                                        )
                                    }
                                },
                                redo = { Repo.deletePreset(preset.id) },
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }

    Group(stringResource(Res.string.ui_your_presets), own.filter { it.folder == null }, deletable = true, tip = Help.favorites)
    PresetOrganiser.folders(own).forEach { folder ->
        Group(folder, own.filter { it.folder == folder }, deletable = true)
    }
    Group(stringResource(Res.string.ui_liberty_4_nc_correction), headphones, deletable = false, tip = if (state.presets.isEmpty()) Help.favorites else null)
    Group(stringResource(Res.string.ui_general), general, deletable = false)
    if (query.isNotBlank() && own.isEmpty() && headphones.isEmpty() && general.isEmpty()) {
        Text(stringResource(Res.string.ui_no_presets_match_that), style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
    }
}

@Composable
private fun PresetRow(preset: Preset, selected: Boolean, edited: Boolean, favorite: Boolean, folders: List<String>, onDelete: (() -> Unit)?) {
    var renaming by remember { mutableStateOf(false) }
    if (renaming) {
        PresetDetailsDialog(preset, folders, onDismiss = { renaming = false })
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .clickable(role = Role.RadioButton) { Repo.loadPreset(preset) }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.CenterStart) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(Res.string.ui_selected), tint = Palette.Lit, modifier = Modifier.size(20.dp))
            }
        }
        // Padded so a second line keeps clear of the row's edges rather than pushing the name to the top.
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                preset.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) Palette.Lit else Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Edits from earlier that come back when this preset is chosen again.
            if (edited) {
                Text(
                    stringResource(Res.string.ui_has_unsaved_edits),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = { Repo.toggleFavorite(preset.id) }) {
            Icon(
                // Filled against outlined, so the state doesn't rely on colour alone.
                if (favorite) painterResource(Res.drawable.ic_star) else painterResource(Res.drawable.ic_star_border),
                contentDescription = if (favorite) stringResource(Res.string.ui_remove_from_favourites, preset.name) else stringResource(Res.string.ui_add_to_favourites, preset.name),
                tint = if (favorite) Palette.Lit else Palette.TextDim,
            )
        }
        if (onDelete != null) {
            IconButton(onClick = { renaming = true }) {
                Icon(painterResource(Res.drawable.ic_edit), contentDescription = stringResource(Res.string.ui_edit, preset.name), tint = Palette.TextDim)
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(Res.drawable.ic_delete), contentDescription = stringResource(Res.string.ui_delete, preset.name), tint = Palette.TextDim)
            }
        }
    }
}

@Composable
private fun ManageSection(state: AppState) {
    val context = LocalContext.current
    val scope = LocalSnackScope.current
    val snackbar = LocalSnackbar.current
    var saving by remember { mutableStateOf(false) }
    val saved = state.presets.firstOrNull { it.id == state.current.id }

    val importer = rememberOpenFile { uri: java.io.File? ->
        if (uri == null) return@rememberOpenFile
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = uri.inputStream().use { s ->
                        // Real EQ files are tiny. The limit protects against opening the wrong file.
                        s.readAtMost(MAX_IMPORT_BYTES).decodeToString()
                    }
                    val name = uri.name
                    Triple(AutoEqParser.parse(text), ShapeText.parseGraphicEq(text), name)
                }
            }
            val (parsed, curve, name) = result.getOrElse {
                notify(snackbar, appText(Res.string.ui_could_not_read_that_file, reason(it)))
                return@launch
            }
            val title = name?.substringBeforeLast('.')?.removeSuffix(" ParametricEQ")?.removeSuffix(" GraphicEQ")?.ifBlank { null }
            when {
                parsed != null -> {
                    Repo.importPreset(title ?: appText(Res.string.ui_imported_eq), parsed)
                    notify(snackbar, appText(Res.string.ui_imported_filters, parsed.filters.size))
                }
                curve != null -> {
                    Repo.importCurve(title ?: appText(Res.string.ui_imported_eq), curve)
                    notify(snackbar, appText(Res.string.ui_imported_a_curve_with_points, curve.size))
                }
                else -> notify(snackbar, appText(Res.string.ui_no_eq_found_choose_an_autoeq))
            }
        }
    }
    val exporter = rememberSaveFile { uri: java.io.File? ->
        if (uri == null) return@rememberSaveFile
        val text = BackupCodec.encode(Repo.state.value)
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching { uri.outputStream().use { it.write(text.encodeToByteArray()) } }
            }
            notify(snackbar, if (written.isSuccess) appText(Res.string.ui_backup_saved) else appText(Res.string.ui_could_not_save_backup, reason(written.exceptionOrNull())))
        }
    }
    val eqExporter = rememberSaveFile { uri: java.io.File? ->
        if (uri == null) return@rememberSaveFile
        val text = ShapeText.graphicEq(Processing.exportCurve(Repo.state.value.current))
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching { uri.outputStream().use { it.write(text.encodeToByteArray()) } }
            }
            notify(snackbar, if (written.isSuccess) appText(Res.string.ui_eq_file_saved) else appText(Res.string.ui_could_not_save_file, reason(written.exceptionOrNull())))
        }
    }
    val parametricExporter = rememberSaveFile { uri: java.io.File? ->
        if (uri == null) return@rememberSaveFile
        val preset = Repo.state.value.current
        val filters = Processing.exportFilters(preset) ?: return@rememberSaveFile
        val text = AutoEqParser.format(Processing.preampOf(preset), filters)
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching { uri.outputStream().use { it.write(text.encodeToByteArray()) } }
            }
            notify(snackbar, if (written.isSuccess) appText(Res.string.ui_eq_file_saved) else appText(Res.string.ui_could_not_save_file, reason(written.exceptionOrNull())))
        }
    }
    val restorer = rememberOpenFile { uri: java.io.File? ->
        if (uri == null) return@rememberOpenFile
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    uri.inputStream().use { it.readAtMost(MAX_BACKUP_BYTES).decodeToString() }
                }.getOrNull()
            }
            // A restore replaces presets with the same names, so it can be taken back for a while.
            val before = Repo.state.value
            when {
                text == null -> notify(snackbar, appText(Res.string.ui_could_not_read_that_file_2))
                Repo.restoreBackup(text) -> {
                    val after = Repo.state.value
                    offerUndo(snackbar, appText(Res.string.ui_backup_restored), undo = { Repo.update { before } }, redo = { Repo.update { after } })
                }
                else -> notify(snackbar, appText(Res.string.ui_that_isn_t_an_eigentone_backup))
            }
        }
    }

    SettingsSection(stringResource(Res.string.ui_this_preset)) {
        ActionRow(stringResource(Res.string.ui_save_as_new), Help.saveNew) { saving = true }
        ShareRows { message -> scope.notify(snackbar, message) }
    }
    // Choosing between presets, by ear and by eye, side by side.
    SettingsSection(stringResource(Res.string.ui_compare_presets)) {
        ActionRow(stringResource(Res.string.ui_compare_two_sounds), Help.compare, detail = stringResource(Res.string.ui_switch_instantly_with_an_optional_blind), onClick = LocalOpenCompare.current)
        RowDivider()
        ActionRow(stringResource(Res.string.ui_compare_curves), Help.compareCurves, detail = stringResource(Res.string.ui_compare_curves_detail), onClick = LocalOpenCurves.current)
    }
    SettingsSection(stringResource(Res.string.ui_files_and_backup)) {
        ActionRow(stringResource(Res.string.ui_import_eq_file), Help.importFile) {
            importer.launch(arrayOf("text/*", "application/octet-stream"))
        }
        RowDivider()
        ActionRow(stringResource(Res.string.ui_export_eq_file), Help.exportFile) { eqExporter.launch("${state.current.name} GraphicEQ.txt") }
        RowDivider()
        val canExportFilters = remember(state.current) { Processing.exportFilters(state.current) != null }
        ActionRow(
            stringResource(Res.string.ui_export_filters_file),
            Help.exportParametric,
            detail = if (canExportFilters) null else stringResource(Res.string.ui_this_preset_uses_faders_or_a),
            enabled = canExportFilters,
        ) { parametricExporter.launch("${state.current.name} ParametricEQ.txt") }
        RowDivider()
        ActionRow(stringResource(Res.string.ui_back_up_presets), Help.backup) { exporter.launch("eigentone-backup.json") }
        RowDivider()
        ActionRow(stringResource(Res.string.ui_restore_presets), Help.restore) {
            restorer.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
        }
    }

    if (saving) {
        SaveDialog(initial = state.current.name, onDismiss = { saving = false }) { name ->
            Repo.saveCurrentAs(name)
            saving = false
        }
    }
}

/** Finds and imports a headphone profile from AutoEq's published results. */
@Composable
fun AutoEqDialog(onDismiss: () -> Unit, onImported: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<CatalogEntry>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var measurements by remember { mutableStateOf<List<CatalogEntry>?>(null) }
    // The connected headphones, found by their name, so the search opens on them. Kept above the steps below,
    // so coming back from comparing measurements still says where the search came from.
    val connected = rememberConnectedOutputNames()
    var detected by remember { mutableStateOf<Pair<String, CatalogEntry>?>(null) }
    // Chosen, but waiting for the search to finish closing, so the comparison opens after it rather than over it.
    var nextMeasurements by remember { mutableStateOf<List<CatalogEntry>?>(null) }
    nextMeasurements?.let { waiting ->
        LaunchedEffect(waiting) {
            kotlinx.coroutines.delay(DIALOG_GAP_MS)
            measurements = waiting
            nextMeasurements = null
        }
        return
    }

    measurements?.let { options ->
        MeasurementCompare(
            options,
            onDismiss = { measurements = null },
            onChosen = { message ->
                measurements = null
                onImported(message)
            },
        )
        return
    }

    // Runs again from Try again, so a failed download isn't a dead end.
    LaunchedEffect(attempt) {
        problem = null
        val loaded = withContext(Dispatchers.IO) { runCatching { AutoEqCatalog.loadIndex(context.cacheDir) } }
        entries = loaded.getOrNull()
        problem = loaded.exceptionOrNull()?.let { appText(Res.string.ui_could_not_load_the_headphone_list, reason(it)) }
    }

    LaunchedEffect(entries) {
        val list = entries ?: return@LaunchedEffect
        if (query.isNotBlank() || detected != null) return@LaunchedEffect
        val found = connected
            .filter { it.isNotBlank() }
            .firstNotNullOfOrNull { name -> AutoEqCatalog.bestMatch(list, name)?.let { name to it } }
            ?: return@LaunchedEffect
        detected = found
        query = found.second.name
    }
    val results = remember(entries, query, detected) {
        val found = AutoEqCatalog.search(entries.orEmpty(), query)
        // The best measurement of the connected pair comes first.
        val best = detected?.second?.takeIf { it in found }
        if (best == null) found else listOf(best) + (found - best)
    }
    MotionDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Tooltip,
        title = { Text(stringResource(Res.string.ui_find_headphones)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(colors = fieldColors(), 
                    value = query,
                    onValueChange = {
                        query = it
                        // A failed download belongs to the earlier search, so it goes once the search changes.
                        if (entries != null) problem = null
                    },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.ui_headphone_model)) },
                    enabled = entries != null && !busy,
                )
                when {
                    problem != null -> Column {
                        Text(problem.orEmpty(), color = Palette.Danger, style = MaterialTheme.typography.bodySmall)
                        if (entries == null) TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.ui_try_again)) }
                    }
                    entries == null -> Text(stringResource(Res.string.ui_loading_the_list), color = Palette.TextDim)
                    busy -> Text(stringResource(Res.string.ui_downloading), color = Palette.TextDim)
                    query.isNotBlank() && results.isEmpty() -> Text(stringResource(Res.string.ui_no_matches), color = Palette.TextDim)
                    detected?.takeIf { it.second.name == query } != null -> Text(
                        stringResource(Res.string.ui_autoeq_detected, detected!!.first),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Lit,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results) { entry ->
                        Text(
                            entry.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 28.dp)
                                .clickable(enabled = !busy, role = Role.Button) {
                                    // Several measurements are worth hearing before choosing one.
                                    val same = AutoEqCatalog.measurementsOf(entries.orEmpty(), entry)
                                    if (same.size > 1) {
                                        nextMeasurements = same
                                        return@clickable
                                    }
                                    busy = true
                                    problem = null
                                    scope.launch {
                                        val text = withContext(Dispatchers.IO) {
                                            runCatching { AutoEqCatalog.loadProfile(entry) }
                                        }
                                        val parsed = text.getOrNull()?.let(AutoEqParser::parse)
                                        val info = if (parsed == null) null else withContext(Dispatchers.IO) { AutoEqCatalog.loadCorrectionInfo(entry) }
                                        busy = false
                                        if (parsed == null) {
                                            problem = appText(Res.string.ui_could_not_download_that_profile_it)
                                        } else {
                                            Repo.importPreset(entry.title, parsed, info)
                                            onImported(appText(Res.string.ui_added, entry.title))
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ui_close)) } },
    )
}

@Composable
private fun SaveDialog(
    title: String = stringResource(Res.string.ui_save_preset),
    initial: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val state by Repo.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(io.github.dragjon.eigentone.data.PresetOrganiser.freeName(state, initial).takeIf { initial.isNotBlank() } ?: "") }
    val taken = io.github.dragjon.eigentone.data.PresetOrganiser.nameTaken(state, name)
    MotionDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Tooltip,
        title = { Text(title) },
        text = {
            OutlinedTextField(colors = fieldColors(), 
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.ui_name)) },
                isError = taken,
                supportingText = if (taken) ({ Text(stringResource(Res.string.ui_name_taken, name.trim())) }) else null,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !taken, onClick = { onSave(name.trim()) }) { Text(stringResource(Res.string.ui_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ui_cancel)) } },
    )
}

@Composable
private fun SystemTab(state: AppState, status: EngineStatus) {
    val context = LocalContext.current
    val output by Repo.output.collectAsStateWithLifecycle()
    // Android can stop a background EQ to save battery; Windows keeps Eigentone's service running, so the
    // battery row never shows.
    val exempt = true

    EveryPresetSections(state)

    SettingsSection(stringResource(Res.string.ui_appearance)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.ui_theme), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                InfoButton(Help.theme)
            }
            // The same joined selector as the audio route, so every few-way choice looks alike (user decision).
            val modes = ThemeMode.entries
            val names = listOf(
                stringResource(Res.string.ui_theme_device),
                stringResource(Res.string.ui_theme_light),
                stringResource(Res.string.ui_theme_dark),
            )
            // Recolouring the whole app is heavy, so the thumb slides first and the theme follows once it's there,
            // rather than the toggle waiting for the recolour before it can move.
            var picked by remember { mutableStateOf<Int?>(null) }
            val themeScope = rememberCoroutineScope()
            SlidingToggle(
                options = names.map { ToggleOption(it) },
                selected = picked ?: modes.indexOf(state.themeMode),
                onSelect = { i ->
                    picked = i
                    themeScope.launch {
                        kotlinx.coroutines.delay(THEME_SWITCH_AFTER_MS)
                        Repo.update { it.copy(themeMode = modes[i]) }
                        picked = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RowDivider()
        ThemeColourRow(state)
        RowDivider()
        SwitchSetting(
            stringResource(Res.string.ui_advanced_controls),
            Help.advanced,
            state.advancedControls,
            detail = stringResource(if (state.advancedControls) Res.string.ui_advanced_on else Res.string.ui_advanced_off),
        ) { on -> Repo.update { it.copy(advancedControls = on) } }
    }


    AccessibilitySection(state)

    SettingsSection(stringResource(Res.string.ui_engine), trailing = { InfoButton(Help.power) }) {
        if (ANDROID_ONLY && Disclosure.engine(state)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.ui_audio_route), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                InfoButton(Help.engineMode)
            }
            val modes = EngineMode.entries
            SlidingToggle(
                options = modes.map { ToggleOption(engineModeLabel(it)) },
                selected = modes.indexOf(state.engineMode),
                onSelect = { i -> Repo.update { it.copy(engineMode = modes[i]) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RowDivider()
        SwitchSetting(stringResource(Res.string.ui_low_latency), Help.lowLatency, state.lowLatency) { on ->
            Repo.update { it.copy(lowLatency = on) }
        }
        RowDivider()
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.ui_eq_precision), style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
                InfoButton(Help.eqPrecision)
            }
            SlidingToggle(
                options = listOf(
                    ToggleOption(stringResource(Res.string.ui_eq_precision_standard)),
                    ToggleOption(stringResource(Res.string.ui_eq_precision_fine)),
                ),
                selected = if (state.fineEq) 1 else 0,
                onSelect = { i -> Repo.update { it.copy(fineEq = i == 1) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RowDivider()
        }
        SwitchSetting(stringResource(Res.string.ui_start_automatically), Help.startOnBoot, state.startOnBoot) { on ->
            Repo.update { it.copy(startOnBoot = on) }
        }
        if (!exempt) {
            RowDivider()
            ActionRow(stringResource(Res.string.ui_allow_background_use), Help.battery, detail = stringResource(Res.string.ui_battery_optimisation_may_stop_the_eq_whe)) {

            }
        }
    }

    var showGuide by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    SettingsSection(stringResource(Res.string.ui_help_and_reset)) {
        if (WINDOWS_TOUR_READY) {
            ActionRow(stringResource(Res.string.tour_take), Help.tour) { Tour.start() }
            RowDivider()
        }
        ActionRow(stringResource(Res.string.welcome_show_again), Help.welcome) {
            Repo.update { it.copy(welcomeDone = false, welcomeStep = 0) }
        }
        RowDivider()
        ActionRow(stringResource(Res.string.ui_getting_the_best_sound), Help.guide) { showGuide = true }
        RowDivider()
        ActionRow(stringResource(Res.string.ui_reset_sound_settings_2), Help.reset) { confirmReset = true }
    }
    if (showGuide) GuideDialog(onDismiss = { showGuide = false })
    if (confirmReset) {
        MotionDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Palette.Tooltip,
            title = { Text(stringResource(Res.string.ui_reset_sound_settings)) },
            text = { Text(stringResource(Res.string.ui_the_sound_goes_back_to_flat_your_saved_p)) },
            confirmButton = {
                TextButton(onClick = {
                    Repo.resetSound()
                    confirmReset = false
                }) { Text(stringResource(Res.string.ui_reset), color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(Res.string.ui_cancel)) } },
        )
    }

    DiagnosticsSection(status, output, onConsole = LocalOpenConsole.current)

    SettingsSection(stringResource(Res.string.ui_open_source)) {
        val version = remember {
            io.github.dragjon.eigentone.data.CrashLog.VERSION
        }
        Text(
            stringResource(Res.string.ui_open_source_body, version),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextDim,
            modifier = Modifier.padding(16.dp),
        )
        RowDivider()
        ActionRow(stringResource(Res.string.ui_view_source), Help.openSource, detail = SOURCE_URL.removePrefix("https://")) {
            io.github.dragjon.eigentone.platform.Actions.openUrl(SOURCE_URL)
        }
        RowDivider()
        ActionRow(stringResource(Res.string.ui_report_problem), Help.reportProblem, detail = stringResource(Res.string.ui_report_problem_detail)) {
            io.github.dragjon.eigentone.platform.Actions.openUrl("$SOURCE_URL/issues")
        }
    }

    if (Disclosure.status(state)) SettingsSection(stringResource(Res.string.ui_status)) {
        Fact(stringResource(Res.string.help_status_running_title), Help.statusRunning, if (status.running) stringResource(Res.string.ui_yes) else stringResource(Res.string.ui_no))
        RowDivider()
        Fact(stringResource(Res.string.help_status_mode_title), Help.statusMode, status.mode)
        RowDivider()
        Fact(stringResource(Res.string.help_status_bands_title), Help.statusBands, if (status.bandCount > 0) "${status.bandCount}" else stringResource(Res.string.ui_none))
        RowDivider()
        Fact(stringResource(Res.string.help_status_control_title), Help.statusControl, if (status.hasControl) stringResource(Res.string.ui_yes) else stringResource(Res.string.ui_no))
        RowDivider()
        Fact(stringResource(Res.string.help_status_readback_title), Help.statusReadback, status.readback ?: stringResource(Res.string.ui_not_checked_yet))
        RowDivider()
        Fact(stringResource(Res.string.help_status_repairs_title), Help.statusRepairs, "${status.rebuilds}")
        RowDivider()
        Fact(stringResource(Res.string.help_status_output_title), Help.statusOutput, output.label)
        if (status.sessions.isNotEmpty()) {
            RowDivider()
            Fact(stringResource(Res.string.help_status_apps_title), Help.statusApps, status.sessions.joinToString())
        }
        if (status.bandCount in 1 until 64) {
            RowDivider()
            Text(
                stringResource(Res.string.ui_this_phone_only_allowed_bands_so, status.bandCount),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim,
                modifier = Modifier.padding(16.dp),
            )
        }
        status.error?.let {
            RowDivider()
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Danger,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun GuideDialog(onDismiss: () -> Unit) {
    val tips = listOf(
        stringResource(Res.string.ui_get_a_good_seal) to stringResource(Res.string.ui_with_earbuds_fit_matters_more_than),
        stringResource(Res.string.ui_use_one_eq_at_a_time) to stringResource(Res.string.ui_eq_in_a_headphone_app_such_2),
        stringResource(Res.string.ui_start_with_find_my_sound) to stringResource(Res.string.ui_it_picks_a_correction_for_your),
        stringResource(Res.string.ui_change_a_little_at_a_time) to stringResource(Res.string.ui_a_change_of_2_or_3),
        stringResource(Res.string.ui_prefer_cuts_to_boosts) to stringResource(Res.string.ui_if_something_is_too_loud_cutting),
        stringResource(Res.string.ui_compare_with_bypass) to stringResource(Res.string.ui_your_ears_adapt_within_minutes_so),
        stringResource(Res.string.ui_try_several_songs) to stringResource(Res.string.ui_one_track_can_mislead_you_test),
    )
    MotionDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Tooltip,
        title = { Text(stringResource(Res.string.ui_getting_the_best_sound)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tips.forEach { (title, body) ->
                    Column {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.ui_close)) } },
    )
}

@Composable
private fun Fact(label: String, tip: Tip, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same size and height as every other setting row.
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Palette.Text)
        InfoButton(tip)
        // Mono digits are for numbers that change; words such as a mode or a device name read as text.
        val numeric = value.all { it.isDigit() || it == '/' || it == ' ' || it == '.' }
        Text(
            value,
            Modifier
                .weight(1f)
                .padding(start = 8.dp),
            style = if (numeric) Readout else MaterialTheme.typography.bodyMedium,
            color = Palette.TextDim,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The sound's history, as a photo editor keeps one: each settled change to the current sound, newest at the
 * top, named by what changed. Clicking a step goes back to it, and the steps after it grey out; they're still
 * there to click until a new change is made from the older step, which replaces them, as in Lightroom. Undo
 * and redo move along the same list rather than adding to it. Kept here rather than in the shared Repo, which
 * keeps its undo history to itself.
 */
private fun kotlinx.coroutines.flow.Flow<AppState>.mapCurrent(): kotlinx.coroutines.flow.Flow<Preset> =
    kotlinx.coroutines.flow.flow { collect { emit(it.current) } }

private object History {
    class Step(val before: Preset?, val after: Preset, val at: java.time.LocalTime)

    private const val KEEP = 100
    private const val SETTLE_MS = 600L

    /** Newest first. */
    val steps = androidx.compose.runtime.mutableStateListOf<Step>()

    /** Which step the sound is at: 0 is the newest. Steps before it (newer) are the greyed-out ones. */
    var at by mutableStateOf(0)

    /** A sound this panel is putting back, so the recorder doesn't log it as a new change. */
    private var moving: Preset? = null

    @Composable
    fun Recorder(@Suppress("UNUSED_PARAMETER") state: AppState) {
        LaunchedEffect(Unit) {
            Repo.state.mapCurrent().distinctUntilChanged().debounce(SETTLE_MS).collect { current -> record(current) }
        }
    }

    private fun record(current: Preset) {
        if (steps.getOrNull(at)?.after == current) return
        if (moving == current) {
            moving = null
            return
        }
        // Undo or redo reached a neighbouring step: move to it rather than logging it again.
        val near = listOf(at + 1, at - 1).firstOrNull { steps.getOrNull(it)?.after == current }
        if (near != null) {
            at = near
            return
        }
        // A new change from an older step replaces the steps after it.
        repeat(at) { steps.removeAt(0) }
        at = 0
        steps.add(0, Step(steps.firstOrNull()?.after, current, java.time.LocalTime.now()))
        while (steps.size > KEEP) steps.removeAt(steps.lastIndex)
    }

    private fun goTo(index: Int) {
        val step = steps.getOrNull(index) ?: return
        if (index == at) return
        at = index
        moving = step.after
        Repo.commit()
        Repo.edit { step.after }
        Repo.commit()
    }

    @Composable
    fun Panel(listMax: androidx.compose.ui.unit.Dp = HISTORY_MAX_DP.dp) {
        SettingsSection(stringResource(Res.string.win_panel_history)) {
            if (steps.size <= 1) {
                Text(
                    stringResource(Res.string.win_history_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            val time = remember { java.time.format.DateTimeFormatter.ofPattern("HH:mm") }
            // Scrolls within the panel once it's long, so the panels below stay where they are.
            val list = rememberScrollState()
            Box(Modifier.fillMaxWidth().heightIn(max = listMax)) {
                Column(Modifier.fillMaxWidth().verticalScroll(list)) {
                    steps.forEachIndexed { i, step ->
                        androidx.compose.runtime.key(System.identityHashCode(step)) {
                        val label = when {
                            step.before == null -> stringResource(Res.string.win_history_start, step.after.name)
                            step.before.id != step.after.id -> stringResource(Res.string.win_history_loaded, step.after.name)
                            else -> changeSummary(remember(step) { PresetChanges.of(step.before, step.after) }).ifEmpty { step.after.name }
                        }
                        val current = i == at
                        val undone = i < at
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (current) Palette.SurfaceHigh else Color.Transparent)
                                .clickable { goTo(i) }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(3.dp).height(14.dp).background(if (current) Palette.Lit else Color.Transparent))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    current -> Palette.Text
                                    undone -> Palette.TextDim.copy(alpha = 0.55f)
                                    else -> Palette.TextDim
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                            Text(step.at.format(time), style = Readout, color = Palette.TextDim.copy(alpha = if (undone) 0.55f else 1f))
                        }
                        }
                    }
                }
                // Only once the steps overflow; a scrollbar always takes the panel's whole height.
                if (list.maxValue > 0) {
                    androidx.compose.foundation.VerticalScrollbar(
                        adapter = androidx.compose.foundation.rememberScrollbarAdapter(list),
                        modifier = Modifier.align(Alignment.CenterEnd).matchParentSize(),
                    )
                }
            }
        }
    }

    private const val HISTORY_MAX_DP = 260
    /** Room History keeps however long the preset list is, and what its heading and note take of it. */
    const val HISTORY_MIN_DP = 150
    const val HISTORY_CHROME_DP = 80
}

/**
 * The canvas's toolbar, as a studio's: the sound and whether it's saved on the left, then how it's edited,
 * then the view and the history keys on the right.
 */
@Composable
private fun CanvasToolbar(state: AppState, inGraphic: Boolean, showParametric: Boolean, graphicMode: Boolean, setGraphic: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(CANVAS_TOOLBAR_DP.dp).background(Palette.Surface).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(state.current.name, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (showParametric) {
            SlidingToggle(
                options = listOf(ToggleOption(stringResource(Res.string.ui_graphic)), ToggleOption(stringResource(Res.string.ui_parametric))),
                selected = if (graphicMode) 0 else 1,
                onSelect = { setGraphic(it == 0) },
                modifier = Modifier.width(200.dp),
            )
        }
        if (inGraphic && Disclosure.bandCount(state)) BandCountToggle(state.current.tone, Modifier.width(132.dp))
        Box(Modifier.width(1.dp).height(18.dp).background(Palette.Track))
        if (inGraphic && Disclosure.faderOptions(state)) FaderOptions(LocalOpenFaders.current)
        AnalyserToggle(state, Modifier)
        UndoRedoButtons()
        if (inGraphic) {
            TextButton(enabled = !GraphicEq.isFlat(GraphicEq.normalized(state.current.tone.graphicDb, state.current.tone.graphicBands)), onClick = ::resetFaders) {
                Text(stringResource(Res.string.ui_reset))
            }
        }
        InfoButton(if (inGraphic) Help.graphic else Help.eqMode)
    }
}

private const val CANVAS_TOOLBAR_DP = 40

/** The tools, which open in the Tools module's pane. */
enum class StudioTool { FINDER, REFINE, HEARING, PEAKS, SWEEP, COMPARE, CURVES, SHAPES, CONSOLE }

/** One tool in the list: [tool] is null for one that opens a dialog of its own instead, such as the microphone. */
private class ToolEntry(val tool: StudioTool?, val title: StringResource, val detail: StringResource, val tip: Tip)

private val TOOL_GROUPS: List<Pair<StringResource, List<ToolEntry>>> by lazy {
    listOf(
        Res.string.win_tools_fit to listOf(
            ToolEntry(StudioTool.FINDER, Res.string.ui_find_my_sound, Res.string.ui_a_short_guided_listening_test, Help.finder),
            ToolEntry(StudioTool.REFINE, Res.string.ui_refine, Res.string.ui_refine_detail, Help.refine),
            ToolEntry(StudioTool.HEARING, Res.string.ui_hearing_check, Res.string.ui_builds_a_curve_for_how_you_hear, Help.hearing),
        ),
        Res.string.win_tools_problems to listOf(
            ToolEntry(StudioTool.PEAKS, Res.string.ui_find_peaks_for_me, Res.string.ui_a_guided_test_that_finds_peaks_and_dips, Help.peakFinder),
            ToolEntry(StudioTool.SWEEP, Res.string.ui_frequency_sweep, Res.string.ui_find_and_fix_peaks_in_your_headphones_by, Help.sweep),
            ToolEntry(null, Res.string.ui_measure_action, Res.string.ui_measure_action_detail, Help.measure),
        ),
        Res.string.win_tools_compare to listOf(
            ToolEntry(StudioTool.COMPARE, Res.string.ui_compare_two_sounds, Res.string.ui_switch_instantly_with_an_optional_blind, Help.compare),
            ToolEntry(StudioTool.CURVES, Res.string.ui_compare_curves, Res.string.ui_compare_curves_detail, Help.compareCurves),
        ),
        Res.string.win_tools_shapes to listOf(
            ToolEntry(StudioTool.SHAPES, Res.string.help_shape_library_title, Res.string.win_tools_shapes_detail, Help.shapeLibrary),
        ),
        Res.string.win_tools_trouble to listOf(
            ToolEntry(StudioTool.CONSOLE, Res.string.ui_debug_console, Res.string.ui_debug_console_detail, Help.console),
        ),
    )
}

/**
 * Tools: the list down the left, and the open tool beside it, as a studio opens a tool in its main pane. With none
 * open, the pane shows every tool as a tile with what it's for.
 */
@Composable
private fun ToolsWorkspace(tool: StudioTool?, setTool: (StudioTool?) -> Unit, closeTool: () -> Unit, onSetEnabled: (Boolean) -> Unit) {
    val output by Repo.output.collectAsStateWithLifecycle()
    var measuring by remember { mutableStateOf(false) }
    fun choose(e: ToolEntry) {
        if (e.tool == null) measuring = true else setTool(e.tool)
    }
    Row(Modifier.fillMaxSize()) {
        PanelTrack(TOOL_RAIL_DP.dp, rememberScrollState(), background = Palette.Surface) {
            TOOL_GROUPS.forEach { (group, entries) ->
                SettingsSection(stringResource(group)) {
                    entries.forEach { e -> ToolRow(e, selected = e.tool != null && e.tool == tool) { choose(e) } }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
        Box(Modifier.weight(1f).fillMaxHeight().background(Palette.Background)) {
            when (tool) {
                null -> ToolOverview(::choose)
                StudioTool.FINDER -> FinderScreen(onSetEnabled = onSetEnabled, onClose = closeTool)
                StudioTool.REFINE -> RefineScreen(onSetEnabled = onSetEnabled, onClose = closeTool)
                StudioTool.HEARING -> HearingScreen(onClose = closeTool)
                StudioTool.PEAKS -> PeakFinderScreen(onClose = closeTool)
                StudioTool.SWEEP -> SweepScreen(onClose = closeTool)
                StudioTool.COMPARE -> CompareScreen(onSetEnabled = onSetEnabled, onClose = closeTool)
                StudioTool.CURVES -> CurveCompareScreen(onClose = closeTool)
                StudioTool.SHAPES -> ShapeBrowser(onClose = closeTool)
                StudioTool.CONSOLE -> DebugConsoleScreen(onClose = closeTool)
            }
        }
    }
    if (measuring) {
        val snackbar = LocalSnackbar.current
        val scope = LocalSnackScope.current
        RoomMeasureDialog(output, onDismiss = { measuring = false }) { name ->
            measuring = false
            scope.notify(snackbar, appText(Res.string.ui_added, name))
        }
    }
}

@Composable
private fun ToolRow(entry: ToolEntry, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Palette.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(30.dp).background(if (selected) Palette.Lit else Color.Transparent))
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(stringResource(entry.title), style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(entry.detail), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Every tool but the console as a tile, the tiles sharing the pane between them so it's filled at any size. */
@Composable
private fun ToolOverview(onChoose: (ToolEntry) -> Unit) {
    val tiles = TOOL_GROUPS.flatMap { (group, entries) -> entries.filter { it.tool != StudioTool.CONSOLE }.map { group to it } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(TOOL_TILE_COLUMNS).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (group, entry) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(PanelShape)
                            .background(Palette.Surface)
                            .clickable { onChoose(entry) }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(group).uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim, letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp))
                        Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(stringResource(entry.detail), style = MaterialTheme.typography.bodySmall, color = Palette.Text)
                        Text(
                            stringResource(entry.tip.body),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextDim,
                            // Whole lines only, so a long explanation ends in an ellipsis rather than a cut line.
                            maxLines = TOOL_TIP_LINES,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(stringResource(Res.string.win_tools_open), style = MaterialTheme.typography.bodyMedium, color = Palette.Lit)
                    }
                }
                repeat(TOOL_TILE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Presets: the library down the left, the sound in use drawn large in the middle, and what to do with it on the right. */
@Composable
private fun PresetsWorkspace(state: AppState, scrolls: Map<Tab, ScrollState>, openTune: () -> Unit) {
    val output by Repo.output.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxSize()) {
        PanelTrack(LEFT_PANEL_DP.dp, scrolls.getValue(Tab.PRESETS), background = Palette.Surface) { PresetListSection(state) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
        CurvePane(
            state,
            output.key,
            title = state.current.name,
            subtitle = stringResource(Res.string.win_curve_on, output.label),
            modifier = Modifier.weight(1f),
        ) {
            FilledTonalButton(onClick = openTune) { Text(stringResource(Res.string.win_edit_in_tune)) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
        PanelTrack(RIGHT_PANEL_DP.dp, rememberScrollState(), background = Palette.Surface) {
            if (Disclosure.perApp(state)) PerAppSection(state)
            ManageSection(state)
        }
    }
}

/** Devices: the output's own settings down the left, and everything that plays on it drawn large beside them. */
@Composable
private fun DevicesWorkspace(state: AppState, scrolls: Map<Tab, ScrollState>) {
    val output by Repo.output.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxSize()) {
        PanelTrack(DEVICE_PANEL_DP.dp, scrolls.getValue(Tab.HEADPHONES), background = Palette.Surface) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(PANEL_GAP_DP.dp)) { HeadphonesTab(state) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
        CurvePane(
            state,
            output.key,
            title = output.label,
            subtitle = stringResource(Res.string.win_device_curve, state.current.name),
            modifier = Modifier.weight(1f),
        )
    }
}

/** A sound's whole curve as it plays on an output, filling the pane: a heading, the graph, and room for actions. */
@Composable
private fun CurvePane(
    state: AppState,
    outputKey: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxHeight().background(Palette.Background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 2)
            }
            actions()
        }
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(PanelShape)
                .background(Palette.Surface)
                .padding(GRAPH_INSET_DP.dp),
        ) {
            val height = maxHeight
            val curve = remember(state.current, state.bypass, state.loudness, state.earCorrections, outputKey) {
                Processing.effectiveFilters(state, 0.0, outputKey)
            }
            EqGraph(
                filters = emptyList(),
                curveFilters = curve,
                spectrum = { null },
                selectedId = null,
                dimmed = !state.enabled || state.bypass,
                onSelect = {},
                onDrag = { _, _, _ -> },
                onDragEnd = {},
                height = { height },
                morphKey = state.current.id,
            )
        }
    }
}

private const val TOOL_RAIL_DP = 300
private const val TOOL_TILE_COLUMNS = 3
private const val TOOL_TIP_LINES = 6
private const val DEVICE_PANEL_DP = 420
