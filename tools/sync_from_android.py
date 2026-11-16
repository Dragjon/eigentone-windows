"""Copies the shared logic and its tests from Eigentone for Android, so both apps sound and behave the same.

    python tools/sync_from_android.py <path to the Android repo>

The data/ and dsp/ packages are pure Kotlin apart from three Android classes, which are swapped for the JVM
versions in app/src/main/kotlin/.../platform. Files listed in WINDOWS_OWN are written for Windows instead
and never overwritten. Anything else that still needs Android stops the sync, naming the file.
"""
import pathlib
import re
import shutil
import sys

PACKAGE = pathlib.Path("io/github/dragjon/eigentone")
SHARED = ["data", "dsp", "ui"]
WINDOWS_OWN = {"data/CrashLog.kt"}
# Interface files that need nothing but Compose, copied as they are. The rest of ui/ is ported separately.
UI_AS_IS = {"ui/SeedScheme.kt"}
# Tests that can't run on Windows yet. Empty now that the interface is ported.
PENDING_TESTS: set = set()
SWAPS = {
    "import android.content.Context": "import io.github.dragjon.eigentone.platform.Context",
    "import android.util.AtomicFile": "import io.github.dragjon.eigentone.platform.AtomicFile",
    "import android.util.Log": "import io.github.dragjon.eigentone.platform.Log",
    "android.os.SystemClock.": "io.github.dragjon.eigentone.platform.SystemClock.",
}


def sync(android: pathlib.Path, windows: pathlib.Path) -> int:
    problems = []
    copied = 0
    for kind, src_root, dst_root in [
        ("main", android / "app/src/main/java", windows / "app/src/main/kotlin"),
        ("test", android / "app/src/test/java", windows / "app/src/test/kotlin"),
    ]:
        for part in SHARED:
            src_dir = src_root / PACKAGE / part
            dst_dir = dst_root / PACKAGE / part
            if not src_dir.is_dir():
                continue
            dst_dir.mkdir(parents=True, exist_ok=True)
            for src in sorted(src_dir.glob("*.kt")):
                rel = f"{part}/{src.name}"
                if kind == "main" and rel in WINDOWS_OWN:
                    continue
                if kind == "main" and part == "ui" and rel not in UI_AS_IS:
                    continue
                if kind == "test" and rel in PENDING_TESTS:
                    (dst_dir / src.name).unlink(missing_ok=True)
                    continue
                text = src.read_text(encoding="utf-8")
                for old, new in SWAPS.items():
                    text = text.replace(old, new)
                # Imports, and names written out in full in the code. Compose keeps its androidx.compose
                # names on the desktop, so only the rest of Android counts.
                left = re.findall(r"\b(?:android\.[A-Za-z_.]+|androidx\.(?!compose\.)[A-Za-z_.]+)", text)
                if left:
                    problems.append(f"{kind}/{rel}: still needs " + ", ".join(sorted(set(left))))
                    continue
                (dst_dir / src.name).write_text(text, encoding="utf-8", newline="\n")
                copied += 1
    print(f"Copied {copied} files.")
    for p in problems:
        print("Not copied, needs Android:", p)
    return 1 if problems else 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    root = pathlib.Path(__file__).resolve().parent.parent
    sys.exit(sync(pathlib.Path(sys.argv[1]), root))
