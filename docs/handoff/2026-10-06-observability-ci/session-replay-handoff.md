# בדיקת הקלטת שימוש — 6.10.2026

הבדיקה בוצעה לקריאה בלבד; לא שונו קוד האפליקציה, האתר או מסמכי הריפו. מקורות רשמיים נקראו דרך מאגרי היצרנים; דפי התיעוד הציבוריים עצמם נחסמו ב־proxy. לא נבדקו דשבורדים, תוכנית תשלום, מכסות, אירועי production או הקלטה באמולטור/טלפון.

## מה קיים באפליקציה

- `app/android/app/build.gradle.kts:135` — PostHog Android 3.71.4.
- `app/android/app/build.gradle.kts:136` — Sentry Android Core 8.59.0, ללא מודול replay.
- `Telemetry.kt:58-66` — Sentry לקריסות, PII כבוי, ללא screenshots/view hierarchy, ללא tracing.
- `Telemetry.kt:74` — הקלטת PostHog כבויה מפורשות.
- `Telemetry.kt:20` — התיעוד מבטיח שאין הקלטת מסך.
- `Telemetry.kt:92-102` — המתג הקיים מכבה PostHog ו־Sentry. הקלטה מחייבת טיפול גם במקטעים שממתינים לשליחה.
- `MapView.kt:39` — המפה היא GLSurfaceView, ולא רכיב Compose רגיל.
- `MapView.kt:179` — נקודת המיקום מצוירת ב־Canvas; מיסוך טקסט ותמונות אינו מסתיר אותה.
- באתר `site/js/telemetry.js:61` — replay כבוי; הסקריפטים מוגשים מקומית בגרסה נעוצה, כך שאין להניח שדי בהפעלת switch בדשבורד כדי לקבל recorder מלא באתר.

## Sentry Android 8.59.0: כן, עם מגבלות מוגדרות

ה־SDK והמסמכים הרשמיים מאמתים:

1. הקלטה נתמכת ב־API 26 ומעלה, בדיוק המינימום של האפליקציה.
2. Compose נתמך; התיעוד מציג את `Modifier.sentryReplayMask()` ו־`sentryReplayUnmask()`. **מיסוך תוכן פנימי בתוך AndroidView המוטמע ב־Compose אינו נתמך.** התיעוד העדכני מציג גם דוגמה למיסוך ה־AndroidView כולו דרך modifier, ולמיסוך native View יש API של tags/extension; אלה אינם הוכחה שה־wrapper, ה־SurfaceView וכל ה־Canvas overlays באפליקציה הזאת ממוסכים נכון ב־8.59.0. אין להסתמך על מיסוך wrapper כמנגנון הפרטיות של המפה בלי POC. בפיילוט משהים את כל המפה לפני הצגת התוכן. טקסט, תמונות, WebViews וקלט רגילים ממוסכים כברירת מחדל.
3. התלות `sentry-android-core` לבדה לא כוללת replay. יש להוסיף `implementation("io.sentry:sentry-android-replay:8.59.0")` לצד התלות הקיימת, או להחליף ב־sentry-android (האחרונה מוסיפה גם NDK; לכן הוספת replay בלבד מצומצמת יותר).
4. `sessionSampleRate` מקליט מדגם של סשנים שלמים; `onErrorSampleRate` מאפשר כ־30 שניות לפני שגיאה וממשיך לאחריה. ההקלטה כברירת מחדל בקצב של frame אחד לשנייה; אינה וידאו 60/120FPS של המשחקים.
5. עבור המפה/משחקי OpenGL יש בגרסה המדויקת `options.sessionReplay.isCaptureSurfaceViews = true`, אפשרות ניסיונית עם PixelCopy בלבד. כשהיא כבויה, SurfaceView יכול להיות שחור/שקוף בהקלטה. לא ניתן למסך אובייקטים בודדים בתוך SurfaceView: או שכל השטח ממוסך או שכל הפיקסלים נשלחים. היכולת נמצאת גם בקוד ה־SDK המדויק, לא רק בתיעוד החדש.
6. קיימות פעולות start/startBuffering/pause/resume/stop. התחלה ידנית עוקפת את sampling שנקבע, ולכן אין לקרוא start בכל מעבר מסך ולהניח שמדגם 5% עדיין נאכף. pause נשמר במעבר לרקע ולסשן חדש באותו תהליך; יש לתזמן אותו לפני הצגת תוכן פרטי.
7. `beforeSendReplay` מאפשר לדחות מקטע לפני capture; אינו תחליף למיסוך פיקסלים ואינו מוכיח שכל envelope שכבר בתור יימחק עם opt-out.

מקורות:

- https://docs.sentry.io/platforms/android/session-replay/
- https://docs.sentry.io/platforms/android/session-replay/privacy/
- https://github.com/getsentry/sentry-docs/blob/master/docs/platforms/android/session-replay/index.mdx
- https://github.com/getsentry/sentry-docs/blob/master/docs/platforms/android/session-replay/privacy/index.mdx
- https://github.com/getsentry/sentry-java/blob/8.59.0/sentry/src/main/java/io/sentry/SentryReplayOptions.java#L178
- https://github.com/getsentry/sentry-java/blob/8.59.0/sentry/src/main/java/io/sentry/SentryReplayOptions.java#L278
- https://github.com/getsentry/sentry-java/blob/8.59.0/sentry/src/main/java/io/sentry/SentryReplayOptions.java#L297
- https://github.com/getsentry/sentry-java/blob/8.59.0/sentry/src/main/java/io/sentry/SentryReplayOptions.java#L446
- https://github.com/getsentry/sentry-java/blob/8.59.0/sentry/src/main/java/io/sentry/SentryReplayOptions.java#L503
- https://github.com/getsentry/sentry-java/blob/8.59.0/sentry-android-replay/src/main/java/io/sentry/android/replay/screenshot/PixelCopyStrategy.kt#L132
- https://github.com/getsentry/sentry-java/blob/8.59.0/sentry-android-replay/src/main/java/io/sentry/android/replay/ReplayIntegration.kt#L255
- https://github.com/getsentry/sentry-java/blob/8.59.0/sentry-android-replay/src/main/java/io/sentry/android/replay/ReplayIntegration.kt#L383
- https://github.com/getsentry/sentry-java/blob/8.59.0/sentry-android/build.gradle.kts#L31
- https://github.com/getsentry/sentry-java/blob/8.59.0/sentry/src/main/java/io/sentry/SentryOptions.java#L3484

## PostHog Android 3.71.4

- replay כלול כבר בתלות הקיימת, אין צורך בספרייה נוספת.
- צריך להפעיל גם בהגדרות הפרויקט וגם ב־SDK; נדרשת גרסה >=3.4.0 ו־API >=26.
- עבור Compose חייבים `sessionReplayConfig.screenshot = true`; מצב wireframes לא משחזר Compose.
- `maskAllTextInputs` ו־`maskAllImages` דולקים כברירת מחדל; `captureLogcat` גם דולק כברירת מחדל ולכן חייבים לכבותו באפליקציה הזאת.
- בגרסה המדויקת קיימים `postHogMask()` ו־`postHogUnmask()`, sampleRate, קצב ברירת מחדל של snapshot בשנייה, וכן `startSessionReplay`/`stopSessionReplay`.
- במימוש המצולם שנבדק, PixelCopy מקבל Window בלבד. לא נמצאה לכידה נפרדת/קומפוזיציה של SurfaceView בדומה ל־Sentry. לכן אין בסיס להבטיח שהמפה ב־OpenGL תופיע בהקלטת PostHog; נדרשת בדיקת מכשיר.
- PostHog עדיף כשעיקר העבודה הוא להתחיל ממשפך/אירוע ולצפות בביקורים שבהם הייתה נטישה. Sentry עדיף כשמתחילים משגיאה ורוצים לראות מה קרה לפניה; עבור התצוגה הגרפית הקיימת יש לו גם מסלול לכידת SurfaceView מפורש.

מקורות:

- https://posthog.com/docs/session-replay/installation/android
- https://github.com/PostHog/posthog.com/blob/master/contents/docs/session-replay/_snippets/android-installation.mdx
- https://github.com/PostHog/posthog.com/blob/master/contents/docs/session-replay/_snippets/android-privacy.mdx
- https://github.com/PostHog/posthog-android/blob/android-v3.71.4/posthog-android/src/main/java/com/posthog/android/replay/PostHogSessionReplayConfig.kt
- https://github.com/PostHog/posthog-android/blob/android-v3.71.4/posthog-android/src/main/java/com/posthog/android/replay/PostHogReplayIntegration.kt#L1823

## ההמלצה והצעת המימוש

מקליט אחד: Sentry replay במדגם קטן ובשגיאות; PostHog ממשיך למדוד שימוש, משפכים ושימור לפי החוזה הקיים. זו המלצה על חלוקת תפקידים; להבנת שימוש עיקרית עדיין בודקים אירועים ומשפכים, וההקלטות נותנות דוגמאות חזותיות. Sentry נבחר בגלל בקשת ההקלטה בכלי הקיים ומסלול SurfaceView המפורש. אין סיבה להקליט את אותו מסך פעמיים ולהכפיל עומס/אחסון. לא מובטח שכל ביקור או מפה יופיעו: ההקלטה מחייבת הסכמה, זכאות מסך, sampling, SDK, מכסה וקבלת המקטעים בשרת. אם בעתיד הצורך יהיה ניתוח נטישה בתוך אותו dashboard, אפשר לבחור PostHog כמקליט היחיד אחרי בדיקת מפת OpenGL.

הצעת קוד בסיסית אחרי הסדרת פרטיות. **הקטעים לא קומפלו מול האפליקציה ולא הורצו; אלה קטעי SDK לתכנון בלבד, עם משתני placeholder שאינם קיימים בריפו.** חתימות setters של sampling, לכידת SurfaceView, `setNetworkCaptureBodies` ו־`beforeSendReplay` נבדקו מול קוד המקור של 8.59.0 המקושר למעלה. דוגמאות masking/extension בתיעוד העדכני אינן אימות שהשילוב ב־Compose/OpenGL של האפליקציה בטוח בגרסה הזאת.

```kotlin
implementation("io.sentry:sentry-android-replay:8.59.0")
```

```kotlin
// SDK draft בלבד: eligible נקבע לפני init לפי הסכמה, מסך, build ורובוטים.
// 5%/100% הן הצעה לדיון בכפוף למכסה בפועל, לא ערכי production מאושרים.
o.sessionReplay.sessionSampleRate = if (eligible) 0.05 else 0.0
o.sessionReplay.onErrorSampleRate = if (eligible) 1.0 else 0.0
o.sessionReplay.maskAllText = true
o.sessionReplay.maskAllImages = true
o.sessionReplay.isCaptureSurfaceViews = false // pilot: מפה paused תמיד עד POC
o.sessionReplay.setNetworkCaptureBodies(false)
```

ה־API הבא קיים בדיוק ב־8.59.0. המשתנה `uploadPolicy` הוא חוזה למימוש עתידי, ואינו מחלקה קיימת בריפו: עליו לבדוק את זכאות ההקלטה/מקטע ואת ביטול ההסכמה באופן שנשמר גם אחרי restart. בדיקה של המצב הנוכחי בלבד אינה מספיקה.

```kotlin
o.beforeSendReplay = io.sentry.SentryOptions.BeforeSendReplayCallback { event, _ ->
    if (uploadPolicy.maySend(event.replayId)) event else null
}
```

לאחר POC שמאמת כל SurfaceView וכל overlay, ורק לתצוגות ציבוריות ובטוחות, אפשר לשנות את שתי אפשרויות ה־SDK האלה. מיסוך כל ה־SurfaceViews כברירת מחדל מחייב החרגה מצומצמת ומבוקרת של instance בטוח; החרגה רחבה לפי class אינה מספקת הוכחת פרטיות.

```kotlin
o.sessionReplay.isCaptureSurfaceViews = true // experimental + PixelCopy בלבד
o.sessionReplay.addMaskViewClass("android.view.SurfaceView")
// החרגת native View בטוח היא עבודת POC נפרדת;
// אין כאן modifier או extension שמבטיח מיסוך AndroidView/overlay באפליקציה.
```

API הניווט הבא מאומת, אך התזמון חייב להיות לפני rendering של מסך רגיש, על main thread, בתוך המימוש שמחזיק את הניווט:

```kotlin
Sentry.replay().pause()
// resume רק לאחר שהמסך הקודם נעלם, והסשן לא בוטל ב־opt-out:
Sentry.replay().resume()
```

אין להפעיל את הקוד לבדו. נדרשים באותה עבודת מימוש:

1. מצב הסכמה להקלטה, כבוי עד אישור מתאים; המתג הקיים נשאר מתג master שמכבה גם replay. בלי replay ברובוטים של גוגל וב־debug רגיל; בדיקת replay רק בסביבת QA מפורשת ונפרדת, במדגם 100% זמני.
2. מיסוך/השהיית הקלטה לכל תוכן קבוצות, הזמנות וקודים, חשבון וכניסה, כרטיס הטיסה ותאריכים אישיים, קלט חופשי. בפיילוט: מפה paused תמיד, כולל ללא הרשאת מיקום, עד POC שמוכיח שכל שטח המפה כולל overlay ממוסך כשצריך. הנקודה חושפת מיקום גם בלי שדות קואורדינטות. רכיב Canvas מותאם חייב מיסוך מפורש/השהיה; כללי טקסט ותמונות לבדם אינם מספיקים.
3. תיאום pause/resume עם נתיב Compose לפני הצגת תוכן רגיש, ולא רק ActivityLifecycleCallbacks (באפליקציה פעילות אחת למסכים רבים); טיפול גם ב־deep links, חלונות דיאלוג וחזרה מהרקע. מעבר למסך ציבורי מחזיר replay רק אם ההסכמה וה־master עדיין בתוקף.
4. opt-out: לבטל הסכמה לפני close/stop, לסמן את סשן ההקלטה הישן כמבוטל באופן שנשמר, לדחות את מקטעיו ב־beforeSendReplay, ולהפסיק recording. אין לחזור עם resume לסשן המבוטל; צריך לבדוק disposal נתמך של pending/persisted replay ותורי transport, גם אחרי process restart. Gate שבודק רק `consent=true` בזמן השליחה עלול לאפשר מקטע ישן אחרי opt-in עתידי. גם gate לפי replay ID אינו לבדו הוכחה לניקוי envelope שכבר נלכד לתור transport, ולכן נדרשת בדיקת מטא־דאטה וקבצי וידאו אחרי off/background/restart. Sentry stop במצב session משגר את המקטע הממתין; buffer mode משליך buffer שלא נשלח. אין להציג stop או close בלבד כיישום opt-out מלא. סדר/ניקוי נשארים משימת מימוש ובדיקה, לא פתרון מאומת במסמך זה.
5. breadcrumbs/scope עם שמות מסכים ציבוריים מהחוזה בלבד כדי שמעברי Compose ברורים; לא שם קבוצה, מזהה חשבון, קוד הזמנה או query URL. ניקוי URL/breadcrumb והימנעות מאיסוף request/response bodies.
6. בדיקות בנייה/יחידה נדרשות לפי Android CLAUDE, ואז QA במכשיר: עברית RTL ושאר שפות, מפה ומשחק OpenGL ללא שחור, קלט ודיאלוגים עם נתוני סימון סינתטיים, מסך מיקום עם נקודה, כיבוי באמצע סשן, חזרה מהרקע ופתיחה ב־deep link, אין הקלטת רובוטים. לבדוק את ההקלטה עצמה לאחר העלאה, לא רק screenshots מקומיים. למדוד זמן frames, CPU, זיכרון, סוללה ורשת מול baseline במפת OpenGL ובמשחק; לא אומתה כאן עלות הביצועים.

## פרטיות ועלות

המדיניות הנוכחית **לא מכסה replay**. ב־`docs/PRIVACY.md:33-36` נאספים אירועי שימוש ודיווח טכני בלבד; בשורות 27 ו־29 יש הבטחות לגבי הטיול והמיקום; שורה 75 מכסה אירועי שימוש באתר; בשורה 95 התחייבות להודיע באפליקציה על שינוי מהותי. `docs/GROWTH.md:107` קובע מפורשות שאין הקלטות. צריך הצעת עדכון למדיניות בכל ארבע השפות, תיאור ההקלטות/אינטראקציות/מיסוך/ספק/זמן שמירה/כיבוי והודעה באפליקציה, ובדיקת הצהרות החנות. לפי CLAUDE המדיניות משתנה רק באישור פיני; Android תפוס בסשן חיצוני והתכנון נוגע לפרטיות ולכלי חיצוני, ולכן נדרש תיאום עם סשן האנדרואיד וסקירת הארכיטקט הקבוע לפני מימוש. התיאום והסקירה עדיין ממתינים; סקירת העמית הטכנית כאן אינה סקירת סשן הארכיטקט הקבוע. אין להסיק שהמתג הקיים הפועל כברירת מחדל נותן הסכמה להקלטה חדשה.

לא אומתו תוכניות המחיר והמכסה של המשתמש. יש לקבוע sampling קטן, מגבלת הוצאות/מכסת replay במסוף היצרן, זמן שמירה נפרד והתרעות שימוש לפני production. בקירוב, כמות ההקלטות היא 5% מהביקורים ועוד ביקורים שלא נבחרו במדגם ובהם הייתה שגיאה (לא 5% מכל מה שנשלח). קצב frame אחד לשנייה אינו הסרטת משחק מלאה. אין להבטיח שהשכבה החינמית מספיקה בלי הכרת כמות הביקורים והשגיאות והתוכנית הנוכחית. בלוג התמחור של PostHog מתאר מכסה של 5,000 אך הוא מ־2024 ולא מוכיח את תמחור replay Android היום; אין להציגו כמחיר מאומת.

מקורות להמשך אימות המחיר:

- https://sentry.io/pricing/
- https://posthog.com/pricing
- https://posthog.com/docs/billing/estimating-usage-costs

## מה לא אומת

- גישה/הגדרות/נתוני שימוש בדשבורדים, מכסות, retention בפועל ומחיר החשבון.
- קומפילציה של קטעי ההצעה; צילום Compose/OpenGL/Canvas של האפליקציה עצמה בפועל, מיסוך של AndroidView כולו כולל SurfaceView/overlays, אפס דליפה בתורים אחרי opt-out, ואיכות masking בכל שפה.
- בדיקת הסכמה משפטית/דרישות חנויות, וביצועים/צריכת סוללה/גודל קובץ התקנה.
- באתר: התאמת bundle Sentry המקומי ומנגנון טעינת recorder מקומי לתנאי CSP; לא מוצע שינוי באתר כחלק מהמימוש הראשון.
