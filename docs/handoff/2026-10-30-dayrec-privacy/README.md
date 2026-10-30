# טיוטה: שורת הפרטיות ליום הגלישה (החלטה 76)

**טיוטה לאישור פיני. לא ב-`docs/PRIVACY.md` עד האישור ועד המיזוג הראשון של הפיצ'ר** (תנאי 1 של הארכיטקט, `docs/ARCHITECTURE.md`, מ-8). אחרי האישור: רוסית וגאורגית מאותו נוסח, `python3 tools/build-privacy.py`, ותאריך תוקף חדש.

## עברית

**בתקציר (השורה שמתחילה "האפליקציה והאתר עובדים בלי הרשמה"), במקום "מיקום רק כשנוגעים ב"איפה אני", והוא לא יוצא מהמכשיר":**

> מיקום רק כשנוגעים ב"איפה אני" או כשמתחילים יום גלישה, והוא לא יוצא מהמכשיר.

**פסקה חדשה אחרי "מיקום":**

> **יום גלישה (באפליקציה בלבד).** כשמתחילים יום גלישה, האפליקציה מקליטה את המיקום, את הגובה ואת תנועת הטלפון (מחיישני התנועה שבו) כל עוד ההקלטה פועלת, גם כשהמסך כבוי, ומציגה התראה כל הזמן הזה. ההקלטה משמשת רק להצגת המהירות, המסלולים, הרכבלים והמסלול של היום, והיא נשמרת רק בטלפון: היא לא נשלחת אלינו או לאף אחד אחר, ולא נכנסת לגיבוי בענן. אפשר למחוק יום אחד או את כל הימים בכל רגע, והכל נמחק גם כשמוחקים את האפליקציה. במדידת השימוש (אם היא פועלת) נשלח רק שיום התחיל והסתיים, כמה זמן נמשך וכמה ירידות ורכבלים היו, בלי מיקום ובלי מהירות. קובץ המסלול (GPX) יוצא מהטלפון רק כשבוחרים לשתף אותו.

**בפסקה על הרשאות ("מלבד המיקום ל"איפה אני""):**

> מלבד המיקום ל"איפה אני" וליום גלישה, וחיישני התנועה ליום גלישה (למעלה), האפליקציה לא מבקשת גישה למצלמה, למיקרופון, לאנשי הקשר או לקבצים.

## English

**Summary:** Location only when you tap "Where am I" or start a ski day, and it never leaves your device.

**New paragraph after "Location":**

> **Ski day (app only).** When you start a ski day, the app records your location, altitude and the phone's movement (from its motion sensors) for as long as recording is on, including with the screen off, and shows a notification the whole time. The recording is used only to show your speed, runs, lifts and route for the day, and it is stored only on your phone: it is not sent to us or to anyone else, and it is not included in cloud backups. You can delete one day or all days at any time, and everything is deleted when you delete the app. If analytics is on, we only receive that a day started and ended, how long it lasted and how many runs and lifts there were, with no location and no speed. The route file (GPX) leaves your phone only if you choose to share it.

**Permissions paragraph:** Apart from location for "Where am I" and ski days, and motion sensors for ski days (above), the app does not ask for access to your camera, microphone, contacts or files.

## השלכות (אחרי האישור)

- **גוגל פליי, טופס הנתונים:** מיקום מדויק ומשוער: "לא נאסף" (מעובד במכשיר בלבד). הצהרת השירות בחזית (מיקום) עם סרטון: נפרדת, כשיש מסך להקליט.
- **אפל:** תווית הפרטיות בלי שינוי (לא נאסף); `NSLocationWhenInUseUsageDescription`, `NSMotionUsageDescription` אם נדרש, והסבר לסוקר על מצב הרקע.
