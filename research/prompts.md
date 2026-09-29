# פרומפטים ששימשו לאיסוף המידע

## 1. מחקר המסלולים החסרים (שיחה נפרדת של Claude)

דורש גישה לדומיינים: `overpass-api.de`, `overpass.kumi.systems`, `api.openstreetmap.org`, `status.mta.ski`.

```
משימת מחקר בלבד: להשלים נתוני מיקום אמיתיים למסלולים שחסרים במפת גודאורי.
לא לשנות את דף האתר עצמו. רק לאסוף, לאמת ולשמור נתונים.

האתר: https://claude.ai/artifact/1uDUdt45A2c77zooPZBGYK
קרא אותו עם Artifact (action "read") כדי להכיר את הנתונים הקיימים.
הסקריפט id="data" מכיל את המסלולים והרכבלים, בקואורדינטות [lat,lon].

מה חסר (שמות וצבעים לפי המפה הרשמית של MTA):
1. Tatra 2, כחול
2. Soliko 2, כחול
3. Shino, כחול
4. Firni 1 / Firni 2, כחולים. ב-OSM יש רק דרך אחת, 668794719, בשם "Frini".
   צריך לקבוע אם זה 1 או 2, ולמצוא את השני.
5. Baby, אזור מתחילים ירוק, ליד התחנה העליונה של Goodaura, צמוד לרכבל Snow Park.
6. Bombora, אזור מתחילים ירוק, ליד התחנה התחתונה של Kudebi, ליד Khada.

מועמדים שכבר זוהו (קטעים בלי שם ב-OSM):
- 668794712: 231 מ׳, מסתיים בתחנה התחתונה של Shino. כנראה חלק מ-Shino.
- 1259783832: 1.3 ק״מ, מ-Tatra 1 עד הבסיס ליד Goodaura/Zuma. אולי Soliko 2 או Tatra 2.

מקורות, לפי הסדר:
1. Overpass (overpass-api.de, ואם הוא עמוס overpass.kumi.systems),
   בתיבה 42.44,44.40,42.58,44.58:
   - כל ה-ways וה-relations עם piste:type, כולל route=piste, גם בלי name.
   - תגיות שם חלופיות: piste:name, name:ka, name:ru, name:en.
     לחפש גם שמות בגאורגית וברוסית (למשל Татра, Солико, Шино, Фирни, Бомбора).
   - aerialway מכל סוג, כולל magic_carpet, platter ו-rope_tow. אולי אלה המעליות של Baby ו-Bombora.
   - landuse=winter_sports ושטחי piste:type=downhill מסוג area.
   - היסטוריית העריכה של הדרכים המועמדות (API של OSM). אולי היה להן שם בעבר.
2. הקלטות GPS ציבוריות של OSM:
   https://api.openstreetmap.org/api/0.6/trackpoints?bbox=44.44,42.45,44.55,42.53&page=0
   (ולעבור על הדפים הבאים). לאתר ריכוזים צפופים של נקודות לאורך
   המסלולים החסרים, להפיק מהם קו מרכזי, ולרשום כמה הקלטות תומכות בכל קו.
3. דף הסטטוס הרשמי של MTA: https://status.mta.ski/en/gudauri/gudauri
   זו רשימת המסלולים והרכבלים הרשמית. לבדוק אם יש מאחוריו API או JSON עם מידע נוסף.
4. OpenSkiMap או מקורות GPX פתוחים אחרים, אם הם נגישים בלי התחברות.

כללי דיוק:
- את המפה הרשמית של MTA (איור) לא עוקבים ולא מעתיקים. מותר להשתמש בה רק כדי
  לזהות איזה קו שייך לאיזה מסלול: שם, צבע, איפה הוא מתחיל ואיפה הוא נגמר.
- לא ממציאים קווים. מסלול שלא נמצא לו מקור נשמר עם status "not-found".
- לכל מסלול: status, confidence ומקורות, עם נימוק קצר.

איפה לשמור (חשוב, כדי שאפשר יהיה להשתמש בזה אחר כך):
במסד הנתונים של האתר, עם Artifact action "write_db" וה-url של האתר.
collection: "missingRuns". מסמך אחד לכל מסלול, doc_id:
tatra-2, soliko-2, shino, firni-1, firni-2, baby, bombora.
שדות:
{
  "name": "Tatra 2",
  "color": "blue",
  "kind": "run" או "beginner-area",
  "status": "osm-named" / "osm-unnamed-match" / "gps" / "not-found",
  "confidence": "high" / "medium" / "low",
  "geojson": "מחרוזת JSON של GeoJSON Feature: LineString או MultiLineString לקו,
              Polygon לשטח. סדר [lon,lat], עד 500 נקודות",
  "osmIds": [],
  "gpsTracks": 0,
  "sources": [],
  "notes": "נימוק קצר בעברית"
}
את הגאומטריה שומרים כמחרוזת, לא כמערכים מקוננים.
בנוסף, מסמך "_summary" באותו collection, עם: התאריך, אילו מקורות נבדקו ואילו
נחסמו, ורשימה קצרה של הממצאים.

בסוף: טבלה קצרה בצ'אט עם כל מסלול, ה-status, ה-confidence והמקור.
אם דומיין כלשהו חסום, לכתוב איזה.
```

## 2. אותה שאלה ל-ChatGPT, עם דרישה למקורות ניתנים לבדיקה

```
אני צריך את הממצאים שלך על המסלולים החסרים במפת גודאורי, בצורה מדויקת וניתנת לבדיקה.

כלל חשוב לפני הכל: לכל פריט, תגיד במפורש איך הגעת אליו:
- "retrieved": שלפת אותו עכשיו ממקור חיצוני (API, קובץ, דף).
- "inferred": הסקת אותו, למשל התאמה לפי מיקום.
- "not-found": לא מצאת.
אם לא הייתה לך גישה לרשת בשיחה, או שאתה עונה מהזיכרון, תכתוב את זה.
אל תשחזר קואורדינטות מהזיכרון, ואל תעקוב אחרי המפה המאוירת של MTA.

המסלולים (שמות וצבעים לפי המפה הרשמית של MTA, תיבה 42.44,44.40,42.58,44.58):
1. Tatra 2, כחול
2. Soliko 2, כחול
3. Shino, כחול
4. Firni 1 ו-Firni 2, כחולים. ב-OSM יש דרך אחת, 668794719, בשם "Frini".
   לאיזה מהשניים היא שייכת, ואיפה השני?
5. Baby, אזור מתחילים ירוק, ליד התחנה העליונה של Goodaura וליד רכבל Snow Park.
6. Bombora, אזור מתחילים ירוק, ליד התחנה התחתונה של Kudebi וליד Khada.

שני מועמדים שכבר זיהינו (דרכי OSM בלי שם). האם הם תואמים משהו מהרשימה?
- 668794712 (231 מ׳, מסתיימת בתחנה התחתונה של Shino)
- 1259783832 (1.3 ק״מ, מ-Tatra 1 עד הבסיס ליד Goodaura ו-Zuma)

לכל מסלול תן:
- status: retrieved / inferred / not-found
- confidence: high / medium / low, עם נימוק של משפט אחד
- המקור המדויק:
  * OSM: מזהי way או relation, והתגיות שלהם כפי שהן (name, piste:name, name:ka, name:ru, piste:difficulty)
  * Overpass: השאילתה המלאה, מילה במילה, ואיזה שרת
  * הקלטות GPS: ה-URL המדויק (כולל page), מזהי ההקלטות וכמה הקלטות תמכו בקו
  * כל מקור אחר: URL מלא, ואם זה קובץ, שם הקובץ ואיזה חלק ממנו
- איך הקו הופק: נלקח כמו שהוא, חובר מכמה קטעים, או חושב מנקודות GPS (באיזו שיטה)
- התאריך שבו שלפת את הנתונים

בסוף, תן:
1. GeoJSON FeatureCollection אחד, רק עם פריטים שה-status שלהם retrieved או inferred.
   סדר קואורדינטות [lon,lat]. בכל Feature שדות properties:
   name, color, kind (run / beginner-area), status, confidence,
   source_type, source (URL או שאילתה), osm_ids, gps_track_ids, method, retrieved_at, notes.
2. רשימת "איך לשחזר": השאילתות וה-URL-ים המדויקים, כדי שאוכל להריץ אותם בעצמי ולקבל את אותם נתונים.
3. רשימת הדומיינים שהשתמשת בהם.
```

## 3. שאילתת Overpass הבסיסית (מסלולים ורכבלים)

```
[out:json][timeout:60];
(
  way["piste:type"="downhill"](42.44,44.40,42.58,44.58);
  way["aerialway"](42.44,44.40,42.58,44.58);
);
out body;
>;
out skel qt;
```

## 4. נתוני גובה

אריחי Terrarium מ-AWS Terrain Tiles:
`https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png`
או אריח SRTM אחד שמכסה את כל האזור:
`https://s3.amazonaws.com/elevation-tiles-prod/skadi/N42/N42E044.hgt.gz`
