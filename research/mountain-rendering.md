# איך מציירים הר: מחקר קצר (19.11.2026)

למה: פיני ביקש לחשוב מאפס על איך ההר נראה ואיך רואים עליו מסלול (סבב "ההר" בעיצוב מחדש, `design/redesign/README.md`).
**מגבלה:** הכל מתקצירי חיפוש. הדפים עצמם לא נקראו, והאפליקציות לא הותקנו ולא נבדקו.

## טכניקות

| טכניקה | מה היא נותנת | מה לקחנו | מקור |
|---|---|---|---|
| מפות סקי מצוירות (James Niehues) | מבט אחד "מכופף" שמראה את כל ההר; מסלולים רחבים מצוירים רחבים; צילום מהאוויר ומנקודת המבט של הגולש לפני הציור | מבט אחד שנבחר מראש לכל צד של ההר. **לא לקחנו** את העיוות הידני: אצלנו ההטלה מדויקת | [freeskier.com](https://freeskier.com/stories/james-niehues-profile), [foxweather.com](https://www.foxweather.com/lifestyle/james-niehues-reflects-on-his-legacy-of-hand-painting-ski-maps), [atlasobscura.com](https://atlasobscura.com/articles/meet-the-artist-mapping-ski-slopes-around-the-world), [sugarbush.com](https://blog.sugarbush.com/magazine/mapper-of-mountains/) |
| הסגנון השוויצרי (Eduard Imhof) | הצללה בצבע: גוונים חמים באור, סגולים בעמקים ובצל; "פרספקטיבה אווירית": ניגוד חזק בגבוה וחלש בנמוך, כמו אובך | אור חם, צל כחול-סגול, אובך במרחק ובעמקים | [Terrain cartography, ויקיפדיה](https://en.wikipedia.org/wiki/Terrain_cartography), [Cartographic Relief Shading with Neural Networks](https://arxiv.org/pdf/2010.01256) |
| קווי גובה מוארים (Kitiro Tanaka) | קו גובה בהיר בצד המואר וכהה בצד המוצל | הבסיס למראה "דגם שכבות" | אותו ערך בוויקיפדיה |
| Plan oblique relief (Tom Patterson, Bernhard Jenny, 2007) | תבליט שבו הפיקסלים מוזזים צפונה לפי הגובה: להר יש גוף, אבל אין קיצור מרחק ואין נקודת מגוז, ולכן הצורות נשמרות | מבט מקביל בלי פרספקטיבה, שבו כל פיקסל הוא מקום ידוע | [Cartographic Perspectives 57](https://cartographicperspectives.org/index.php/journal/article/download/cp57-jenny-patterson/pdf/1605), [shadedrelief.com](https://www.shadedrelief.com/bio.html) |
| תבליט מחושב מראש ב-Blender (Daniel Huffman) | מורידים מודל גובה, מחשבים הצללה איטית ואיכותית פעם אחת, ומשלבים עם צבע | לחשב את ההר פעם אחת ולא בכל פריים. אצלנו בכלי קטן משלנו, בלי Blender | [הפוסט המקורי](https://somethingaboutmaps.wordpress.com/2017/11/16/creating-shaded-relief-in-blender/) (לא נקרא ישירות), [stamen.com](https://stamen.com/shadows-on-maps-are-getting-a-lot-more-exciting-and-heres-why/), [axismaps.com](https://axismaps.com/blog/relief-mapping-in-10-easy-steps) |

## אפליקציות (לפי התיאור בחנות בלבד)

| אפליקציה | מה כתוב | מקור |
|---|---|---|
| Slopes | מפות אתרים אינטראקטיביות בדו-ממד ובתלת-ממד, לכל אתר | [App Store](https://apps.apple.com/app/slopes/id643351983) |
| Slopie: 3D Ski Map | יותר מ-4,000 אתרים ב-69 מדינות | [App Store](https://apps.apple.com/app/id6790541552) |
| Ski Resort 3D | 470 אתרים כטיסה בתלת-ממד | [App Store](https://apps.apple.com/ml/app/id6781581024) |
| GoSki | מפות תלת-ממד, מצב רכבלים ותחזית; מפות מפורטות רק לכמה אתרים | [App Store](https://apps.apple.com/app/id6759206548) |
| We Ski | מפות תלת-ממד וזמני המתנה, יותר מ-2,000 אתרים | [App Store](https://apps.apple.com/app/id1541405311) |
| Outmap | ממשיך של FATMAP לסקי הרים (בעיקר ברשת ובמחשב) | [outmap.pro](https://outmap.pro/llms.txt) |

**מה שעולה מזה (פרשנות שלי, לא נבדק בשימוש):** כולן מציעות תלת-ממד כללי להרבה אתרים. לא מצאתי אפליקציה שמתוארת כמציירת הר אחד באיכות של איור. לא חיפשתי את גודאורי בתוכן.

## מה נבנה בעקבות המחקר

`design/redesign/tools/mountain.py`: מצייר את ההר ממודל הגובה שבריפו במבט מקביל (אקסונומטרי), עם אור מעוצב, צל מוטל, סלע לפי שיפוע, קווי גובה ואובך; ומעליו את המסלולים והרכבלים באותה נוסחת הטלה, כולל מה שמוסתר מאחורי רכס. בלי הגזמת גובה ובלי ספרייה חיצונית מעבר ל-numpy ו-Pillow.
