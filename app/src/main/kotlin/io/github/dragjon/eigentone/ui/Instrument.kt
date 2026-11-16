package io.github.dragjon.eigentone.ui

import org.jetbrains.compose.resources.*

import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dragjon.eigentone.resources.*

private const val MORPH_MS = 220
private const val RAIL_TICKS = 24

/** Where text comes from outside of composition, such as a message shown after a file is saved. */
object AppText

/** Reading a string outside a screen. Compose Multiplatform reads resources asynchronously; these are quick. */
fun appText(id: StringResource, vararg args: Any?): String =
    kotlinx.coroutines.runBlocking { org.jetbrains.compose.resources.getString(id, *args.map { it ?: "" }.toTypedArray()) }

fun appPlural(id: PluralStringResource, count: Int, vararg args: Any?): String =
    kotlinx.coroutines.runBlocking {
        org.jetbrains.compose.resources.getPluralString(id, count, *args.map { it ?: "" }.toTypedArray())
    }

/** The frame every tool shares: a bar with a way back, its progress if it has steps, then the page. */
@Composable
fun ToolScreen(
    title: String,
    onBack: () -> Unit,
    progress: Float? = null,
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Bar)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.ui_back),
                        tint = Palette.Text,
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) StepRail(progress)
        }
        // In a wide pane the page is wider than a phone's, and the tool's own explanation stands beside it, so
        // the room either side isn't left empty.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val aside = LocalToolAside.current?.takeIf { maxWidth >= TOOL_ASIDE_FROM_DP.dp }
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier
                            .widthIn(max = TOOL_PAGE_MAX_DP.dp)
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        content = content,
                    )
                }
                if (aside != null) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
                    Column(
                        Modifier
                            .width(TOOL_ASIDE_DP.dp)
                            .fillMaxHeight()
                            .background(Palette.Surface)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(Res.string.win_tool_about).uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                        Text(stringResource(aside.title), style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                        Text(stringResource(aside.body), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                    }
                }
            }
        }
    }
}

/** The explanation of the tool open in the Tools pane, shown beside it when there's room. */
val LocalToolAside = androidx.compose.runtime.compositionLocalOf<Tip?> { null }

private const val TOOL_PAGE_MAX_DP = 760
private const val TOOL_ASIDE_DP = 320
private const val TOOL_ASIDE_FROM_DP = 1000

/**
 * The steps of a tool, such as its intro, its questions and its result. A new step slides in from the
 * right as the last one slides out to the left, and the page's height follows smoothly.
 */
@Composable
fun <S> ToolSteps(step: S, spacing: Dp = 16.dp, content: @Composable ColumnScope.(S) -> Unit) {
    androidx.compose.animation.AnimatedContent(
        targetState = step,
        transitionSpec = {
            (
                androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(STEP_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 5 } +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(STEP_MS, delayMillis = 60))
                ) togetherWith (
                androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(STEP_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 5 } +
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(STEP_MS / 2))
                ) using androidx.compose.animation.SizeTransform(clip = false)
        },
        label = "step",
    ) { shown ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) { content(shown) }
    }
}

private const val STEP_MS = 280

/** How far through a test, as an engraved scale that fills from the left. */@Composable
fun StepRail(target: Float) {
    // Fills to each new answer rather than jumping to it.
    val progress by androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.tween(320), label = "rail")
    val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = "$percent%" },
    ) {
        val tick = 1.dp.toPx()
        for (i in 0..RAIL_TICKS) {
            val x = (size.width - tick) * i / RAIL_TICKS
            val filled = i.toFloat() / RAIL_TICKS <= progress
            drawRect(
                if (filled) Palette.LitFill else Palette.Track,
                topLeft = Offset(x, if (filled) 0f else size.height / 2),
                size = Size(tick, if (filled) size.height else size.height / 2),
            )
        }
    }
}

/** Which of a few notes or stages is active, like the indicator lamps on a panel. */
@Composable
fun Lamps(count: Int, lit: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until count) {
            Canvas(Modifier.size(width = 28.dp, height = 6.dp)) {
                drawRect(if (i == lit) Palette.LitFill else Palette.Track)
            }
        }
    }
}

/** The audiogram's own symbol for an ear: a blue X for the left and a red O for the right. */
@Composable
fun EarMark(left: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val stroke = 2.5.dp.toPx()
        val inset = 3.dp.toPx()
        if (left) {
            drawLine(Palette.LeftEar, Offset(inset, inset), Offset(size.width - inset, size.height - inset), stroke)
            drawLine(Palette.LeftEar, Offset(inset, size.height - inset), Offset(size.width - inset, inset), stroke)
        } else {
            drawCircle(Palette.RightEar, size.minDimension / 2 - inset, style = Stroke(stroke))
        }
    }
}

/**
 * Glides to new values when [key] changes, such as switching preset, so a change looks like what the
 * sound is doing. Other changes go straight through, so a fader never lags behind a finger. The system's
 * Remove animations setting also makes this instant.
 */
@Composable
fun rememberMorphed(target: DoubleArray, key: Any?): DoubleArray {
    val memory = remember { MorphMemory(target, key) }
    var amount by remember { mutableFloatStateOf(1f) }
    if (memory.key != key) {
        memory.key = key
        memory.from = memory.shown
        amount = 0f
    }
    LaunchedEffect(key) {
        if (amount < 1f) {
            animate(0f, 1f, animationSpec = tween(MORPH_MS, easing = FastOutSlowInEasing)) { value, _ -> amount = value }
        }
    }
    val from = memory.from
    val shown = if (amount >= 1f || from.size != target.size) {
        target
    } else {
        DoubleArray(target.size) { from[it] + (target[it] - from[it]) * amount }
    }
    memory.shown = shown
    return shown
}

private class MorphMemory(var from: DoubleArray, var key: Any?) {
    var shown: DoubleArray = from
}
