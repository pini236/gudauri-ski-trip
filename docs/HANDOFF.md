# תוכנית המשך: מה נשאר ואיך לעשות את זה

המסמך הזה ממשיך בדיוק מהמקום שבו העבודה ב-claude.ai נעצרה (30.9.2026). ההקשר הכללי, כללי הדיוק והסכמות נמצאים ב-`CLAUDE.md`.

סדר העבודה: שלב אחרי שלב, commit לכל שלב, ועצירה לאישור של פיני בסוף כל שלב.

**עדכון 30.9.2026:** ההחלטות של שלב 0 התקבלו ורשומות ב-`docs/STATUS.md`. שם גם מעקב ההתקדמות. שינוי מהותי: **שלב 3 הוקטן**, ואין בו מסד נתונים ולא התחברות בשלב הזה. פרטי הטיסה של הקבוצה יהיו קובץ נתונים בריפו, והסרטונים יגיעו מקובץ התחלתי. ההיגיון של Supabase שמופיע בשלב 3 למטה נשמר לשלב ההתאמה האישית, ב-`docs/ROADMAP.md`.

---

## שלב 0: החלטות פתוחות (לשאול את פיני לפני שמתחילים)

להציג כל שאלה עם ההמלצה, ולחכות לתשובה.

| # | שאלה | המלצה |
|---|---|---|
| 1 | **מקור האמת:** הריפו בלבד, או להמשיך לעדכן גם את הארטיפקט ב-claude.ai? | הריפו. אם רוצים מראה ב-claude.ai, לבנות אותו אוטומטית (שלב 1). |
| 2 | **Backend לנתונים המשותפים:** Supabase עם התחברות במייל, או קוד קבוצה משותף? | Supabase עם magic link ורשימת חברים. פיני כבר מחובר ל-Supabase. |
| 3 | **פרטיות:** האתר ציבורי לכל מי שיש לו קישור, או רק לחברים? | המפה ציבורית. כרטיס הטיסה והוספת סרטונים רק לחברים מחוברים. `noindex` לכל האתר. |
| 4 | **דומיין:** כתובת `*.vercel.app` או דומיין משלו? | להתחיל עם `vercel.app`. |
| 5 | **החלק הבא באתר** (שלב 6): ציוד, רשימת אריזה, מזג אוויר ושלג, תוכנית ימים? | לשאול. |

---

## שלב 1: ארגון הריפו, בלי לשנות התנהגות

המטרה: קבצים נפרדים שקל לערוך ולהשוות, בלי שום שינוי במה שהמשתמש רואה.

1. לפצל את `site/index.html`:
   ```
   site/
     index.html            (markup בלבד)
     css/site.css
     js/relief.js          (GudRelief)
     js/app.js             (הסקריפט הראשי)
     data/runs-and-lifts.json
     data/terrain.json
   ```
2. **טעינת נתונים:** היום הסקריפט הראשי קורא את ה-JSON סינכרונית מתגיות `<script>`. להחליף ל-`fetch` של שני הקבצים לפני האתחול: לעטוף את הקוד ב-`async function main()` שרץ אחרי `Promise.all`. בזמן הטעינה להציג מצב טעינה פשוט.
3. להעביר את `data/*.json` מהשורש אל `site/data/`, כדי שלא יהיו שני עותקים (ב-Vercel ה-root יהיה `site`). לעדכן את `README.md`.
4. **מראה ל-claude.ai (אם פיני בחר בזה בשלב 0):** סקריפט `tools/build-single.mjs` שמאחד הכל חזרה לקובץ אחד (`dist/artifact.html`), עם הנתונים בתגיות `<script type="application/json">`, כמו היום.
5. **בדיקות:** `tests/smoke.spec.ts` עם Playwright, לפי הרשימה ב-`CLAUDE.md`. להשוות לצילומי המסך ב-`design/screenshots/`.

**סיום שלב:** האתר נראה ומתנהג בדיוק כמו קודם, מקומית ובבדיקות.

---

## שלב 2: פריסה ב-Vercel (סטטי)

1. **`vercel.json`** בשורש:
   - `cleanUrls: true`.
   - כותרות: `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, ו-`X-Robots-Tag: noindex` (אם פיני בחר פרטיות).
   - `Cache-Control` לקבצי `site/data/*.json`: למשל שעה.
2. **ב-`index.html`:**
   - לוודא שיש `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">`, ואת ריפוד ה-safe-area שהגיע מהסביבה של claude.ai (`:root { padding-top: env(safe-area-inset-top) ... }`).
   - `<meta name="robots" content="noindex">`.
   - תגיות Open Graph, כדי שקישור שנשלח בוואטסאפ ייראה טוב: title, description, ותמונה. אפשר להשתמש בצילום מ-`design/screenshots/phone-home-light.png`.
   - favicon ו-`theme-color` (`#EEF2F5` בהיר, `#0D1522` כהה).
3. **חיבור הריפו ב-Vercel** (פיני עושה בממשק, או דרך `vercel link`):
   - Framework Preset: Other
   - Root Directory: `site`
   - Build Command: ריק
   - Preview deployment לכל branch.
4. לבדוק את כתובת ה-preview: דף בית, מפה, תלת-ממד, טלפון ומחשב.

**סיום שלב:** האתר עובד ב-Vercel במצב קריאה בלבד (מפה, תלת-ממד, פרטים).

---

## שלב 3: backend לנתונים המשותפים

זה החלק החסר היחיד במערכת כדי שהאתר יעבוד מחוץ ל-claude.ai: שמירה משותפת והרשאות.

### אפשרות מומלצת: Supabase

**טבלאות ו-RLS** (migration ב-`supabase/migrations/`):

```sql
create table public.members (
  email text primary key,
  display_name text,
  role text not null default 'member' check (role in ('member','admin'))
);

create table public.videos (
  id uuid primary key default gen_random_uuid(),
  piste text not null,
  url text not null check (url ~ '^https?://'),
  title text check (char_length(title) <= 80),
  by_name text check (char_length(by_name) <= 30),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid()
);

create table public.trip (
  id text primary key,          -- 'flight'
  origin text, destination text, flight text,
  updated_at timestamptz not null default now(),
  updated_by uuid default auth.uid()
);

create or replace function public.is_member() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.members
                 where lower(email) = lower(auth.jwt() ->> 'email'));
$$;

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.members
                 where lower(email) = lower(auth.jwt() ->> 'email') and role = 'admin');
$$;

alter table public.members enable row level security;
alter table public.videos  enable row level security;
alter table public.trip    enable row level security;

create policy "members read members" on public.members for select using (public.is_member());

create policy "anyone reads videos"  on public.videos for select using (true);
create policy "members add videos"   on public.videos for insert with check (public.is_member());
create policy "owner or admin deletes" on public.videos for delete
  using (public.is_admin() or created_by = auth.uid());

create policy "members read trip"    on public.trip for select using (public.is_member());
create policy "members write trip"   on public.trip for insert with check (public.is_member());
create policy "members update trip"  on public.trip for update using (public.is_member());

alter publication supabase_realtime add table public.videos, public.trip;
```

הערות:
- `from` ו-`to` הן מילים שמורות ב-SQL, לכן בטבלה הן `origin` ו-`destination`. ה-adapter ממפה אותן חזרה ל-`from` ו-`to` של הקוד.
- אם פיני בוחר "הכל רק לחברים", לשנות את `anyone reads videos` ל-`public.is_member()`.
- **את כתובות המייל של החברים מכניסים לטבלת `members` דרך לוח הבקרה של Supabase, לא דרך הריפו.** פיני מקבל `role = 'admin'`.

**Auth:**
- Magic link (`signInWithOtp` עם `emailRedirectTo: location.origin`).
- ב-Supabase Auth: Site URL = כתובת ה-Vercel, ולהוסיף את כתובות ה-preview לרשימת ה-Redirect URLs.

**צד לקוח:**
1. `site/js/config.js`: `SUPABASE_URL` ו-`SUPABASE_ANON_KEY` (ציבוריים; ההגנה היא ה-RLS).
2. לטעון את `@supabase/supabase-js@2` כגרסת UMD נעוצה מ-jsdelivr.
3. **`site/js/store.js`:** שכבת adapter עם ממשק אחד ושלושה מימושים:
   - `claude`: כשיש `window.claude` (שומר על ההתנהגות של היום, בשביל המראה ב-claude.ai).
   - `supabase`: כשיש `config.js`.
   - `none`: קריאה בלבד.

   ממשק:
   ```
   Store.init()                         -> {backend, user, canWrite}
   Store.onVideos(cb)                   -> unsubscribe   // cb(list of {id,piste,url,title,by,at})
   Store.addVideo({piste,url,title,by})
   Store.deleteVideo(id)
   Store.canDelete(video)
   Store.onTrip(cb)                     -> unsubscribe   // cb({from,to,flight})
   Store.saveTrip({from,to,flight})
   Store.signIn(email) / Store.signOut() / Store.user()
   ```
4. להחליף ב-`app.js` את `initDb`, ההוספה ב-`bindForm`, את `delVid`, את `watchTrip` ואת ה-submit של כרטיס הטיסה בקריאות ל-`Store`. ההודעות למשתמש נשארות, רק הודעת ההרשאה משתנה: "צריך להתחבר עם המייל שפיני הוסיף".
5. **ממשק התחברות:**
   - קישור "התחברות" בסרגל העליון ובכותרת של המפה בטלפון.
   - טופס מייל. אחרי שליחה: "שלחנו קישור למייל".
   - כשמחובר: שם המשתמש וקישור "יציאה".
   - כפתור "עריכת פרטי הטיסה" וטופס הוספת הסרטונים מופיעים רק לחברים.

### אפשרות חלופית (אם פיני לא רוצה חשבונות)

Vercel Functions עם Upstash Redis (או KV דומה) וקוד קבוצה משותף במשתנה סביבה `GROUP_CODE`:
- נקודות קצה: `/api/videos` (GET, POST, DELETE) ו-`/api/trip` (GET, PUT).
- כתיבה רק עם הקוד בכותרת.
- פשוט יותר לחברים, אבל חלש יותר: קוד שדלף נותן גישת כתיבה לכל מי שמחזיק בו.

**סיום שלב:** ב-Vercel אפשר להתחבר, להוסיף ולמחוק סרטון, ולערוך את כרטיס הטיסה. מי שלא ברשימה רואה את המפה בלבד.

---

## שלב 4: סרטונים למסלולים

זו הייתה חלק מהבקשה המקורית: קישורים לסרטונים על כל מסלול, "שאני אמצא או שפיני יוסיף".

1. לכל מסלול ב-`site/data/runs-and-lifts.json` לחפש ב-YouTube (וגם ב-Vimeo): `Gudauri <שם המסלול>`, וגם שמות ברוסית ובגאורגית.
2. **להכניס רק סרטון שבאמת מראה את המסלול הזה:** השם מופיע בכותרת, בתיאור או בפרקים, או שהסרטון מזהה אותו בבירור. לא לנחש לפי "סרטון מגודאורי".
3. לכל סרטון לרשום: `url, title, channel, published, duration, evidence` (משפט על איך נקבע שזה המסלול).
4. תוצר:
   - `research/videos.md`: טבלה לפי מסלול, כולל רשימת המסלולים שלא נמצא להם סרטון.
   - `site/data/videos-seed.json`.
5. אחרי אישור של פיני: להכניס לטבלת `videos` עם `by_name = 'מחקר'`.

---

## שלב 5: פערים בנתוני המפה

רשימה מלאה ב-`research/missing-runs/README.md` וב-`research/lifts.md`.

**קטעים בלי מקור:**
- ההמשך של Soliko 2 (נמצאו 372 מ׳ מתוך כ-1,180).
- החלק התחתון של Firni 1 והחלק העליון של Firni 2.
- הקטע המערבי של Shino לכיוון Alpina.
- הגבולות של אזורי המתחילים Baby ו-Bombora.

**רכבלים חסרים:** Alpina, ומעליות Baby ו-Bombora (J-bar לפי skiresort).

**אי-התאמות בגובה:**
- Kikilo ו-New Goodaura: כ-150 מ׳ מול דף הסטטוס של MTA.
- Kobi 1 ו-Kobi 2: כ-200 מ׳ מול gudauri.com.
- Bombora: כ-70 מ׳ מתחת למה ש-skiresort מציין.

**משימות:**
1. **לפני הטיסה:** להריץ שוב את שאילתות ה-Overpass (`research/prompts.md`), כי ייתכן שמישהו מיפה בינתיים. להשוות למה שיש.
2. **כלי ייבוא GPX:** `tools/gpx-import/`:
   - קלט: קבצי GPX שפיני יקליט בחופשה.
   - לזהות קטעי גלישה בירידה (מהירות וירידה בגובה), ולהתאים אותם למסלול לפי קרבה לקצוות ולקווים קיימים.
   - לפשט לעד 500 נקודות, בפורמט `[lat, lon]`.
   - להריץ את בדיקות הדיוק מ-`CLAUDE.md`, ולעדכן את `research` של המסלול (`status: 'gps'`, ודאות לפי מספר ההקלטות).
3. **מסמך לפיני לחופשה:** `research/trip-recording.md`:
   - אילו קטעים להקליט (הרשימה למעלה).
   - באיזו אפליקציה, כך שאפשר לייצא GPX (למשל Slopes, Strava או OsmAnd).
   - לבדוק גובה בתחנות Kikilo, New Goodaura ו-Kobi.
4. **אופציונלי:** להעלות את הקווים שהוקלטו ל-OpenStreetMap, כדי שהנתונים יהיו זמינים לכולם.

---

## שלב 6: החלק הבא באתר

בדף הבית יש מקום מסומן, "חלקים נוספים בקרוב". לפי מה שפיני יבחר בשלב 0:

- **ציוד:** התוכן כבר קיים ב-`research/gear.md` (נעליים, גרביים, תוספות).
- **רשימת אריזה:** אישית ומשותפת, עם סימון. משתמשת ב-backend משלב 3.
- **מזג אוויר ושלג:** Open-Meteo (חינמי, בלי מפתח) לנקודות בגבהים שונים: הכפר (~2,000 מ׳), Goodaura העליונה (~2,700 מ׳), Sadzele (~3,200 מ׳).
- **מצב רכבלים ומסלולים:** מדף הסטטוס של MTA. הדף מחזיר לעיתים שגיאת 500, ולכן כדאי Vercel Cron שמושך ושומר עותק אחרון תקין.
- **מצב אופליין (PWA):** על ההר הקליטה חלשה. service worker שמשמר את האתר, הנתונים, three.js והגופנים.
- **תוכנית ימים:** התקדמות לפי רמה, מבוססת על `research/runs.md`.

כל חלק חדש צריך:
- לשמור על השפה העיצובית.
- לקבל שלט בעמוד השלט בדף הבית ובניווט העליון, במקום "חלקים נוספים בקרוב".

---

## שלב 7: תיעוד

- לעדכן את `README.md`: מבנה חדש, הרצה מקומית, בדיקות, פריסה, backend.
- לעדכן את `site/README.md`.
- כל מחקר חדש נכנס ל-`research/`, עם מקורות.

---

## דברים שפיני עושה בעצמו

- לחבר את הריפו ל-Vercel (או לאשר `vercel link`).
- ליצור פרויקט Supabase, או לאשר שימוש בקיים, ולהכניס את כתובות המייל של החברים לטבלת `members`.
- למלא את פרטי הטיסה אחרי שלב 3.
- להקליט GPS בחופשה לפי `research/trip-recording.md`.
