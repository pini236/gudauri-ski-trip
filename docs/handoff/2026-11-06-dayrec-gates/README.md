# שערי יום הגלישה: מה רק פיני יכול לעשות (6.11.2026)

**הרשימה הזו כתובה לפיני. סשן החנויות לא לוחץ בקונסולות.** כל צעד בא עם כתובת, מה להקליד או להדביק, ומה לא לגעת. הנוסחים כאן הם עותק של `docs/PLAY.md` (סעיף 5, "הצהרת השירות בחזית") ושל `docs/APPLE.md` (5.9 וסעיף 7ג); אם הם מתעדכנים שם, מה שבמסמכים האלה גובר.

## לפני שמתחילים: שלוש תלויות שלא אצלך

1. **הסרטון** להצהרה בגוגל. סשן ה-DevOps מקליט אותו באמולטור (תסריט ב-`docs/PLAY.md`). בלעדיו לא מתחילים את חלק א.
2. **הקובץ של גוגל פליי שמכיל את ההרשאה** (סשן האנדרואיד, בנייה של החנות מ-`main`). בלי קובץ כזה אין מה לשלוח.
3. **בנייה `variant=dayrec` של האייפון** ב-TestFlight (סשן האייפון, אחרי מיזוג `claude/ios-dayrec` דרך `#if DAYREC`, באישור המנהל). עד אז אסור לבנות אותה ולא להגיש 1.1.

סשן המנהל ישלח לך הודעה כשכל אחת מהן מוכנה.

---

## חלק א: גוגל פליי, הצהרת שירות בחזית

**כלל:** מגישים אותה **בגרסה נפרדת**, בלי שום תיקון אחר בה. גוגל בודקת את ההצהרה לפני שהגרסה יוצאת, ולא מפרסמת כמה זמן זה לוקח.

1. **הסרטון ליוטיוב.** להעלות כ"לא רשום" (`Unlisted`), בלי שמות אמיתיים בסרטון, ולהעתיק את הקישור.
   ```
   https://studio.youtube.com
   ```
2. **פתיחת ההצהרה.** בקונסולה: האפליקציה `Gudi: Gudauri Ski Map`, בתפריט `Policy and programs`, ואז `App content`, ואז `Foreground service permissions`, ואז `Start` (או `Manage` אם כבר נפתח). אם בתפריט אין `Policy and programs`, זה תחת `Monitor and improve`.
   ```
   https://play.google.com/console
   ```
3. **סוג השירות:** לבחור `Location`.
4. **מקרה שימוש.** אם הרשימה מאפשרת לכתוב בעצמך, להדביק:
   ```
   User-initiated activity tracking: recording the user's own ski day or a single ski run
   ```
   אם לא מאפשרת: לבחור `Navigation`, ובתיאור שבצעד הבא כבר כתוב שזה מעקב פעילות שהמשתמש התחיל.
5. **תיאור הפיצ'ר** (`Describe the feature`), להדביק:
   ```
   Ski day recording. While the app is open, the user taps "Record" and chooses "All day" or "One descent". The app then starts a location foreground service that reads GPS location, speed and altitude once per second, including while the screen is off and the phone is in a pocket. This lets the app show live speed, recognise which lift and which ski run the user is on, and build a timeline and map of the day. A notification is shown the whole time, with a Stop button. Recording stops when the user taps Stop, when a single measured run ends, or automatically after 20 minutes without movement outside the ski resort (60 minutes inside it), with a warning first. Location is processed and stored only on the device. It is never sent to our servers or to anyone else. The app does not request background location (ACCESS_BACKGROUND_LOCATION) or a battery-optimisation exemption.
   ```
6. **אם המערכת דוחה את המשימה** (`If deferred`), להדביק:
   ```
   The user taps Record at the top of a run or when boarding a lift, and expects recording to begin at once. If the start is deferred, the beginning of the run or ride is not recorded: the top speed, the lift and the first run are missing or wrong, and the result shown to the user is incorrect.
   ```
7. **אם המערכת קוטעת את המשימה** (`If interrupted`), להדביק:
   ```
   If recording is interrupted, the day has gaps: runs and lifts during the gap are missing from the timeline, and speed, distance and vertical drop are wrong. The app saves every reading as it goes and offers to resume, and it shows any gap longer than 10 seconds to the user, but the time while it was stopped cannot be recovered.
   ```
8. **קישור לסרטון:** להדביק את הקישור מצעד 1. `Save`.
9. **הגרסה.** במסלול `Production`: `Create new release`, להעלות את הקובץ מסשן האנדרואיד, להדביק את הערות הגרסה הקיימות מ-`docs/PLAY.md`, ובדף `Publishing overview` לשלוח לסקירה. **לא לצרף לאותה גרסה שום תיקון אחר.**
   ```
   https://play.google.com/console
   ```
10. **לכתוב** ל-`docs/PLAY.md` (או להגיד לסשן המנהל) את תאריך ההגשה, ואחר כך את התשובה של גוגל.

**לא לגעת:**
- **`Data safety` (טופס הנתונים):** בלי שינוי. המיקום, המהירות וחיישני התנועה נשארים בטלפון ולכן לא "נאספים". הטופס משתנה רק אם בעתיד יישלח מהטלפון מיקום, מהירות או שם מסלול (למשל שיתוף לקבוצה).
- **הצהרת "מיקום ברקע":** לא קיימת אצלנו ולא מוסיפים אותה. ההרשאה `ACCESS_BACKGROUND_LOCATION` אינה בקובץ, ואסור שתיכנס. אם הקונסולה פתאום דורשת אותה, **לעצור ולשאול את המנהל.**
- **דף החנות:** בלי תיאור של יום הגלישה עד שהפיצ'ר נדלק (מתארים רק מה שיש).
- **שום הדלקה של היכולת `dayrec`** בנתוני האתר לפני ששורת הפרטיות פורסמה (ראו "מה לא קורה עד אז").

---

## חלק ב: אפל, גרסה 1.1

**לא מגישים עד שלושת התלויות למעלה, ושורת הפרטיות פורסמה ב-`/privacy`.** לא לשלוח לאפל בנייה אחרת, ולא בנייה שמסומנת לבודקים.

### הכנה בחשבון המפתח וב-GitHub (פעם אחת, לפי הסדר)

1. **מזהה שני** להרחבה (Live Activity), בלי יכולות נוספות. ב-`Identifiers` ללחוץ `+`, `App IDs`, `App`, ולהקליד:
   ```
   io.github.pini236.skiapp.live
   ```
   שם תיאור: `Gudi Live Activity`. לא לסמן שום `Capability`.
   ```
   https://developer.apple.com/account/resources/identifiers/add/bundleId
   ```
2. **פרופיל שני.** `Profiles`, `+`, סוג `App Store` (תחת `Distribution`), המזהה החדש `.live`, **אותה תעודת הפצה** כמו הפרופיל הראשון. שם:
   ```
   Gudi Live Activity App Store
   ```
   להוריד את הקובץ.
   ```
   https://developer.apple.com/account/resources/profiles/add
   ```
3. **סוד שביעי ב-GitHub.** בסביבה `ios-release`, `Add environment secret`. שם:
   ```
   IOS_PROFILE_LIVE_BASE64
   ```
   ערך: הפרופיל ב-base64, באותה פקודה כמו הסוד של הפרופיל הראשון (`docs/APPLE.md`, 4.2 ד). השם הסופי נקבע על ידי DevOps והארכיטקט; אם הם שינו אותו, סשן האייפון יגיד לך.
   ```
   https://github.com/pini236/gudauri-ski-trip/settings/environments
   ```
4. **ניקוי המק:** למחוק את קובץ הפרופיל וה-base64 מהמק ומההורדות (כמו `docs/APPLE.md` 4.2 ה), ולהודיע לסשן האייפון שגמרת.

### בנייה, בדיקה והגשה

5. **ל-TestFlight.** כשסשן האייפון אומר שהבנייה מוכנה: בגיטהאב, `Actions`, `ios-release`, `Run workflow`, ובשדה `variant` לבחור `dayrec`. אחרי העיבוד, להתקין מ-TestFlight ולבדוק בטלפון: "מתחילים", הרשאה "בזמן השימוש", נעילת מסך והמשך הקלטה, עצירה, וציר הזמן.
   ```
   https://appstoreconnect.apple.com/apps/6825506333/testflight/ios
   ```
6. **גרסה חדשה.** ב-`Distribution`, `+` ליד `iOS App`, מספר גרסה `1.1`.
   ```
   https://appstoreconnect.apple.com/apps/6825506333/distribution/ios/version/inflight
   ```
7. **בחירת הבנייה:** לבחור את בניית ה-`dayrec` שבדקת. **לא** בנייה אחרת.
8. **מה חדש** (`What's New`). אנגלית:
   ```
   Ski day: record your day on the mountain with one tap, even with the screen locked. See your speed, the lifts and runs you took, and a timeline and map of the whole day. Everything stays on your phone.
   ```
   עברית ורוסית: מהנוסח המאושר ב-`i18n/store-listing-1.1.md`, סעיף 2. **רק אחרי שהנוסח הסופי מסבב 29 סגור** (סשן התוכן). גאורגיה מקבלת אנגלית.
9. **הערות לסוקר** (`App Review Information`, `Notes`, עד 4,000 בייטים). להחליף את ההערות מ-1.0 בנוסח הזה:
   ```
   Ski day (new in this version)

What it does: the user taps "Start a ski day" (or "Measure a run") while the app is open. The app then records location once per second, including with the screen locked, to show live speed, recognise the lift and ski run the user is on, and build a timeline and map of the day.

Why background location (UIBackgroundModes: location): skiers keep the phone in a pocket with the screen locked. Recording starts only from the user's tap in the foreground, uses "While Using the App" authorization only (we never ask for "Always"), shows the blue location indicator the whole time, and stops when the user taps Stop, when a measured run ends, or automatically after a period without movement. Location is processed and stored only on the device and is never sent to us or to anyone else.

How to test without snow:
1. Open the app, go to the map, tap "Start a ski day" and allow location ("While Using the App", Precise).
2. Lock the phone and walk or drive for a few minutes. The recording continues: speed, distance and the route on the map update.
3. Unlock, tap Stop, and open the day's timeline. Every number is measured anywhere. Outside Gudauri, Georgia, segments are shown without lift and run names ("off the runs", "climb"), because names come only from our map of that resort.
4. To see a full day with lifts and runs, open History and choose the sample day (marked "Sample").
   ```
   שורה 4 ("sample day") נשארת **רק אם היום לדוגמה נבנה בבנייה עצמה**; סשן האייפון יגיד. אם הוא לא נבנה, למחוק את שורה 4, אבל אז חייבת להיות לסוקר דרך אחרת לראות את הפיצ'ר (הנחיה 2.1), ולא להגיש בלי תשובה מהמנהל. אם נכנס גם Live Activity, להוסיף שורה: `A Live Activity on the Lock Screen and in the Dynamic Island shows the live numbers while recording; it is updated by the app only, without push notifications.`
10. **קבוצת "App Review":** לוודא שהקוד שבהערות עדיין עובד (קבוצה פעילה, תאריכי ינואר 2027), ושהסוקר לא מתבקש לאשר.
11. **שליחה לסקירה,** עם שחרור ידני (`Manually release this version`), כמו ב-1.0. אחרי האישור, להחליט מתי לשחרר: **לא לפני שיום הגלישה מוכן להידלק** (ראו למטה).

**לא לגעת:**
- **תווית הפרטיות (`App Privacy`):** בלי שינוי למיקום, לתנועה ולכושר (הכל נשאר בטלפון). לא לסמן `Precise Location`, `Coarse Location` או `Fitness`.
- **אין מקום בטופס ל"סיבה למצב רקע":** ההסבר נמצא בהערות לסוקר בלבד.
- **לא להגיש `variant=dayrec` כדי "לבדוק".** בנייה כזו נשלחת לסקירה רק כגרסה 1.1 בכוונה.
- **לא למזג ולא לבנות** את `claude/ios-dayrec` בעצמך. זה אצל סשן האייפון ובאישור המנהל.
- **Live Activity:** אם סשן האייפון אומר שהוא לא נכנס ב-1.1, המזהה `.live` עדיין שימושי, אבל אין להוסיף `NSSupportsLiveActivities` או שורה בהערות.

---

## מה לא קורה עד אז (כדי שלא תפספס)

- **שורת הפרטיות** ליום הגלישה (אושרה, עם שני תיקונים קטנים) מתפרסמת ב-`/privacy` על ידי הסשן המשפטי **לפני** שהיכולת נדלקת. זה לא אצלך, חוץ מאישור הנוסח הסופי אם יתבקש.
- **ייצוא היומן הגולמי לבודקים** דורש ממך נוסח הסכמה, שורה במדיניות ובדיקה שהאימון כבוי בחשבון ה-Claude שלך (`docs/handoff/2026-10-31-tester-log-consent/README.md`, פריט 14 ברשימת העבודה). **זה לא חלק מההגשות לחנויות,** והוא לא נכנס לבנייה של החנות.
- **הדלקת היכולת `dayrec`** בנתוני האתר (`site/data/resorts.json`) נעשית רק אחרי: הצהרת גוגל אושרה, 1.1 אושרה באפל, והמדיניות פורסמה. סשן המנהל יבקש אישור.
- **סבב 32 (הדמות), מקומות ואחרים:** לא קשורים, לא נוגעים.

## מה סשן החנויות עושה כשאתה אומר

- קורא את התשובה של גוגל או של אפל ומנסח תגובה אם נדחה.
- בודק מול הקוד לפני ההגשה: הערות הסוקר, התאמת התווית והמניפסט, והוספת Live Activity אם נכנס.
- מעדכן את `docs/PLAY.md` ו-`docs/APPLE.md` ביומן.
