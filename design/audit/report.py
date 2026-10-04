# Reads fonts.jsonl and faces.json (fonts.mjs) and reports where the rendered fonts leave the design language.
import json, collections, sys, re, os, tempfile
# usage: python3 design/audit/report.py [folder with fonts.jsonl and faces.json, default $OUT or gud-audit in temp]
here = sys.argv[1] if len(sys.argv) > 1 else os.environ.get('OUT') or os.path.join(tempfile.gettempdir(), 'gud-audit')
recs = [json.loads(l) for l in open(f'{here}/fonts.jsonl') if l.strip()]
faces = json.load(open(f'{here}/faces.json')) if os.path.exists(f'{here}/faces.json') else {}
OK = {'Karantina', 'IBM Plex Sans Hebrew', 'IBM Plex Sans', 'Oswald', 'Noto Sans Georgian'}
DISPLAY = {'he': 'Karantina', 'en': 'Karantina', 'ru': 'Oswald', 'ka': 'Noto Sans Georgian'}
BODY = {'he': 'IBM Plex Sans Hebrew', 'en': 'IBM Plex Sans', 'ru': 'IBM Plex Sans', 'ka': 'Noto Sans Georgian'}
first = lambda fam: fam.split(',')[0].strip().strip('"\'')
fallback, wrongfam, other, faux = [], [], [], []
tot = collections.Counter(); good = collections.Counter()
for r in recs:
    if 'ac-gsi' in r['path'] or 'gsi' in r['path']: continue
    lang = r['lang']; f1 = first(r['family']); used = r.get('used') or []
    glyphs = sum(u[1] for u in used) or 1
    bad = [(u[0], u[1]) for u in used if not any(u[0].startswith(o) for o in OK) and u[1] > 0]
    tot[lang] += 1
    stretch = r.get('stretch', '100%'); st = float(stretch.rstrip('%')) if stretch.endswith('%') else 100
    role = 'display' if f1 in ('Karantina', 'Oswald') or (f1 == 'Noto Sans Georgian' and st < 100) else 'body' if f1 in ('IBM Plex Sans Hebrew', 'IBM Plex Sans', 'Noto Sans Georgian') else 'other'
    exp = DISPLAY[lang] if role == 'display' else BODY[lang] if role == 'body' else None
    ok = True
    if bad: fallback.append((r, bad)); ok = False
    if role == 'other': other.append(r); ok = False
    elif f1 != exp: wrongfam.append((r, role, exp)); ok = False
    # the font that drew most of the glyphs, and the weight it was asked for
    main = max(used, key=lambda u: u[1])[0] if used else '?'
    main = next((o for o in sorted(OK, key=len, reverse=True) if main.startswith(o)), main)
    fl = faces.get(f"{r['state']}|{lang}|{r['w']}", [])
    ws = []
    for f in fl:
        fam, wt = f.split('|')[:2]
        if fam == main:
            ws += [int(x) for x in re.findall(r'\d+', wt)]
    if ws and int(r['weight']) > max(ws) + 50: faux.append((r, main, max(ws))); ok = False
    if ok: good[lang] += 1
print('texts checked, and in the expected fonts:')
for l in ['he', 'en', 'ru', 'ka']: print(f'  {l}: {good[l]} of {tot[l]} ({100*good[l]/max(tot[l],1):.1f}%)')
def show(title, items, key, fmt, n=40):
    print(f'\n== {title}: {len(items)}')
    groups = collections.OrderedDict()
    for it in items: groups.setdefault(key(it), []).append(it)
    for k, v in sorted(groups.items(), key=lambda kv: -len(kv[1]))[:n]: print(f'{len(v):4d} ', fmt(k, v))
show('fallback to a font outside the language (glyphs drawn by a system font)', fallback,
     lambda x: (x[0]['lang'], re.sub(r'\[\d+\]', '', x[0]['path'].split(' > ')[-1]), tuple(sorted(set(b[0] for b in x[1])))),
     lambda k, v: f"{k[0]} {k[1]} -> {', '.join(k[2])} | e.g. '{v[0][0]['text']}' ({v[0][0]['state']}, {v[0][0]['w']})")
show('family not the one the language sets for its role', wrongfam,
     lambda x: (x[0]['lang'], x[1], first(x[0]['family']), x[2], x[0]['path'].split(' > ')[-1]),
     lambda k, v: f"{k[0]} {k[1]}: {k[2]} instead of {k[3]} at {k[4]} | e.g. '{v[0][0]['text']}' ({v[0][0]['state']})")
show('font-family outside the language altogether', other,
     lambda x: (x['lang'], first(x['family']), x['path'].split(' > ')[-1]),
     lambda k, v: f"{k[0]} {k[1]} at {k[2]} | e.g. '{v[0]['text']}' ({v[0]['state']})")
# the characters behind each fallback: not in any web font of the element's own stack
cov = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'coverage.json')))
def covered(fam, ch):
    return any(lo <= ord(ch) <= hi for lo, hi in cov.get(fam, []))
miss = collections.Counter()
for r, bad in fallback:
    stack = [f.strip().strip('"\'') for f in r['family'].split(',')]
    web = [f for f in stack if f in cov]
    for ch in set(r['text']):
        if ch.isspace(): continue
        if not any(covered(f, ch) for f in web): miss[(r['lang'], ch, ' / '.join(web))] += 1
print('\n== characters no web font in the element\'s stack covers (language, character, stack):')
for (l, ch, st), n in sorted(miss.items(), key=lambda kv: -kv[1])[:60]: print(f'  {n:3d} {l} {ch!r} U+{ord(ch):04X}  [{st}]')
show('heavier than any loaded face (the browser fakes the bold)', faux,
     lambda x: (x[0]['lang'], x[1], x[0]['weight'], x[2], x[0]['path'].split(' > ')[-1]),
     lambda k, v: f"{k[0]} {k[1]} weight {k[2]} (loaded up to {k[3]}) at {k[4]} | e.g. '{v[0][0]['text']}' ({v[0][0]['state']})")
