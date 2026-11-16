"""Draws Eigentone's Windows icon from the logo the app shows (ic_logo.xml).

The logo is a set of round-ended straight strokes, turned 45 degrees, so it's drawn here directly. As most
Windows 11 icons are, it stands on its own with no tile behind it, as large as the icon allows. The logo's
white strokes are the gaps where one strand crosses another, so here they're cut out of the strands, leaving
the taskbar showing through, rather than painted white. Writes the program's icon (every size Windows asks
for) and a PNG the window, the tray and the welcome use.

Run from the repository root:  python tools/make_icon.py
"""
import math
import pathlib
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
LOGO = ROOT / "app/src/main/composeResources/drawable/ic_logo.xml"
ICO = ROOT / "app/icons/eigentone.ico"
PNG = ROOT / "app/src/main/composeResources/drawable/app_icon.png"
# The window and tray take a single image and Windows shrinks it to the taskbar's size, so they get the small,
# bold drawing.
SMALL_PNG = ROOT / "app/src/main/composeResources/drawable/app_icon_small.png"

MARGIN = 0.07  # of the side, kept clear for the round ends and the shadow
GAP = "#FFFFFF"  # the logo's colour for the gaps between strands
STRAND = "#1A73E8"  # the logo's colour for the two strands, drawn here as a gradient instead
RUNG = "#8AB4F8"  # the logo's colour for the rungs between them
# The strands run from a bright sky blue at the top right to violet at the bottom left: vivid on a dark
# taskbar and still deep enough on a light one. The rungs are a lighter tint between the two.
GRADIENT_FROM = (0x5C, 0xC8, 0xFF)
GRADIENT_TO = (0x7C, 0x5C, 0xFF)
RUNG_COLOUR = (0xB8, 0xD4, 0xFF)
SMALL = 32  # at this size and below only the strands are drawn, bolder, so it stays crisp
SMALL_BOLDER = 1.6
# A little depth, as Windows 11's icons have: a thin deep rim around the logo, so its edge reads on any
# taskbar, and a soft shadow just below it.
RIM_COLOUR = (0x1C, 0x22, 0x5E)
RIM = 0.018  # of the side
SHADOW_OPACITY = 0.45
SHADOW_BLUR = 0.03  # of the side
SHADOW_DROP = 0.025  # of the side
CANVAS = 1024  # drawn large, then scaled down, for smooth edges
ANDROID = "{http://schemas.android.com/apk/res/android}"


def strokes():
    root = ET.parse(LOGO).getroot()
    viewport = float(root.get(ANDROID + "viewportWidth"))
    group = root.find("group")
    angle = math.radians(float(group.get(ANDROID + "rotation", "0")))
    px, py = float(group.get(ANDROID + "pivotX")), float(group.get(ANDROID + "pivotY"))
    lines = []
    for path in group.findall("path"):
        points = [tuple(map(float, p)) for p in re.findall(r"([-\d.]+),([-\d.]+)", path.get(ANDROID + "pathData"))]
        colour = path.get(ANDROID + "strokeColor")
        width = float(path.get(ANDROID + "strokeWidth"))
        turned = []
        for x, y in points:
            dx, dy = x - px, y - py
            turned.append((px + dx * math.cos(angle) - dy * math.sin(angle), py + dx * math.sin(angle) + dy * math.cos(angle)))
        lines.append((turned, colour, width))
    return viewport, lines


def draw(size: int) -> Image.Image:
    viewport, lines = strokes()
    small = size <= SMALL
    if small:
        # Only the strands, unbroken: at this size the rungs blur and the gaps chop the strands into dots.
        lines = [(points, colour, width * SMALL_BOLDER) for points, colour, width in lines if colour.upper() == STRAND]
    image = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    pen = ImageDraw.Draw(image)
    # Fitted to the strokes themselves, round ends included, so the logo fills the icon.
    xs = [x for points, _, w in lines for x, _ in points]
    ys = [y for points, _, w in lines for _, y in points]
    pad = max(w for _, _, w in lines) / 2
    left, top, right, bottom = min(xs) - pad, min(ys) - pad, max(xs) + pad, max(ys) + pad
    side = max(right - left, bottom - top)
    scale = CANVAS * (1 - 2 * MARGIN) / side
    ox = CANVAS / 2 - (left + right) / 2 * scale
    oy = CANVAS / 2 - (top + bottom) / 2 * scale
    for points, colour, width in lines:
        # Drawn straight into the pixels, so a gap's transparent stroke cuts through what's under it.
        c = colour.upper()
        if c == GAP:
            rgb = (0, 0, 0, 0)
        elif c == STRAND:
            rgb = (1, 2, 3, 255)  # a marker, replaced by the gradient below
        elif c == RUNG:
            rgb = RUNG_COLOUR + (255,)
        else:
            rgb = tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
        w = width * scale
        xy = [(ox + x * scale, oy + y * scale) for x, y in points]
        pen.line(xy, fill=rgb, width=round(w), joint="curve")
        for x, y in xy:  # round joins and ends, as the vector's strokes have
            pen.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=rgb)
    # The strands' gradient, from the top right corner to the bottom left.
    pixels = image.load()
    for y in range(CANVAS):
        for x in range(CANVAS):
            if pixels[x, y] == (1, 2, 3, 255):
                t = ((CANVAS - x) + y) / (2 * CANVAS)
                pixels[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(GRADIENT_FROM, GRADIENT_TO)) + (255,)
    # The rim: the logo's shape grown by a little, in the deep colour, under the logo.
    alpha = image.getchannel("A")
    grow = int(CANVAS * RIM) | 1
    rim_mask = alpha.filter(ImageFilter.MaxFilter(grow))
    rim = Image.new("RGBA", image.size, RIM_COLOUR + (255,))
    rim.putalpha(rim_mask)
    # The shadow: that shape again, softened and dropped a little, faint.
    shadow_mask = rim_mask.filter(ImageFilter.GaussianBlur(CANVAS * SHADOW_BLUR)).point(lambda a: int(a * SHADOW_OPACITY))
    shadow = Image.new("RGBA", image.size, (0, 0, 0, 255))
    shadow.putalpha(shadow_mask)
    out = Image.new("RGBA", image.size, (0, 0, 0, 0))
    out.alpha_composite(shadow, (0, int(CANVAS * SHADOW_DROP)))
    out.alpha_composite(rim)
    out.alpha_composite(image)
    return out.resize((size, size), Image.LANCZOS)


def main():
    ICO.parent.mkdir(parents=True, exist_ok=True)
    # Each size drawn on its own, so the small ones get their simpler, bolder drawing.
    sizes = (16, 20, 24, 32, 40, 48, 64, 128, 256)
    images = [draw(s) for s in sizes]
    images[-1].save(ICO, sizes=[(s, s) for s in sizes], append_images=images[:-1])
    draw(256).save(PNG)
    draw(SMALL).save(SMALL_PNG)
    print("wrote", ICO.relative_to(ROOT), "and", PNG.relative_to(ROOT))


if __name__ == "__main__":
    main()
