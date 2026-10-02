#!/usr/bin/env python3
"""Check that the main site takes all its words from the i18n layer (i18n/strings.json).

    python3 tools/check-site-i18n.py      (exit code 1 on errors)

1. Every key the site uses exists in i18n/strings.json: T('...'), E('...'), H('...'), slots('...') and any other
   string literal shaped like a key in site/js/app.js, relief.js and account.js, and every key in the data-i18n*
   attributes of site/index.html. A literal that ends in "_" or "." (e.g. 'common.color_'+c) must be the start of a key.
2. No Hebrew is left in site/js/app.js and account.js outside comments.
3. Every Hebrew text and every Hebrew attribute in site/index.html is covered:
   - text: the element or one of its ancestors has data-i18n, data-i18n-html, data-i18n-tpl, data-i18n-plural,
     or data-i18n-js (text that app.js draws itself from the keys named there);
   - attributes (aria-label, alt, title, placeholder, content): named in the element's data-i18n-attr.
"""
import json, pathlib, re, sys
from html.parser import HTMLParser

ROOT = pathlib.Path(__file__).resolve().parent.parent
HEB = re.compile(r"[֐-׿]")
AREAS = "meta|nav|common|home|ticket|daynight|about|map|run|lift|status|meet|games"
KEY_LIT = re.compile(r"""(['"`])((?:%s)\.[a-z0-9_.]*)\1""" % AREAS)
FILE = re.compile(r"\.(png|jpe?g|webp|svg|json|wav|html)$")  # a file name such as 'meet.png', not a key
TEXT_ATTRS = ("data-i18n", "data-i18n-html", "data-i18n-tpl", "data-i18n-plural", "data-i18n-js")
CHECKED_ATTRS = ("aria-label", "alt", "title", "placeholder", "content")
VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr"}


def js_code_only(src):
    """The source with comments blanked out (strings, templates and regular expressions kept)."""
    out, i, n = [], 0, len(src)
    stack = []  # open template literals and their ${ } depth
    prev = ""  # last significant character outside strings, to tell a regex from a division

    def regex_ok():
        return prev == "" or prev in "(,=:[!&|?{};+-*%<>~^" or re.search(r"\b(return|typeof|case|in|of)\s*$", "".join(out[-12:]))

    while i < n:
        c = src[i]
        if stack and stack[-1] == "tpl":
            if c == "\\":
                out.append(src[i:i + 2]); i += 2; continue
            if c == "`":
                stack.pop(); out.append(c); i += 1; prev = "`"; continue
            if src.startswith("${", i):
                stack.append(0); out.append("${"); i += 2; prev = "{"; continue
            out.append(c); i += 1; continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i)); i = j; continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append(re.sub(r"[^\n]", " ", src[i:j])); i = j; continue
        if c in "'\"":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            out.append(src[i:j + 1]); i = j + 1; prev = c; continue
        if c == "`":
            stack.append("tpl"); out.append(c); i += 1; continue
        if c == "/" and regex_ok():
            j, cls = i + 1, False
            while j < n and (cls or src[j] != "/") and src[j] != "\n":
                if src[j] == "\\":
                    j += 1
                elif src[j] == "[":
                    cls = True
                elif src[j] == "]":
                    cls = False
                j += 1
            out.append(src[i:j + 1]); i = j + 1; prev = "/"; continue
        if stack and isinstance(stack[-1], int):
            if c == "{":
                stack[-1] += 1
            elif c == "}":
                if stack[-1] == 0:
                    stack.pop(); out.append(c); i += 1; continue
                stack[-1] -= 1
        out.append(c); i += 1
        if not c.isspace():
            prev = c
    return "".join(out)


class Page(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack, self.keys, self.errors = [], [], []
        self.cur = None  # [key, text, has_child, line]: a plain data-i18n element, to compare with the Hebrew
        self.he_texts = []

    def covered(self):
        return any(a for _, a in self.stack)

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        line = self.getpos()[0]
        if self.cur:
            self.cur[2] = True
        if a.get("data-i18n") and tag not in VOID:
            self.cur = [a["data-i18n"].strip(), "", False, line, tag]
        for name in TEXT_ATTRS:
            if a.get(name):
                self.keys += [(k.strip(), f"index.html:{line} {name}") for k in a[name].split(";") if k.strip()]
        attr_keys = {}
        for part in (a.get("data-i18n-attr") or "").split(";"):
            if ":" in part:
                at, k = part.split(":", 1)
                attr_keys[at.strip()] = k.strip()
                self.keys.append((k.strip(), f"index.html:{line} data-i18n-attr"))
        for at in CHECKED_ATTRS:
            if a.get(at) and HEB.search(a[at]) and at not in attr_keys:
                self.errors.append(f"index.html:{line}: <{tag} {at}=\"{a[at][:40]}\"> has no data-i18n-attr for {at}")
        # translate="no": the same in every language on purpose (a language's own name in the language list)
        here = any(a.get(name) for name in TEXT_ATTRS) or a.get("translate") == "no"
        if tag not in VOID:
            self.stack.append((tag, here))

    def handle_endtag(self, tag):
        if self.cur and tag == self.cur[4]:
            self.he_texts.append(self.cur)
            self.cur = None
        while self.stack:
            t, _ = self.stack.pop()
            if t == tag:
                break

    def handle_data(self, data):
        if self.cur:
            self.cur[1] += data
        if HEB.search(data) and not self.covered():
            self.errors.append(f"index.html:{self.getpos()[0]}: text \"{data.strip()[:50]}\" has no i18n attribute")


def main():
    strings = json.loads((ROOT / "i18n/strings.json").read_text(encoding="utf-8"))["strings"]
    errors = []

    def need(key, where):
        if key.endswith(("_", ".")):
            if not any(k.startswith(key) for k in strings):
                errors.append(f"{where}: no key starts with '{key}'")
        elif key not in strings:
            errors.append(f"{where}: key '{key}' is not in i18n/strings.json")

    used = 0
    for name in ("site/js/app.js", "site/js/relief.js", "site/js/account.js"):
        src = (ROOT / name).read_text(encoding="utf-8")
        code = js_code_only(src)
        for m in KEY_LIT.finditer(code):
            if FILE.search(m.group(2)):
                continue
            need(m.group(2), f"{name}:{code.count(chr(10), 0, m.start()) + 1}")
            used += 1
        if name.endswith(("app.js", "account.js")):
            for no, line in enumerate(code.split("\n"), 1):
                if HEB.search(line):
                    errors.append(f"{name}:{no}: Hebrew outside a comment: {line.strip()[:80]}")

    page = Page()
    page.feed((ROOT / "site/index.html").read_text(encoding="utf-8"))
    for k, where in page.keys:
        need(k, where)
    errors += page.errors
    # The page is shown as written when the language is Hebrew (i18n.js fills it only for the other languages),
    # so the Hebrew in the HTML must be the Hebrew in the strings file: an empty or stale label is what people would see.
    for key, text, has_child, line, _ in page.he_texts:
        he = strings.get(key, {}).get("he")
        if he is not None and not has_child and text != he:
            errors.append(f"index.html:{line}: the Hebrew of '{key}' is \"{text[:40]}\", but strings.json says \"{he[:40]}\"")

    for e in errors:
        print("ERROR", e)
    print(f"{used} key literals in the scripts, {len(page.keys)} keys in index.html, {len(errors)} errors")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
