#!/usr/bin/env python3
"""Checks the ski-pass prices (round 40): that site/data/prices.json is well formed, and the shared price calculation
(tools/fixtures/skipass-calc.json) against the reference rule below. The apps implement the same rule and test against the same file.
Rule: a number of days that is in the table costs the table's price. Any other number (8, 9, 11 to 14) is the cheapest way to cover it
with at most two tickets from the table; on a tie fewer tickets, then the bigger ticket first. More than 14 days, or no way: no price (never an estimate)."""
import json, re, sys, itertools, datetime
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
MAX_DAYS = 14


def price(passes, days, who):
    """Returns {'total': int, 'parts': [days, ...]} or None."""
    table = {p['days']: p[who] for p in passes}
    if days in table:
        return {'total': table[days], 'parts': [days]}
    if days < 1 or days > MAX_DAYS:
        return None
    best = None
    for a, b in itertools.combinations_with_replacement(sorted(table, reverse=True), 2):
        if a + b != days:
            continue
        cand = (table[a] + table[b], 2, [a, b])
        if best is None or cand[:2] < best[:2]:
            best = cand
    return None if best is None else {'total': best[0], 'parts': best[2]}


def is_date(s):
    try:
        datetime.date.fromisoformat(s) if re.fullmatch(r'\d{4}-\d{2}-\d{2}', s) else (_ for _ in ()).throw(ValueError)
        return True
    except (ValueError, TypeError):
        return False


bad = 0
def fail(msg):
    global bad
    print('FAIL ' + msg); bad += 1


doc = json.load(open(ROOT / 'site/data/prices.json'))
if doc.get('schema') != 1: fail('schema must be 1')
if doc.get('currency') != 'GEL': fail('currency must be GEL (no conversions)')
if doc.get('audience') != 'foreign': fail('version 1 carries only the price for foreigners')
if not str(doc.get('url', '')).startswith('https://'): fail('url must be https')
if not doc.get('src'): fail('src is required')
for k in ('checked', 'until'):
    if not is_date(doc.get(k)): fail('%s must be a YYYY-MM-DD date' % k)
if is_date(doc.get('checked')) and is_date(doc.get('until')) and doc['until'] < doc['checked']:
    fail('until is before checked')
ps = doc.get('passes', [])
days = [p.get('days') for p in ps]
if not ps or days != sorted(set(days)): fail('passes must be sorted by days, without repeats')
for p in ps:
    for k in ('days', 'adult', 'child'):
        if not (isinstance(p.get(k), int) and not isinstance(p.get(k), bool) and p[k] > 0):
            fail('%s: %s must be a positive whole number' % (p.get('days'), k))
    if isinstance(p.get('adult'), int) and isinstance(p.get('child'), int) and p['child'] > p['adult']:
        fail('%s: a child ticket costs more than the adult one' % p.get('days'))
prev = 0
for p in ps:
    if isinstance(p.get('adult'), int):
        if p['adult'] <= prev: fail('%s: a longer pass must cost more than a shorter one' % p['days'])
        prev = p['adult']
ids = [p.get('id') for p in ps]
if not all(isinstance(i, str) and i for i in ids) or len(set(ids)) != len(ids): fail('every pass needs a unique id')
if not (isinstance(doc.get('rules'), list) and all(isinstance(k, str) and k.startswith('app.') for k in doc['rules'])):
    fail('rules must be a list of i18n keys (app.*); a key the app does not know is skipped')
i18n = json.load(open(ROOT / 'i18n/strings.json'))['strings']
for k in doc.get('rules', []):
    if k not in i18n: fail('rule %s is not in i18n/strings.json' % k)
APPLE = re.compile(r'^https://apps\.apple\.com/[A-Za-z0-9_./-]+(\?hl=[A-Za-z-]+)?$')
PLAY = re.compile(r'^https://play\.google\.com/store/apps/details\?id=[A-Za-z0-9_.]+(&hl=[A-Za-z-]+)?$')
for k, rx in (('ios', APPLE), ('android', PLAY)):
    u = doc.get('buy', {}).get(k)
    if u is not None and not rx.match(u): fail('buy.%s must be a plain store link (only hl is allowed as a parameter): %s' % (k, u))
n = doc.get('night', {})
if not all(isinstance(n.get(k), int) and n[k] > 0 for k in ('adult', 'child')): fail('night needs whole adult and child prices')
if not (isinstance(doc.get('child'), list) and len(doc['child']) == 2 and doc['child'][0] == doc.get('free_under')):
    fail('child must be [from, to] and start at free_under')

fx = json.load(open(ROOT / 'tools/fixtures/skipass-calc.json'))
if fx['passes'] != [{k: p[k] for k in ('days', 'adult', 'child')} for p in ps]:
    fail('the fixture prices differ from site/data/prices.json: rebuild tools/fixtures/skipass-calc.json')
for c in fx['cases']:
    for who in ('adult', 'child'):
        got = price(fx['passes'], c['days'], who)
        if got != c[who]:
            fail('%d days, %s: got %s, expected %s' % (c['days'], who, got, c[who]))
print('skipass: %d days checked, %s' % (len(fx['cases']), 'FAIL' if bad else 'ok'))
sys.exit(1 if bad else 0)
