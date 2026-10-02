#!/usr/bin/env python3
"""Build the site's language files from i18n/strings.json (the single source).

Writes site/i18n/<lang>.json for he, en, ru, ka: {"lang", "dir", "released", "strings": {key: value}}.
"pending" counts the strings no native speaker has checked yet ("check"). Since decision 39 that is information
only; the languages the site picks from the browser are listed in site/js/i18n.js (RELEASED).
Keys of the native apps (app.*) are left out, and each game (game.<id>.*) gets its own file,
site/i18n/game-<id>.<lang>.json, so the home page does not load the games' words. Run after every change to i18n/strings.json:

    python3 tools/build-site-strings.py
"""
import json, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "i18n" / "strings.json"
OUT = ROOT / "site" / "i18n"
DIRS = {"he": "rtl", "en": "ltr", "ru": "ltr", "ka": "ltr"}


def main():
    data = json.loads(SRC.read_text(encoding="utf-8"))
    langs = data["meta"]["languages"]
    groups = {"": {}}
    for k, v in data["strings"].items():
        if k.startswith("app."):
            continue
        g = k.split(".")[1] if k.startswith("game.") else ""
        groups.setdefault(g, {})[k] = v
    OUT.mkdir(parents=True, exist_ok=True)
    for g, strings in sorted(groups.items()):
        for lang in langs:
            pending = sum(1 for v in strings.values() if lang in v.get("check", []))
            out = {"lang": lang, "dir": DIRS[lang], "pending": pending,
                   "strings": {k: v[lang] for k, v in sorted(strings.items())}}
            name = f"game-{g}.{lang}.json" if g else f"{lang}.json"
            (OUT / name).write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
            print(f"site/i18n/{name}: {len(strings)} strings, " + (f"{pending} not checked by a native speaker" if pending else "checked"))


if __name__ == "__main__":
    main()
