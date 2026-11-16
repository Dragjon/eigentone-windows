package io.github.dragjon.eigentone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How wide the page's content is, so a page can choose a layout for the room it has. */
val LocalPageWidth = compositionLocalOf { 0.dp }

/** How many columns of sections fit a content width: one in a narrow window, up to three on a wide screen. */
fun columnsFor(width: Dp): Int = when {
    width < TWO_COLUMNS_FROM -> 1
    width < THREE_COLUMNS_FROM -> 2
    else -> 3
}

private val TWO_COLUMNS_FROM = 760.dp
private val THREE_COLUMNS_FROM = 1320.dp

/** Marks a section that runs across every column, such as a page's heading. */
fun Modifier.fullSpan(): Modifier = this.then(FullSpan)

private object FullSpan : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = FullSpan
}

/**
 * A page's sections in columns, as Windows Settings lays out its cards: each goes into whichever column is
 * shortest so far, so the columns stay even and the reading order stays roughly top to bottom. A section
 * marked [fullSpan] starts below everything before it and runs across all the columns.
 */
@Composable
fun SectionColumns(columns: Int, gap: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val columnWidth = ((width - gapPx * (columns - 1)) / columns).coerceAtLeast(0)
        val tops = IntArray(columns)
        val placed = measurables.map { m ->
            val spans = columns == 1 || m.parentData === FullSpan
            if (spans) {
                val top = tops.max()
                val p = m.measure(Constraints(maxWidth = width))
                val next = top + p.height + if (p.height > 0) gapPx else 0
                tops.fill(next)
                Triple(p, 0, top)
            } else {
                val c = tops.indices.minBy { tops[it] }
                val p = m.measure(Constraints(maxWidth = columnWidth))
                val top = tops[c]
                if (p.height > 0) tops[c] += p.height + gapPx
                Triple(p, c * (columnWidth + gapPx), top)
            }
        }
        val height = (tops.max() - gapPx).coerceAtLeast(0)
        layout(width, height) {
            placed.forEach { (p, x, y) -> p.place(x, y) }
        }
    }
}
