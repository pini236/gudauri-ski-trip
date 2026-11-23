#!/usr/bin/env python3
"""Checks the season-opening rules (round 39): the shared cases in tools/fixtures/season-countdown.json against the reference rules below,
and that the real season field in site/data/resorts.json is well formed. The apps implement the same rules and test against the same file."""
import datetime, json, re, sys
from pathlib import Path
from zoneinfo import ZoneInfo
ROOT = Path(__file__).resolve().parent.parent


def local_date(s):
    """A real calendar date written YYYY-MM-DD, or None."""
    if not isinstance(s, str) or not re.fullmatch(r'\d{4}-\d{2}-\d{2}', s):
        return None
    try:
        return datetime.date.fromisoformat(s)
    except ValueError:
        return None


def decide(now_utc, season, report_fresh, seen_open, tz):
    if report_fresh:
        return {'state': 'live', 'days': None, 'postponed': False}
    opn = local_date(season.get('open')) if isinstance(season, dict) else None
    if opn is None:
        return {'state': 'none', 'days': None, 'postponed': False}
    today = now_utc.astimezone(ZoneInfo(tz)).date()
    days = (opn - today).days
    if days > 0:
        seen = local_date(seen_open)
        return {'state': 'countdown', 'days': days, 'postponed': bool(seen and seen < opn)}
    if days == 0:
        return {'state': 'opening_day', 'days': 0, 'postponed': False}
    return {'state': 'no_info', 'days': None, 'postponed': False}


bad = 0
fx = json.load(open(ROOT / 'tools/fixtures/season-countdown.json'))
for c in fx['cases']:
    now = datetime.datetime.fromisoformat(c['now'].replace('Z', '+00:00'))
    got = decide(now, c['season'], c['report'] == 'fresh', c.get('seen_open'), fx['tz'])
    if got != c['expect']:
        print('FAIL %s\n got      %s\n expected %s' % (c['name'], got, c['expect'])); bad += 1
resorts = json.load(open(ROOT / 'site/data/resorts.json'))
for r in resorts['resorts']:
    s = r.get('season')
    if s is None:
        continue
    if local_date(s.get('open')) is None:
        print('FAIL %s: season.open is not a YYYY-MM-DD date' % r['id']); bad += 1
    if 'close' in s and local_date(s['close']) is None:
        print('FAIL %s: season.close must be a real date or absent (never null)' % r['id']); bad += 1
    if not str(s.get('url', '')).startswith('https://') or local_date(s.get('updated')) is None or not s.get('src'):
        print('FAIL %s: season needs src, an https url and an updated date' % r['id']); bad += 1
    if 'announced' in s and local_date(s['announced']) is None:
        print('FAIL %s: season.announced must be a real date or absent' % r['id']); bad += 1
    if 'note' in s and s['note'] not in json.load(open(ROOT / 'i18n/strings.json'))['strings']:
        print('FAIL %s: season.note must be a key of i18n/strings.json' % r['id']); bad += 1
    if r.get('tz') != fx['tz']:
        print('FAIL %s: the fixtures assume %s, the resort has %s' % (r['id'], fx['tz'], r.get('tz'))); bad += 1
print('season: %d cases, %s' % (len(fx['cases']), 'FAIL' if bad else 'ok'))
sys.exit(1 if bad else 0)
