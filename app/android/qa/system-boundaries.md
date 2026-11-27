# גבולות המערכת: מי קורא לקוד שלנו, ומאיזה שרשור

**למה המסמך הזה קיים (14.11.2026).** באייפון, בנייה 55 עם יום הגלישה קרסה בטלפון אמיתי ברגע שהתחילה הקלטה: מטפל של מד התאוצה נקרא מתור צדדי בזמן שהוא היה מבודד לשרשור הראשי. בסימולטור אין מד תאוצה, ולכן הבדיקות לא תפסו. באנדרואיד אותה מחלקה של בעיות נראית אחרת (לא קריסה מיידית, אלא מרוץ שמקלקל נתונים לפעמים), ולכן עוברים על כל מקום שבו המערכת קוראת לנו.

**הכלל:** מטפל שהמערכת קוראת לו (חיישן, מיקום, שירות, התראה, שעון מעורר, רשת) לא מניחים לו שרשור. לכל אחד כותבים באיזה שרשור הוא מגיע, ומה הוא נוגע בו. מה שנוגע בו משני שרשורים חייב להיות נעול, אטומי, `@Volatile`, או שהעבודה מועברת לשרשור אחד.

## הטבלה

| נקודת מגע | איפה | באיזה שרשור הקריאה מגיעה | מה היא נוגעת בו | מצב |
|---|---|---|---|---|
| מד תאוצה, ג'ירוסקופ, ברומטר (`SensorEventListener`) | `dayrec/DayRecService.kt` | שרשור ההקלטה (`HandlerThread("dayrec")`), כי נרשם עם `handler` | `MotionMeter`, `ImpactBuffer` (שניהם `@Synchronized`), `pressure` (`@Volatile`) | תקין. נרשם בשרשור ההקלטה, ולא בראשי |
| GPS (`LocationListener`) | `dayrec/DayRecService.kt` | שרשור ההקלטה (`requestLocationUpdates(..., handler.looper)`) | קובץ היום, המנוע, `DayRec.*` (כולם `@Volatile`) | תקין |
| סטטוס הלוויינים (`GnssStatus.Callback`) | `dayrec/DayRecService.kt` | שרשור ההקלטה | `satsUsed`, `satsSeen` (`@Volatile`) | תקין |
| עצירה מההתראה או מהמסך (`onStartCommand`, `STOP`) | `dayrec/DayRecService.kt` | **השרשור הראשי** | היה: סגירת הקובץ, `teardown()` ו-`engine = null` תוך כדי שקריאת GPS רצה באותו רגע | **תוקן (14.11.2026):** העצירה עוברת לשרשור ההקלטה (`requestFinish`), ו-`finish` מוגן ב-`compareAndSet` |
| `onDestroy` | `dayrec/DayRecService.kt` | השרשור הראשי | `teardown()` | **תוקן:** `teardown` רץ פעם אחת (`torn`), וגם `stopping` הוא אטומי |
| ניתוח היום המלא (`finals`) | `dayrec/DayRecService.kt`, `dayrec/DayEngine.kt` | executor משלו | העתק של רשימת הנקודות בזמן שהשרשור של ההקלטה מוסיף נקודות | **תוקן:** `DayEngine.add`, `addEvent` וההעתקה ב-`final` תחת נעילה אחת (העתק של רשימה שגדלה יכול להחזיר תאים ריקים) |
| המסך החי קורא את מצב ההקלטה | `dayrec/DayRec.kt`, `dayrec/ui/RecordUi.kt` | השרשור הראשי (Compose) | שדות `DayRec.*` | תקין: כולם `@Volatile`, ו-`Snapshot` הוא אובייקט שלא משתנה |
| התראה קבועה (`NotificationManager.notify`) | `dayrec/DayRecService.kt` | שרשור ההקלטה | המערכת | תקין (`notify` בטוח משכל שרשור) |
| שעון מעורר ו-`BroadcastReceiver` של התזכורות | `meet/Reminders.kt` | `onReceive` בשרשור הראשי, `arm` ב-`Dispatchers.IO`, `set` מנגיעה | קריאה-וכתיבה של רשימות בהעדפות (`armed`, `codes`, `next_code`, `off`) | **תוקן:** כל פעולה שקוראת ואז כותבת היא `@Synchronized` על האובייקט. בלי זה, שתי כתובות הפעלה יכלו להיכנס לאותו קוד, ותזכורת אחת לא הייתה מצלצלת |
| מיקום עצמי על המפה (`LocationListener`) | `map/WhereAmI.kt` | השרשור הראשי (`Looper.getMainLooper()`) | מצב Compose (`state`) | תקין: הכל בשרשור אחד |
| Compass for the far card and sheet (`SensorEventListener`, rotation vector) | `redesign/Far.kt` (`rememberHeading`) | the main thread (registered with a `Handler` on the main looper) | a Compose state (the heading) | OK: one thread; registered only while the card or sheet is shown (UX part 7, 27.11.2026) |
| ציור המפה (`GLSurfaceView.Renderer`) | `map/MapRenderer.kt`, `map/MapView.kt` | שרשור הציור | המצלמה (`@Synchronized`), שדות `@Volatile`; תגובות לשרשור הראשי עוברות ב-`post` | תקין |
| הרשאות (`ActivityResultContract`) | `MainActivity.kt`, `map/MapScreen.kt`, `group/GroupScreen.kt` | השרשור הראשי | מצב Compose | תקין |
| רשת: חיבור חוזר (`NetworkCallback.onAvailable`) | `server/Sync.kt` | שרשור של המערכת | `flush()`, שרק מעביר את העבודה ל-worker | תקין |
| רשת: תור הכתיבות והעותק המקומי | `server/Sync.kt` | worker יחיד, ומסכים בשרשור הראשי | `StateFlow` של הקבוצה: קריאה-ואז-כתיבה (`flow.value = f(flow.value)`) | **תוקן:** כולם עברו ל-`flow.update { }`, שהוא אטומי |
| זמן אמת (WebSocket) | `server/Realtime.kt` | שרשור של OkHttp ו-timer משלו | הערוצים, תחת `synchronized` | תקין |
| סינכרון הטיול לשרת | `server/TripSync.kt` | executor יחיד | העדפות | תקין |
| מקור ההתקנה (`InstallReferrerStateListener`) | `telemetry/OpenSource.kt` | נקבע על ידי הספרייה | `answered` | **תוקן:** אטומי |
| הקלטת מסך של Sentry | `telemetry/Replay*.kt` | שרשור הראשי וה-executor של השולח | `privacy` תחת `synchronized`; השאר מהשרשור הראשי | תקין, בדיקה לפי מבנה (השדות הפשוטים נקראים רק מהשרשור הראשי) |
| מדד פריימים | `perf/FrameStats.kt`, `MainActivity.kt` | שרשור `frame-metrics` | `@Synchronized` | תקין |
| צליל | `fx/Synth.kt` | שרשור משלו | רשימות הקולות תחת נעילה | תקין |
| קבצי יום, תוצאה ורגעים | `dayrec/DayLog.kt`, `MomentsStore.kt`, `DayAnalysis.kt` | שרשורים שונים | הקובץ: `DayLog` (`@Synchronized`), `MomentsStore.apply` (`@Synchronized`), כתיבה להעתק ואז החלפה | תקין |
| יומן אבחון | `qa/Diag.kt` | executor יחיד | קובץ | תקין |

**מה לא נמצא:** `WorkManager` ושידורים שנרשמים בקוד (`registerReceiver`) אין באפליקציה. ההרשאות נשאלות רק מהמסכים.

## הבדיקות

**יחידה, בלי מכשיר** (`SystemBoundariesTest`, `SyncTest`): כל חלק שהמטפלים נוגעים בו מופעל משני שרשורים או יותר באותו זמן, ושום דבר לא נזרק:

- `DayEngine.final()` בזמן שנקודות נכנסות.
- `ImpactBuffer`: הזנת התאוצה, הזנת הג'ירוסקופ והניקוי משלושה שרשורים.
- `MotionMeter`: הזנה ולקיחה במקביל.
- `DayLog`: נקודות וסגירה משני שרשורים, וקריאה בו-זמנית; הקובץ נקרא אחר כך שלם.
- `Sync`: כתיבות ו-`flush()` ממספר שרשורים, ו-worker אמיתי.

**מה אי אפשר ביחידה:** מחלקות שדורשות `Context` של המערכת (`DayRecService`, `Reminders`, `WhereAmI`). אין בפרויקט Robolectric, וקריאה ל-`Context` ביחידה נכשלת. לכן בהן רק:

- ההגנה בקוד (נעילה, אטומיות, העברת העבודה לשרשור אחד), שנבדקה בקריאה.
- שלב באמולטור (`qa/run.sh`, `dayrec`): האמולטור **כן** מדמה חיישנים, ולכן בזמן ההקלטה מוזרמת מכה של כ-5 גרם (`adb emu sensor set acceleration`) ומצופה שורת הלוג `dayrec blow window written`. מטפל שנוגע במשהו של השרשור הראשי היה קורס, וכשל כזה נתפס ב-`errors.txt` של הריצה. אם האמולטור לא מוסר את המכה, השלב רושם הערה ולא נכשל (איסוף החיישנים באמולטור משתנה).
- הבדיקה הקיימת של עצירה מההתראה בזמן שהמסך החי פתוח (`dayrec-notification`, ובדיקת `dumpsys` שהשירות ירד).

**מה שאף בדיקה לא תופסת:** ההתנהגות של חיישנים אמיתיים בטלפונים שונים (קצב, אצווה, חיישן שחסר). זה נבדק רק בשטח ובטלפון של פיני.

## כשמוסיפים נקודת מגע חדשה

1. לכתוב בשורה אחת בטבלה: איזה מטפל, באיזה שרשור, מה הוא נוגע בו.
2. מה שנוגע בו משני שרשורים: נעילה, `@Volatile`, אטומי, או להעביר לשרשור אחד.
3. בדיקת יחידה שמפעילה את החלק הטהור משרשורים צדדיים (עם `together(...)` מ-`SystemBoundariesTest`).
4. אם אי אפשר ביחידה: שלב ב-`qa/run.sh`, ואם החיישן או המערכת ניתנים לדימוי באמולטור, להזרים אותם.
