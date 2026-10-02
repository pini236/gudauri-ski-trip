# טיוטה: עדכון מדיניות הפרטיות לחשבונות, קבוצות ושרת

**ממתין לאישור של פיני. הטקסט ב-`docs/PRIVACY.md` (שפורסם ואושר) לא שונה.** אחרי האישור: מעבירים את הסעיפים לשם, מעדכנים את התאריך בראש המדיניות, מריצים `python3 tools/build-privacy.py`, ומעדכנים את טופס הנתונים ב-`docs/PLAY.md` (למטה). **המדיניות חייבת להתפרסם לפני שהבדיקה הסגורה מקבלת אפליקציה עם חשבונות**, כי היום היא אומרת שהטיול לא נשלח לשום מקום, ואחרי הצטרפות לקבוצה הוא נשלח.

## למה היא לא נכונה היום

1. "האפליקציה לא מבקשת שם או מייל", ו"הטיול שלך" "לא נשלח אלינו": נכון רק עד הצטרפות לקבוצה או כניסה עם גוגל.
2. אין כלום על השרת (סופבייס, פרנקפורט), על קבוצות, מפגשים, שיאים ובקשות הצטרפות, על כניסה עם גוגל, על יומן הפעולות, על ניקוי אורחים ועל מחיקת חשבון.
3. "אם גיבוי המכשיר דלוק, אנדרואיד עשוי לגבות אותם": באפליקציה הגיבוי כבוי עכשיו (תוקן בענף התיקונים), אז המשפט צריך להשתנות. באתר אין גיבוי.
4. "האתר לא דורש הרשמה" ו"הטיול שלך בדפדפן בלבד": באתר יש עכשיו קבוצות וכניסה עם גוגל, והטיול עולה לקבוצה כשמצטרפים.
5. "מה שהשרתים רואים": האתר טוען גם את ספריית `supabase-js` (מ-jsdelivr, רק כשיש חיבור) ואת הכניסה של גוגל (`accounts.google.com`), ולוח המדידה טוען סקריפטים מהשרתים של PostHog ו-Sentry.

## עברית

### בקצרה (מחליף את הסעיף הקיים)

- האפליקציה והאתר עובדים בלי הרשמה, ולא מבקשים שם, מייל, טלפון או מיקום.
- ההגדרות, המועדפים והשיאים נשארים רק אצלך. "הטיול שלך" נשאר רק אצלך עד שמצטרפים לקבוצה.
- **קבוצות** הן הפעם היחידה שמידע נשלח לשרת שלנו, ורק אם בחרת להצטרף לקבוצה או ליצור אותה. בקבוצה חברי הקבוצה רואים את השם שבחרת, את הטיול שבחרת לשתף, את המפגשים ואת השיאים.
- אנחנו אוספים גם מדידת שימוש אנונימית ודיווחי קריסות. אפשר לכבות את שניהם בהגדרות.
- אין פרסומות, לא מוכרים מידע, ולא עוקבים אחריך באפליקציות או באתרים אחרים.

### מה נשמר רק אצלך (מחליף את הסעיף הקיים)

ההגדרות, המועדפים והשיאים במשחקים נשמרים בטלפון (או בדפדפן) בלבד, ולא נשלחים אלינו. "הטיול שלך" (טיסה ותאריכים) נשמר גם הוא רק אצלך, **עד שאתה מצטרף לקבוצה**: אז עותק שלו נשמר גם בשרת, כדי שחברי הקבוצה יראו את הטיסה שלך (ראו "חשבונות וקבוצות"). גיבוי המכשיר כבוי באפליקציה: מה שנשמר בה לא עובר לגיבוי של אנדרואיד.

### חשבונות וקבוצות (סעיף חדש)

אפשר להשתמש במפה, במשחקים ובכרטיס הטיסה בלי להירשם. כדי להצטרף לקבוצה או ליצור אותה, האפליקציה והאתר מתחברים לשרת שלנו (Supabase, בשרתים בפרנקפורט שבגרמניה).

**מה נשמר בשרת:**

1. **זהות.** הצטרפות לקבוצה בקישור או בקוד יוצרת זהות אנונימית: מזהה אקראי, בלי שם ובלי מייל. כניסה עם חשבון גוגל שומרת בשירות ההתחברות את כתובת המייל, השם והתמונה שגוגל מוסרת, ואת המזהה של החשבון. אנחנו מעתיקים לפרופיל רק את השם, כברירת מחדל לשם בקבוצה; לא מציגים את המייל או את התמונה לאף אחד, ולא משתמשים בהם לשום דבר אחר.
2. **פרופיל:** השם שבחרת בקבוצה (עד 40 תווים) ושפת הממשק.
3. **קבוצות:** שם הקבוצה ותאריכי הטיול שלה, מי חבר בה ובאיזה תפקיד (חבר או מנהל), הזמנות (קוד וקישור), ובקשות הצטרפות.
4. **טיסות:** אם בחרת לשתף את הטיול שלך עם קבוצה, העותק שלו (תאריכים, מספרי טיסה, שדות תעופה ושעות) נראה לחברי הקבוצה. מנהל קבוצה יכול להזין טיסה בשם חבר, וזה מסומן.
5. **מפגשים ושיאים:** מפגשים שחברי הקבוצה קובעים (תחנה, שעה והערה), והשיא הכי גבוה שלך בכל משחק, שנראה לחברי הקבוצה.
6. **יומן פעולות** (נשמר 180 יום, לא נגיש לאפליקציות): מי עשה פעולה רגישה ומתי (יצירת קבוצה, הצטרפות, אישור או דחייה של בקשה, הוצאת חבר, מינוי מנהל, מחיקת חשבון, וניסיונות שגויים להזין קוד הזמנה). **לוגים טכניים של השרת** (נשמרים יום אחד): הפעולה, מזהה המשתמש, התוצאה וזמן התגובה, וכתובת ה-IP של הבקשה, כמו בכל שרת.

**מי רואה מה:** חבר בקבוצה רואה את שאר החברים בה (שם), את הטיסות שהם בחרו לשתף, את המפגשים ואת השיאים. מי שאינו חבר לא רואה כלום. מנהל רואה גם בקשות הצטרפות. מי שמקבל קישור הזמנה (או קוד) לקבוצה שאינה דורשת אישור רואה את שמות החברים בה לפני שהוא מצטרף, כדי שחבר שהחליף טלפון יוכל לבקש לחזור.

**כמה זמן:** המידע נשמר כל עוד החשבון והקבוצה קיימים. זהות אנונימית שאינה חברה באף קבוצה נמחקת מהשרת אחרי 30 יום. קבוצה ללא חברים נמחקת. מי שעוזב קבוצה, הטיסה שלו מפסיקה להופיע בה.

**מחיקה:** אפשר למחוק את החשבון בכל רגע, בלי לפנות אלינו: באפליקציה (הגדרות, חשבון), או באתר בכתובת `gudauri-ski-trip.vercel.app/account`. המחיקה מוציאה אותך מכל הקבוצות (אם היית המנהל, חבר רשום ותיק מתמנה במקומך), ומוחקת את הפרופיל, הטיולים, השיאים וזהות ההתחברות. מפגשים שקבעת נשארים בקבוצה בלי שמך. השם שלך נמחק גם מיומן הפעולות.

**שירותים שמעבדים בשבילנו:** Supabase (מסד הנתונים, ההתחברות וזמן אמת; שרתים בפרנקפורט), וגוגל, לכניסה עם חשבון גוגל (חלה עליה מדיניות הפרטיות של גוגל). אנחנו לא מוכרים ולא מעבירים את המידע הזה לאף אחד אחר.

### האתר (תוספת לסעיף הקיים)

בלי הרשמה האתר לא משתמש בעוגיות, והמפה, ההגדרות והשיאים נשמרים בדפדפן בלבד. **אם מצטרפים לקבוצה או נכנסים עם גוגל,** הדפדפן שומר גם את ההתחברות (בזיכרון הדפדפן, לא בעוגייה) ועותק של הקבוצה, והמידע נשמר בשרת כמתואר ב"חשבונות וקבוצות". כדי להציג את האתר ולאפשר כניסה, הדפדפן טוען גם את ספריית ההתחברות (supabase-js, מ-jsDelivr, רק כשיש חיבור לקבוצה), את הכניסה של גוגל (רק כשלוחצים על "כניסה עם גוגל"), ואת סקריפטי המדידה של PostHog ו-Sentry (אלא אם כיבית את המדידה). השירותים האלה רואים את כתובת ה-IP שלך.

### הזכויות שלך (תוספת)

כדי לראות, לתקן או למחוק מידע שנשמר בשרת: מחיקת החשבון (למעלה) מוחקת הכל. לשאלות או לתיקון, pinisagent@gmail.com. הנתונים בקבוצה נראים כבר לחברי הקבוצה, ולכן אפשר גם פשוט לבקש מהם או מהמנהל.

### אבטחה והעברה לחו״ל (מחליף את הסעיף הקיים)

כל התקשורת מוצפנת (HTTPS). מסד הנתונים של הקבוצות, ההתחברות ושירותי המדידה והקריסות מאחסנים את המידע בשרתים באירופה. הגישה למידע בשרת מוגבלת בכללים ברמת השורה: כל אחד רואה רק קבוצות שהוא חבר בהן.

## English

### In short (replaces the existing section)

- The app and the website work without signing up, and never ask for your name, email, phone number or location.
- Your settings, favorites and game high scores stay only with you. "Your trip" stays only with you until you join a group.
- **Groups** are the one case where data is sent to our server, and only if you choose to join or create a group. In a group, the other members see the name you chose, the trip you chose to share, the meetups and the high scores.
- We also collect anonymous usage statistics and crash reports. You can turn both off in Settings.
- No ads. We do not sell data, and we do not track you across other apps or websites.

### Stored only with you (replaces the existing section)

Your settings, favorites and game high scores are stored only on your phone (or in your browser) and are never sent to us. "Your trip" (flights and dates) also stays only with you **until you join a group**: then a copy is also kept on our server, so the group's members can see your flight (see "Accounts and groups"). Device backup is turned off in the app: what it stores is not included in Android backups.

### Accounts and groups (new section)

You can use the map, the games and the boarding pass without signing up. To join or create a group, the app and the website connect to our server (Supabase, servers in Frankfurt, Germany).

**What is stored on the server:**

1. **Identity.** Joining a group by link or code creates an anonymous identity: a random ID, with no name and no email. Signing in with a Google account stores, in the sign-in service, the email address, name and picture that Google provides, and the account's ID. We copy only the name into the profile, as the default for your name in a group; we do not show your email or picture to anyone and do not use them for anything else.
2. **Profile:** the name you chose in the group (up to 40 characters) and the interface language.
3. **Groups:** the group's name and trip dates, who is a member and in what role (member or admin), invitations (code and link), and join requests.
4. **Flights:** if you chose to share your trip with a group, its copy (dates, flight numbers, airports and times) is visible to the group's members. A group admin can enter a flight on a member's behalf, and this is marked.
5. **Meetups and scores:** meetups the group's members set (station, time and a note), and your best score in each game, visible to the group's members.
6. **Action log** (kept 180 days, not accessible to the apps): who did a sensitive action and when (creating a group, joining, approving or rejecting a request, removing a member, appointing an admin, deleting an account, and wrong attempts to enter an invitation code). **Technical server logs** (kept one day): the action, the user ID, the result and the response time, and the IP address of the request, as on any server.

**Who sees what:** a group member sees the other members (name), the flights they chose to share, the meetups and the scores. Someone who is not a member sees nothing. An admin also sees join requests. Someone who receives an invitation link (or code) to a group that does not require approval sees the members' names before joining, so that a member who changed phones can ask to come back.

**How long:** data is kept as long as the account and the group exist. An anonymous identity that is in no group is deleted from the server after 30 days. A group with no members is deleted. When you leave a group, your flight stops showing in it.

**Deletion:** you can delete your account at any time, without contacting us: in the app (Settings, Account), or on the website at `gudauri-ski-trip.vercel.app/account`. Deletion removes you from every group (if you were the admin, a long-standing registered member becomes admin), and deletes your profile, trips, scores and sign-in identity. Meetups you set stay in the group without your name. Your name is also removed from the action log.

**Services that process data for us:** Supabase (database, sign-in and realtime; servers in Frankfurt), and Google, for signing in with a Google account (Google's privacy policy applies). We do not sell this data or pass it to anyone else.

### The website (addition to the existing section)

Without signing up, the website uses no cookies, and the map, settings and scores are stored only in your browser. **If you join a group or sign in with Google,** your browser also keeps the sign-in (in the browser's storage, not a cookie) and a copy of the group, and the data is stored on the server as described in "Accounts and groups". To display the site and allow sign-in, your browser also loads the sign-in library (supabase-js, from jsDelivr, only when connected to a group), Google's sign-in (only when you press "Sign in with Google"), and the PostHog and Sentry measurement scripts (unless you turned measurement off). These services see your IP address.

### Your rights (addition)

To see, correct or delete data stored on the server: deleting your account (above) deletes everything. For questions or corrections, pinisagent@gmail.com. Group data is already visible to the group's members, so you can also simply ask them or the admin.

### Security and international transfer (replaces the existing section)

All communication is encrypted (HTTPS). The groups database, sign-in, and the usage and crash services store data on servers in the EU. Access to server data is limited by row-level rules: everyone sees only the groups they belong to.

---

## טופס הנתונים בגוגל פליי (`docs/PLAY.md`, סעיף ג)

הסעיף שם כבר מזכיר `Personal info` ו-`App activity`. אחרי האישור לעדכן אותו כך (גרסה עם חשבונות):

| קטגוריה | סוג | מטרה | חובה או רשות | משותף? |
|---|---|---|---|---|
| `Personal info` | `Name` | `App functionality`, `Account management` | רשות (השם בקבוצה) | לא; חברי הקבוצה רואים אותו, וזה לא "שיתוף" בהגדרת גוגל כי הוא חלק מהפעולה שהמשתמש ביקש |
| `Personal info` | `Email address` | `Account management` | רשות, רק בכניסה עם גוגל | לא |
| `Personal info` | `User IDs` | `App functionality`, `Account management` | רשות (הזהות בקבוצה, גם אנונימית) | לא |
| `App activity` | `Other user-generated content` | `App functionality` | רשות (טיולים, מפגשים, שיאים) | לא |
| `App activity` | `App interactions` | `Analytics` | רשות (מתג המדידה) | לא |
| `App info and performance` | `Crash logs`, `Diagnostics` | `Analytics`, `App functionality` | רשות (מתג המדידה) | לא |
| `Device or other IDs` | `Device or other IDs` | `Analytics` | רשות (מתג המדידה) | לא |

- **מחיקה:** כן, באפליקציה (הגדרות, חשבון) ובדף `gudauri-ski-trip.vercel.app/account` (גוגל פליי דורשת קישור כזה בשדה "Delete account URL"). הצהרה: גם בלי החשבון אפשר לבקש מחיקה של נתונים.
- **הצפנה בתעבורה:** כן.
- **שאלון הדירוג:** `User interaction` = כן (חברי קבוצה רואים שמות ומפגשים), לפי הסעיף ב-`docs/PLAY.md` שכבר מזכיר את זה.
- שני דברים לבדוק לפני שממלאים: (1) שפיני מאשר שהשם והתמונה מגוגל נשמרים בשירות ההתחברות של סופבייס (כך זה עובד בכניסה עם גוגל), ושמוצג רק השם; (2) אם לגוגל פליי נחשב `Photos` או `Email` כשהם נשמרים רק בשירות ההתחברות ולא נקראים: להצהיר `Email address` בלבד, ולנסח בשדה התיאור שהתמונה לא בשימוש.
