# בדיקת מיסוך Sentry Replay — 7.10.2026

נבדקו מקור Sentry Java בגרסה 8.59.0 וה־AAR שהורד בבנייה, מול bytecode של Compose UI 1.12.1 באפליקציה. אין כאן בדיקת וידאו במכשיר; קומפילציה ובדיקות הרשאות משלוח אינן מוכיחות מיסוך פיקסלים.

- [Views.kt](https://github.com/getsentry/sentry-java/blob/8.59.0/sentry-android-replay/src/main/java/io/sentry/android/replay/util/Views.kt#L47) מפעיל סריקת Compose מתוך מודול Replay. אין צורך בתלות `sentry-compose-android` בשביל הסריקה הזו.
- [ComposeViewHierarchyNode.kt](https://github.com/getsentry/sentry-java/blob/8.59.0/sentry-android-replay/src/main/java/io/sentry/android/replay/viewhierarchy/ComposeViewHierarchyNode.kt#L279) מזהה `AndroidComposeView`, מטייל ב־`Owner.root` וממפה רכיבי טקסט ותמונה לקטגוריות שדגלי המיסוך מכסים.
- [כשל בסריקת העץ](https://github.com/getsentry/sentry-java/blob/8.59.0/sentry-android-replay/src/main/java/io/sentry/android/replay/viewhierarchy/ComposeViewHierarchyNode.kt#L302) עלול להשאיר את תת־עץ Compose בלי מיסוך. אין הנחה שכל `AndroidComposeView` ממוסך כברירת מחדל. כשל מקומי בקריאת semantics ממסך את ה־node המקומי.
- טווח התמיכה המוצהר במקור מסתיים ב־Compose 1.10.2, בעוד האפליקציה משתמשת ב־1.12.1. הקריאות הישירות העיקריות (`Owner.getRoot`, `LayoutNode.getSemanticsConfiguration/getChildren$ui/getOuterCoordinator$ui/getCoordinates/getModifierInfo`, `NodeCoordinator.isTransparent`) קיימות ב־bytecode המקומי. לא הוכח כשל בפועל, וגם לא הוכחה תאימות מלאה במכשיר.
- [זיהוי תמונות](https://github.com/getsentry/sentry-java/blob/8.59.0/sentry-android-replay/src/main/java/io/sentry/android/replay/util/Nodes.kt#L77) הוא heuristic. Vector/Color/Brush יכולים להישאר גלויים; [סריקת AndroidView בתוך Compose](https://github.com/getsentry/sentry-java/blob/8.59.0/sentry-android-replay/src/main/java/io/sentry/android/replay/viewhierarchy/ComposeViewHierarchyNode.kt#L252) אינה נתמכת. ציורי Canvas/Bitmap וטקסט שמצויר במשחק אינם מובטחים כממוסכים.

נבדק ההיקף המותר: רשימת המשחקים, Merge, Fresh, School, Descent ו־Snowball מציגים משאבים ותוכן משחק מקומי וניקוד. `MainActivity` אינו מעביר שמות ל־Descent/Snowball; ברירת המחדל שלהם מחזירה `null`. callback של שיא שולח נתון לקבוצה, בלי להציג חברים או טבלת קבוצות במסכים האלה. אין טופסי חשבון, טיול או מיקום במסכים המותרים. רשימת המסכים וההסכמה הנפרדת ממשיכות להיות תנאי צילום ומשלוח.

הנוסח למשתמש ובמדיניות מתאר מיסוך אוטומטי של רכיבי ממשק ומבהיר שתוכן המשחק יכול להופיע בהקלטה. אין הבטחה שכל טקסט/תמונה מוסתרים. נדרשת בדיקת ההקלטה במכשיר עם גרסאות האפליקציה המדויקות, כולל מעבר ממסך פרטי, לפני אישור הפיילוט לשימוש בפועל.
