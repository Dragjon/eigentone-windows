package io.github.dragjon.eigentone.ui

import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import org.jetbrains.compose.resources.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.LinearEasing
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.activity.compose.BackHandler
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.flow.first
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.dragjon.eigentone.resources.*
import io.github.dragjon.eigentone.data.AppState
import io.github.dragjon.eigentone.data.BuiltIns
import io.github.dragjon.eigentone.data.Profiles
import io.github.dragjon.eigentone.data.Repo
import io.github.dragjon.eigentone.data.ThemeMode

private const val STEPS = 5

/** Set as the welcome hands over, so the app opens out of the line the welcome closed into. */
internal val openingFromWelcome = mutableStateOf(false)

/** How far the power-on had already played on the welcome's last page when the app took over, 0 to 1. */
internal val openingFrom = androidx.compose.runtime.mutableFloatStateOf(0f)

/** Long enough to watch the two lines meet as the page flattens onto them. */
private const val CLOSE_MS = 900

/**
 * The first run, in five short steps: what Eigentone is, turning it on, the headphones, finding a sound, and
 * how much control to show. Every step can be skipped, the place is saved if the app closes, and Settings,
 * Help, opens it again.
 */
@Composable
fun WelcomeScreen(onSetEnabled: (Boolean) -> Unit, onFindSound: () -> Unit) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val output by Repo.output.collectAsStateWithLifecycle()
    val step = state.welcomeStep.coerceIn(0, STEPS - 1)
    fun go(to: Int) = Repo.update { it.copy(welcomeStep = to.coerceIn(0, STEPS - 1)) }
    fun finish() = Repo.update { it.copy(welcomeDone = true, welcomeStep = 0, guidedSetupSeen = true) }

    BackHandler(enabled = step > 0) { go(step - 1) }

    // Start listening closes the session into a single lit line, the one the app then opens out of.
    val closing = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun startListening() {
        if (closing.value > 0f) return
        scope.launch {
            closing.animateTo(1f, tween(CLOSE_MS, easing = FastOutSlowInEasing))
            openingFromWelcome.value = true
            finish()
        }
    }
    fun next() = if (step < STEPS - 1) go(step + 1) else startListening()

    // The arrows and Enter move through the steps, as in any setup.
    val keys = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { keys.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                val c = closing.value
                drawRect(Palette.Background)
                drawContent()
                if (c > 0f) {
                    val w = size.width
                    val mid = size.height / 2f
                    val spill = FastOutSlowInEasing.transform(c)
                    drawLine(Palette.Lit, Offset(w, mid), Offset(w - w * spill, mid), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            .graphicsLayer {
                val c = closing.value
                scaleY = 1f - 0.994f * c
                scaleX = 1f - 0.06f * c
                alpha = 1f - ((c - 0.6f) / 0.4f).coerceIn(0f, 1f)
            }
            .background(Palette.Bar)
            .focusRequester(keys)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    androidx.compose.ui.input.key.Key.DirectionRight, androidx.compose.ui.input.key.Key.Enter -> { next(); true }
                    androidx.compose.ui.input.key.Key.DirectionLeft -> { if (step > 0) go(step - 1); true }
                    else -> false
                }
            },
    ) {
        // The studio's own top bar, saying this is setup.
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(painterResource(Res.drawable.app_icon_small), contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Eigentone", style = MaterialTheme.typography.titleMedium, color = Palette.Text, modifier = Modifier.padding(start = 10.dp))
            Text(
                stringResource(Res.string.win_welcome_setup_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextDim,
                letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            if (step < STEPS - 1) TextButton(onClick = ::finish) { Text(stringResource(Res.string.win_welcome_skip)) }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // The steps, as a panel down the left.
            Column(Modifier.width(WELCOME_RAIL_DP.dp).fillMaxHeight()) {
                SettingsSection(stringResource(Res.string.win_welcome_steps)) {
                    WELCOME_STEPS.forEachIndexed { i, name ->
                        StepRow(i, stringResource(name), current = i == step, done = i < step) { go(i) }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Palette.Surface))
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
            // Your sound in the middle, as on Tune's canvas, changing as setup changes it.
            CurvePane(
                state,
                output.key,
                title = stringResource(STEP_TITLES[step]),
                subtitle = stringResource(STEP_BODIES[step]),
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
            // This step's choices, as panels on the right.
            Box(Modifier.width(WELCOME_PANEL_DP.dp).fillMaxHeight().background(Palette.Bar)) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    when (step) {
                        0 -> Hello()
                        1 -> TurnOn(state, onSetEnabled)
                        2 -> YourHeadphones(state)
                        3 -> FindYourSound {
                            // Comes back to the last step once the test is done or left.
                            go(4)
                            onFindSound()
                        }
                        else -> HandsOn(state)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(Res.string.welcome_step_of, step + 1, STEPS), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            Spacer(Modifier.weight(1f))
            if (step > 0) FilledTonalButton(onClick = { go(step - 1) }) { Text(stringResource(Res.string.ui_back)) }
            Button(onClick = ::next, modifier = Modifier.widthIn(min = 120.dp)) {
                Text(stringResource(if (step < STEPS - 1) Res.string.win_welcome_next else Res.string.welcome_finish))
            }
        }
    }
}

/** One step in the list: its number, ticked once done, and its name; the current one lit, as a selected row is. */
@Composable
private fun StepRow(index: Int, name: String, current: Boolean, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (current) Palette.SurfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(18.dp).background(if (current) Palette.Lit else androidx.compose.ui.graphics.Color.Transparent))
        Box(
            Modifier
                .padding(start = 9.dp)
                .size(18.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (current || done) Palette.LitFill else Palette.Track),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.OnLit, modifier = Modifier.size(12.dp))
            } else {
                Text("${index + 1}", style = Readout, color = if (current) Palette.OnLit else Palette.TextDim)
            }
        }
        Text(name, style = MaterialTheme.typography.bodyMedium, color = if (current) Palette.Text else Palette.TextDim, modifier = Modifier.padding(start = 10.dp))
    }
}

/** Plain text in a panel, at the panel's own indent. */
@Composable
private fun PanelText(text: String, dim: Boolean = true, lit: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            lit -> Palette.Lit
            dim -> Palette.TextDim
            else -> Palette.Text
        },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun Hello() {
    SettingsSection(stringResource(Res.string.win_welcome_what)) {
        Feature(Res.drawable.ic_tab_eq, Res.string.welcome_feature_eq)
        Feature(Res.drawable.ic_tab_headphones, Res.string.welcome_feature_headphones)
        Feature(Res.drawable.ic_tab_presets, Res.string.welcome_feature_presets)
    }
    SettingsSection(stringResource(Res.string.win_welcome_about)) {
        PanelText(stringResource(Res.string.welcome_hello_note))
    }
}

@Composable
private fun Feature(icon: DrawableResource, text: StringResource) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = Palette.Lit, modifier = Modifier.size(16.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun TurnOn(state: AppState, onSetEnabled: (Boolean) -> Unit) {
    val status by Repo.status.collectAsStateWithLifecycle()
    SettingsSection(stringResource(Res.string.win_welcome_power)) {
        SwitchSetting(stringResource(Res.string.win_welcome_eigentone_on), Help.power, state.enabled) { on -> onSetEnabled(on) }
        val ok = state.enabled && status.running && status.hasControl && status.error == null
        PanelText(
            when {
                !state.enabled -> stringResource(Res.string.win_welcome_off_note)
                status.error != null -> status.error.orEmpty()
                !status.running -> stringResource(Res.string.ui_starting)
                !status.hasControl -> stringResource(Res.string.ui_another_audio_app_has_taken_control)
                else -> stringResource(Res.string.welcome_on_done)
            },
            lit = ok,
        )
    }
    SettingsSection(stringResource(Res.string.ui_use_one_eq_at_a_time)) {
        PanelText(stringResource(Res.string.ui_eq_in_a_headphone_app_such_2))
    }
}

@Composable
private fun YourHeadphones(state: AppState) {
    val output by Repo.output.collectAsStateWithLifecycle()
    val builtIn = rememberConnectedOutputNames().firstNotNullOfOrNull(BuiltIns::correctionFor)
    var finding by remember { mutableStateOf(false) }
    var tuning by remember { mutableStateOf(false) }
    val corrected = state.current.filters.any { it.gainDb != 0.0 } || state.current.tone.importedCurve.isNotEmpty()

    SettingsSection(stringResource(Res.string.win_welcome_output)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(output.kind.icon()), contentDescription = null, tint = Palette.Lit, modifier = Modifier.size(16.dp))
            Text(output.label, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.padding(start = 12.dp))
        }
        if (!Profiles.supportsModes(output.key) && builtIn == null) {
            PanelText(stringResource(Res.string.welcome_headphones_none, output.label))
        } else if (corrected) {
            PanelText(stringResource(Res.string.welcome_headphones_using, state.current.name), lit = true)
            ActionRow(stringResource(Res.string.welcome_headphones_tune), Help.correction, detail = stringResource(Res.string.welcome_headphones_tune_note)) { tuning = true }
        } else if (builtIn != null) {
            ActionRow(stringResource(Res.string.ui_use_correction, builtIn.name), Help.correction) {
                Repo.assignToOutput(output.key, builtIn.id)
                Repo.loadPreset(builtIn)
            }
        } else {
            ActionRow(stringResource(Res.string.welcome_headphones_find), Help.correction) { finding = true }
        }
    }
    SettingsSection(stringResource(Res.string.win_welcome_later)) {
        PanelText(stringResource(Res.string.welcome_headphones_later))
    }
    if (finding) AutoEqDialog(onDismiss = { finding = false }) { finding = false }
    if (tuning) CorrectionTuner(onDismiss = { tuning = false })
}

@Composable
private fun FindYourSound(onStart: () -> Unit) {
    SettingsSection(stringResource(Res.string.welcome_sound_title)) {
        ActionRow(stringResource(Res.string.win_welcome_sound_start), Help.finder, detail = stringResource(Res.string.welcome_sound_note), onClick = onStart)
        PanelText(stringResource(Res.string.welcome_sound_later))
    }
}

@Composable
private fun HandsOn(state: AppState) {
    SettingsSection(stringResource(Res.string.win_welcome_controls)) {
        Column(Modifier.selectableGroup()) {
            Choice(stringResource(Res.string.welcome_simple), stringResource(Res.string.welcome_simple_body), !state.advancedControls) {
                Repo.update { it.copy(advancedControls = false) }
            }
            Choice(stringResource(Res.string.welcome_full), stringResource(Res.string.welcome_full_body), state.advancedControls) {
                Repo.update { it.copy(advancedControls = true) }
            }
        }
        PanelText(stringResource(Res.string.welcome_hands_where))
    }
    SettingsSection(stringResource(Res.string.ui_theme)) {
        val modes = ThemeMode.entries
        SlidingToggle(
            options = listOf(
                ToggleOption(stringResource(Res.string.ui_theme_device)),
                ToggleOption(stringResource(Res.string.ui_theme_light)),
                ToggleOption(stringResource(Res.string.ui_theme_dark)),
            ),
            selected = modes.indexOf(state.themeMode),
            onSelect = { i -> Repo.update { it.copy(themeMode = modes[i]) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
    }
    SettingsSection(stringResource(Res.string.win_welcome_quick)) {
        PanelText(stringResource(Res.string.welcome_hands_note))
    }
}

/** A choice as a radio row: a small round mark, the name, and what it means beneath. */
@Composable
private fun Choice(title: String, body: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) Palette.SurfaceHigh else androidx.compose.ui.graphics.Color.Transparent)
            .handCursor()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(14.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .border(1.5.dp, if (selected) Palette.Lit else Palette.TextDim, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Palette.Lit))
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
    }
}

/** The steps' names, for the list down the left, and each one's heading and explanation over the curve. */
private val WELCOME_STEPS = listOf(
    Res.string.win_welcome_step_hello,
    Res.string.win_welcome_step_on,
    Res.string.win_welcome_step_headphones,
    Res.string.win_welcome_step_sound,
    Res.string.win_welcome_step_hands,
)
private val STEP_TITLES = listOf(
    Res.string.welcome_hello_title,
    Res.string.welcome_on_title,
    Res.string.welcome_headphones_title,
    Res.string.welcome_sound_title,
    Res.string.welcome_hands_title,
)
private val STEP_BODIES = listOf(
    Res.string.welcome_hello_body,
    Res.string.welcome_on_body,
    Res.string.welcome_headphones_body,
    Res.string.ui_compare_two_versions_of_a_song_a_few_tim,
    Res.string.welcome_hands_body,
)
private const val WELCOME_RAIL_DP = 240
private const val WELCOME_PANEL_DP = 360

/** How far through the window from [start] to [end] the progress is, from 0 to 1. */
private fun phase(progress: Float, start: Float, end: Float) = ((progress - start) / (end - start)).coerceIn(0f, 1f)

private const val SWEEP_MS = 420

/** How far a sideways swipe has to travel to turn the page, or a quick flick's speed and least distance. */
private const val SWIPE_DP = 40
private const val SWIPE_FLICK_DP = 300
private const val SWIPE_NUDGE_DP = 12
private const val STRIPS = 10

/** How long the arrow after the dots takes to light up as the last step arrives. */
private const val ARROW_LIGHT_MS = 320

/** How close to the line page the slide has to be for the app to start opening. */
private const val LINE_ARRIVED = 0.04f

/** How far a swipe must go before the page turns on letting go, as a share of the width. */
private const val SNAP_AT = 0.2f

/** How much later each strip starts than the one to its left, as a share of the sweep. */
private const val STRIP_STAGGER = 0.06f

/** How far through its own fall a strip is, given the whole sweep's progress. */
private fun stripProgress(strip: Int, progress: Float): Float =
    ((progress - strip * STRIP_STAGGER) / (1f - (STRIPS - 1) * STRIP_STAGGER)).coerceIn(0f, 1f)

/** One fader-wide strip of the page, from top to bottom, reaching past both ends so nothing shows between. */
private class StripShape(private val strip: Int) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val width = size.width / STRIPS
        // A hair wider than its share, so neighbours overlap and no seam shows before they move.
        return Outline.Rectangle(Rect(width * strip, -size.height, width * (strip + 1) + 1f, size.height * 2))
    }
}

/**
 * A strip of the page just finished. Like a fader pulled down, it drops and fades, a moment after the one to
 * its left, so the page leaves as a sweep from low to high.
 */
@Composable
private fun FaderStrip(order: Int, strip: Int, progress: Float, modifier: Modifier, content: @Composable () -> Unit) {
    val shape = remember(strip) { StripShape(strip) }
    Box(
        modifier
            .graphicsLayer {
                val local = stripProgress(order, progress)
                translationY = local * local * size.height * 0.45f
                alpha = 1f - local
                this.shape = shape
                clip = true
            }
            .background(Palette.Background),
    ) { content() }
}

/** The lit line leading the sweep, like the cursor of a frequency sweep. */
@Composable
private fun SweepLine(progress: Float, backward: Boolean, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            val along = (progress / (1f - (STRIPS - 1) * STRIP_STAGGER * 0.5f)).coerceIn(0f, 1f)
            val x = size.width * if (backward) 1f - along else along
            val fade = sin(PI * progress).toFloat()
            drawRect(Palette.Lit.copy(alpha = 0.12f * fade), topLeft = Offset(x - 8.dp.toPx(), 0f), size = Size(16.dp.toPx(), size.height))
            drawLine(Palette.Lit.copy(alpha = fade), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        },
    )
}

/** Where the welcome is, as dots: lit up to the current step. */
@Composable
private fun StepDots(step: Int, modifier: Modifier, onStart: () -> Unit) {
    val label = stringResource(Res.string.welcome_step_of, step + 1, STEPS)
    val last = step == STEPS - 1
    val startLabel = stringResource(Res.string.welcome_finish)
    // A pale tint of the same blue in both themes, so the ring lifts the circle rather than darkening it (user decision).
    val ring = androidx.compose.ui.graphics.lerp(Palette.LitFill, androidx.compose.ui.graphics.Color.White, if (Palette.dark) 0.8f else 0.45f)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(STEPS) { i ->
                Box(
                    Modifier
                        .size(width = if (i == step) 24.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (i <= step) Palette.LitFill else Palette.Track),
                )
            }
        }
        // One more stop past the last step (user decision): a quiet arrow until it's next, then the same arrow in a
        // lit circle, with no word, easing in as the last step arrives so the way in is plain without a jolt.
        val lit by androidx.compose.animation.core.animateFloatAsState(if (last) 1f else 0f, tween(ARROW_LIGHT_MS, easing = FastOutSlowInEasing), label = "startArrow")
        Box(
            Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = 0.85f + 0.15f * lit
                    scaleY = 0.85f + 0.15f * lit
                }
                .clip(CircleShape)
                .background(Palette.LitFill.copy(alpha = lit))
                // A ring in another shade of the same blue, lighter at night and deeper by day, so the lit circle
                // stands apart from the page without black or white (user decision), fading in with it.
                .border(2.dp, ring.copy(alpha = lit), CircleShape)
                .then(
                    if (last) {
                        Modifier
                            .handCursor().clickable(role = Role.Button, onClick = onStart)
                            .semantics { contentDescription = startLabel }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.lerp(Palette.Track, Palette.OnLit, lit),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Curved, wavy wavefronts at the edge a sideways drag started from, rippling inward from faint to strong as the
 * drag nears the point where it turns the page. [pull] runs from -1 (dragging left) to 1 (dragging right).
 */
@Composable
private fun DragWave(pullNow: () -> Float, modifier: Modifier) {
    val colour = Palette.LitFill
    Canvas(modifier) {
        val pull = pullNow()
        if (kotlin.math.abs(pull) < 0.02f) return@Canvas
        val strength = kotlin.math.abs(pull)
        // Dragging left starts at the right edge, and dragging right at the left.
        val fromRight = pull < 0f
        val edge = if (fromRight) size.width else 0f
        val inward = if (fromRight) -1f else 1f
        val depth = WAVE_DEPTH_DP.dp.toPx() * strength
        for (k in 0 until WAVE_RINGS) {
            // The outermost ring is strongest, each further in fainter, so they ripple from faint to strong.
            val reach = depth * (1f - k * 0.28f)
            if (reach <= 1f) continue
            val path = androidx.compose.ui.graphics.Path()
            val steps = 48
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val y = size.height * t
                // A bow that bulges most at the middle, with a gentle ripple along it.
                val bow = kotlin.math.sin(t * Math.PI).toFloat()
                val ripple = 1f + 0.08f * kotlin.math.sin(t * Math.PI * 6 + k).toFloat()
                val x = edge + inward * reach * bow * ripple
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                colour.copy(alpha = strength * (0.85f - k * 0.25f).coerceAtLeast(0.1f)),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = (3f - k).coerceAtLeast(1f) * density, cap = StrokeCap.Round),
            )
        }
    }
}

private const val WAVE_DEPTH_DP = 56
private const val WAVE_RINGS = 3
private const val WAVE_RETREAT_MS = 160

/** The ripple that runs along the closing wave: how high, how many crests, and how fast it travels. */
private const val FLOW_DP = 40
private const val FLOW_WAVES = 4f
private const val FLOW_SPEED = 7f

/** How far behind the leading end the tail sets off, as a share of the closing. */
private const val SLITHER_LAG = 0.45f

/**
 * The page past the last step: the app's own power-on, played from [progress] as the page slides in, drawn over the
 * whole screen just as the app draws it, so that when it takes over nothing moves or changes size.
 */
@Composable
private fun LinePage(progress: () -> Float, modifier: Modifier) {
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    val screenHeight = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    var top by remember { mutableIntStateOf(0) }
    Box(modifier.onGloballyPositioned { top = it.boundsInRoot().top.toInt() }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(constraints.maxWidth, screenHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -top) }
                },
        ) { drawPowerOn(progress()) }
    }
}