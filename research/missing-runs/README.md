# המסלולים החסרים: מחקר והשלמה

ששה מסלולים מהמפה הרשמית לא הופיעו בנתוני OpenStreetMap עם שם. בספטמבר 2026 נערך מחקר בשיחה נפרדת, עם בדיקה צולבת מול חבילת מחקר של ChatGPT. התוצאות נשמרו במסד הנתונים של האתר (collection `missingRuns`), והקבצים כאן הם עותק שלהן, מסמך אחד לכל מסלול. הגאומטריה נמצאת בשדה `geojson` כמחרוזת JSON (סדר [lon,lat]).

| מסלול | סוג | מקור | ודאות | הקלטות GPS | דרכי OSM | מה חסר |
|---|---|---|---|---|---|---|
| Tatra 2 | מסלול | דרך OSM עם השם הזה (היסטורית, נמחקה ב-2024) | גבוהה | 3 | 668794706, 1018641392 |  |
| Soliko 2 | מסלול | דרך OSM עם השם הזה (היסטורית, נמחקה ב-2024) | בינונית | 2 | 158744056 | נמצאו רק 372 המטרים העליונים מתוך כ-1,180 מ׳. ההמשך עד תחתית ההר לא נמצא בשום מקור. |
| Shino | דרך מקשרת | דרך OSM בלי שם, התאמה לפי מיקום | נמוכה | 1 | 1259783832 | הקטע המערבי של הדרך המקשרת, לכיוון Alpina, לא נמצא בנתונים. |
| Firni 1 | מסלול | דרך OSM בלי שם, התאמה לפי מיקום | נמוכה | 0 | 668794719 | נמצא רק החלק העליון, 692 מ׳ בצד המזרחי של הגונדולה. ההמשך לא נמצא בשום מקור. |
| Firni 2 | מסלול | דרך OSM בלי שם, התאמה לפי מיקום | נמוכה | 0 | 668794719 | נמצא רק החלק התחתון, 1.6 ק״מ בצד המערבי של הגונדולה. ההתחלה בחלק העליון לא נמצאה. |
| Baby | אזור מתחילים | דרך OSM בלי שם, התאמה לפי מיקום | בינונית | 12 | 472374254 | קו המרכז של אזור המתחילים. גבולות האזור והמעלית עצמה לא נמצאו בנתונים. |
| Bombora | אזור מתחילים | דרך OSM בלי שם, התאמה לפי מיקום | בינונית | 8 | 158817286 | קו המרכז של אזור המתחילים. גבולות האזור והמעלית עצמה לא נמצאו בנתונים. |

**השוואה מלאה למפה הרשמית של 2025/2026** (כל מסלול ורכבל, ומה חסר מעבר לטבלה כאן): `research/mta-map-compare.md`.

## בדיקה לפני הכנסה למפה

כל שבעת הקווים נבדקו מול מודל הגובה ומול מיקומי הרכבלים לפני שהוכנסו לאתר:
- **כולם יורדים ברציפות,** עם לכל היותר 4 מ׳ של עלייה נגדית לאורך הקו.
- **כולם מתחברים לרכבלים הנכונים.** למשל, Tatra 2 מסתיים בדיוק בתחנה התחתונה של Shino, ו-Bombora עובר מהתחתית של Khada לתחתית של Kudebi.

## שאלות פתוחות (מתוך `_summary.json`)

- Soliko 2 beyond its first 372 m, Firni 1 below its upper part, Firni 2 above its lower part, and the western part of the Shino ski way have no real-position source.
- Baby and Bombora have centerlines only: no boundaries and no J-bar lines. Bombora's elevation does not match the skiresort J-bar figures.
- Confirming the ChatGPT claim about the YouTube video title needs a working fetch of youtube.com.

## מקורות שנבדקו

- overpass-api.de: works with GET only; POST and some large queries end in connection reset; attic (historical) snapshots work partially
- overpass.kumi.systems: fallback, returned aerialway, area, name and around queries; timed out (504) on piste ways and relations
- api.openstreetmap.org: way and node history, changeset comments, relation 6946649 (Gudauri site), public trackpoints (84 pages, 412,114 points, 183 recordings, 205,414 winter points)
- skiresort.com / skiresort.info (redirects): lift list with Baby, Bombora and Alpina as J-bar lifts (Tatralift, 2016)
- vagabondadventures.ge: third-party run list with lengths (Tatra 2 = 2250 m, Soliko 2 = 1180 m, Bombora = 300 m)
- gudauri.com/about-gudauri/ski-lifts.html and gudauri.travel: lift table and a map image only, no per-run data
- snow-forecast.com and weski.com: map image only, no run names
- openskimap.org: JavaScript app built on OSM, no extra data
- GPS analysis: elevation model from the site's terrain script, winter-only downhill points, per-candidate track support, residual clusters
- Official MTA trail map image supplied by the user in chat (used only to identify names, colours, starts and ends; no lines were traced or copied)
- ChatGPT research package (ZIP supplied by the user): SHA-256 manifest verified; all 10 way histories are identical to the ones pulled independently here; geometry compared numerically with the saved docs; the claim that way 1259783832 was carved out of Tatra 1 (28 of 29 nodes) verified from node lists; the claim about a YouTube title could not be verified (HTTP 429)
- Official MTA map legend: the dashed line is 'Ski Way' (connecting road)

## מקורות שנחסמו או נכשלו

- status.mta.ski: HTTP 500 from the server on 3 attempts (/en/gudauri/gudauri, /en/gudauri, root); /en/gudauri returned 404; no API or JSON could be inspected
- gudauri.com map image (map-gudauri-ski-resort.jpg): could not be opened, the fetch tool does not support images
- youtube.com video page: HTTP 429 (retried during the cross-check), the title claim in the ChatGPT package is unverified
- pvd-club.com trail map: robots.txt fetch failed (timeout)
- No domain was blocked by the egress proxy policy.

חבילת המחקר המקורית של ChatGPT (קובץ ZIP) הועלתה בשיחה אחרת ולא נמצאת כאן. אם רוצים לשמור גם אותה, אפשר להוסיף אותה ל-`research/missing-runs/chatgpt/`.
