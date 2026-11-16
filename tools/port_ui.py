"""One-time port of the Android app's interface (ui/) to Compose for desktop.

    python tools/port_ui.py <path to the Android repo>

Compose keeps its androidx.compose names on the desktop, so most of each file carries over. This rewrites
what differs mechanically: Android resources become Compose Multiplatform resources (R.string.x becomes
Res.string.x, with the matching stringResource and painterResource). What's left that needs Android is
ported by hand afterwards, so after the first run this script is only a record of how the port began.
"""
import pathlib
import re
import sys

PACKAGE = pathlib.Path("io/github/dragjon/eigentone/ui")

IMPORTS = {
    "import io.github.dragjon.eigentone.R\n": "import io.github.dragjon.eigentone.resources.*\n",
    "import androidx.compose.ui.res.stringResource": "import org.jetbrains.compose.resources.stringResource",
    "import androidx.compose.ui.res.painterResource": "import org.jetbrains.compose.resources.painterResource",
    "import androidx.compose.ui.res.pluralStringResource": "import org.jetbrains.compose.resources.pluralStringResource",
    "import androidx.annotation.StringRes": "import org.jetbrains.compose.resources.StringResource",
    "import androidx.annotation.DrawableRes": "import org.jetbrains.compose.resources.DrawableResource",
    "import androidx.annotation.PluralsRes": "import org.jetbrains.compose.resources.PluralStringResource",
}
ANNOTATED = [
    ("StringRes", "StringResource"),
    ("DrawableRes", "DrawableResource"),
    ("PluralsRes", "PluralStringResource"),
]


def port(text: str) -> str:
    for old, new in IMPORTS.items():
        text = text.replace(old, new)
    for kind in ["string", "drawable", "plurals", "font"]:
        text = text.replace(f"R.{kind}.", f"Res.{kind}.")
    for annotation, type_name in ANNOTATED:
        # @StringRes val label: Int  ->  val label: StringResource
        text = re.sub(r"@" + annotation + r"\s+((?:val |var )?\w+): Int", r"\1: " + type_name, text)
        text = re.sub(r"@" + annotation + r"\s+", "", text)
    return text


def main(android: pathlib.Path, windows: pathlib.Path) -> None:
    src = android / "app/src/main/java" / PACKAGE
    dst = windows / "app/src/main/kotlin" / PACKAGE
    dst.mkdir(parents=True, exist_ok=True)
    for f in sorted(src.glob("*.kt")):
        (dst / f.name).write_text(port(f.read_text(encoding="utf-8")), encoding="utf-8", newline="\n")
        print("ported", f.name)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    main(pathlib.Path(sys.argv[1]), pathlib.Path(__file__).resolve().parent.parent)
