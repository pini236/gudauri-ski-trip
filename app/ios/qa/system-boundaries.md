# נקודות המגע עם המערכת באייפון

**הכלל:** סגירה (closure) שמועברת ל-API של המערכת, והמערכת קוראת לה בחזרה על תור או שרשור משלה, נבנית בפונקציה `nonisolated static`, לא בתוך מחלקה או פונקציה של `@MainActor`. סגירה שנבנתה שם יורשת את בידוד ה-MainActor, ו-Swift 6 בודק בזמן ריצה שהיא רצה על התור הראשי; אם המערכת קוראת לה מתור אחר, האפליקציה קורסת (`EXC_BREAKPOINT`, `dispatch_assert_queue`). זה מה שקרה בבנייה 55 (TestFlight, 14.11.2026): מטפל מד התאוצה בהקלטה. הסימולטור אין בו מד תאוצה, ולכן `ios.yml` לא תפס.

**לכל נקודה למטה:** מי קורא, על איזה תור, ומה המצב.

| נקודה | קובץ | התור שבו המערכת קוראת | המצב |
|---|---|---|---|
| מטפל מד התאוצה (`CMMotionManager`) | `DayRec/DayRecorder.swift`, `accelHandler` | `motionQueue` (לא ראשי) | **תוקן** (14.11.2026): `nonisolated static`; `AccelWindow` ב-`GudiDomain`, עם בדיקה מתור צדדי |
| סיום בקשת דיוק מדויק (`requestTemporaryFullAccuracyAuthorization`) | `DayRec/DayRecorder.swift`, `accuracyDone` | לא מובטח ראשי | **תוקן**: `nonisolated static`, חוזר ל-MainActor ב-`Task` |
| מנהל המיקום, ה-delegate (`CLLocationManagerDelegate`) | `DayRec/DayRecorder.swift`, `Map/WhereAmI.swift` | התור של היצירה (ראשי), אבל לא מובטח | תקין: השיטות `nonisolated`, ורק ערכים פשוטים עוברים ל-MainActor ב-`Task` |
| כניסה עם אפל (`ASAuthorizationController`) | `Account/SignIn.swift`, `resumer` | ראשי בפועל, לא מובטח | **הוקשח**: `nonisolated static` |
| כניסה עם גוגל (`ASWebAuthenticationSession`) | `Account/SignIn.swift`, `webDone` | ראשי בפועל, לא מובטח | **הוקשח**: `nonisolated static` |
| טיימר השליחה של המדידה (`Timer`) | `App/Telemetry.swift`, `tick` | ה-run loop של השרשור שיצר אותו (ראשי) | **הוקשח**: `nonisolated static` |
| סוקט הזמן האמיתי (`URLSessionWebSocketTask`) | `Group/GroupService.swift`, `WebSocket` | תור של `URLSession` | תקין: המחלקה לא מבודדת (`@unchecked Sendable`), הסגירות שלה לא יורשות MainActor |
| צליל המשחקים (`AVAudioSourceNode`) | `Games/Common/Synth.swift` | שרשור הצליל בזמן אמת | תקין: `Synth` לא מבודד, והסגירה לא נוגעת במצב מבודד |
| התראות (`UNUserNotificationCenterDelegate`) | `Meet/Reminders.swift`, `Delegate` | תור של המערכת | תקין: שיטות `async` במחלקה לא מבודדת, וחזרה ל-MainActor דרך `MainActor.run` |
| מסך המפה ב-Metal (`MTKViewDelegate`) | `Map3D/Map3DEngine.swift` | הראשי (ה-`MTKView` מציירת על הראשי) | תקין: `@preconcurrency`, רץ על הראשי |
| שעון פריימים (`CADisplayLink`) | `Games/*` | הראשי (`add(to: .main)`) | תקין: `@objc selector`, לא סגירה |
| `Task.detached` (חישובים כבדים) | כמה קבצים | מאגר השרשורים | תקין: הסגירה לא מבודדת ולא נוגעת במצב מבודד |
| `Activity` (Live Activity) | `DayRec/DayLive.swift` | `async`, בתוך `Task` | תקין: אין סגירה שמועברת למערכת |
| `URLSession.shared.data` / `dataTask` | `Weather`, `StatusSource`, `Telemetry`, `Http` | `async`, או בלי סגירה | תקין |

**בדיקות:** `SystemBoundaryTests` (`Packages/GudiCore/Tests/GudiDomainTests`) מפעילות את `AccelWindow` ואת דפוס ה-`nonisolated static` מתור צדדי. **מה שחסר:** בדיקה שמפעילה את המטפלים שבקוד האפליקציה עצמו (כמו `accelHandler`) דורשת יעד בדיקות של האפליקציה (`project.yml` ו-`ios.yml`, אצל איש ה-DevOps); עד אז הכלל הזה והרשימה הזו הם ההגנה, וכלל אוטומטי (grep) בבנייה מוצע לאיש ה-DevOps.

**כשמוסיפים נקודה חדשה:** מוסיפים לה שורה כאן.
