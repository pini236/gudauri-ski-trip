#!/usr/bin/env python3
"""Builds site/sitemap.xml. Run from the repo root: python3 tools/build-sitemap.py

Search engines (1.11.2026): the home page and each game are listed once per language, each language at its own address
(?lang=xx, which js/i18n.js honours), with xhtml:link alternates for all of them and the plain address as the x-default,
the same as the hreflang links in site/index.html and in the games (tools/build-games.py). The privacy and support pages
pick their language from the #hash, which search engines do not see, so they are one address each. A ski resort in testing
(?resort=) is never listed (robots.txt keeps them out too); tests/seo.spec.ts checks both."""
import pathlib

root = pathlib.Path(__file__).resolve().parent.parent
SITE = 'https://gudauri-ski-trip.vercel.app'
LANGS = ('he', 'en', 'ru', 'ka')
MULTI = ['/', '/games/descent/', '/games/school/', '/games/fresh-snow/', '/games/snowball/', '/games/merge/']
SINGLE = ['/privacy', '/support']  # right after the home page


def multi(path):
    alts = ''.join(f'<xhtml:link rel="alternate" hreflang="{l}" href="{SITE}{path}?lang={l}"/>' for l in LANGS)
    alts += f'<xhtml:link rel="alternate" hreflang="x-default" href="{SITE}{path}"/>'
    urls = [f'{SITE}{path}'] + [f'{SITE}{path}?lang={l}' for l in LANGS]
    return ''.join(f'  <url><loc>{u}</loc>{alts}</url>\n' for u in urls)


def build():
    body = multi(MULTI[0]) + ''.join(f'  <url><loc>{SITE}{p}</loc></url>\n' for p in SINGLE)
    body += ''.join(multi(p) for p in MULTI[1:])
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n'
            + body + '</urlset>\n')


if __name__ == '__main__':
    (root / 'site/sitemap.xml').write_text(build(), encoding='utf-8')
    print('wrote site/sitemap.xml')
