#!/usr/bin/env python3
"""Validate i18n/strings.json: four languages, same placeholders, CLDR plural forms, clean scripts.

Run: python3 i18n/check.py   (exit code 1 on errors)
"""
import json, re, sys, pathlib

HERE = pathlib.Path(__file__).resolve().parent
LANGS = ["he", "en", "ru", "ka"]
PLURALS = {"he": {"one", "two", "other"}, "en": {"one", "other"},
           "ru": {"one", "few", "many", "other"}, "ka": {"one", "other"}}
# Script that must not appear inside a word of another script (catches stray letters).
SCRIPTS = {"he": r"֐-׿", "ru": r"Ѐ-ӿ", "ka": r"Ⴀ-ჿ"}
KEY_RE = re.compile(r"^[a-z0-9_]+(\.[a-z0-9_]+)+$")
PH_RE = re.compile(r"\{([a-zA-Z_][a-zA-Z0-9_]*)\}")


def forms(v):
    return list(v.values()) if isinstance(v, dict) else [v]


def placeholders(v):
    out = set()
    for f in forms(v):
        out |= set(PH_RE.findall(f))
    return out


def mixed(text):
    """Words that mix two of: Hebrew, Cyrillic, Georgian, Latin letters."""
    bad = []
    for w in re.findall(r"\w+", text):
        kinds = {k for k, rng in SCRIPTS.items() if re.search(f"[{rng}]", w)}
        if re.search(r"[A-Za-z]", w):
            kinds.add("latin")
        if len(kinds) > 1:
            bad.append(w)
    return bad


def main():
    data = json.loads((HERE / "strings.json").read_text(encoding="utf-8"))
    strings = data["strings"]
    errors, pending = [], {l: 0 for l in LANGS}
    for key, e in strings.items():
        if not KEY_RE.match(key):
            errors.append(f"{key}: bad key")
        for l in LANGS:
            if l not in e or e[l] in ("", None, {}):
                errors.append(f"{key}: missing {l}")
        if any(l not in e for l in LANGS):
            continue
        plural = [isinstance(e[l], dict) for l in LANGS]
        if any(plural) and not all(plural):
            errors.append(f"{key}: plural in some languages only")
        for l in LANGS:
            if isinstance(e[l], dict) and set(e[l]) != PLURALS[l]:
                errors.append(f"{key}: {l} plural forms {sorted(e[l])}, expected {sorted(PLURALS[l])}")
        ref = placeholders(e["he"])
        for l in LANGS[1:]:
            if placeholders(e[l]) != ref:
                errors.append(f"{key}: {l} placeholders {sorted(placeholders(e[l]))} != he {sorted(ref)}")
        for l in LANGS:
            for f in forms(e[l]):
                m = mixed(f)
                if m:
                    errors.append(f"{key}: {l} mixed-script word(s) {m}")
        if "where" not in e:
            errors.append(f"{key}: missing 'where'")
        for l in e.get("check", []):
            if l not in LANGS:
                errors.append(f"{key}: unknown language in check: {l}")
            else:
                pending[l] += 1
    for err in errors:
        print("ERROR", err)
    print(f"{len(strings)} strings, {len(errors)} errors")
    print("pending native review: " + ", ".join(f"{l} {n}" for l, n in pending.items() if n))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
