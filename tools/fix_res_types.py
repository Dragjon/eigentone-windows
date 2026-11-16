"""Porting helper: where the compiler reports a resource now returned from something declared Int, retype it.

    python tools/fix_res_types.py <compile log>

For "Return type mismatch: expected 'kotlin.Int', actual '...Resource'", the nearest declaration above the
error that ends in ': Int' (or '): Int =' / '): Int {') is changed to the resource type.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path("app/src/main/kotlin")
pat = re.compile(r"^e: file:///C:/Users/dragon/Downloads/Eigentone-Windows/app/src/main/kotlin/(\S+?\.kt):(\d+):\d+ Return type mismatch: expected 'kotlin\.Int', actual 'org\.jetbrains\.compose\.resources\.(\w+)'")
fixes = {}
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    m = pat.match(line)
    if m:
        fixes.setdefault(m.group(1), []).append((int(m.group(2)), m.group(3)))
for rel, items in fixes.items():
    path = ROOT / rel
    lines = path.read_text(encoding="utf-8").split("\n")
    for lineno, typ in items:
        for i in range(lineno - 1, max(-1, lineno - 40), -1):
            new = re.sub(r"\): Int(\s*[={])", r"): " + typ + r"\1", lines[i], count=1)
            if new == lines[i]:
                new = re.sub(r"(\bva[lr] \w+): Int\b", r"\1: " + typ, lines[i], count=1)
            if new != lines[i]:
                lines[i] = new
                print(f"{rel}:{i + 1}: Int -> {typ}")
                break
    text = "\n".join(lines)
    if "org.jetbrains.compose.resources." not in text or True:
        for typ in {t for _, t in items}:
            imp = f"import org.jetbrains.compose.resources.{typ}"
            if imp not in text:
                text = re.sub(r"^(package .+\n)", r"\1\n" + imp + "\n", text, count=1)
    path.write_text(text, encoding="utf-8", newline="\n")
