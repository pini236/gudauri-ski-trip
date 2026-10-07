# Sella Ronda: בדיקת הנתונים

נבנה ב-2026-10-07 בפקודה `python3 tools/build-resort.py tools/resorts/sellaronda.json --cache <dir>`. אל תערכו ביד; מריצים שוב.

- **מסלולים:** 424 (247 עם מספר או שם), 600 קטעים מהמפה הפתוחה, 281.6 ק״מ של קו.
- **רכבלים:** 153 (135 עם שם).
- **מודל גובה:** 1398×1169 נקודות כל 20 מ׳, מ-938 עד 3340 מ׳. המקור: TINITALY/1.1 (INGV), מודל קרקע של 10 מ׳ לכל איטליה, CC BY 4.0, שני אריחים (w51570, w51070) שהורדו ב-7.10.2026.
- **לא נכנסו:** 116 קווים (מסלולי סקי קרוס־קאנטרי, הליכה ומזחלות, וקווים בלי דרגת קושי).

## בדיקות לכל מסלול

עלייה נגדית: סכום העליות לאורך הקו, מלמעלה למטה, במודל שהאתר מצייר (כלל הדיוק 4: עד כ-10 מ׳). מסלול שנכשל בבדיקה מקבל ודאות `low`, ולא מוסתר: הקו עצמו מהמפה הפתוחה.

| מסלול | צבע | אורך (מ׳) | עלייה נגדית (מ׳) | הערות |
|---|---|---|---|---|
| 3-Tre | red | 3439 | 1 | דרגות קושי שונות בקטעים: advanced, intermediate |
| 3-Tre - Mortic | red | 962 | 0 | במפה הפתוחה: Mortic |
| 3-Tre var. agonistica | red | 608 | 0 | במפה הפתוחה: 3-Tre variante Agonistica |
| Abrusé | blue | 601 | 3 | תקין |
| Alpenrose | blue | 1162 | 5 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Alpha | black | 1385 | 1 | תקין |
| Altin | red | 3050 | 6 | תקין |
| Antercrëp | red | 1226 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Arabba | blue | 1875 | 1 | תקין |
| Arlara | blue | 1843 | 2 | במפה הפתוחה: Arlara 1 |
| Arlara - Pralongiá II | blue | 751 | 5 | במפה הפתוחה: Arlara - Pralongia II |
| Armentarola | blue | 1466 | 2 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Avoie | blue | 677 | 1 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Bamby | red | 1020 | 0 | במפה הפתוחה: Bamby 1 |
| Bamby 2 | red | 919 | 0 | תקין |
| Bear Slope | blue | 805 | 14 | עלייה נגדית 14 מ׳; במפה הפתוחה: Pista dell'Orso |
| Bec de Roces | red | 1152 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Belvedere | blue | 863 | 0 | תקין |
| Belvedere 1 | red | 1997 | 7 | במפה הפתוחה: Belvedere I |
| Belvedere 2 | red | 1230 | 0 | במפה הפתוחה: Belvedere II |
| Biancaneve | blue | 322 | 0 | תקין |
| Biok | blue | 1105 | 0 | תקין |
| Biok - Pralongiá II | blue | 1361 | 1 | במפה הפתוחה: Biok - Pralongia II |
| Biok - Saraghes | blue | 1794 | 0 | תקין |
| Bones Braker / Funtanes | red | 1603 | 1 | במפה הפתוחה: Spacca Ossi / Funtanes |
| Boè | red | 3132 | 2 | במפה הפתוחה: Boè |
| Boé | red | 264 | 0 | דרגות קושי שונות בקטעים: easy, intermediate |
| Boé - Campolongo | red | 1073 | 3 | במפה הפתוחה: Boe - Campolongo |
| Braia Fraida | blue | 840 | 3 | תקין |
| Braia Fraida - Arlara | blue | 438 | 6 | תקין |
| Braia Fraida - Roby | blue | 734 | 2 | תקין |
| Bravo | red | 2003 | 2 | תקין |
| Burz | black | 999 | 1 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Cadepunt | blue | 335 | 0 | תקין |
| Campo Freina | red | 331 | 0 | תקין |
| Campolongo | red | 1359 | 2 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Canazei | red | 1675 | 0 | הסוף רחוק מרכבל ומסלול |
| Capanna Nera | blue | 1517 | 1 | תקין |
| Catores | black | 727 | 4 | תקין |
| Catores - Fermeda | red | 472 | 0 | תקין |
| Cendevaves 1 | blue | 338 | 0 | תקין |
| Cendevaves 2 | blue | 342 | 1 | תקין |
| Charly | red | 3746 | 9 | הצבע במפה הפתוחה blue, ברשימה הרשמית red: לפי הרשמית |
| Cherz 1 | blue | 1221 | 1 | במפה הפתוחה: Cherz I |
| Cherz 2 | red | 1124 | 3 | במפה הפתוחה: Cherz II |
| Ciampai | blue | 726 | 6 | תקין |
| Ciampinoi 3 | black | 1575 | 0 | תקין |
| Ciampinoi 4 | red | 2613 | 0 | תקין |
| Ciampinoi 5 | red | 716 | 2 | תקין |
| Ciampinoi 6 | red | 961 | 1 | תקין |
| Ciampinoi - Piza Pranseies | red | 135 | 0 | תקין |
| Cinque Dita | red | 1130 | 3 | תקין |
| Cir | black | 2170 | 0 | תקין |
| Cir-Ria | red | 235 | 0 | במפה הפתוחה: Cir - Ria |
| Città dei Sassi | blue | 1367 | 5 | תקין |
| Col Alt 4 | red | 1856 | 1 | תקין |
| Col Alt 4 1/2 | blue | 638 | 0 | במפה הפתוחה: Col Alt 4½ |
| Col Pradat | black | 1031 | 0 | תקין |
| Col Raiser - Plan Da Tieja | blue | 608 | 0 | הצבע במפה הפתוחה red, ברשימה הרשמית blue: לפי הרשמית; במפה הפתוחה: Col Raiser - Plan de Tieja |
| Col d'Altin 1 | blue | 188 | 1 | תקין |
| Col d'Altin 2 | blue | 154 | 0 | תקין |
| Col dei Rossi | red | 1694 | 5 | תקין |
| Colfosco 1 | red | 883 | 4 | תקין |
| Colfosco 2 | blue | 2160 | 3 | תקין |
| Collegamento Armentarola - Piz Sorega | blue | 590 | 2 | במפה הפתוחה: Armentarola - Piz Sorega |
| Collegamento Boè - Vallon | blue | 101 | 2 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Collegamento Carpazza | red | 264 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Collegamento Ciampinoi | red | 305 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Collegamento Crep de Munt - Boé | blue | 1814 | 0 | במפה הפתוחה: Crep de Mont - Boè |
| Collegamento Piz Arlara - Costes da l'Ega | blue | 692 | 1 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Collegamento Pralongià II - Pralongià I | blue | 711 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Collegamento Sodlisia - Frara | blue | 311 | 3 | תקין |
| Colraiser | red | 2338 | 1 | תקין |
| Colraiser - Fermeda | blue | 1074 | 5 | תקין |
| Colz - Doninz | blue | 224 | 1 | תקין |
| Comici - Tramans | red | 568 | 0 | תקין |
| Comici II | red | 400 | 1 | תקין |
| Connecting slope Gran Paradiso - Sotsasslong | red | 120 | 0 | הצבע במפה הפתוחה blue, ברשימה הרשמית red: לפי הרשמית; במפה הפתוחה: Gran Paradiso - Sotsaslong |
| Connection Alpha - Bravo | black | 194 | 0 | דרגות קושי שונות בקטעים: advanced, intermediate; במפה הפתוחה: Collegamento Alpha - Bravo |
| Connection Ciampinoi - Costabella | red | 626 | 1 | במפה הפתוחה: Collegamento Ciampinoi - Costabella |
| Connection Saslong - Slope B | red | 697 | 4 | במפה הפתוחה: Collegamento Saslong B |
| Costabella | red | 638 | 2 | תקין |
| Costabella Variante | blue | 204 | 1 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Costes da l'Ega | blue | 700 | 3 | תקין |
| Costoratta | red | 884 | 5 | תקין |
| Crep De Mont | blue | 1238 | 0 | במפה הפתוחה: Crep de Mont |
| Curona | blue | 776 | 1 | תקין |
| Dantercepies | red | 3187 | 0 | דרגות קושי שונות בקטעים: easy, intermediate; ברשימה הרשמית עוד מסלול בשם הזה, בצבע אחר, ואין לו קו נפרד במפה הפתוחה: Dantercepies (blue) |
| Dantercepies - Risaccia | blue | 260 | 0 | תקין |
| Dantercepies - Selva | blue | 765 | 5 | תקין |
| Dantercepies Variante | blue | 1311 | 0 | דרגות קושי שונות בקטעים: easy, intermediate; לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Diego | black | 1486 | 1 | תקין |
| Doninz | blue | 201 | 1 | תקין |
| Doninz - Colz | blue | 278 | 0 | תקין |
| Due Baite | blue | 673 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Due Baite I | blue | 383 | 3 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Falk | red | 1412 | 3 | תקין |
| Fermeda 1 | red | 1949 | 0 | תקין |
| Fermeda 2 | red | 1299 | 1 | תקין |
| Fermeda - S.Cristina | blue | 1487 | 0 | במפה הפתוחה: Fermeda - S. Cristina |
| Fodoma | black | 2000 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Forcella Incisia | blue | 1467 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Forcelles | red | 1283 | 2 | תקין |
| Frainella | red | 462 | 2 | תקין |
| Frara | blue | 2505 | 1 | דרגות קושי שונות בקטעים: easy, intermediate |
| Frara - Sodlisia | blue | 869 | 0 | במפה הפתוחה: Frara Sodlisia |
| Frara - Val Setus | red | 640 | 3 | תקין |
| Frara - variante | blue | 1095 | 0 | במפה הפתוחה: Frara Variante |
| Frea | red | 1013 | 1 | תקין |
| Fungeia | red | 199 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Fungeia - Campo Freina | blue | 774 | 12 | עלייה נגדית 12 מ׳ |
| Gabia | blue | 578 | 0 | תקין |
| Gardena | red | 1646 | 0 | תקין |
| Gardenaccia A | red | 1393 | 0 | תקין |
| Gardenaccia B | blue | 2327 | 8 | תקין |
| Gardenaccia nera | black | 216 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Gherdecia | red | 452 | 0 | במפה הפתוחה: Gardeccia |
| Gonzaga | red | 559 | 1 | תקין |
| Gran Paradiso | blue | 1653 | 8 | תקין |
| Gran Paradiso - Piz Seteur | blue | 333 | 0 | דרגות קושי שונות בקטעים: advanced, intermediate; הצבע במפה הפתוחה red, ברשימה הרשמית blue: לפי הרשמית |
| Gran Pela | black | 512 | 2 | תקין |
| Gran Risa | black | 2029 | 5 | תקין |
| Grohmann | red | 571 | 0 | תקין |
| Incisa | blue | 1512 | 1 | תקין |
| Kids Fun Line La Crusc | blue | 244 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| La Baita - Pralongiá valle | blue | 762 | 9 | במפה הפתוחה: La Baita - Pralongià valle |
| La Brancia 14 | blue | 843 | 1 | תקין |
| La Brancia - Pralongiá II | blue | 1586 | 4 | תקין |
| La Ciampinoi | black | 538 | 0 | תקין |
| La Crusc 1 | blue | 2338 | 1 | תקין |
| La Crusc 1 variante | red | 663 | 1 | תקין |
| La Crusc 2 | blue | 1589 | 3 | תקין |
| La Fraina | red | 944 | 4 | תקין |
| La Longia | red | 3360 | 3 | תקין |
| La Longia - Furnes | red | 3133 | 29 | עלייה נגדית 29 מ׳ |
| La Para | blue | 429 | 0 | תקין |
| La Ria | black | 1010 | 0 | תקין |
| La Vizza | red | 2038 | 1 | דרגות קושי שונות בקטעים: easy, intermediate |
| Larciunei | blue | 326 | 0 | תקין |
| Maria | blue | 1087 | 1 | תקין |
| Martinelli | red | 1496 | 0 | תקין |
| Mesola | blue | 1169 | 3 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Mickey Mouse | blue | 97 | 0 | תקין |
| Mount Fermeda - Seceda topstation | red | 400 | 1 | במפה הפתוחה: Monte Fermeda |
| Nives | blue | 311 | 0 | תקין |
| Ornella | red | 4297 | 4 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Padon | red | 574 | 0 | הסוף רחוק מרכבל ומסלול; לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Padon A | red | 19 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Panorama | red | 489 | 3 | תקין |
| Paprika | black | 1332 | 0 | דרגות קושי שונות בקטעים: advanced, intermediate |
| Paprika - Comici1 | red | 235 | 0 | במפה הפתוחה: Paprika - Comici I |
| Parallel 1 | blue | 640 | 0 | תקין |
| Parallel 2 | blue | 622 | 0 | תקין |
| Pezzei | blue | 419 | 0 | תקין |
| Pian Frataces | red | 2941 | 4 | תקין |
| Pista del Lupo | blue | 282 | 2 | תקין |
| Pista del Sole | blue | 2203 | 1 | במפה הפתוחה: Pista del sole |
| Pista di collegamento Bamby | blue | 1910 | 2 | תקין |
| Pista di collegamento Boé - Costoratta | red | 195 | 8 | במפה הפתוחה: Boè - Costoratta |
| Pitla Pela | red | 794 | 0 | תקין |
| Pitla Pela Variant | red | 419 | 0 | במפה הפתוחה: Pitla Pela Variante |
| Piz Sella 5 | red | 2242 | 2 | דרגות קושי שונות בקטעים: advanced, intermediate; ברשימה הרשמית עוד מסלול בשם הזה, בצבע אחר, ואין לו קו נפרד במפה הפתוחה: Piz Sella 5 (black) |
| Piz Seteur - Città dei Sassi | blue | 326 | 3 | תקין |
| Piz Seteur - Comici | red | 340 | 1 | תקין |
| Piz Seteur - Gran Paradiso | red | 362 | 0 | תקין |
| Piz Seteur - Sotsaslong | red | 153 | 1 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Piz Sorega | blue | 74 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Piz Sorega - Biok | blue | 175 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Piz Sorega A | red | 1901 | 0 | תקין |
| Piz Sorega B | blue | 3355 | 0 | תקין |
| Plan Boè | blue | 191 | 1 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Plan Da Tieja | blue | 210 | 0 | במפה הפתוחה: Plan de Tieja |
| Plan de Gralba | blue | 1254 | 4 | תקין |
| Pordoi | red | 3778 | 4 | דרגות קושי שונות בקטעים: easy, intermediate; הצבע במפה הפתוחה blue, ברשימה הרשמית red: לפי הרשמית |
| Portados | red | 886 | 7 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Pralongiá - Punta Trieste | blue | 834 | 5 | במפה הפתוחה: Pralongiá  - Punta Trieste |
| Pralongiá I | blue | 789 | 0 | במפה הפתוחה: Pralongià I |
| Pralongiá I - Arlara | red | 310 | 1 | במפה הפתוחה: Pralongià I - Arlara |
| Pralongiá II | blue | 1223 | 0 | במפה הפתוחה: Pralongià II |
| Pre Dai Corf | blue | 1176 | 2 | במפה הפתוחה: Pre dai Corf |
| Pudra | blue | 454 | 0 | תקין |
| Punta Trieste - La Baita | blue | 854 | 5 | תקין |
| Puntea 1 | blue | 380 | 0 | תקין |
| Puntea 2 | blue | 389 | 3 | תקין |
| Raccordo 1 | red | 452 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Raccordo Boé - Crep de Mont | red | 445 | 0 | במפה הפתוחה: Boè - Crep de Mont |
| Raccordo Ciampai - La Fraina | blue | 272 | 4 | במפה הפתוחה: Ciampai - La Fraina |
| Raccordo Ciampai - n. 10 | blue | 353 | 0 | במפה הפתוחה: Ciampai - n.10 |
| Raccordo Col Alt - Braia Fraida | blue | 608 | 0 | במפה הפתוחה: Col Alt - Braia Fraida |
| Raccordo La Brancia - n.15 | blue | 490 | 0 | במפה הפתוחה: La Brancia 15 |
| Raccordo Piz Sorega B - Armentarola | blue | 3789 | 1 | במפה הפתוחה: Piz Sorega B - Armentarola |
| Raccordo Salere - Alpenrose | red | 735 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Resciesa - Furnes | red | 2565 | 4 | תקין |
| Rientro | blue | 1086 | 5 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Risaccia | blue | 456 | 0 | תקין |
| Roby | blue | 673 | 0 | תקין |
| Roby - Bamby | blue | 1281 | 0 | תקין |
| Rodella | red | 844 | 0 | תקין |
| Rodella 3 / Tre | red | 35 | 1 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Rutort | blue | 1031 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Salei | red | 654 | 0 | תקין |
| Salere | red | 2838 | 8 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Sas Betit | blue | 1802 | 3 | תקין |
| Saslong | black | 3262 | 0 | תקין |
| Saslong A | black | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול; לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Saslong B | red | 3778 | 4 | תקין |
| Sass Becè | red | 1272 | 3 | תקין |
| Sass Becé | red | 41 | 0 | במפה הפתוחה: Sass Becé |
| Sass de la Vegla | blue | 948 | 2 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Savinè | red | 1208 | 3 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Schiappen - Sella | blue | 1695 | 0 | במפה הפתוחה: Schiappen-Sella |
| Seceda - Fermeda | red | 434 | 0 | תקין |
| Sef | blue | 1787 | 3 | דרגות קושי שונות בקטעים: easy, intermediate; ברשימה הרשמית עוד מסלול בשם הזה, בצבע אחר, ואין לו קו נפרד במפה הפתוחה: Sef (red) |
| Sef - Gran Paradiso | blue | 499 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Seggiovia Pralongià | red | 1384 | 8 | ברשימה הרשמית עוד מסלול בשם הזה, בצבע אחר, ואין לו קו נפרד במפה הפתוחה: Seggiovia Pralongiá (blue); במפה הפתוחה: Seggiovia Pralongià |
| Seggiovia Pralongiá | blue | 594 | 0 | ברשימה הרשמית עוד מסלול בשם הזה, בצבע אחר, ואין לו קו נפרד במפה הפתוחה: Seggiovia Pralongiá (red) |
| Seggiovia Pralongiá - Pralongiá II | blue | 301 | 0 | תקין |
| Skilift Armentarola | blue | 533 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Skiweg Skilift Nives - La Poza | blue | 1328 | 2 | תקין |
| Sochers | red | 678 | 0 | תקין |
| Sochers 2 | red | 614 | 0 | תקין |
| Sodlisia | blue | 1388 | 4 | תקין |
| Sole/Piz Seteur | blue | 648 | 4 | במפה הפתוחה: Sole |
| Sotsaslong | blue | 1252 | 3 | תקין |
| Sourasass | black | 2339 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Sponata | red | 1742 | 1 | תקין |
| Stella Alpina | blue | 726 | 8 | תקין |
| Stella Alpina variante | blue | 405 | 0 | במפה הפתוחה: Stella Alpina Variante |
| Terza Punta | red | 308 | 0 | תקין |
| Tramans | red | 1660 | 3 | תקין |
| Tremor | black | 811 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Tschucky | blue | 298 | 0 | תקין |
| Val Setus | red | 1010 | 2 | תקין |
| Val Setus - Plans | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול; לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Vallelunga | black | 833 | 1 | תקין |
| Vallon | black | 1222 | 3 | תקין |
| Variante Avoie | blue | 420 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Variante Bec | red | 406 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Variante Col Pradat | blue | 1169 | 2 | תקין |
| Variante Il Muro | black | 226 | 0 | במפה הפתוחה: Variante il Muro |
| Variante Marmotte | red | 468 | 0 | תקין |
| Variante Ornella | black | 612 | 0 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Variante Sole | blue | 235 | 0 | תקין |
| Variante Super Red | red | 456 | 0 | תקין |
| Verbindung Monte Pana-Saslong | black | 969 | 6 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Verbindung Saslong-Sesselbahn Monte Pana | red | 565 | 10 | לא נמצא ברשימה הרשמית של האתר; הצבע מהמפה הפתוחה |
| Vernel | red | 2931 | 1 | תקין |
| u20925570 | blue | 521 | 1 | תקין |
| u20925990 | red | 67 | 0 | תקין |
| u30005097 | red | 132 | 2 | תקין |
| u30007449 | red | 501 | 0 | תקין |
| u30074454 | blue | 29 | 0 | תקין |
| u30074462 | blue | 66 | 0 | תקין |
| u30074478 | blue | 63 | 0 | תקין |
| u30075933 | blue | 47 | 0 | תקין |
| u30122436 | red | 234 | 0 | תקין |
| u32126468 | red | 61 | 0 | תקין |
| u32200057 | red | 31 | 0 | תקין |
| u32463010 | red | 34 | 0 | תקין |
| u32479025 | red | 144 | 0 | תקין |
| u32479026 | red | 20 | 0 | תקין |
| u32479027 | red | 110 | 0 | תקין |
| u32479030 | red | 24 | 0 | תקין |
| u32559110 | red | 65 | 0 | תקין |
| u32559144 | red | 37 | 0 | תקין |
| u42283595 | blue | 5 | 0 | תקין |
| u44283176 | blue | 246 | 1 | תקין |
| u48800924 | blue | 365 | 0 | תקין |
| u49068271 | red | 285 | 8 | תקין |
| u49068273 | red | 36 | 1 | תקין |
| u49436046 | blue | 42 | 0 | תקין |
| u49436056 | blue | 161 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u52007132 | blue | 124 | 1 | תקין |
| u52037873 | blue | 144 | 0 | תקין |
| u95083109 | blue | 270 | 1 | תקין |
| u135176804 | red | 34 | 1 | תקין |
| u135176811 | red | 277 | 0 | תקין |
| u150974880 | red | 236 | 0 | תקין |
| u199413986 | blue | 159 | 0 | תקין |
| u208293533 | red | 85 | 3 | תקין |
| u208446672 | blue | 49 | 0 | תקין |
| u208446676 | blue | 33 | 1 | תקין |
| u208446681 | blue | 54 | 0 | תקין |
| u208446683 | blue | 54 | 0 | תקין |
| u208446686 | blue | 41 | 0 | תקין |
| u208580618 | red | 103 | 1 | תקין |
| u208719710 | blue | 98 | 1 | תקין |
| u208719714 | blue | 20 | 0 | תקין |
| u208719716 | blue | 67 | 1 | תקין |
| u209187049 | red | 364 | 0 | תקין |
| u240495380 | blue | 24 | 0 | תקין |
| u246802319 | blue | 26 | 0 | תקין |
| u261752047 | red | 35 | 1 | תקין |
| u262565483 | blue | 27 | 2 | תקין |
| u262565486 | blue | 16 | 0 | תקין |
| u262565487 | blue | 142 | 1 | תקין |
| u262565491 | blue | 247 | 0 | תקין |
| u262773704 | red | 75 | 0 | תקין |
| u262804548 | red | 51 | 1 | תקין |
| u263014386 | red | 486 | 0 | תקין |
| u263014394 | red | 217 | 0 | תקין |
| u315940715 | blue | 110 | 0 | תקין |
| u331117760 | red | 94 | 0 | הסוף רחוק מרכבל ומסלול |
| u331422350 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u331422351 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול |
| u331858946 | red | 0 | 0 | תקין |
| u331962049 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u331962055 | blue | 171 | 0 | תקין |
| u331962858 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u331964369 | red | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u332290590 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול |
| u332290592 | red | 0 | 0 | תקין |
| u402501555 | red | 349 | 1 | תקין |
| u402966406 | red | 358 | 0 | תקין |
| u402966407 | blue | 48 | 0 | תקין |
| u402966412 | red | 337 | 0 | תקין |
| u444734366 | blue | 391 | 15 | עלייה נגדית 15 מ׳; הסוף רחוק מרכבל ומסלול |
| u475335391 | red | 158 | 4 | תקין |
| u475335393 | blue | 173 | 0 | תקין |
| u476055692 | red | 162 | 7 | תקין |
| u476055696 | blue | 80 | 0 | תקין |
| u513412259 | red | 163 | 1 | תקין |
| u673429273 | blue | 39 | 0 | תקין |
| u673429274 | blue | 83 | 0 | תקין |
| u673609556 | red | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u673609564 | black | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u673609570 | red | 39 | 0 | תקין |
| u673609572 | blue | 32 | 0 | תקין |
| u673609577 | red | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u673630271 | blue | 5 | 0 | תקין |
| u673630272 | blue | 10 | 0 | תקין |
| u673630273 | blue | 11 | 0 | תקין |
| u673630274 | blue | 112 | 0 | תקין |
| u673630277 | blue | 105 | 1 | תקין |
| u674493145 | blue | 117 | 0 | תקין |
| u688015899 | blue | 99 | 3 | תקין |
| u688999697 | blue | 366 | 2 | תקין |
| u689987513 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u690153112 | black | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u690155965 | red | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u690155969 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u690193670 | blue | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול |
| u690203995 | red | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u690212768 | blue | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u690212769 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול |
| u690413212 | red | 0 | 0 | תקין |
| u690413213 | black | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u690465604 | red | 23 | 0 | תקין |
| u690477113 | black | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול |
| u690521795 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול |
| u690523951 | red | 334 | 5 | תקין |
| u690523953 | red | 340 | 13 | עלייה נגדית 13 מ׳ |
| u690560430 | red | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u690580214 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u691055228 | blue | 42 | 0 | תקין |
| u692472711 | blue | 42 | 0 | תקין |
| u693108745 | blue | 7 | 0 | תקין |
| u799067931 | blue | 558 | 0 | תקין |
| u919755850 | blue | 24 | 0 | תקין |
| u920996107 | blue | 39 | 0 | תקין |
| u923006212 | red | 168 | 0 | תקין |
| u923006220 | blue | 812 | 0 | תקין |
| u923006225 | blue | 26 | 0 | תקין |
| u923054910 | blue | 130 | 0 | תקין |
| u958896639 | blue | 20 | 0 | תקין |
| u1017390945 | red | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u1038197323 | red | 81 | 4 | תקין |
| u1038344810 | red | 156 | 0 | תקין |
| u1107322383 | blue | 44 | 0 | תקין |
| u1116902929 | blue | 121 | 1 | תקין |
| u1125405615 | red | 38 | 0 | תקין |
| u1125517487 | blue | 34 | 0 | תקין |
| u1131551145 | red | 37 | 0 | תקין |
| u1137219286 | blue | 22 | 0 | תקין |
| u1149687831 | red | 31 | 0 | תקין |
| u1153962850 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול |
| u1153962851 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u1155605672 | red | 17 | 0 | תקין |
| u1210291742 | blue | 95 | 0 | תקין |
| u1222219834 | red | 453 | 1 | תקין |
| u1224058390 | blue | 54 | 0 | תקין |
| u1225012919 | red | 311 | 6 | תקין |
| u1237766482 | blue | 381 | 0 | תקין |
| u1240699923 | black | 0 | 0 | הסוף רחוק מרכבל ומסלול |
| u1249133105 | blue | 57 | 0 | תקין |
| u1249541237 | black | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול |
| u1250367702 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u1257788059 | black | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול |
| u1318120773 | blue | 407 | 1 | תקין |
| u1320601208 | red | 61 | 5 | תקין |
| u1320607280 | blue | 195 | 0 | תקין |
| u1337112297 | red | 191 | 0 | תקין |
| u1337115695 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u1337115696 | blue | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u1337115698 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול |
| u1337115784 | red | 0 | 0 | תקין |
| u1337140557 | red | 205 | 0 | תקין |
| u1337140558 | red | 0 | 0 | ההתחלה רחוקה מרכבל ומסלול; הסוף רחוק מרכבל ומסלול |
| u1361973851 | red | 348 | 6 | הסוף רחוק מרכבל ומסלול |
| u1365100229 | blue | 113 | 4 | תקין |
| u1383729389 | blue | 223 | 0 | תקין |
| u1417548454 | blue | 248 | 3 | תקין |
| u1417549593 | blue | 144 | 0 | תקין |
| u1417549595 | blue | 29 | 0 | תקין |
| u1417549596 | blue | 16 | 0 | תקין |
| u1417549761 | blue | 81 | 0 | תקין |
| u1417551117 | blue | 159 | 0 | תקין |
| u1453352855 | blue | 56 | 0 | תקין |
| u1453352856 | blue | 43 | 0 | תקין |
| u1479527777 | red | 100 | 4 | תקין |
| u1479527778 | red | 507 | 0 | הסוף רחוק מרכבל ומסלול |
| u1479527779 | blue | 259 | 0 | הסוף רחוק מרכבל ומסלול |
| u1479527780 | blue | 874 | 4 | ההתחלה רחוקה מרכבל ומסלול |
| u1479527781 | blue | 290 | 2 | תקין |
| u1479530988 | red | 902 | 0 | תקין |
| u1483654814 | red | 276 | 2 | תקין |
| u1483731989 | red | 36 | 0 | תקין |
| u1483732004 | blue | 39 | 1 | תקין |
| u1484632008 | blue | 9 | 0 | תקין |
| u1488051359 | blue | 60 | 1 | תקין |
| u1504248401 | blue | 81 | 1 | תקין |
| u1504248402 | blue | 50 | 2 | תקין |
| u1504252890 | blue | 135 | 0 | תקין |
| u1504498352 | blue | 219 | 0 | תקין |

## לא נכנסו

- Monte Pana - Saltria: not a downhill run (nordic)
- Palusc, La Buja: not a downhill run (nordic)
- Setsas, Störes, Conturines, 5-6-7: not a downhill run (nordic)
- Saré, Gran Ancëi, Scotoni, Setsas, Störes, Conturines, 2-3-4-5-6-7: not a downhill run (nordic)
- Scotoni, Setsas, Störes, Conturines, 4-5-6-7: not a downhill run (nordic)
- (בלי שם): not a downhill run (snow_park)
- Val Scura: not a downhill run (nordic)
- Palusc, La Buja, Val Scura: not a downhill run (nordic)
- (בלי שם): not a downhill run (snow_park)
- Zona fondo Corvara, Langlaufzentrum Corvara: not a downhill run (nordic)
- Pra da Rì, Capela, Droc, Borbes: not a downhill run (nordic)
- Val Scura: not a downhill run (nordic)
- (בלי שם): no difficulty in OSM, so no colour
- Sprint, 8: not a downhill run (nordic)
- Sass Dlacia, 1: not a downhill run (nordic)
- (בלי שם): not a downhill run (snow_park)
- (בלי שם): no difficulty in OSM, so no colour
- (בלי שם): no difficulty in OSM, so no colour
- Capela, Borbes: not a downhill run (nordic)
- (בלי שם): not a downhill run (snow_park)
- Mont de Sot: not a downhill run (nordic)
- Mont de Sot, Vedl Verzon: not a downhill run (nordic)
- Campo scuola: not a downhill run (nordic)
- Vedl Verzon, Val Scura: not a downhill run (nordic)
- (בלי שם): no difficulty in OSM, so no colour
- Funcross Biok / La Para: not a downhill run (snow_park)
- La Buja, Val Scura: not a downhill run (nordic)
- Valtoi Joyride: not a downhill run (snow_park)
- Saré, Gran Ancëi, Scotoni, Setsas, Störes, Conturines, 2-3-4-5-6-7: not a downhill run (nordic)
- Zona fondo Corvara, Langlaufzentrum Corvara: not a downhill run (nordic)
- Saré, Scotoni, Setsas, Störes, Conturines, 2-4-5-6-7: not a downhill run (nordic)
- (בלי שם): not a downhill run (snow_park)
- (בלי שם): not a downhill run (nordic)
- Gran Ancëi, Scotoni, Setsas, Störes, Conturines, 3-4-5-6-7: not a downhill run (nordic)
- Gardenissima: the course of a race (Gardenissima), drawn over runs that are on the map already; not on the resort's list
- Sprint, Saré, Gran Ancëi, Scotoni, Setsas, Störes, Conturines, 2-3-4-5-6-7-8: not a downhill run (nordic)
- Zona fondo Corvara, Langlaufzentrum Corvara: not a downhill run (nordic)
- La Buja: not a downhill run (nordic)
- Pra da Rì: not a downhill run (nordic)
- (בלי שם): not a downhill run (snow_park)
- Sprint, Saré, 2: not a downhill run (nordic)
- Gran Ancëi, 3: not a downhill run (nordic)
- Saré, Gran Ancëi, Scotoni, Setsas, Störes, Conturines, 2-3-4-5-6-7: not a downhill run (nordic)
- (בלי שם): not a downhill run (nordic)
- Sprint, Saré, Gran Ancëi, Scotoni, Setsas, Störes, Conturines, 2-3-4-5-6-7-8: not a downhill run (nordic)
- Sprint, Scotoni, Setsas, Störes, Conturines, 4-5-6-7-8: not a downhill run (nordic)
- Campo scuola: not a downhill run (nordic)
- Sprint, Gran Ancëi, Scotoni, Setsas, Störes, Conturines, 4-5-6-7-8: not a downhill run (nordic)
- Saré, Gran Ancëi, 2-3: not a downhill run (nordic)
- Palusc, La Buja: not a downhill run (nordic)
- Droc, Borbes: not a downhill run (nordic)
- Mont de Sot, Vedl Verzon, Val Scura: not a downhill run (nordic)
- (בלי שם): no difficulty in OSM, so no colour
- Pra da Rì, Droc: not a downhill run (nordic)
- La Buja, Val Scura: not a downhill run (nordic)
- Conturines, 7: not a downhill run (nordic)
- Capela: not a downhill run (nordic)
- Snow and fun park "Piz Sella": not a downhill run (snow_park)
- Scotoni, Setsas, Störes, Conturines, 4-5-6-7: not a downhill run (nordic)
- Zona fondo Corvara, Langlaufzentrum Corvara: not a downhill run (nordic)
- Mont de Sot, Vedl Verzon, Val Scura: not a downhill run (nordic)
- La Buja, Val Scura: not a downhill run (nordic)
- Mont de Sot, Vedl Verzon, Palusc, La Buja, Val Scura: not a downhill run (nordic)
- Setsas, 5: not a downhill run (nordic)
- Campo scuola: not a downhill run (nordic)
- Droc: not a downhill run (nordic)
- Palusc, Val Scura: not a downhill run (nordic)
- (בלי שם): not a downhill run (nordic)
- Pra da Rì, Droc, Borbes: not a downhill run (nordic)
- Sprint, 8: not a downhill run (nordic)
- Campo scuola, 9: not a downhill run (nordic)
- (בלי שם): not a downhill run (nordic)
- Foxy Kids Park: not a downhill run (snow_park)
- Pra da Rì, Droc, Borbes: not a downhill run (nordic)
- Capela: not a downhill run (nordic)
- Saré, Gran Ancëi, Scotoni, Setsas, Störes, Conturines, Campo scuola, 2-3-4-5-6-7: not a downhill run (nordic)
- Pra da Rì, Droc: not a downhill run (nordic)
- Störes, 6: not a downhill run (nordic)
- Saré, 2: not a downhill run (nordic)
- (בלי שם): no difficulty in OSM, so no colour
- Vedl Verzon, Palusc, La Buja, Val Scura: not a downhill run (nordic)
- Saré, Gran Ancëi, Scotoni, Setsas, Störes, Conturines, 2-3-4-5-6-7: not a downhill run (nordic)
- Funcross Biok / La Para: not a downhill run (snow_park)
- Funzone Edelweiss: not a downhill run (snow_park)
- Campo scuola, 9: not a downhill run (nordic)
- Pra da Rì: not a downhill run (nordic)
- (בלי שם): not a downhill run (nordic)
- Störes, Conturines, 6-7: not a downhill run (nordic)
- Val Scura: not a downhill run (nordic)
- Droc, Borbes: not a downhill run (nordic)
- (בלי שם): not a downhill run (snow_park)
- Mont de Sot, Palusc, La Buja: not a downhill run (nordic)
- Pra da Rì, Droc: not a downhill run (nordic)
- Snowpark Alta Badia: not a downhill run (snow_park)
- Campo scuola: not a downhill run (nordic)
- (בלי שם): not a downhill run (nordic)
- Droc: not a downhill run (nordic)
- Palusc, La Buja: not a downhill run (nordic)
- Saré, 2: not a downhill run (nordic)
- Pra da Rì: not a downhill run (nordic)
- (בלי שם): not a downhill run (nordic)
- Pra da Rì: not a downhill run (nordic)
- Palusc, La Buja, Val Scura: not a downhill run (nordic)
- Palusc: not a downhill run (nordic)
- Setsas, Störes, Conturines, 5-6-7: not a downhill run (nordic)
- Zona fondo Corvara, Langlaufzentrum Corvara: not a downhill run (nordic)
- Pra da Rì, Capela, Droc, Borbes: not a downhill run (nordic)
- (בלי שם): not a downhill run (snow_park)
- Borbes: not a downhill run (nordic)
- Vedl Verzon, Val Scura: not a downhill run (nordic)
- Störes, Conturines, 6-7: not a downhill run (nordic)
- Saré, Gran Ancëi, Scotoni, 2-3-4: not a downhill run (nordic)
- Saré, Gran Ancëi, 2-3: not a downhill run (nordic)
- (בלי שם): not a downhill run (snow_park)
- Campo scuola: not a downhill run (nordic)
- Sprint, 8: not a downhill run (nordic)

## סבבים במפה הפתוחה (לא מצוירים)

קווים שהם רק הסבב עצמו (relation בלי דרך משלו) לא נכנסו, כי המסלולים שמתחתיהם כבר במפה. שמות הסבבים הוסרו משמות המסלולים.

- **Gebirgsjäger - Grande Guerra CCW, Gebirgsjäger-Skitour entgegen dem Uhrzeigersinn, First World War ski tour counterclockwise, Grande Guerra in senso antiorario:** 24 מסלולים עליו: (no name), Arlara 1, Armentarola, Armentarola - Piz Sorega, Bec de Roces, Boe - Campolongo, Boé, Braia Fraida - Arlara, Collegamento Piz Arlara - Costes da l'Ega, Costes da l'Ega, Costoratta, Gran Risa, La Brancia 14, La Fraina, Mesola, Ornella, Padon, Padon A, Piz Sorega, Piz Sorega B, Pordoi, Pre dai Corf, Rientro, Savinè
- **Gebirgsjäger - Grande Guerra CW, Gebirgsjäger-Skitour im Uhrzeigersinn, First World War ski tour clockwise, Grande Guerra in senso orario:** 21 מסלולים עליו: (no name), Antercrëp, Armentarola, Avoie, Boè, Campolongo, Capanna Nera, Ciampai, Ciampai - La Fraina, Col Alt - Braia Fraida, Col Alt 4, Costes da l'Ega, Due Baite I, La Brancia 14, Ornella, Piz Sorega B, Piz Sorega B - Armentarola, Plan Boè, Sass de la Vegla, Skilift Armentarola, Sourasass
- **Sellaronda Arancione alternativa, Sellaronda orange Alternative:** 9 מסלולים עליו: (no name), Campo Freina, Ciampinoi 3, Ciampinoi 4, Comici II, Piz Sella 5, Piz Seteur - Comici, Saslong B, Sef
- **Sellaronda Arancione, Sellaronda orange:** 30 מסלולים עליו: (no name), Alpenrose, Boe - Campolongo, Boé, Città dei Sassi, Collegamento Ciampinoi - Costabella, Costabella, Costoratta, Falk, Frara, Frara Sodlisia, Frara Variante, Frea, Gardena, Maria, Martinelli, Ornella, Pian Frataces, Piz Sella 5, Pordoi, Portados, Raccordo Salere - Alpenrose, Rientro, Salere, Sas Betit, Sass Becè, Savinè, Schiappen-Sella, Sole, Spacca Ossi / Funtanes
- **Sellaronda Verde, Sellaronda grün:** 30 מסלולים עליו: (no name), 3-Tre, Alpenrose, Arabba, Avoie, Belvedere I, Boè, Boé, Campolongo, Ciampinoi - Piza Pranseies, Ciampinoi 5, Cinque Dita, Città dei Sassi, Collegamento Sodlisia - Frara, Costabella, Dantercepies, Dantercepies - Selva, Due Baite I, Frara - Val Setus, Martinelli, Pian Frataces, Piz Sella 5, Piz Seteur - Città dei Sassi, Piz Seteur - Gran Paradiso, Plan Boè, Plan de Gralba, Pordoi, Sass Becè, Sodlisia, Val Setus
- **Super8:** 1 מסלולים עליו: Armentarola
- קווים של הסבב בלבד שלא נכנסו: 49

## מול הרשימה הרשמית

- **ברשימות:** 243 מסלולים בתחום, ועוד 2 מחוץ לתחום (Lagazuoi, Armentarola).
- **יש להם קו:** 206. **אין להם קו במפה הפתוחה** (ברשימת החסרים, לא מצוירים): 37.
- **צבע שונה במפה הפתוחה, ותוקן לפי הרשמי:** Col Raiser - Plan Da Tieja, Gran Paradiso - Piz Seteur, Sef, Seggiovia Pralongiá, Charly, Connecting slope Gran Paradiso - Sotsasslong, Dantercepies, Piz Sella 5, Pordoi, Seggiovia Pralongià.
- **מסלולים אצלנו שאינם ברשימה הרשמית** (הצבע מהמפה הפתוחה): Alpenrose, Armentarola, Avoie, Collegamento Boè - Vallon, Collegamento Piz Arlara - Costes da l'Ega, Collegamento Pralongià II - Pralongià I, Costabella Variante, Dantercepies Variante, Due Baite, Due Baite I, Forcella Incisia, Kids Fun Line La Crusc, Mesola, Piz Sorega, Piz Sorega - Biok, Plan Boè, Rientro, Rutort, Sass de la Vegla, Sef - Gran Paradiso, Skilift Armentarola, Variante Avoie, Antercrëp, Bec de Roces, Campolongo, Collegamento Carpazza, Collegamento Ciampinoi, Fungeia, Ornella, Padon, Padon A, Piz Seteur - Sotsaslong, Portados, Raccordo 1, Raccordo Salere - Alpenrose, Rodella 3 / Tre, Salere, Savinè, Val Setus - Plans, Variante Bec, Verbindung Saslong-Sesselbahn Monte Pana, Burz, Fodoma, Gardenaccia nera, Saslong A, Sourasass, Tremor, Variante Ornella, Verbindung Monte Pana-Saslong.

### חסרים

- Connection Slope Costabella (blue, Val Gardena: Selva - Dantercepies)
- Dantercepies (blue, Val Gardena: Selva - Dantercepies)
- Val (blue, Val Gardena: Selva - Dantercepies)
- Val Setus - Cir (blue, Val Gardena: Selva - Dantercepies)
- Dantercepies-connection (red, Val Gardena: Selva - Dantercepies)
- Dantercepies-connection (red, Val Gardena: Selva - Dantercepies)
- connection slope - risaccia dantercepies (blue, Val Gardena: Selva - Dantercepies)
- Sef (red, Val Gardena: Selva - Plan de Gralba)
- Piz Sella 5 (black, Val Gardena: Selva - Plan de Gralba)
- Connection Gran Paradiso - Città dei Sassi (blue, Val Gardena: Selva - Plan de Gralba)
- Passo Sella (red, Val Gardena: Selva - Passo Sella)
- Sasso Levante (red, Val Gardena: Selva - Passo Sella)
- Daniela (red, Val Gardena: Selva - Passo Sella)
- Pradel-Salei-Grohmann (red, Val Gardena: Selva - Passo Sella)
- Piz Seteur-Plan de Gralba (red, Val Gardena: Selva - Passo Sella)
- Ciampinoi 5 - variant (red, Val Gardena: S. Cristina - Ciampinoi - Selva)
- Piza Pranseies - Ciampinoi (red, Val Gardena: S. Cristina - Ciampinoi - Selva)
- Connection Saslong - Sochers (red, Val Gardena: S. Cristina - Ciampinoi - Selva)
- Connection Freina - Costabella (red, Val Gardena: S. Cristina - Ciampinoi - Selva)
- Boé variante (red, Alta Badia)
- Col Alt 5 (blue, Alta Badia)
- Connection Borest - Costes da l'Ega (blue, Alta Badia)
- Collegamento Capanna Nera (blue, Alta Badia)
- Collegamento Boé - Costes da l'Ega (blue, Alta Badia)
- Connection slope Piz Arlara - Costes da l'Ega (blue, Alta Badia)
- Braia Fraida intermedia - Roby (blue, Alta Badia)
- Bordercross La Para (blue, Alta Badia)
- Pralongiá - San Cassiano (blue, Alta Badia)
- Codes (blue, Alta Badia)
- Baby La Crusc (blue, Alta Badia)
- Baby La Crusc - La Crusc 1 (blue, Alta Badia)
- La Crusc 1 - Baby La Crusc (blue, Alta Badia)
- Val Setus - Cir (blue, Alta Badia)
- Variante Gare (red, Belvedere - Col Rodella (Canazei))
- Col de Lin (blue, Belvedere - Col Rodella (Canazei))
- Campo scuola Col da Fae' (blue, Belvedere - Col Rodella (Canazei))
- Avisio Fraina (blue, Belvedere - Col Rodella (Canazei))
