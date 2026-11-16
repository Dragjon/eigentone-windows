package io.github.dragjon.eigentone.ui

import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import org.jetbrains.compose.resources.*

import androidx.compose.ui.layout.positionInRoot

import androidx.compose.ui.layout.onGloballyPositioned

import kotlinx.coroutines.flow.first

import androidx.compose.ui.layout.onSizeChanged

import kotlin.math.pow

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.relocation.bringIntoViewRequester
import io.github.dragjon.eigentone.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val DOUBLE_TAP_MS = 300L
private const val MUSIC_CHECK_MS = 1000L
private val ROOMY_FOR_OPEN_SECTIONS = 760.dp
private const val TRACK_TICKS = 20

/**
 * A heading above a group of settings on one surface. With a [summary], the group folds away under its
 * heading, and the summary says what's on while it's closed, so nothing changed is ever hidden from view.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsSection(
    title: String?,
    trailing: @Composable (() -> Unit)? = null,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // In the studio every titled panel folds, as a develop panel does, and starts open.
    val folds = title != null
    var open by rememberSaveable(title) { mutableStateOf(true) }
    val shown = !folds || open
    // The chevron turns rather than swapping, so it's plain which way the section is going.
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(FOLD_MS, easing = FastOutSlowInEasing), label = "chevron")
    // Asked for from elsewhere, such as a row in the strip's drawer: it opens and scrolls into view.
    val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    // Every tab stays built, so only a section on the page in view answers, not one of the same name elsewhere.
    val pageShown = LocalPageShown.current
    // The section's size, and how much of the page's top lies under the floating strip, so a section brought into
    // view stops with its heading just below the strip rather than under it.
    var sectionWidth by remember { mutableStateOf(0f) }
    // The heading's height, and the panel's full height, which it has from the first frame of unfolding even
    // while it's still shown only in part.
    var headingHeight by remember { mutableStateOf(0f) }
    var panelHeight by remember { mutableStateOf(0f) }
    val topCover = with(androidx.compose.ui.platform.LocalDensity.current) { LocalTopCover.current.toPx() }
    fun wholeSection() = androidx.compose.ui.geometry.Rect(0f, -topCover, sectionWidth, headingHeight + panelHeight)
    val followScope = rememberCoroutineScope()
    var following by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Where the section is, for working out how far the page has to move to show all of it.
    val sectionPlace = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val page = LocalPageScroll.current
    val bottomRoom = with(androidx.compose.ui.platform.LocalDensity.current) { (LocalBottomCover.current + FOLLOW_BOTTOM_ROOM_DP.dp).toPx() }
    /**
     * Opening, the page scrolls once, alongside the unfolding, to where the whole section will be in view, so the
     * heading moves up to make room rather than the new rows unfolding off the bottom of the screen (user decision).
     * One scroll to the end place, rather than chasing the growing panel, which scrolled twice.
     */
    fun followOpening() {
        following?.cancel()
        following = followScope.launch {
            // Waits for the panel to be built and measured at its full height, which it is from the first frame
            // of unfolding, then scrolls once to where the whole section will show.
            panelHeight = 0f
            kotlinx.coroutines.withTimeoutOrNull(FOLD_MS.toLong()) {
                androidx.compose.runtime.snapshotFlow { panelHeight }.first { it > 0f }
            }
            val place = sectionPlace[0]
            if (page == null || place == null || !place.isAttached) {
                requester.bringIntoView(wholeSection())
                return@launch
            }
            // The page's own scroll, moved on the same curve and time as the unfolding, so the heading rises as the
            // panel grows. Only as far as the panel needs, and never so far the heading goes under the strip.
            val view = page.viewport()
            val top = place.positionInRoot().y - view.top
            val bottom = top + headingHeight + panelHeight
            val wanted = minOf(bottom - (view.height - bottomRoom), top - topCover)
            if (wanted <= 0f) return@launch
            val from = page.state.value
            val start = io.github.dragjon.eigentone.platform.SystemClock.uptimeMillis()
            while (true) {
                val t = ((io.github.dragjon.eigentone.platform.SystemClock.uptimeMillis() - start).toFloat() / FOLD_MS).coerceIn(0f, 1f)
                // The page can only go as far as its content has grown so far; it catches up as the panel opens.
                page.state.scrollTo((from + wanted * FastOutSlowInEasing.transform(t)).toInt())
                if (t >= 1f && page.state.value >= (from + wanted).toInt() - 1) break
                if (io.github.dragjon.eigentone.platform.SystemClock.uptimeMillis() - start > FOLD_MS * 3) break
                androidx.compose.runtime.withFrameNanos { }
            }
        }
    }
    LaunchedEffect(title) {
        if (title == null) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { pageShown() && SectionFocus.request == title }.collect { wanted ->
            if (!wanted) return@collect
            open = true
            // Waits for the tab to slide in and the section to unfold, then brings all of it into view.
            delay(FOLD_MS + 120L)
            requester.bringIntoView(wholeSection())
            SectionFocus.request = null
        }
    }
    // Pressing a folding heading lights a soft circle behind its chevron, as an icon button answers a tap.
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val held by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 60 else 220), label = "foldPress")
    // Every tap flashes the circle, even one too quick for the press to show.
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    val flashScope = rememberCoroutineScope()
    val glow = maxOf(held, flash.value)
    // A studio panel: a slim header bar with a disclosure triangle and the name in small capitals, and the
    // controls straight on the panel's grey below it. Square, and set apart by the gap between panels.
    Column(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .onSizeChanged { sectionWidth = it.width.toFloat() }
            .onGloballyPositioned { sectionPlace[0] = it }
            .background(Palette.Surface),
    ) {
        if (title != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headingHeight = it.height.toFloat() }
                    .height(PANEL_HEADER_DP.dp)
                    .background(Palette.SurfaceHigh)
                    .handCursor().clickable(
                        interactionSource = press,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = stringResource(if (open) Res.string.ui_fold_close else Res.string.ui_fold_open),
                    ) {
                        open = !open
                        if (open) followOpening() else following?.cancel()
                    }
                    .padding(start = 6.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (glow > 0f) Palette.Text else Palette.TextDim,
                    modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = turn / 2f },
                )
                Text(
                    title.uppercase(),
                    style = PanelLegend,
                    color = Palette.Text,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp).weight(1f),
                )
                if (!open && summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (summary == stringResource(Res.string.ui_all_off)) Palette.TextDim else Palette.Lit,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp).padding(end = 6.dp),
                    )
                }
                trailing?.invoke()
            }
        }
        AnimatedVisibility(
            visible = shown,
            enter = expandVertically(tween(FOLD_MS, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(FOLD_MS)),
            exit = shrinkVertically(tween(FOLD_MS, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(FOLD_MS / 2)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { panelHeight = it.height.toFloat() }
                    .animateContentSize(tween(FOLD_MS, easing = FastOutSlowInEasing))
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(PANEL_ROW_GAP_DP.dp),
                content = content,
            )
        }
    }
}

private const val PANEL_HEADER_DP = 28
private const val PANEL_ROW_GAP_DP = 4

/** How long a section takes to fold open or closed. */
internal const val FOLD_MS = 260

/** How much of the top of a page lies under something floating over it, such as the status strip. */
val LocalTopCover = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** How much of the bottom of a page lies under the tabs, which the page now runs beneath. */
val LocalBottomCover = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** Room kept below an opened section, clear of the floating save bar's edge. */
private const val FOLLOW_BOTTOM_ROOM_DP = 16

/** The scroll of the page a section is on, and where that page shows on screen. */
class PageScroll(val state: androidx.compose.foundation.ScrollState, val viewport: () -> androidx.compose.ui.geometry.Rect)

val LocalPageScroll = androidx.compose.runtime.compositionLocalOf<PageScroll?> { null }

/** A section another part of the app wants shown, by its title. The section clears it once it has opened. */
object SectionFocus {
    var request by mutableStateOf<String?>(null)
}

/**
 * Whether the page this is on is the tab in view. Pages out of view stay built but shouldn't act. It's asked
 * rather than handed over as a value, so a tab switch only reaches the few places that ask, at the moment they
 * ask, instead of rebuilding the page.
 */
val LocalPageShown = androidx.compose.runtime.staticCompositionLocalOf<() -> Boolean> { { true } }

/** Joins what's on into one short line for a folded section, or says that nothing is. */
@Composable
fun foldSummary(parts: List<String>): String = parts.joinToString(" · ").ifEmpty { stringResource(Res.string.ui_all_off) }

@Composable
fun RowDivider() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
}

/** Past this many characters a tip opens as a dialog that scrolls, since a tooltip would run off the screen. */
private const val LONG_TIP_CHARS = 300

/**
 * Shows [tip] beside what [anchor] draws: a tooltip for a short explanation, a dialog for a long one.
 * [anchor] gets the function that opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipHost(tip: Tip, anchor: @Composable (open: () -> Unit) -> Unit) {
    val body = stringResource(tip.body)
    if (body.length > LONG_TIP_CHARS) {
        var open by remember { mutableStateOf(false) }
        anchor { open = true }
        if (open) {
            MotionDialog(
                onDismissRequest = { open = false },
                containerColor = Palette.Tooltip,
                titleContentColor = Palette.Text,
                textContentColor = Palette.Text,
                title = { Text(stringResource(tip.title), style = MaterialTheme.typography.titleMedium) },
                text = {
                    Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.verticalScroll(rememberScrollState()))
                },
                confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(Res.string.ui_done)) } },
            )
        }
        return
    }
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = {
            RichTooltip(
                title = { Text(stringResource(tip.title), style = MaterialTheme.typography.titleSmall) },
                colors = TooltipDefaults.richTooltipColors(
                    containerColor = Palette.Tooltip,
                    contentColor = Palette.Text,
                    titleContentColor = Palette.Text,
                ),
            ) { Text(body, style = MaterialTheme.typography.bodyMedium) }
        },
        state = state,
        enableUserInput = false,
    ) { anchor { scope.launch { state.show() } } }
}

/** Opens an explanation next to the setting. Tapping anywhere else closes it. */
@Composable
fun InfoButton(tip: Tip) {
    TipHost(tip) { open ->
        IconButton(onClick = open) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(Res.string.ui_about_setting, stringResource(tip.title)),
                tint = Palette.TextDim,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Something too small or too plain for an info button beside it, such as a meter, which explains itself
 * when tapped instead. The touch area is [modifier]'s, so it can be wider than what it draws.
 */
@Composable
fun TipOnTap(tip: Tip, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val about = stringResource(Res.string.ui_about_setting, stringResource(tip.title))
    TipHost(tip) { open ->
        androidx.compose.foundation.layout.Box(
            modifier.handCursor().clickable(onClickLabel = about, role = Role.Button, onClick = open),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/**
 * A setting's name. Its explanation shows on hover, as in professional desktop tools, rather than behind an
 * info button after every label, which a dense panel has no room for.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SettingLabel(title: String, tip: Tip?, modifier: Modifier = Modifier, color: Color = Palette.Text) {
    val label = @Composable {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (tip == null) {
        androidx.compose.foundation.layout.Box(modifier) { label() }
        return
    }
    androidx.compose.foundation.TooltipArea(
        tooltip = { HoverTip(tip) },
        delayMillis = HOVER_TIP_DELAY_MS,
        modifier = modifier,
    ) { label() }
}

/** A setting's explanation, as a hover card. */
@Composable
fun HoverTip(tip: Tip) {
    Column(
        Modifier
            .widthIn(max = 340.dp)
            .shadow(8.dp, RoundedCornerShape(4.dp))
            .background(Palette.Tooltip, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(stringResource(tip.title), style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        Text(
            stringResource(tip.body),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextDim,
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private const val HOVER_TIP_DELAY_MS = 600

/** A compact on/off toggle for dense panels, drawn rather than Material's large switch. */
@Composable
fun StudioToggle(checked: Boolean, modifier: Modifier = Modifier) {
    val knob by animateFloatAsState(if (checked) 1f else 0f, tween(120), label = "toggle")
    Canvas(modifier.size(width = 28.dp, height = 14.dp)) {
        val r = size.height / 2
        drawRoundRect(
            if (checked) Palette.Lit else Palette.Track,
            cornerRadius = CornerRadius(r),
        )
        val d = size.height - 4.dp.toPx()
        val x = 2.dp.toPx() + (size.width - 4.dp.toPx() - d) * knob
        drawCircle(if (checked) Palette.Background else Palette.TextDim, d / 2, Offset(x + d / 2, size.height / 2))
    }
}

@Composable
fun SwitchSetting(title: String, tip: Tip, checked: Boolean, detail: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp)
            .handCursor().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SettingLabel(title, tip)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        // The whole row toggles and announces itself, so the toggle only draws the state.
        StudioToggle(checked, Modifier.padding(start = 12.dp))
    }
}

/**
 * A row that opens something or runs an action. [opens] draws the chevron, so a row that acts at once,
 * such as copying or deleting, doesn't look like it leads somewhere. [danger] is for removing things.
 */
@Composable
fun ActionRow(
    title: String,
    tip: Tip,
    detail: String? = null,
    enabled: Boolean = true,
    opens: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .handCursor().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // A row that can't be used now looks it, rather than only ignoring taps.
            val color = when {
                !enabled -> Palette.TextDim.copy(alpha = 0.6f)
                danger -> Palette.Danger
                else -> Palette.Text
            }
            SettingLabel(title, tip, color = color)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (opens) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) Palette.TextDim else Palette.TextDim.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Opens more settings inside a section. Looks and works like a folding section's header, at row size. */
@Composable
fun DisclosureRow(label: String, open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(FOLD_MS, easing = FastOutSlowInEasing), label = "disclosure")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp)
            .clip(KeyShape)
            .handCursor().clickable(
                role = Role.Button,
                onClickLabel = stringResource(if (open) Res.string.ui_fold_close else Res.string.ui_fold_open),
            ) { onToggle() }
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Plain text with a chevron, like every other row; amber stays for what's lit or active.
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = Palette.TextDim,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .graphicsLayer { rotationZ = turn },
        )
    }
}

/** A choice from a short list, as a studio's panels offer one: the name, then a compact drop-down. */
@Composable
fun DropdownSetting(title: String, tip: Tip?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).height(SLIDER_ROW_DP.dp + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLabel(title, tip, Modifier.fillMaxWidth(SLIDER_LABEL_SHARE), color = Palette.TextDim)
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Palette.Background)
                    .handCursor().clickable(role = Role.DropdownList) { open = true }
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(options.getOrElse(selected) { "" }, style = MaterialTheme.typography.bodyMedium, color = Palette.Text, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Palette.TextDim, modifier = Modifier.size(16.dp))
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.Tooltip) {
                options.forEachIndexed { i, option ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(option, style = MaterialTheme.typography.bodyMedium, color = if (i == selected) Palette.Lit else Palette.Text) },
                        onClick = {
                            open = false
                            if (i != selected) onSelect(i)
                        },
                        modifier = Modifier.height(30.dp),
                    )
                }
            }
        }
    }
}

/** Reads a value out. Lit means it differs from the default, so changes are easy to spot. */
@Composable
fun SliderSetting(
    title: String,
    tip: Tip,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    origin: Float = range.start,
    detail: String? = null,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit = {},
    /** Where a double tap returns it, such as the value saved in the preset. */
    resetTo: Float = default,
) {
    val changed = abs(value - default) > (range.endInclusive - range.start) * 0.005f
    // As in a studio's develop panel: the name, the slider and the value on one line.
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 1.dp, bottom = 1.dp)) {
        Row(Modifier.fillMaxWidth().height(SLIDER_ROW_DP.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingLabel(title, tip, Modifier.fillMaxWidth(SLIDER_LABEL_SHARE), color = Palette.TextDim)
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                ValueTrack(
                    label = title,
                    readout = readout,
                    value = value,
                    range = range,
                    origin = origin,
                    default = default,
                    onChange = onChange,
                    onFinished = onFinished,
                    resetTo = resetTo,
                )
            }
            Text(
                readout,
                style = Readout,
                color = if (changed) Palette.Lit else Palette.Text,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(SLIDER_VALUE_DP.dp),
            )
        }
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
    }
}

/**
 * Replaces Material's Slider, which jumps to wherever it's touched and fills from the left only.
 *
 * - Only a sideways drag changes the value, so a vertical scroll that starts here can't alter a
 *   setting, and a stray tap does nothing.
 * - The value moves relative to the finger rather than jumping to it.
 * - It fills from [origin], so a centred control at 0 dB looks untouched.
 * - A double tap returns to [resetTo], which is [default] unless given.
 *
 * It stays a real slider for screen readers through its progress semantics.
 */
@Composable
fun ValueTrack(
    label: String,
    readout: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    origin: Float,
    default: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    resetTo: Float = default,
    /** Draws the whole track in these colours, left to right, instead of filled and unfilled, as for a hue. */
    trackColours: List<androidx.compose.ui.graphics.Color>? = null,
) {
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    val resetValue by rememberUpdatedState(resetTo)
    val span = range.endInclusive - range.start
    val clamped = value.coerceIn(range)
    val view = LocalView.current
    val defaultNow by rememberUpdatedState(default)
    // Material's own colour for the unfilled track, a touch deeper so it still shows on a light panel.
    val unfilled = Palette.Track2

    Canvas(
        Modifier
            .dragCursor(horizontal = true)
            .fillMaxWidth()
            .height(SLIDER_ROW_DP.dp)
            .semantics {
                contentDescription = label
                stateDescription = readout
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, range)
                setProgress { target ->
                    change(target.coerceIn(range))
                    finished()
                    true
                }
            }
            .pointerInput(span) {
                var lastTapAt = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Only a drag that starts on the handle moves it, so a finger landing elsewhere on the track
                    // and sliding can't change the setting by accident (tester feedback). The reach is a
                    // finger's width either side of the narrow handle.
                    val handleX = TRACK_HANDLE_DP.dp.toPx() / 2 +
                        (current.coerceIn(range) - range.start) / span * (size.width - TRACK_HANDLE_DP.dp.toPx())
                    val onHandle = kotlin.math.abs(down.position.x - handleX) <= HANDLE_GRAB_DP.dp.toPx()
                    val drag = if (onHandle) {
                        awaitHorizontalTouchSlopOrCancellation(down.id) { event, _ -> event.consume() }
                    } else {
                        // Off the handle, only a tap counts, which a double tap uses to reset.
                        waitForUpOrCancellation()
                        null
                    }
                    if (drag == null) {
                        // Released without dragging sideways, or a vertical scroll took over.
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        val isTap = up != null && !up.pressed && !up.isConsumed
                        if (isTap) {
                            val now = up!!.uptimeMillis
                            if (now - lastTapAt < DOUBLE_TAP_MS) {
                                Haptics.tick(view)
                                change(resetValue)
                                finished()
                                lastTapAt = 0L
                            } else {
                                lastTapAt = now
                            }
                        }
                        return@awaitEachGesture
                    }
                    // The handle travels the width less its own, so the value keeps pace with the finger.
                    val width = (size.width - TRACK_HANDLE_DP.dp.toPx()).coerceAtLeast(1f)
                    var running = current
                    horizontalDrag(drag.id) { event ->
                        val moved = event.positionChange().x
                        val before = running
                        running = (running + moved / width * span).coerceIn(range)
                        // A tick passing the default, and a firmer one reaching either end, felt without looking.
                        Haptics.crossing(view, before, running, defaultNow, range)
                        change(running)
                        event.consume()
                    }
                    finished()
                }
            },
    ) {
        // A studio slider: a hairline track, filled from its zero point, with a small round thumb.
        val handleWidth = TRACK_HANDLE_DP.dp.toPx()
        val trackHeight = TRACK_HEIGHT_DP.dp.toPx()
        val usable = size.width - handleWidth
        val xOf = { v: Float -> handleWidth / 2 + (v - range.start) / span * usable }
        val centre = size.height / 2
        val to = xOf(clamped)
        if (trackColours != null) {
            drawRoundRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(trackColours, startX = handleWidth / 2, endX = size.width - handleWidth / 2),
                topLeft = Offset(handleWidth / 2, centre - 2.dp.toPx()),
                size = Size(usable, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        } else {
            drawRoundRect(
                unfilled,
                topLeft = Offset(handleWidth / 2, centre - trackHeight / 2),
                size = Size(usable, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2),
            )
            val from = xOf(origin.coerceIn(range))
            drawRect(
                Palette.TextDim,
                topLeft = Offset(minOf(from, to), centre - trackHeight / 2),
                size = Size(kotlin.math.abs(to - from), trackHeight),
            )
            // A short tick at the default, so a change from it reads at a glance.
            val tick = xOf(default.coerceIn(range))
            drawRect(Palette.TextDim, topLeft = Offset(tick - 0.5.dp.toPx(), centre - 4.dp.toPx()), size = Size(1.dp.toPx(), 8.dp.toPx()))
        }
        drawCircle(Palette.Background, handleWidth / 2 + 1.dp.toPx(), Offset(to, centre))
        drawCircle(Palette.LitFill, handleWidth / 2, Offset(to, centre))
    }
}

/**
 * The step between a slider's notches: a round figure that splits the range into about five, such as
 * 5 dB on a ±12 dB control, matching the equaliser's faders, or 0.2 on one from 0 to 1.
 */
internal fun notchStep(range: ClosedFloatingPointRange<Float>): Float {
    val span = (range.endInclusive - range.start).coerceAtLeast(1e-6f)
    val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(span / 8.0))).toFloat()
    return listOf(1f, 2f, 2.5f, 5f, 10f, 20f, 25f)
        .map { it * magnitude }
        .filter { span / it in 3f..10f }
        .minByOrNull { kotlin.math.abs(span / it - 5f) }
        ?: (span / 5f)
}

private const val TRACK_HANDLE_DP = 10
/** How far either side of a slider's handle a touch still grabs it: a 48 dp target. */
private const val HANDLE_GRAB_DP = 10
private const val TRACK_HANDLE_HEIGHT_DP = 40
private const val TRACK_HEIGHT_DP = 2
private const val SLIDER_ROW_DP = 22
private const val SLIDER_VALUE_DP = 56
private const val SLIDER_LABEL_SHARE = 0.34f
private const val TRACK_GAP_DP = 6

/**
 * Whether the system's own spatial audio is on. Android reports its spatializer; Windows Sonic can't be read
 * the same way, so on Windows this is always off and Eigentone's surround is offered as usual.
 */
@Composable
fun rememberSystemSpatialAudio(): Boolean = false

/** Names of the connected audio outputs, kept current as devices come and go, from Eigentone's service. */
@Composable
fun rememberConnectedOutputNames(): List<String> {
    val names by io.github.dragjon.eigentone.engine.OutputDetector.names.collectAsState()
    return names
}

/** Whether any app is playing right now, from the level Eigentone's plug-in measures. */
@Composable
fun rememberMusicActive(): Boolean {
    val active by io.github.dragjon.eigentone.engine.AudioService.musicActive.collectAsState()
    return active
}

/** Eigentone changes another app's sound and plays nothing itself, which isn't obvious in a listening test. */
@Composable
fun NothingPlayingHint(modifier: Modifier = Modifier) {
    if (rememberMusicActive()) return
    Text(
        stringResource(Res.string.ui_nothing_is_playing_start_your_music_app),
        style = MaterialTheme.typography.bodySmall,
        color = Palette.Text,
        modifier = modifier,
    )
}

/** A switch that's on has a light knob in a light theme (user decision), rather than a dark one. */
@Composable
fun switchColors() = androidx.compose.material3.SwitchDefaults.colors(
    checkedThumbColor = if (Palette.dark) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary else androidx.compose.ui.graphics.Color.White,
    checkedIconColor = Palette.LitFill,
)

/** Text fields take the lit text colour when focused, since their label and cursor have to be read. */
@Composable
fun fieldColors(
    unfocusedBorder: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
) = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = unfocusedBorder,
    focusedBorderColor = Palette.Lit,
    focusedLabelColor = Palette.Lit,
    cursorColor = Palette.Lit,
    selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
        handleColor = Palette.Lit,
        backgroundColor = Palette.LitFill.copy(alpha = 0.4f),
    ),
)

/** The gaps between sections, kept in one place. */
val SectionSpacing = Arrangement.spacedBy(12.dp)

/**
 * Touch feedback for the moments that matter: passing a default, reaching an end, switching something
 * on or off, and confirming. Everyday taps stay silent, so these mean something.
 */
object Haptics {
    /** Off when touch feedback is turned off under Accessibility. PCs have no vibration, so these do nothing. */
    @Volatile var enabled = true

    fun tick(@Suppress("UNUSED_PARAMETER") view: Any?) = Unit

    fun limit(@Suppress("UNUSED_PARAMETER") view: Any?) = Unit

    fun toggle(@Suppress("UNUSED_PARAMETER") view: Any?, @Suppress("UNUSED_PARAMETER") on: Boolean) = Unit

    fun confirm(@Suppress("UNUSED_PARAMETER") view: Any?) = Unit

    /** Feedback for a value moving from [before] to [after]: past [default], or arriving at an end of [range]. */
    fun crossing(view: Any?, before: Float, after: Float, default: Float, range: ClosedFloatingPointRange<Float>) {
        if (before == after) return
        val reachedEnd = (after == range.start || after == range.endInclusive) && before != after
        val passedDefault = (before < default && after >= default) || (before > default && after <= default)
        when {
            reachedEnd -> limit(view)
            passedDefault -> tick(view)
        }
    }
}

/**
 * A search box sized for a studio's panels: one 28 dp line, a small search icon, a hint that stays put
 * rather than floating up as a label, and a clear key once there's something to clear. Recessed like the
 * sliders' tracks, and outlined in the accent while typing.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.Lit),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier
            .height(SEARCH_FIELD_DP.dp)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(KeyShape)
                    .background(Palette.Background)
                    .border(1.dp, if (focused) Palette.Lit else Palette.Track, KeyShape)
                    .padding(start = 8.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.Search,
                    contentDescription = null,
                    tint = Palette.TextDim,
                    modifier = Modifier.size(16.dp),
                )
                Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    field()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(KeyShape)
                            .handCursor().clickable { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Filled.Close,
                            contentDescription = stringResource(Res.string.ui_clear_search),
                            tint = Palette.TextDim,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        },
    )
}

private const val SEARCH_FIELD_DP = 28

/**
 * The pointer says what a control does, as in other Windows apps: a hand over what can be clicked, a two-way
 * arrow over what's dragged, a crosshair over the curve. Text boxes show the text cursor themselves.
 */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Hand)

fun Modifier.dragCursor(horizontal: Boolean): Modifier = pointerHoverIcon(
    androidx.compose.ui.input.pointer.PointerIcon(
        java.awt.Cursor(if (horizontal) java.awt.Cursor.E_RESIZE_CURSOR else java.awt.Cursor.N_RESIZE_CURSOR),
    ),
)

fun Modifier.crossCursor(): Modifier = pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon.Crosshair)
