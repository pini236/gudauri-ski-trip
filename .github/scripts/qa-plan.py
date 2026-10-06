#!/usr/bin/env python3
"""The parts of the emulator run (.github/workflows/android-qa.yml).

usage: qa-plan.py <scenario> <ref name>
  scenario: all, auto, or parts with commas. main and "all" run every part; "auto" on a branch picks the parts
  the changed files need (.github/qa-parts.tsv); a list runs as written.
Prints parts=<json list of groups> and final=<group> for $GITHUB_OUTPUT. A group is parts that share one emulator:
the short ones are paired (each pays about two minutes to start an emulator), the others run alone.
"""
import json, subprocess, sys

ALL = "map,descent,games,school,snowball,home,group,meet,status,run,locate,weather,lang,phone,store".split(",")
SMOKE = "home"
PAIRS = [("store", "lang"), ("status", "weather"), ("locate", "descent")]
# a change in any of these runs everything
EVERYTHING = ("app/android/", "site/data/", "i18n/", "tools/build-app-", ".github/workflows/android-qa.yml",
              ".github/scripts/qa-", ".github/qa-parts.tsv")


def changed():
    base = subprocess.run(["git", "merge-base", "origin/main", "HEAD"], capture_output=True, text=True).stdout.strip()
    out = subprocess.run(["git", "diff", "--name-only", base, "HEAD"], capture_output=True, text=True, check=True).stdout
    return [f for f in out.splitlines() if f]


def table():
    rows = []
    for line in open(".github/qa-parts.tsv", encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        prefix, parts = line.rstrip("\n").split("\t")
        rows.append((prefix, parts.split(",")))
    return rows


def auto():
    rows, need = table(), set()
    for f in changed():
        hit = [p for pre, p in rows if f.startswith(pre)]
        if hit:
            for p in hit:
                need.update(x for x in p if x != "none")
        elif f.startswith(EVERYTHING):
            return list(ALL)
    return [p for p in ALL if p in need] or [SMOKE]


def groups(parts):
    left, out = list(parts), []
    for a, b in PAIRS:
        if a in left and b in left:
            out.append(f"{a},{b}")
            left.remove(a)
            left.remove(b)
    return [g for g in left] + out


scenario, ref = (sys.argv[1] or "all"), sys.argv[2]
if scenario == "all" or (scenario == "auto" and ref == "main"):
    # main always runs every part when it is not told which: its results are the release's
    parts = list(ALL)
elif scenario == "auto":
    parts = auto()
else:
    parts = [p.strip() for p in scenario.split(",") if p.strip()]
g = groups(parts)
# the release build's start and the crash-report check run with one group: not the phone's language, which restarts the system
final = next((x for x in g if x != "phone"), g[0])
print("parts=" + json.dumps(g))
print("final=" + final)
