# Ski resorts: published size, season, live status, apps, trail maps

All facts accessed **2026-10-06**. "SR" = skiresort.info resort page (`https://www.skiresort.info/ski-resort/<slug>/`); its "Ski slopes Total" and "Ski lifts Total" boxes were read directly from the HTML. "unverified" = could not confirm from a source; nothing is guessed.

App ratings: App Store values from the iTunes Search/Lookup API (`https://itunes.apple.com/lookup?id=<id>&country=<cc>`); Google Play values from the store page with `gl=<country>` (Play hides the rating in countries with few ratings, so the resort's home country was used).

Trail maps: skiresort.info has a trail-map page (`.../<slug>/trail-map/`) with an embedded map for **every** resort below (checked 2026-10-06). The resort's own map link is listed where one was found.

---

## 1. Gudauri (Georgia), baseline

- **Published size:**
  - SR: **34.8 km** slopes (4.6 easy / 19.4 intermediate / 10.8 difficult), **17 lifts** (+3 additional), elevation 1993–3276 m. https://www.skiresort.info/ski-resort/gudauri/ (2026-10-06)
  - Georgian National Tourism Administration (government): "**eighteen** working ski lifts", "total length of all the packed-snow slopes at Gudauri is **37 kilometers**". https://georgia.travel/resorts/gudauri (2026-10-06)
  - Operator MTA (mta.ski) gives no km or lift count for Gudauri. https://mta.ski/en (2026-10-06)
- **Season:** SR general season "late December – late April"; 2026/27 dates 2026-12-19 to 2027-04-18. https://www.skiresort.info/ski-resort/gudauri/ (2026-10-06)

## 2. Bakuriani (Georgia): Didveli, Kokhta-Mitarbi

- **Published size:**
  - Operator MTA: "With **12 ski lifts and 33 km** of trails… three distinct skiing areas: Didveli, Kokhta, and Mitarbi." https://mta.ski/en (2026-10-06)
  - SR (one combined resort; lifts include "Didveli Gondola", "Kokhta Plato/Gora", "Mitarbi I"): **29 km** (2.3 / 13.2 / 13.5), **20 lifts** (+2 additional), 1641–2702 m. https://www.skiresort.info/ski-resort/bakuriani/ (2026-10-06)
  - SR has no separate pages for Didveli or Kokhta-Mitarbi (those slugs return the generic portal page).
- **Season:** MTA: "winter season, which runs from December to March". SR general: "mid December – mid April"; 2026/27: 2026-12-19 to 2027-03-29. (same URLs)
- **Live status:** operator page **https://status.mta.ski/bakuriani/didveli** (linked from mta.ski). It is a Next.js web page rendered on the server (HTML). The browser code calls no public JSON endpoint; the data is fetched server-side. Today it returns **HTTP 500** (off-season, the same behaviour as the Gudauri page). Per-item content could not be checked now.
- **App:** shared Georgian ski-pass app (see "Georgia: apps" below). No Bakuriani-specific official app found.
- **Trail map:** yes, SR https://www.skiresort.info/ski-resort/bakuriani/trail-map/. No operator map URL found on mta.ski.

## 3. Tetnuldi and Hatsvali (Svaneti / Mestia, Georgia)

- **Published size:**
  - Operator MTA (Mestia = Hatsvali + Tetnuldi combined): "a total of **37 km** of trails and **14 ski lifts**". https://mta.ski/en (2026-10-06)
  - SR Tetnuldi: **13.7 km** (5.3 / 6.4 / 2), **5 lifts**, 2265–3160 m. https://www.skiresort.info/ski-resort/tetnuldi/ (2026-10-06)
  - SR Hatsvali: **7 km** (1.4 / 2.6 / 3), **4 lifts**, 1868–2348 m. https://www.skiresort.info/ski-resort/hatsvali-mestia/ (2026-10-06)
  - Note: the SR sum (20.7 km, 9 lifts) is well below MTA's 37 km / 14 lifts.
- **Season:** MTA: "winter season, running from December to April". SR Tetnuldi: "late December – mid April" (2026-12-23 to 2027-04-11). SR Hatsvali: "late December – early April" (2026-12-23 to 2027-03-29).
- **Live status:** operator page **https://status.mta.ski/svaneti/mestia** (linked from mta.ski). Same server-rendered HTML; **HTTP 500** today.
- **App:** see "Georgia: apps".
- **Trail map:** yes, SR https://www.skiresort.info/ski-resort/tetnuldi/trail-map/ and https://www.skiresort.info/ski-resort/hatsvali-mestia/trail-map/

## 4. Goderdzi (Georgia)

- **Published size:**
  - Operator MTA: "**8 km** of trails served by **3 ski lifts**". https://mta.ski/en (2026-10-06)
  - SR: **7.2 km** (4.9 / 2.3 / 0), **3 lifts**, 1724–2366 m. https://www.skiresort.info/ski-resort/goderdzi/ (2026-10-06)
- **Season:** MTA: "season running from December to April". SR: "mid December – early April" (2026-12-19 to 2027-04-04).
- **Live status:** operator page **https://status.mta.ski/goderdzi/** (linked from mta.ski). Server-rendered HTML; **HTTP 500** today.
- **App:** see "Georgia: apps".
- **Trail map:** yes, SR https://www.skiresort.info/ski-resort/goderdzi/trail-map/

### Georgia: apps (all four MTA resorts)

- **SKIPASS – Gudauri & Beyond** (developer LEMONDO BUSINESS LLC). Description: "Enjoy seamless access to Gudauri, Bakuriani, Goderdzi, and Svaneti ski lifts. Top up your existing ski pass or order a new one."
  - App Store (GE): **4.76 from 317 ratings**. https://apps.apple.com/ge/app/skipass-gudauri-beyond/id6698866856 (2026-10-06)
  - Google Play (GE): **4.6, 155 reviews**, 5K+ downloads, updated Jan 21, 2026. https://play.google.com/store/apps/details?id=com.lemondo.skipass (2026-10-06)
  - **Whether MTA endorses it is unverified:** mta.ski does not link it, and no source naming it as MTA's official app was found. Secondary sources say MTA offers an app for the multi-card, but none names it (https://biletebi.ge/en/sabagiro, 2026-10-06).
- Do not confuse it with **"MTA MountainApp"** (SPOTTERON GmbH): a University of Zurich / Tbilisi State University landscape research app, not a ski app. https://play.google.com/store/apps/details?id=com.spotteron.mountainapp (100+ downloads).
- **No official app with per-lift status was found for the Georgian resorts.**

## 5. Bansko (Bulgaria)

- **Published size:**
  - SR: **75 km** (44 / 25 / 6), **14 lifts** (+1 and +2 additional), 990–2530 m. https://www.skiresort.info/ski-resort/bansko/ (2026-10-06)
  - Official site (banskoski.com, "The official website of ski in Bansko"): the lift table lists **15 installations** (gondola, 8 chairlifts, drags and baby lifts; "Tzarna mogila – Closed") and 21 numbered runs, with **no total km stated**. https://banskoski.com/en/page/lifts-and-ski-slopes (2026-10-06)
  - "75 km… 13 ski lifts" appears on skibansko.bg (via search; not confirmed as official, and its status page returned 404).
- **Season:** SR "early December – mid April"; 2026/27: 2026-12-12 to 2027-04-11. Official hours on banskoski.com: gondola 08:30–17:00, lifts 08:45–16:15.
- **Live status:** **yes. The homepage https://banskoski.com/en is an HTML page** with every lift and every slope listed, each with a `class="status"` marker (31 status elements). No public JSON found.
- **App:** **Bansko Ski** (OpenLink Software Bulgaria AD). The Play description says "The official mobile application of Bansko Ski Zone… information about working lifts and slopes, purchase ski-passes"; banskoski.com links to it.
  - Google Play (BG): **4.2, 289 reviews**, 50K+ downloads. https://play.google.com/store/apps/details?id=bg.openlinksw.banskoski
  - App Store (BG): **2.77 from 13 ratings**. https://apps.apple.com/bg/app/bansko-ski/id1066328168
- **Trail map:** yes, official https://banskoski.com/en/map and SR https://www.skiresort.info/ski-resort/bansko/trail-map/

## 6. Borovets (Bulgaria)

- **Published size:**
  - SR: **58 km** (24 / 29 / 5), **13 lifts** (+1 additional), 1315–2550 m. https://www.skiresort.info/ski-resort/borovets/ (2026-10-06)
  - Official site status widget: "**0/12** Open lifts" and "**0/31** Open slopes" (counts of the listed items). https://www.borovets-bg.com/en (2026-10-06). No official km total found.
- **Season:** SR "mid December – mid April"; 2026/27: 2026-12-19 to 2027-04-11.
- **Live status:** **yes, HTML pages per item:** https://www.borovets-bg.com/en/information/lifts-status and https://www.borovets-bg.com/en/information/slopes-status (Open / Closed / Partly open, "Open only" filter, "View on map"). Today every item reads "Closed for the summer". No public JSON found.
- **App:** **My Borovets** (OpenLink Software Bulgaria AD). Play description: "The official mobile application of Borovets mountain resort… Lifts & trails status".
  - Google Play (BG): **3.9, 156 reviews**, 50K+. https://play.google.com/store/apps/details?id=bg.openlinksw.myborovets
  - App Store (BG): **3.76 from 17 ratings**. https://apps.apple.com/bg/app/my-borovets/id1448875074
- **Trail map:** yes, official https://www.borovets-bg.com/en/information/maps (image `/files/richeditor/maps/zimna-karta-2026.jpg`) and SR.

## 7. Pamporovo (Bulgaria)

- **Published size:** SR: **29.8 km** (17.8 / 7.6 / 4.4), **13 lifts**, 1473–1926 m. https://www.skiresort.info/ski-resort/pamporovo/ (2026-10-06). Official figures **unverified**: pamporovo.me is a JavaScript single-page app with no text in the HTML.
- **Season:** SR "early December – mid April"; 2026/27: 2026-12-19 to 2027-04-11.
- **Live status:** **not found.** pamporovo.me has routes for `/map` and `/ski-zones/…` (from its routing file https://pamporovo.me/assets/routing-mock.json), but no per-item status could be confirmed.
- **App:** **not found** (searched Google Play and the App Store for "Pamporovo" and "Пампорово").
- **Trail map:** yes, SR https://www.skiresort.info/ski-resort/pamporovo/trail-map/ (the official site has a `/map` route; content not verified).

## 8. Mount Hermon ski site (Israel)

- **Published size:**
  - SR: **25 km** (3 / 20 / 2), **10 lifts**, 1600–2040 m. https://www.skiresort.info/ski-resort/mount-hermon-neve-ativ/ (2026-10-06)
  - Official skihermon.co.il (winter page): "באתר קיימים **9** רכבלים, מעליות וטי-ברים לשימוש הגולשים" ("the site has 9 cable cars, chairlifts and T-bars for skiers"). https://skihermon.co.il/חורף-בחרמון/ (2026-10-06). Official km **unverified**.
- **Season:** SR "mid December – mid March". The official page says the site opens by weather; hours 08:00–16:00, last chairlift ride 15:20.
- **Live status:** **yes, an HTML section** "מסלולי גלישה ורכבלים" (runs and lifts) on https://skihermon.co.il/גולשים/#routes, timestamped ("מעודכן לתאריך 23/04/2026 בשעה 08:00", i.e. updated 23/04/2026 at 08:00). It lists items such as ski-school lift, chairlift 2, Wadi Hatul, the Moadon, Si'on, Ar'ar and Shaked runs, the Si'on T-bar, the Si'on chairlift and the snow park. No JSON found.
- **App:** **not found** (App Store IL search for "hermon" and "חרמון"; Google Play search).
- **Trail map:** yes, official https://skihermon.co.il/wp-content/uploads/2023/07/golshim-map.png and https://skihermon.co.il/wp-content/uploads/2023/09/winter-map-1-2.svg, plus SR.

## 9. Rosa Khutor (Russia)

- **Published size:** SR: **102 km** (62 / 24 / 16), **27 lifts** (+3 additional), 940–2509 m (base 560 m). https://www.skiresort.info/ski-resort/rosa-khutor/ (2026-10-06). The official site (rosakhutor.ru) shows an anti-bot page to scripts, so official totals are **unverified**. Secondary sources give 102–105 km and 32 lifts (tourdom.ru, via search).
- **Season:** SR "early December – early April".
- **Live status:** web page **unverified** (blocked). The official app's store description says "check lift status".
- **App:** **Rosa Khutor** (developer "Роза Хутор"), "Official app for Rosa Khutor Resort… check lift status".
  - Google Play (RU): **3.1, 51 reviews**, 10K+, updated Aug 20, 2026. https://play.google.com/store/apps/details?id=com.rosakhutor.mobile
  - App Store: **not found** (lookup by bundle ID returns nothing in the RU and US stores).
- **Trail map:** yes, SR https://www.skiresort.info/ski-resort/rosa-khutor/trail-map/

## 10. Sheregesh (Russia)

- **Published size:**
  - SR: **15 lifts**, 670–1270 m; **SR shows no slope km**. https://www.skiresort.info/ski-resort/sheregesh-kemerovo/ (2026-10-06)
  - sheregesh.ru (run by ООО ГЕШ ГРУПП; resort information and booking portal; official status unverified): "26 горнолыжных трасс" (26 runs), "19 подъемников" (19 lifts). https://sheregesh.ru/about. The single "Единый ски-пасс" (unified ski pass) "объединяет 19 подъемников разных операторов" (covers 19 lifts of different operators): https://sheregesh.ru/skipass. Total km **unverified**: secondary sources say ~35 km.
  - Note: several lift operators work here; there is no single resort operator.
- **Season:** sheregesh.ru homepage "Катаем с ноября" ("skiing from November"); the about page mentions snow until late April. SR season: unverified (not listed).
- **Live status:** **not found** on sheregesh.ru.
- **Apps (none confirmed as official):**
  - **Шерегеш Today** (IVAN RYGOVSKIY): App Store RU **4.88 from 629**, https://apps.apple.com/ru/app/id1073435286. Google Play "Sheregesh Today – guide" (Traveler Today), RU **4.6, 609 reviews**, 50K+, https://play.google.com/store/apps/details?id=today.traveler.sheregesh
  - **GeshGo: Весь Шерегеш в кармане** (LLC GESHGO): App Store RU **4.76 from 17**, https://apps.apple.com/ru/app/id1660538923
  - **OhMyGesh — Шерегеш** (OMG LLC): App Store RU **3.66 from 64**, https://apps.apple.com/ru/app/id6736955186
- **Trail map:** yes, SR https://www.skiresort.info/ski-resort/sheregesh-kemerovo/trail-map/

## 11. Elbrus (Russia)

- **Published size:**
  - Official resort-elbrus.ru homepage: "**17 км** горнолыжных трасс" (17 km of ski runs), "**3** канатные дороги" (3 cable cars). https://resort-elbrus.ru/ (2026-10-06)
  - SR (Mt. Elbrus): **23 km** (6 / 12 / 5), **6 lifts** (+1 additional), 2350–3847 m. https://www.skiresort.info/ski-resort/mt-elbrus/ (2026-10-06)
  - News sources say 23 km after 5.2 km were added (lenta.ru and tourdom.ru, via search). The figures conflict.
- **Season:** SR "early December – early June". Secondary: the 2025/26 season opened Nov 1 (via search, unverified).
- **Live status:** **yes, an HTML page** https://resort-elbrus.ru/tracks with "Статус канатных дорог" (cable car status: Азау–Кругозор, Кругозор–Мир, Мир–Гарабаши, Мир-2–Баштала, Баштала–Чиран, each Открыто/Закрыто [open/closed] with hours) and "Статус трасс" (run status, each run with Открыто/Закрыто, difficulty, length and vertical). The page is server-rendered from `https://admin.resort-elbrus.ru/api`; no public API documentation was found. Also https://resort-elbrus.ru/resortMap.
- **App:** **Эльбрус горнолыжный курорт** ("Elbrus ski resort"; SMART RESHENIYA OOO; seller URL resort-elbrus.ru; description: "Статусы трасс и канатных дорог", i.e. run and cable-car status).
  - App Store RU: **4.25 from 12 ratings**. https://apps.apple.com/ru/app/id6753877494
  - Google Play RU: **3.3, 7 reviews**, 1K+. https://play.google.com/store/apps/details?id=com.elbrus.mobile.app
- **Trail map:** yes, official https://resort-elbrus.ru/resortMap and SR.

## 12. Dombay (Russia)

- **Published size:**
  - SR: **20 km** (14 / 5.2 / 0.8), **13 lifts**, 1636–3168 m. https://www.skiresort.info/ski-resort/dombay/ (2026-10-06)
  - dombai-kd.ru ("Официальный сайт Домбая", the official Dombay site) lists runs 1–10. The eight runs with a length add up to **16.08 km** (my sum of the listed lengths; runs 9 and 10 have no length): https://dombai-kd.ru/karta-trass/. That site covers only the 3-stage gondola system (Домбай-1/2/3). Secondary sources say Dombay has several independent lift systems, each with its own ski pass (kupibilet.ru and others, via search).
- **Season:** SR "mid December – mid April".
- **Live status:** **yes, HTML.** https://dombai-kd.ru/ and https://dombai-kd.ru/kanatnye-dorogi-dombaya/ show Работает/Закрыто (open/closed) for each gondola stage. https://dombai-kd.ru/karta-trass/ shows Закрыто/Открыто (closed/open) per run. It covers the KD system only.
- **App:** **not found** (searched the App Store RU and Google Play for "Домбай").
- **Trail map:** yes, official https://dombai-kd.ru/karta-trass/ (image `/wp-content/uploads/2026/03/mapNool.jpg`) and SR.

## 13. Shymbulak (Kazakhstan)

- **Published size:** SR: **20 km** (4 / 10 / 6), **9 lifts**, 2260–3180 m (gondola base 1626 m). https://www.skiresort.info/ski-resort/chimbulak/ (2026-10-06). Official shymbulak.com is a JavaScript app with no text in the HTML, so official totals are **unverified**.
- **Season:** SR "mid November – mid April", hours 09:00–18:00.
- **Live status:** **not found on the website.** The site's routes are `/live`, `/weather` and so on; the `/live` meta text says "Live broadcasts from the ski slopes" (webcams); no lift-status route was found. The **app** description promises "о открытых подъемников и склонов" (open lifts and slopes) in real time.
- **App:** **Shymbulak Mountain Resort**, official ("Официальное приложение").
  - App Store KZ: **2.81 from 151 ratings**. https://apps.apple.com/kz/app/shymbulak-mountain-resort/id1591411405
  - Google Play KZ: **2.4, 190 reviews**, 10K+, updated Sep 25, 2026. https://play.google.com/store/apps/details?id=kz.shymbulak
- **Trail map:** yes, SR https://www.skiresort.info/ski-resort/chimbulak/trail-map/

## 14. Tsaghkadzor (Armenia)

- **Published size:** SR: **30 km** (14 / 14 / 2), **7 lifts**, 1966–2819 m. https://www.skiresort.info/ski-resort/tsaghkadzor/ (2026-10-06). Official site **unverified**: search names winterarmenia.com, but the proxy could not reach it (502), and neither could tsaghkadzor.ski.
- **Season:** SR "early December – late April".
- **Live status:** **not found / unverified.**
- **App:** **not found** (searched the App Store AM and Google Play).
- **Trail map:** yes, SR https://www.skiresort.info/ski-resort/tsaghkadzor/trail-map/

---

## Summary table

| Resort | Official km / lifts | SR km / lifts | Live per-item status | Official app (rating) |
|---|---|---|---|---|
| Gudauri | 37 km / 18 (georgia.travel) | 34.8 / 17 | status.mta.ski (HTML, 500 off-season) | SKIPASS (Lemondo, affiliation unverified): iOS 4.76 (317), Play 4.6 (155) |
| Bakuriani | 33 km / 12 (MTA) | 29 / 20 | status.mta.ski/bakuriani/didveli (HTML, 500) | same |
| Tetnuldi + Hatsvali | 37 km / 14 combined (MTA) | 13.7 / 5 and 7 / 4 | status.mta.ski/svaneti/mestia (HTML, 500) | same |
| Goderdzi | 8 km / 3 (MTA) | 7.2 / 3 | status.mta.ski/goderdzi (HTML, 500) | same |
| Bansko | km not stated / 15 listed | 75 / 14 | banskoski.com (HTML) | Bansko Ski: Play 4.2 (289), iOS 2.77 (13) |
| Borovets | 12 lifts, 31 slopes (status counts) | 58 / 13 | borovets-bg.com lifts-status and slopes-status (HTML) | My Borovets: Play 3.9 (156), iOS 3.76 (17) |
| Pamporovo | unverified | 29.8 / 13 | not found | not found |
| Mt Hermon | 9 lifts | 25 / 10 | skihermon.co.il/גולשים (HTML) | not found |
| Rosa Khutor | unverified (site blocked) | 102 / 27 | in app; web unverified | Rosa Khutor: Play 3.1 (51); iOS not found |
| Sheregesh | 19 lifts, 26 runs (sheregesh.ru) | km n/a / 15 | not found | none official; Sheregesh Today iOS 4.88 (629) |
| Elbrus | 17 km / 3 cable cars | 23 / 6 | resort-elbrus.ru/tracks (HTML) + app | iOS 4.25 (12), Play 3.3 (7) |
| Dombay | ~16.08 km (sum of listed runs, KD system) | 20 / 13 | dombai-kd.ru (HTML, gondola system only) | not found |
| Shymbulak | unverified | 20 / 9 | app only | iOS 2.81 (151), Play 2.4 (190) |
| Tsaghkadzor | unverified | 30 / 7 | not found | not found |

No third-party status widgets (Skiperformance, Snowcountry, intermaps) were found on any of the official pages examined.
