package io.github.dragjon.eigentone.ui

import org.jetbrains.compose.resources.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dragjon.eigentone.resources.*
import io.github.dragjon.eigentone.data.ThemeMode

/** Every colour role, so a theme can only be complete. */
data class ThemeColors(
    val background: Color,
    /** The top and bottom bars, a step darker than the page so they read as fixed. */
    val bar: Color,
    /** Behind the status strip, so it stands apart from both the bars and the page. */
    val strip: Color,
    /** Things that are off, on the strip's green. */
    val stripDim: Color,
    /** Plain text on the strip's green, which stays dark in every theme. */
    val stripText: Color,
    /** Things that are on, on the strip's green. */
    val stripOn: Color,
    /** A warning on the strip's green. */
    val stripDanger: Color,
    /** A softer green under the strip when it's opened, so the details read as a drawer pulled out of it. */
    val stripPanel: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val tooltip: Color,
    /** Hairlines between rows. Decorative, so they sit below control contrast on purpose. */
    val line: Color,
    /** Control boundaries such as off switches and segments. At least 3:1 against every surface. */
    val outline: Color,
    val track: Color,
    val text: Color,
    val textDim: Color,
    /** Lit text, readouts and lines, which have to be read on the page. */
    val lit: Color,
    /** What lit controls are filled with: keys, segments, caps, rails and switches. */
    val litFill: Color,
    /** A lit control while held. */
    val litPressed: Color,
    /** Legends on [litFill]. */
    val onLit: Color,
    /** Orange rather than red, so a problem can't be mistaken for the right ear. */
    val danger: Color,
    val leftEar: Color,
    /** Also dashed on graphs, so colour isn't the only difference between the ears. */
    val rightEar: Color,
    /** Text in the strip's opened drawer, made for its tone. Unspecified means the strip's own text colours suit it. */
    val drawerText: Color = Color.Unspecified,
    val drawerDim: Color = Color.Unspecified,
)

/**
 * Modern near-black (user decision): neutral greys, with no brown or blue cast, so the amber, the ear colours and the green
 * strip are the only colour on screen. Amber means one thing: lit or active, like a glowing meter.
 */
val DarkColors = ThemeColors(
    background = Color(0xFF0F0F0F),
    bar = Color(0xFF090909),
    strip = Color(0xFF174035),
    stripDim = Color(0xFFBFD3C9),
    stripText = Color(0xFFEEEEEA),
    stripOn = Color(0xFFFFC24A),
    stripDanger = Color(0xFFFF9A5C),
    stripPanel = Color(0xFF1F4B3F),
    surface = Color(0xFF191919),
    surfaceHigh = Color(0xFF252525),
    tooltip = Color(0xFF292929),
    line = Color(0xFF2D2D2D),
    outline = Color(0xFF909090),
    track = Color(0xFF767676),
    text = Color(0xFFF2F2F2),
    textDim = Color(0xFFADADAD),
    lit = Color(0xFFFFC24A),
    litFill = Color(0xFFFFC24A),
    litPressed = Color(0xFFE0A53A),
    onLit = Color(0xFF2A1C00),
    danger = Color(0xFFFF9A5C),
    leftEar = Color(0xFF62B2F2),
    rightEar = Color(0xFFF06B5C),
)

/**
 * The same instrument in daylight, made modern (user decision): a white page with soft neutral-grey panels, bars only
 * a breath off white, and charcoal rather than black text, with no warm paper tones. Lit controls are filled with the
 * same bright amber as at night, under dark legends. Amber that has to be read as text on white would need to be so
 * dark it turns brown, so lit text and lines are a vivid orange instead, and warnings move to a deeper red-orange to
 * stay apart from it. The strip is a light amber tint with dark legends, and the ear colours deepen so they read as
 * ink on a chart, as an audiogram does on paper.
 */
val LightColors = ThemeColors(
    background = Color(0xFFFFFFFF),
    bar = Color(0xFFF7F7F7),
    strip = Color(0xFFFFE7C7),
    stripDim = Color(0xFF6B5640),
    stripText = Color(0xFF1A1206),
    stripOn = Color(0xFF8A4200),
    stripDanger = Color(0xFF8A1A00),
    stripPanel = Color(0xFFFFF2E1),
    surface = Color(0xFFF4F4F4),
    surfaceHigh = Color(0xFFEAEAEA),
    tooltip = Color(0xFFFFFFFF),
    line = Color(0xFFE5E5E5),
    outline = Color(0xFF868686),
    track = Color(0xFF8A8A8A),
    text = Color(0xFF1F1F1F),
    textDim = Color(0xFF5F5F5F),
    lit = Color(0xFFB35300),
    litFill = Color(0xFFFFB21A),
    litPressed = Color(0xFFF2A100),
    onLit = Color(0xFF241800),
    danger = Color(0xFFA82A00),
    leftEar = Color(0xFF0A5FAD),
    rightEar = Color(0xFFC4231A),
)

/**
 * The colours in use. These are read everywhere, including while drawing graphs, and reading one here
 * means that a theme change redraws whatever used it.
 */
object Palette {
    var colors by mutableStateOf(DarkColors)
        internal set

    /** Whether the theme in use is dark, whichever palette it came from. */
    var dark by mutableStateOf(true)
        internal set

    val Background get() = colors.background
    val Bar get() = colors.bar
    val Strip get() = colors.strip
    val StripDim get() = colors.stripDim
    val StripText get() = colors.stripText
    val StripOn get() = colors.stripOn
    val StripDanger get() = colors.stripDanger
    val StripPanel get() = colors.stripPanel
    val DrawerText get() = colors.drawerText.takeOrElse { colors.stripText }
    val DrawerOn get() = colors.drawerText.takeOrElse { colors.stripOn }
    val DrawerDim get() = colors.drawerDim.takeOrElse { colors.stripDim }
    val Surface get() = colors.surface
    val SurfaceHigh get() = colors.surfaceHigh
    val Tooltip get() = colors.tooltip
    val Line get() = colors.line
    val Outline get() = colors.outline
    val Track get() = colors.track
    val Text get() = colors.text
    val TextDim get() = colors.textDim
    val Lit get() = colors.lit
    val LitFill get() = colors.litFill

    /** The selected fader's cap: bright at night, and the lit orange by day rather than a black block. */
    val SelectedCap get() = if (dark) colors.text else colors.lit

    /** Lit text that has to stand out on a dark page, such as the levels above the faders: lighter at night. */
    val LitBright get() = if (dark) androidx.compose.ui.graphics.lerp(colors.lit, Color.White, 0.45f) else colors.lit

    /**
     * The unfilled part of every selector, slider and fader track. At night a touch of the theme colour; by day a
     * plain grey, since the tinted container looked off on white (user decision).
     */
    val Track2 get() = if (dark) {
        androidx.compose.ui.graphics.lerp(colors.surfaceHigh, colors.litFill, 0.16f)
    } else {
        // Light enough to sit gently on the white page as well as on a panel.
        androidx.compose.ui.graphics.lerp(colors.surfaceHigh, colors.background, 0.35f)
    }

    /** Floating notes, such as unsaved changes and messages: a tint of the theme colour rather than grey (user decision). */
    val Note get() = androidx.compose.ui.graphics.lerp(colors.surfaceHigh, colors.litFill, if (dark) 0.22f else 0.14f)
    val NoteText get() = colors.text

    /** The edge of an unlit key or toggle: clear enough to see on any panel, quieter than a control's outline. */
    val KeyBorder get() = androidx.compose.ui.graphics.lerp(colors.line, colors.outline, 0.55f)
    val LitPressed get() = colors.litPressed
    val OnLit get() = colors.onLit
    val Danger get() = colors.danger
    val LeftEar get() = colors.leftEar
    val RightEar get() = colors.rightEar

    /**
     * The light a lit control gives off: amber on the dark faceplate. Nothing in daylight, where a lit
     * control is dark ink and couldn't glow.
     */
    // None in Material You, where lit things are flat (user decision).
    val Glow get() = Color.Transparent
}

/**
 * The same colours with quiet text, hairlines and tracks drawn nearer the main text, for anyone who finds
 * the dim greys hard to read.
 */
internal fun ThemeColors.contrasted(): ThemeColors {
    fun toward(from: Color, to: Color, amount: Float) = androidx.compose.ui.graphics.lerp(from, to, amount)
    return copy(
        textDim = toward(textDim, text, 0.5f),
        line = toward(line, outline, 0.6f),
        outline = toward(outline, text, 0.35f),
        track = toward(track, text, 0.3f),
        stripDim = toward(stripDim, stripText, 0.5f),
    )
}

/**
 * A colour being tried while the hue slider moves. The theme wears it at once, so the app recolours as the
 * finger slides; it's saved as the setting once the finger lifts, and cleared.
 */
object ThemePreview {
    var seed by mutableStateOf<Long?>(null)
}

/** Whether this choice means dark right now. Follows the device live, including a dark mode schedule. */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    // Follows the phone; MainActivity keeps Android's choice for the app on "follow the phone" whenever Device is set.
    ThemeMode.DEVICE -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

// Windows 11's own typeface, read from the system: Text for reading, Display for headings and labels. Older
// Windows without it falls back to Segoe UI.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun segoe(family: String, weights: List<FontWeight>) = FontFamily(
    weights.flatMap { w ->
        listOf(
            androidx.compose.ui.text.platform.SystemFont(family, w),
            androidx.compose.ui.text.platform.SystemFont("Segoe UI", w),
        )
    },
)

private val Body: FontFamily = segoe("Segoe UI Variable Text", listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold))

private val Engraved: FontFamily = segoe("Segoe UI Variable Display", listOf(FontWeight.Medium, FontWeight.SemiBold))

val ReadoutFamily: FontFamily @Composable get() = FontFamily(Font(Res.font.jetbrains_mono))

private val Type: Typography @Composable get() = Typography(
    displaySmall = TextStyle(fontFamily = Engraved, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium),
    headlineMedium = TextStyle(fontFamily = Engraved, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = Engraved, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = Engraved, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 11.sp, lineHeight = 14.sp),
    // Centred in its line and trimmed, so a word sits in the middle of a pill button rather than low in it.
    labelLarge = TextStyle(
        fontFamily = Body,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
        ),
    ),
    labelMedium = TextStyle(fontFamily = Engraved, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Engraved, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium),
)

/** A panel's engraved legend. */
val PanelLegend: TextStyle @Composable get() = TextStyle(
    fontFamily = Engraved,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.2.sp,
)

/** A tool's main measurement, large enough to read at arm's length. */
val BigReadout: TextStyle @Composable get() = TextStyle(fontFamily = ReadoutFamily, fontSize = 38.sp, lineHeight = 44.sp)

/** Monospaced digits, so measurements stay still while their values change. */
val Readout: TextStyle @Composable get() = TextStyle(fontFamily = ReadoutFamily, fontSize = 11.sp, lineHeight = 14.sp)

// Material You's own corner scale, so menus, chips, fields and dialogs are as soft as the rest.
// Windows 11's corner scale: 4 dp on controls, 8 dp on cards, flyouts and dialogs.
private val Corners = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

/**
 * Material You's colours, taken from the wallpaper (user decision), in the app's own roles. The ears and warnings
 * keep their fixed colours, since what they mean can't change with the wallpaper.
 */
private fun fromScheme(s: androidx.compose.material3.ColorScheme, fixed: ThemeColors, dark: Boolean): ThemeColors {
    fun over(top: Color, alpha: Float, under: Color) = androidx.compose.ui.graphics.lerp(under, top, alpha)
    return fixed.copy(
        // The same arrangement in both themes (user decision): the page in Material's surface tone, and the bars in
        // its lowest, a shade apart from the page, near-black at night and white by day.
        background = s.surface,
        // Material's lowest tone: near-black framing the page at night, and white by day (user decisions).
        bar = s.surfaceContainerLowest,
        // The wallpaper's own colour, rather than a grey, so the status window stands out (user decision).
        strip = s.primary,
        stripDim = over(s.onPrimary, 0.72f, s.primary),
        stripText = s.onPrimary,
        stripOn = s.onPrimary,
        // A lighter shade of the strip's own colour for its drawer.
        // At night the strip's colour is a pale tint, so its drawer goes a shade deeper; by day a shade lighter.
        // The drawer is the strip itself carried on down, in the same colour with the same text, set apart only by a
        // faint line and its shadow. A different tone flipped light and dark between the two, so they read as two
        // pieces rather than one bar opening (user decision).
        stripPanel = s.primary,
        // A tinted step off the page in both themes, so panels read as panels on white too.
        surface = s.surfaceContainerLow,
        surfaceHigh = s.surfaceContainerHighest,
        // Menus and dialogs: a raised grey at night, and white with their shadow by day, rather than a dark grey on white.
        tooltip = if (dark) s.surfaceContainerHigh else s.surfaceContainerLowest,
        line = s.outlineVariant,
        outline = s.outline,
        track = s.outline,
        text = s.onSurface,
        textDim = s.onSurfaceVariant,
        lit = s.primary,
        litFill = s.primary,
        litPressed = over(s.onPrimary, 0.12f, s.primary),
        onLit = s.onPrimary,
    )
}

/**
 * The studio's graphite, as in a professional editor such as Lightroom: neutral greys in steps, so panels,
 * headers and the canvas read apart without borders, and controls in light grey rather than colour. The
 * accent (the colour picked, or Windows' own) is kept for what's live: the curve, the selection, what's on.
 */
fun studioColors(accent: Color, dark: Boolean): ThemeColors {
    // Legends on the accent: dark on a light accent, light on a deep one.
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF141414) else Color(0xFFF5F5F5)
    val pressed = androidx.compose.ui.graphics.lerp(accent, if (dark) Color.White else Color.Black, 0.15f)
    return if (dark) ThemeColors(
        background = Color(0xFF1E1E1E),
        bar = Color(0xFF161616),
        strip = Color(0xFF2B2B2B),
        stripDim = Color(0xFF8C8C8C),
        stripText = Color(0xFFD4D4D4),
        stripOn = accent,
        stripDanger = Color(0xFFE8875A),
        stripPanel = Color(0xFF303030),
        surface = Color(0xFF282828),
        surfaceHigh = Color(0xFF333333),
        tooltip = Color(0xFF3A3A3A),
        line = Color(0xFF111111),
        outline = Color(0xFF6E6E6E),
        track = Color(0xFF4A4A4A),
        text = Color(0xFFDADADA),
        textDim = Color(0xFF8E8E8E),
        lit = accent,
        litFill = accent,
        litPressed = pressed,
        onLit = onAccent,
        danger = Color(0xFFE8875A),
        leftEar = Color(0xFF62B2F2),
        rightEar = Color(0xFFF06B5C),
    ) else ThemeColors(
        // A light studio: white panels on a soft grey, as in Windows' own light apps, with crisp dark text.
        background = Color(0xFFEDEDED),
        bar = Color(0xFFF7F7F7),
        strip = Color(0xFFFFFFFF),
        stripDim = Color(0xFF6B6B6B),
        stripText = Color(0xFF1A1A1A),
        stripOn = accent,
        stripDanger = Color(0xFFB3401A),
        stripPanel = Color(0xFFF2F2F2),
        surface = Color(0xFFFFFFFF),
        surfaceHigh = Color(0xFFF1F1F1),
        tooltip = Color(0xFFFFFFFF),
        line = Color(0xFFDCDCDC),
        outline = Color(0xFF8A8A8A),
        track = Color(0xFFD0D0D0),
        text = Color(0xFF1B1B1B),
        textDim = Color(0xFF616161),
        lit = accent,
        litFill = accent,
        litPressed = pressed,
        onLit = onAccent,
        danger = Color(0xFFB3401A),
        leftEar = Color(0xFF0A5FAD),
        rightEar = Color(0xFFC4231A),
    )
}

/** Material's own components (switches, fields, menus) in the same graphite. */
private fun studioScheme(c: ThemeColors, dark: Boolean) = (if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()).copy(
    primary = c.litFill,
    onPrimary = c.onLit,
    primaryContainer = c.surfaceHigh,
    onPrimaryContainer = c.text,
    secondary = c.litFill,
    onSecondary = c.onLit,
    secondaryContainer = c.surfaceHigh,
    onSecondaryContainer = c.text,
    tertiary = c.lit,
    background = c.background,
    onBackground = c.text,
    surface = c.surface,
    onSurface = c.text,
    surfaceVariant = c.surfaceHigh,
    onSurfaceVariant = c.textDim,
    surfaceContainerLowest = c.bar,
    surfaceContainerLow = c.surface,
    surfaceContainer = c.surface,
    surfaceContainerHigh = c.surfaceHigh,
    surfaceContainerHighest = c.tooltip,
    outline = c.outline,
    outlineVariant = c.line,
    inverseSurface = c.text,
    inverseOnSurface = c.background,
)

@Composable
fun EigentoneTheme(dark: Boolean, seed: Long? = null, highContrast: Boolean = false, content: @Composable () -> Unit) {
    // The accent: the colour picked, or else Windows' own, so Eigentone matches the rest of Windows.
    val accentSeed = seed ?: io.github.dragjon.eigentone.platform.WindowsTheme.accent ?: SeedScheme.DEFAULT_SEED
    val colors = androidx.compose.runtime.remember(accentSeed, dark, highContrast) {
        // The seed's own tone at a brightness that reads on the panels: lighter at night, deeper by day.
        val accent = SeedScheme.scheme(accentSeed, dark).primary
        studioColors(accent, dark).let { if (highContrast) it.contrasted() else it }
    }
    val scheme = androidx.compose.runtime.remember(colors, dark) { studioScheme(colors, dark) }
    // Set before anything below reads it, so a change can't show one frame of the old theme.
    if (Palette.colors != colors) Palette.colors = colors
    Palette.dark = dark
    // Scrollbars in the panels' own greys, slim but plain to see, and brighter under the pointer.
    val scrollbars = androidx.compose.foundation.ScrollbarStyle(
        minimalHeight = 24.dp,
        thickness = 6.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
        hoverDurationMillis = 200,
        unhoverColor = colors.textDim.copy(alpha = 0.35f),
        hoverColor = colors.textDim.copy(alpha = 0.75f),
    )
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.LocalScrollbarStyle provides scrollbars) {
        MaterialTheme(colorScheme = scheme, typography = Type, shapes = Corners, content = content)
    }
}
