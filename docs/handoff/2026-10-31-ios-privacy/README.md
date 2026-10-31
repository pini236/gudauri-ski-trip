# מדיניות הפרטיות: האייפון והכניסה עם אפל (טיוטה לאישור פיני)

**הסשן המשפטי, 31.10.2026, לבקשת סשן המנהל.** טיוטה בלבד: `docs/PRIVACY.md` ודף הפרטיות לא שונו. **טקסט המדיניות משתנה רק באישור פיני** (`CLAUDE.md`). זה לא ייעוץ משפטי.

**איך נבדק:** מעבר על הרשימה ב-`docs/APPLE.md` (סעיף 5.2), ועל הקוד של האייפון בענף הראשי (31.10.2026): `app/ios/Gudi/App/Telemetry.swift`, `Account/SignIn.swift`, `Group/GroupService.swift`, `Meet/Reminders.swift`, `About/AboutScreen.swift`, `project.yml`, `PrivacyInfo.xcprivacy`. כל כתובת חיצונית שהקוד פונה אליה נבדקה. יום הגלישה (עוד לא בענף הראשי) לא נכלל כאן; יש לו טיוטה משלו.

## מה הקוד עושה בפועל (הבסיס לנוסח)

| נושא | מה מצאתי בקוד | מה זה אומר למדיניות |
|---|---|---|
| מדידה (PostHog) | בלי ספרייה; מזהה אקראי ב-`UserDefaults`; בלי `identify`; בלי פרופיל; לא נשלח מהסימולטור ומהבדיקות; מתג כיבוי. נשלחים `platform`, גרסה, `build`, שפה, ערכת תצוגה וסוג מכשיר (טלפון או טאבלט), **בלי דגם ובלי גרסת מערכת** | הסעיף הקיים נכון (הוא אומר יותר ממה שהאייפון שולח, וזה בסדר). תוספת קטנה: המזהה יכול לחזור משחזור גיבוי |
| דיווח קריסות (Sentry) | **אין באייפון.** בקוד כתוב שיבוא בהמשך, בפרויקט `gudi-ios` | צריך לומר שהאייפון עוד לא שולח דיווחי קריסה. כשייכנס: לעדכן שוב, באישור פיני |
| הקלטת מסך | אין באייפון | "The website does not record your screen" הופך ל"האייפון והאתר לא מקליטים" |
| גיבוי | עותקי הקבוצות מוחרגים מהגיבוי (`isExcludedFromBackup`), והכניסה בצרור המפתחות "במכשיר הזה בלבד" (`ThisDeviceOnly`). **אבל** ההגדרות, השיאים, "הטיול שלך" והמזהה האקראי נשמרים ב-`UserDefaults`, **שנכלל בגיבוי של האייפון** (iCloud או מחשב) | המשפט "לא נכלל בגיבויים של אנדרואיד" נכון רק לאנדרואיד. צריך משפט לאייפון (חלופה א), או שינוי בקוד (חלופה ב) |
| כניסה עם אפל | `requestedScopes = [.fullName]` בלבד: **האפליקציה לא מבקשת מאפל מייל.** השם (רק בכניסה הראשונה, כך אפל עובדת) נכנס לפרופיל רק אם עוד אין שם. האסימון הולך לסופבייס | ב-`docs/APPLE.md` 5.2 נכתב "אפל נותנת שם ומייל"; לפי הקוד מבקשים רק שם. הנוסח צריך לומר את זה, ולשמור מקום למייל אם אפל בכל זאת כוללת כתובת באסימון (ראו "מה לא אומת") |
| כניסה עם גוגל | דרך הדפדפן של המערכת, `id_token` לסופבייס, בלי שם מגוגל לפרופיל | הסעיף הקיים נכון גם לאייפון |
| מיקום ("איפה אני") | הרשאה "בזמן השימוש" בלבד, והטקסט למשתמש אומר שהמיקום לא נשלח ולא נשמר | הסעיף הקיים נכון |
| תזכורות למפגשים | התראות מקומיות מהטלפון, בלי שרת דחיפה | אין איסוף. אפשר להוסיף משפט קצר (ראו למטה, לא חובה) |
| תמונות ממוזערות ביוטיוב | `i.ytimg.com` | הסעיף הקיים על יוטיוב מכסה |
| התמונה של פיני באודות | באייפון רק קישור ל-`github.com/pini236`, בלי טעינה של תמונה | אין מה להוסיף |
| מחיקת חשבון | קיימת בדף החשבון באפליקציה (`deleteAccount`) | נכון. תוספת: איך מנתקים את האפליקציה גם אצל אפל |

## השינויים, בעברית ובאנגלית

הנוסח העברי מקביל לסעיפים בחלק העברי של `docs/PRIVACY.md`, והאנגלי לחלק האנגלי. **רוסית וגאורגית: אחרי שפיני מאשר**, כמו בטיוטה של יום הגלישה (סשן התוכן מתרגם מהאנגלית).

### 1. הפתיחה

- **היום:** "an Android app (package `io.github.pini236.skiapp`)".
- **EN:**

  > Gudi: Gudauri Ski Map is an app for Android and iPhone (`io.github.pini236.skiapp`) and a website (`gudauri-ski-trip.vercel.app`) with a ski map of Gudauri, Georgia.

- **HE:** "Gudi: Gudauri Ski Map היא אפליקציה לאנדרואיד ולאייפון ואתר עם מפת סקי של גודאורי, גאורגיה." (המזהה והכתובת בשורה משלהם, כמו היום.)

### 2. "Stored only with you", המשפט על הגיבוי

**חלופה א (מומלצת, בלי שינוי בקוד):**

- **EN:**

  > On Android, device backup is turned off in the app, so what it stores is not included in backups. On iPhone, your settings, game scores, "Your trip" and the random usage ID may be included in your iPhone's backup (iCloud or a computer), which Apple keeps under your Apple account and we cannot access; the copy of your groups and your sign-in are never included in backups.

- **HE:** "באנדרואיד הגיבוי כבוי באפליקציה, ולכן מה שהיא שומרת לא נכלל בגיבויים. באייפון ההגדרות, השיאים במשחקים, 'הטיול שלך' והמזהה האקראי של המדידה עשויים להיכלל בגיבוי של האייפון (ב-iCloud או במחשב), שאפל שומרת תחת חשבון האפל שלך ואין לנו גישה אליו. העותק של הקבוצות שלך והכניסה לחשבון לא נכללים בגיבוי אף פעם."

**חלופה ב:** סשן האייפון מעביר את הטיול, השיאים והמזהה לקובץ שמוחרג מהגיבוי, כמו עותקי הקבוצות, ואז המשפט של האנדרואיד נכון לשניהם ("what the apps store is not included in backups"). המחיר: מי שמחליף אייפון מאבד את הטיול והשיאים. **ההמלצה: א.** גיבוי של המשתמש אצל אפל הוא לא העברה אלינו, והמשתמש מרוויח ממנו. אם פיני בוחר ב, זה שינוי קוד אצל סשן האייפון לפני הפרסום.

### 3. "What we collect"

**סעיף 1, המדידה** (תוספת בסוף המשפט על המזהה):

- **EN:** "...and changes if you reinstall the app (on iPhone, restoring a backup may bring the same ID back)."
- **HE:** "...ומשתנה אם מתקינים את האפליקציה מחדש (באייפון, שחזור מגיבוי עשוי להחזיר את אותו מזהה)."

**סעיף 2, דיווח קריסות** (תוספת בסוף):

- **EN:** "The iPhone app does not send crash reports yet."
- **HE:** "אפליקציית האייפון עוד לא שולחת דיווחי קריסה."

**סעיף 3, הקלטת מסך** (המשפט האחרון):

- **היום:** "The website does not record your screen."
- **EN:** "The iPhone app and the website do not record your screen."
- **HE:** "אפליקציית האייפון והאתר לא מקליטים את המסך."

### 4. "Accounts and groups", סעיף 1 (זהות)

אחרי המשפט על גוגל:

- **EN:**

  > Signing in with Apple (on iPhone) stores, in the sign-in service, the account's ID from Apple, and an email address only if Apple provides one; if you chose "Hide My Email", it is a private relay address that Apple forwards to you. The app asks Apple only for your name, which Apple shares only the first time you sign in, and we copy it into the profile only if you have not chosen a name yet. We do not show the email to anyone and do not use it for anything else.

- **HE:** "כניסה עם אפל (באייפון) שומרת בשירות הכניסה את המזהה של החשבון מאפל, וכתובת מייל רק אם אפל מוסרת אותה; אם בחרת 'הסתרת המייל שלי', זו כתובת ממסרת פרטית שאפל מעבירה אליך. האפליקציה מבקשת מאפל רק את השם, שאפל מוסרת רק בכניסה הראשונה, ואנחנו מעתיקים אותו לפרופיל רק אם עוד לא בחרת שם. אנחנו לא מראים את המייל לאף אחד ולא משתמשים בו לשום דבר אחר."

### 5. "Deletion" (תוספת בסוף)

- **EN:** "If you signed in with Apple, you can also stop using Apple sign-in for the app in your iPhone's settings (your name, Sign in with Apple)."
- **HE:** "אם נכנסת עם אפל, אפשר גם להפסיק את הכניסה עם אפל לאפליקציה בהגדרות האייפון (השם שלך, 'התחברות עם Apple')."

השמות המדויקים של התפריטים באייפון (בעברית ובאנגלית) ייבדקו באייפון לפני הפרסום.

### 6. "Services that process data for us"

- **EN:** "...and Google and Apple, for signing in with a Google or Apple account (their privacy policies apply)."
- **HE:** "...וגוגל ואפל, לכניסה עם חשבון גוגל או אפל (חלות מדיניות הפרטיות שלהן)."

### 7. לא חובה: תזכורות

- **EN:** "Meetup reminders are scheduled on your phone and do not go through our server."
- **HE:** "התזכורות למפגשים נקבעות בטלפון ולא עוברות דרך השרת שלנו."

(נכון גם לאנדרואיד. זה ממלא פער ישן מהדוח הראשון שלי: ההתראות לא מוזכרות במדיניות.)

### 8. לא משתנה עכשיו

- **האתר:** "If you join a group or sign in with Google" נשאר. כשהכניסה עם אפל תיכנס לאתר, מוסיפים את אפל שם (`docs/APPLE.md`, 3.1).
- **"In short", "Location", "Content from the internet", "Children", "Your rights":** נכונים לאייפון כמו שהם.

## תווית הפרטיות של אפל ו-`PrivacyInfo.xcprivacy`

| שורה | `docs/APPLE.md` 5.3 | המניפסט בקוד | ההכרעה |
|---|---|---|---|
| `Name`, `Email Address`, `User ID`, `Other User Content` (מקושרים, `App Functionality`) | יש | יש | **נכון.** המייל נשאר בגלל הכניסה עם גוגל (ואולי אפל) |
| `Device ID`, `Product Interaction` (לא מקושרים, `Analytics`) | יש | יש | **נכון,** כל עוד אין `identify` (תנאי הארכיטקט). הקוד עומד בו |
| `Crash Data`, `Performance Data` | יש | **אין** | **המניפסט נכון והטבלה ב-5.3 לא:** בלי Sentry באייפון אין איסוף כזה. בתווית ב-App Store Connect **לא לסמן** אותן עד ש-Sentry נכנס; אז להוסיף בתווית, במניפסט ובמדיניות יחד. (סימון יתר הוא לא הפרה, אבל אומר למשתמש משהו לא נכון) |
| מעקב | אין | `NSPrivacyTracking false`, בלי דומיינים | **נכון** |
| מיקום | לא נאסף | לא מוצהר | **נכון** לגבי "איפה אני". ראו את הסיכון למטה על PostHog |
| סיבות לממשקים | `UserDefaults`, זמן מאז ההפעלה | `CA92.1`, `35F9.1` | **נכון, שניהם.** `CA92.1` ל-`UserDefaults`. `35F9.1`: בתלת-ממד ובמשחקים נקראת `CACurrentMediaTime()`, שבנויה על `mach_absolute_time` (סשן האייפון בדק, 31.10.2026; בבדיקה הראשונה שלי חיפשתי רק `systemUptime` ופספסתי). ההצהרה נשארת: הצהרה חסרה עלולה לגרום לדחייה, ומיותרת לא |

**ממצא ותיקון לסשן החנויות ולסשן האייפון:** לעדכן את הטבלה ב-`docs/APPLE.md` 5.3 כך ש-`Crash Data` ו-`Performance Data` מסומנים "רק אחרי Sentry". לא תיקנתי שם, כי זה מסמך של סשן אחר; עבר לסשן המנהל.

## סיכון שצריך החלטה (לא רק באייפון)

**PostHog מוסיף מדינה לכל אירוע לפי כתובת ה-IP, לפני שהוא זורק את הכתובת** (ההגדרה "Discard client IP data" לא מבטלת את זה; הייתה בדוח הראשון שלי). לפי ההגדרות של אפל, מיקום ברזולוציה נמוכה (`Coarse Location`) שספרייה או שירות של צד שלישי גוזרים ושומרים הוא "איסוף"; גם בטופס של גוגל זה "מיקום משוער". היום לא מצהירים עליו בשום מקום. **שתי דרכים:**

1. **מומלץ:** לכבות את ההעשרה הגיאוגרפית בפרויקט ב-PostHog (פעולה בשירות חי, רק פיני). אז הכל נשאר נכון כמו שהוא, וגרף המדינות בלוח נעלם.
2. להצהיר: `Coarse Location` (לא מקושר, `Analytics`) בתווית ובמניפסט, "מיקום משוער" בטופס של גוגל, ומשפט במדיניות ("the country, derived from the IP address, which is then discarded").

**לעורך דין:** האם מדינה שנגזרת מכתובת IP ונשמרת עם מזהה אקראי היא "מיקום משוער" לפי אפל וגוגל. ההערכה שלי: כן, ולכן אחת משתי הדרכים חובה לפני ההגשה ל-App Store.

## מה לא אומת

- **האם אפל כוללת מייל באסימון גם כשלא מבקשים אותו.** לפי התיעוד של אפל (`ASAuthorization.Scope`, https://developer.apple.com/documentation/authenticationservices/asauthorization/scope), המייל מוסר רק כשמבקשים `email`; לא בדקתי אסימון אמיתי. הנוסח בסעיף 4 נכון בשני המקרים. אפשר לבדוק בכניסה הראשונה של פיני ב-TestFlight: האם בשורה שלו ב-`auth.users` בסופבייס יש מייל.
- **ביטול האסימונים אצל אפל במחיקת חשבון** (`docs/APPLE.md`, 3; ההנחיה של אפל, https://developer.apple.com/support/offering-account-deletion-in-your-app/): הארכיטקט אישר בלי ביטול, בתנאי שלא נשמר אסימון רענון. זו החלטה של פיני, והסיכון הוא דחייה בסקירה, לא הפרת פרטיות. הנוסח בסעיף 5 לא תלוי בה.
- **התפריטים באייפון** (סעיף 5): לבדוק באייפון.

## לפרסם יחד עם שורת יום הגלישה

**כן, הכל יכול לצאת בפעם אחת:** שתי הטיוטות משנות סעיפים שונים (יום הגלישה מוסיף שורה על המיקום וקובץ המסלול; כאן הפתיחה, הגיבוי, הקריסות, הזהות, המחיקה והשירותים), ושתיהן מדברות על מה שכבר בקוד או יהיה בו לפני הפרסום. עם אישור אחד של פיני:

1. סשן התוכן מתרגם את הטיוטה הזו לרוסית ולגאורגית (את יום הגלישה כבר תרגם).
2. שתיהן נכנסות ל-`docs/PRIVACY.md` באותו commit, עם תאריך תוקף אחד, ואז `python3 tools/build-privacy.py`.
3. **תנאי:** יום הגלישה מתפרסם רק כשהקלטת יום הגלישה ממוזגת באחת האפליקציות, ומשפט הגיבוי (סעיף 2) צריך להתאים גם לקובץ היום (`isExcludedFromBackup` באייפון, `dataExtractionRules` באנדרואיד, לפי המפרט: "מחוץ לגיבויים"). אם יום הגלישה עוד לא מוכן, החלק של האייפון יכול לצאת לבד, **לפני ההגשה הראשונה ל-App Store** (וטוב שגם לפני בודקים חיצוניים ב-TestFlight).

**שלוש החלטות לפיני, באותו אישור:** (1) הנוסח בסעיפים 1 עד 6, ו-7 אם רוצים; (2) הגיבוי באייפון: חלופה א (מומלץ) או ב; (3) המדינה ב-PostHog: לכבות (מומלץ) או להצהיר.

## מקורות (נבדקו ב-31.10.2026)

- אפל, App privacy details (הגדרות איסוף, קישור, מעקב, מיקום משוער): https://developer.apple.com/app-store/app-privacy-details/
- אפל, Privacy manifest files ו-Required reason APIs: https://developer.apple.com/documentation/bundleresources/privacy-manifest-files , https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api
- אפל, Sign in with Apple, היקפים (שם ומייל): https://developer.apple.com/documentation/authenticationservices/asauthorization/scope
- אפל, iCloud backup ו-`isExcludedFromBackup`: https://developer.apple.com/documentation/foundation/optimizing-your-app-s-data-for-icloud-backup
- גוגל פליי, Data safety (מיקום משוער): https://support.google.com/googleplay/android-developer/answer/10787469
