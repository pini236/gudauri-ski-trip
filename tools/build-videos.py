#!/usr/bin/env python3
"""Builds site/data/videos-seed.json and research/videos.md from tools/videos-curated.json.

Run from the repo root:  python3 tools/build-videos.py
Edit tools/videos-curated.json, never the two generated files."""
import json, re, datetime, pathlib

root = pathlib.Path(__file__).resolve().parent.parent
cur = json.load(open(root / 'tools/videos-curated.json', encoding='utf-8'))
runs = json.load(open(root / 'site/data/runs-and-lifts.json', encoding='utf-8'))
named = [p['key'] for p in runs['pistes'] if p.get('named')]
at = int(datetime.datetime.fromisoformat(cur['researched'] + 'T12:00:00+00:00').timestamp() * 1000)

EVID = {
    'title': 'הכותרת מציינת את המסלול',
    'multi': 'הכותרת מפרטת כמה מסלולים, וביניהם המסלול הזה',
    'desc': 'התיאור מציין את המסלול',
    'variant': 'הכותרת מציינת גרסה של המסלול',
}
CONF = {'high': 'גבוהה', 'medium': 'בינונית'}

seed = []
for k in named:
    for v in cur['videos'].get(k, []):
        assert v['evidence'] in EVID and v['confidence'] in CONF, (k, v)
        seed.append({'piste': k, 'url': 'https://www.youtube.com/watch?v=' + v['id'], 'title': v['title'],
                     'by': 'מחקר', 'at': at, 'channel': v['channel'], 'length': v['length'],
                     'published': v['published'], 'confidence': v['confidence'], 'evidence': v['evidence']})
json.dump(seed, open(root / 'site/data/videos-seed.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
open(root / 'site/data/videos-seed.json', 'a').write('\n')

L = []
L.append('# סרטונים למסלולים\n')
L.append(f"נוצר אוטומטית מ-`tools/videos-curated.json` בעזרת `tools/build-videos.py`. תאריך המחקר: {cur['researched']}.\n")
L.append('## איך נעשה החיפוש\n')
L.append('- חיפוש ביוטיוב לכל אחד מ-27 המסלולים בכמה שפות (אנגלית, רוסית) ובכמה ניסוחים, ועוד חיפושים ממוקדים למסלולים שלא נמצא להם כלום.')
L.append('- **נכנס רק סרטון שהכותרת או התיאור שלו מציינים במפורש את שם המסלול, כולל המספר.** לא ניחשנו לפי "סרטון מגודאורי".')
L.append('- שם בלי מספר (למשל Kudebi בלבד) לא מספיק למסלול ממוספר, כי אי אפשר לדעת איזה מהם מוצג. הסרטונים האלה מופיעים למטה כמועמדים.')
L.append('- מסלול עם שם ייחודי (Kobi, Pirveli, Shino ועוד) מתקבל לפי השם.')
L.append('- ביוטיוב חסם את משיכת דפי הצפייה, ולכן לא נקראו תיאורים מלאים ופרקים. ההוכחה היא הכותרת, או קטע התיאור שמופיע בתוצאות החיפוש. **מומלץ לצפות בכמה סרטונים לפני שסומכים על הכל.**')
L.append('- תאריך הפרסום מוצג בקירוב (לפי "לפני X שנים" ביום המחקר).\n')
L.append('## רמת ודאות\n')
L.append('- **גבוהה:** הכותרת מציינת את המסלול הזה בלבד.')
L.append('- **בינונית:** הכותרת מפרטת כמה מסלולים, או שההוכחה היא קטע תיאור, או שמדובר בגרסה של המסלול.\n')
L.append('## סרטונים לפי מסלול\n')
L.append('| מסלול | סרטון | ערוץ | אורך | פורסם | ודאות | הוכחה |')
L.append('|---|---|---|---|---|---|---|')
for k in named:
    for v in cur['videos'].get(k, []):
        t = v['title'].replace('|', '/')
        L.append(f"| {k} | [{t}](https://www.youtube.com/watch?v={v['id']}) | {v['channel']} | {v['length']} | {v['published']} | {CONF[v['confidence']]} | {EVID[v['evidence']]} |")
L.append('')
none = [k for k in named if not cur['videos'].get(k)]
L.append(f'## מסלולים שלא נמצא להם סרטון ({len(none)})\n')
L.append('| מסלול | הערה |')
L.append('|---|---|')
for k in none:
    L.append(f"| {k} | {cur['notes'].get(k, 'לא נמצא סרטון שמציין את המסלול.')} |")
L.append('')
L.append('## מועמדים שלא נכנסו (שם בלי מספר, או לא ברור שמראה את המסלול)\n')
L.append('| מסלול קשור | סרטון | ערוץ | אורך | למה לא נכנס |')
L.append('|---|---|---|---|---|')
for c in cur['candidates']:
    t = c['title'].replace('|', '/')
    L.append(f"| {c['for']} | [{t}](https://www.youtube.com/watch?v={c['id']}) | {c['channel']} | {c['length']} | {c['why']} |")
L.append('')
open(root / 'research/videos.md', 'w', encoding='utf-8').write('\n'.join(L))
print(len(seed), 'videos,', len(none), 'pistes without video')
