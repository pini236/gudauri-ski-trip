#!/usr/bin/env python3
"""Accessibility audit of one uiautomator dump (app/android/qa/run.sh calls it after each screenshot).

usage: a11y.py <ui.xml> <density dpi> <screen name>

Prints one line per finding, tab separated: kind, screen, size in dp, bounds, words.
  small     a tappable node under 44 dp on one side (WCAG and the round 10 rule: touch targets of at least 44)
  unlabeled a tappable node with no text and no description anywhere inside it (TalkBack says "button" and nothing else)
Report only: a step that must fail on it can read the file (qa-out/a11y.txt).
"""
import re
import sys
import xml.etree.ElementTree as ET

path, dpi, screen = sys.argv[1], float(sys.argv[2]), sys.argv[3]
MIN_DP = 44
# the system bars and the keyboard are not ours
SKIP_PKG = ("com.android.systemui", "com.google.android.inputmethod", "com.android.inputmethod")


def words(n):
    out = []
    for e in n.iter():
        for k in ("text", "content-desc"):
            v = (e.get(k) or "").strip()
            if v:
                out.append(v)
    return out


def box(n):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
    return tuple(map(int, m.groups())) if m else None


try:
    root = ET.parse(path).getroot()
except ET.ParseError:
    sys.exit(0)

seen = set()
for n in root.iter("node"):
    if n.get("package") in SKIP_PKG or n.get("enabled") == "false":
        continue
    if not (n.get("clickable") == "true" or n.get("checkable") == "true"):
        continue
    b = box(n)
    if not b:
        continue
    x1, y1, x2, y2 = b
    w, h = (x2 - x1) * 160 / dpi, (y2 - y1) * 160 / dpi
    if w <= 1 or h <= 1:
        continue
    said = " / ".join(dict.fromkeys(words(n)))[:60]
    # a screen-wide scroller or the map itself is "clickable" but is not a button
    if w > 360 and h > 360:
        continue
    kinds = []
    if min(w, h) < MIN_DP - 0.5:
        kinds.append("small")
    if not said and (n.get("class") or "") != "android.widget.EditText":
        kinds.append("unlabeled")
    for k in kinds:
        key = (k, b, said)
        if key in seen:
            continue
        seen.add(key)
        print(f"{k}\t{screen}\t{w:.0f}x{h:.0f}dp\t{x1},{y1},{x2},{y2}\t{said}")
