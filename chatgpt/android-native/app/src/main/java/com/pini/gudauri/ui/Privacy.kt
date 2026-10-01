package com.pini.gudauri.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable fun PrivacyDialog(close:()->Unit) {
    val context=LocalContext.current
    AlertDialog(onDismissRequest=close,containerColor=LocalPalette.current.paper,title={Text("מידע על פרטיות")},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("המועדפים וערכת הצבעים נשמרים במכשיר ואינם נשלחים לשרת שלנו. אפשר למחוק אותם בניקוי נתוני האפליקציה בהגדרות אנדרואיד או בהסרתה.")
            Text("אין בגרסה הזאת חשבונות, ניתוח שימוש שלנו או גישה למיקום, למצלמה, למיקרופון ולאנשי הקשר. המפה ופרטי הטיסה הכלולים הם נתונים ציבוריים מהריפו.")
            Text("תמונות סרטונים נטענות מיוטיוב. הנגן נטען לאחר בחירת סרטון ומשתמש במצב פרטיות משופר. בקשות לספק מעבירות מידע טכני כגון כתובת רשת; הטיפול במידע אצל גוגל כפוף למדיניות שלה.")
            Text("שיתוף מופעל בבחירתך ובאפליקציה שתבחר. קישורים חיצוניים כפופים למדיניות השירות שאליו עוברים.")
            Text("נקודת המפגש, היום והשעה נמצאים בקישור השיתוף עצמו, ללא חשבון או שרת תיאום. מי שמקבל את הקישור יכול לקרוא אותם. תמונת המפגש נוצרת במכשיר ונשמרת זמנית במטמון; השיתוף מעניק לאפליקציה שתבחר גישה לתמונה הזאת בלבד. שמות חברי הקבוצה אינם נכללים בכרטיס המפגש.")
            TextButton(onClick={openUrl(context,"https://policies.google.com/privacy")}){Text("מדיניות הפרטיות של גוגל ↗")}
        }},confirmButton={TextButton(onClick=close){Text("סגור")}})
}
