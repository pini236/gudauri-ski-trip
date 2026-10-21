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
