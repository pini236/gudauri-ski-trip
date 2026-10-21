# אפליקציית האייפון: הוראות לסשן שעובד כאן

נטען בנוסף ל-`CLAUDE.md` הראשי. קודם: `docs/STATUS.md` (שלב 13.8), `docs/ARCHITECTURE.md` (סעיף 11, ותנאי הסקירה מ-21.10.2026) ו-`docs/APPLE.md` (החשבון, החתימה והחנות).

- **נייטיב מלא:** `Swift` ו-`SwiftUI`, iOS 17 ומעלה, אייפון בלבד. המפה ב-`Metal` ישירות. בלי WebView ובלי קוד משותף עם האתר או עם האנדרואיד. `chatgpt/` לא שלנו.
- **נבנה בענן, בלי מק** (החלטה 71): הקוד נכתב כאן, ונבנה ונבדק על המקים של GitHub (`.github/workflows/ios.yml`). הפרויקט נוצר מ-`project.yml` ב-XcodeGen; `Gudi.xcodeproj` לא בריפו ולא עורכים אותו.
- **שכבות** (סעיף 11): `Packages/GudiCore` עם `GudiData` (הנתונים הארוזים) ו-`GudiDomain` (החישובים), בלי UIKit ו-SwiftUI; המסכים ב-`Gudi/`. אין מעגלים בין שכבות.
- **לפני כל דחיפה:** `swift test --package-path app/ios/Packages/GudiCore` עובר (בסביבת הענן יש Swift ל-Linux ב-`/opt/swift/.../usr/bin`). זה נוחות, לא שער: האמת היא הריצה ב-GitHub על macOS (הארכיטקט).
- **יישור עם האתר והאנדרואיד:** כל חישוב נבדק מול אותם ערכים כמו באנדרואיד ומול קבצי האתר עצמם (`site/js/relief.js`, `site/css/site.css`, `tools/fixtures/*.json`), בלי עותקים. יכולת חדשה מוסיפה שורה ב-`docs/PARITY.md`.
- **זמן:** חישובי הטיול בימים ובשעות של הטלפון (`Day`, `ClockTime`); שעון גודאורי קבוע, UTC+4. עיגול: `(x+0.5).rounded(.down)`.
- **הנתונים רק מהאתר, לקריאה:** `tools/build-ios-data.py` (לא נוגעים ב-`build-app-data.py` של האנדרואיד). אסור לארוז את `trip.json` (החלטה 27).
- **טקסט:** רק מ-`i18n/strings.json`, עם החלפת שפה בתוך האפליקציה בלי הפעלה מחדש (החלטה 47). מחולל מפתחות עם טיפוסים (בבנייה); `leading`/`trailing` בלבד, אף פעם לא `left`/`right`.
- **ספרייה חדשה** (supabase-swift, PostHog, Sentry ועוד): סקירה קצרה אצל הארכיטקט לפני, ו-`Package.resolved` בריפו.
- **אין סודות בריפו.** החתימה וההעלאה ל-TestFlight רק ב-`main`, בסביבה `ios-release` (`docs/APPLE.md`, 4), כשיש חשבון.
- **מסך חדש או שינוי מהותי מהאתר:** קנבס קודם (החלטה 18). העתקה של מסך שאושר לא צריכה.
- **הצנרת** (`.github/workflows/ios.yml`) שייכת לאיש ה-DevOps (החלטה 66): שינוי בה עובר אצלו.

## המבנה והמוסכמות (21.10.2026)

- **תיקייה לכל חלק** ב-`Gudi/`: `Home`, `Map`, `Meet`, `Games`, `Group`, `About`, `Trip`, ו-`UI`, `FX`, `App` משותפים. סשן או סוכן שעובד על חלק נוגע רק בתיקייה שלו (ובתיקיית משנה משלו ב-`GudiCore`), ומוסיף שורות רק לקובץ הצילומים שלו.
- **ניווט:** `Route` ו-`AppModel` (`App/AppModel.swift`). מסך נפתח ב-`app.go(.map(run: …))`. חזרה: כפתור החזרה של המערכת והחלקה מהשוליים (אייפון סטייל).
- **מסגרת מסך:** `Screen(title:)` (`UI/Screen.swift`): סרגל הניווט של המערכת, הכותרת בגופן השלטים.
- **עיצוב:** `@Environment(\.ski)`: `ski.colors` (הטוקנים של האתר, יום ולילה) ו-`ski.type` (`display(size)`, `text(size, weight)`, לפי השפה). בלי צבע או גופן מחוץ לזה.
- **נתונים:** `@Environment(AppModel.self)`: `runs`, `terrainURL`, `status`, `trip`.
- **טקסט:** `S.<מפתח>` (נוצר מ-`i18n/strings.json` בכלי `tools/build-ios-strings.py`). מחרוזת חדשה נכנסת קודם ל-`i18n/strings.json`, באזור `app`, בארבע השפות.
- **צליל ורטט:** `Fx.shared` (`FX/Fx.swift`).
- **אייפון סטייל:** גיליונות (`.sheet` עם `presentationDetents`), החלקה לחזרה, `UIImpactFeedbackGenerator`, סמלי SF כשאין סמל שאושר בקנבס, גודל טקסט ומצב כהה, `accessibilityLabel` לכל כפתור, יעדי מגע של 44. המראה עצמו (שלטים, כרטיסים, צבעים וגופנים) כמו באתר ובאנדרואיד.
- **צילומים בכל ריצה:** `app/ios/qa/shots/<חלק>.txt`, שורה לכל צילום: `<שם> <שפה> <day|night> <טיול 0|1> <מסך>`, והמסך מ-`Route(qa:)` (למשל `games`, `game:merge`, `map:Tatra_2`). מורידים את התוצר `ios-screenshots` של הריצה ומסתכלים עליו לפני מיזוג.
- **המקור לכל מסך:** הקוד של האנדרואיד באותו שם (`app/android/app/src/main/java/io/github/pini236/skiapp/<חלק>/`), שכבר תואם לאתר ולמסכים שאושרו. מעתיקים את ההתנהגות והמידות, וכותבים ב-SwiftUI.
