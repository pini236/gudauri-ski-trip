# Benchmark resorts B: size, season, live status, apps, trail maps

All facts accessed **2026-10-06**. skiresort.info URLs now redirect (301) to skiresort.com; the skiresort.com URL is cited.
"Unverified" means it could not be confirmed from a fetched source. No numbers were estimated.

Method notes:
- Store ratings come from Google Play search or details pages and Apple App Store pages, fetched on the access date. Some Play detail pages could not be read. For those, only the rating from the search card is given and the review count is marked "unverified".
- "Format" for live status comes from fetching the official page (curl and WebFetch) and inspecting the HTML. "Server-rendered HTML" means per-item open/closed states are in the HTML returned by the server. "JS/embedded" means the per-item data is loaded client-side or inside an embedded map. No resort was found to publish a documented public JSON API.
- The season is off now (October), so every status page showed closed or near-zero values. Formats were inferred from page structure.

---

## 1. Sölden (Austria)

- **Piste km / lifts (official):** 144 km of slopes, 31 lifts. Sources: https://soelden.com/winter/ski-area/information-on-the-ski-area.html (via search snippet) and https://www.tyrol.com/things-to-do/sports/skiing/ski-resorts/a-soelden-ski-area
- **Piste km / lifts (skiresort):** 145.7 km of slopes plus 8.4 km of ski routes (easy 76.4, intermediate 40.1, difficult 29.2), 31 lifts. https://www.skiresort.com/en/ski-resort/soelden/
- **Season:** generally early October to early May; current season 2026-10-09 to 2027-05-02 (glacier). https://www.skiresort.com/en/ski-resort/soelden/
- **Live status:** yes. https://soelden.com/winter/live-info/current-ski-resort-information.html. **Format:** server-rendered HTML. Each lift, slope and hut is an item with `"state":"closed"/...`, an icon type (`chairlift-6`, `drag-lift`…) and a title (for example "8EUB Gaislachkogl I"), embedded as JSON in HTML data attributes. "Open map" links go to the **Intermaps** interactive map (`https://winter.intermaps.com/soelden?focus=...`).
- **Official app:** "Sölden" on Google Play (developer **intermaps**, package `intermaps.isoelden`), 3.4★, review count unverified. https://play.google.com/store/search?q=soelden&c=apps. App Store: "Sölden – iSölden" (Ötztal Tourismus), 1.0★ (1 rating). https://apps.apple.com/app/id396875365. The app shows lift and slope status.
- **Trail map:** yes. Interactive Intermaps map https://winter.intermaps.com/soelden plus a 2D downloadable map page https://soelden.com/winter/ski-area/information-on-the-ski-area/soelden-ski-area-map.html (via search snippet).

## 2. Ischgl / Silvretta Arena (Austria)

- **Piste km / lifts (official):** 239 km of slopes, 46 lifts. https://www.ischgl.com/en/
- **Piste km / lifts (skiresort):** 239 km of slopes plus 15 km of ski routes (easy 47, intermediate 140, difficult 52), 45 lifts. https://www.skiresort.com/en/ski-resort/ischgl-samnaun-silvretta-arena/
- **Season:** generally late November to early May; current season 2026-11-26 to 2027-05-02. Same source.
- **Live status:** yes. https://www.ischgl.com/en/winter/silvretta-arena/open-facilities. **Format:** server-rendered HTML list of every facility with status, hours and technical data (page about 1.3 MB). Per-item **Intermaps** map embeds (`https://winter.intermaps.com/silvretta_arena?focus=L_32800_group`). The page shows a last-update timestamp.
- **Official app:** "Ischgl" (Silvrettaseilbahn AG, `com.ischgl`). Google Play 3.1★ (472 reviews, 100K+ downloads): https://play.google.com/store/search?q=ischgl&c=apps. App Store 2.9★ (8 ratings): https://apps.apple.com/app/id396306147
- **Trail map:** yes, interactive (Intermaps): https://winter.intermaps.com/silvretta_arena

## 3. Saalbach Hinterglemm Leogang Fieberbrunn, Skicircus (Austria)

- **Piste km / lifts (official):** 270 km of slopes (140 blue, 112 red, 18 black), 70 cableways and lifts. https://www.saalbach.com/en/winter/ski-resort
- **Piste km / lifts (skiresort):** 270 km, 69 lifts. https://www.skiresort.com/en/ski-resort/saalbach-hinterglemm-leogang-fieberbrunn-skicircus/
- **Season:** official planned dates 27.11.2026 to 04.04.2027 (https://www.saalbach.com/en/winter/ski-resort); skiresort: late November to early April.
- **Live status:** https://www.saalbach.com/en/winter/ski-resort/current-lift-operation. **Format:** the HTML holds no per-lift list (checked). Status comes through an embedded **Intermaps** interactive map (`https://winter.intermaps.com/saalbach_hinterglemm_leogang_fieberbrunn?lang=en`) and text notices. Search snippets show notices such as "A2 Schattberg X-press II … closed due to strong wind".
- **Official app:** "HOME of LÄSSIG" (developer **intermaps**, `intermaps.saalbachhinterglemm`), Google Play 3.4★, review count unverified. https://play.google.com/store/search?q=skicircus%20saalbach&c=apps. App Store rating unverified.
- **Trail map:** yes, interactive Intermaps (above).

## 4. Mayrhofen / Zillertal, Mountopolis (Austria)

- **Piste km / lifts (official):** unverified. The official site redirects to mayrhofen.at, and no total was found on the pages fetched.
- **Piste km / lifts (skiresort):** 142 km of slopes plus 8.7 km of ski routes, 61 lifts. https://www.skiresort.com/en/ski-resort/mayrhofen-penken-ahorn-rastkogel-eggalm-mountopolis/
- **Season:** generally early December to mid April; current season 2026-12-04 to 2027-04-11. Same source.
- **Live status:** yes. https://www.mayrhofen.at/en/stories/open-cable-cars-mayrhofner-bergbahnen-winter. **Format:** server-rendered HTML, each lift with an open/closed icon (`opened.svg` / `closed.svg`) and sector counters ("Open Facilities 1/7"). The provider is not named on the page. The HTML contains Intermaps, Skiline and Feratel references.
- **Official app:** the lift company promoted a "Mayrhofner Mountain App" with ski navigation (2021-22 press text: https://www.mayrhofner-bergbahnen.com/fileadmin/userdaten/presseportal/Pressetexte/Mountopolis_Texte_Winter/2021-22_EN_Mayrhofner_Bergbahnen_The_Mayrhofner_Bergbahnen_in_winter.pdf). Not found on Google Play today (unverified). The current status page points to the regional **myZillertal** app (Zillertal Booking GmbH, `app.alturos.zib`), Google Play 3.5★, review count unverified. https://play.google.com/store/search?q=mayrhofen&c=apps
- **Trail map:** yes, a downloadable "Mountopolis winter panorama/slope map" linked from the status page above.

## 5. St. Anton am Arlberg, Ski Arlberg (Austria)

- **Piste km / lifts (official, whole Ski Arlberg):** "over 300 km of ski runs and 85 cable cars and lifts", per the skiarlberg.at text quoted in a search snippet (unverified on a fetched page). https://www.skiarlberg.at/en/st-anton/live-info/cable-cars-lifts
- **Piste km / lifts (skiresort, Ski Arlberg = St. Anton/St. Christoph/Stuben/Lech/Zürs/Warth/Schröcken):** 299.7 km (easy 130, intermediate 120, difficult 49.7), 85 lifts. https://www.skiresort.com/en/ski-resort/st-anton-st-christoph-stuben-lech-zuers-warth-schroecken-ski-arlberg/
- **Season:** generally early December to late April; current season 2026-12-02 to 2027-04-18. Same source.
- **Live status:** yes. https://www.skiarlberg.at/en/st-anton/live-info/cable-cars-lifts. **Format:** server-rendered HTML table of named lifts (Albonabahn I, Fangbahn…) with open/closed state. Each row links to the **Intermaps** map (`https://skiarlberg.intermaps.com/skiarlberg?focus=L_41108&show=lifts`). An Arlberger Bergbahnen page also exists: https://arlbergerbergbahnen.com/en/winter/ski-area/cable-cars-and-pistes
- **Official app:** "Ski Arlberg" (developer **intermaps**, `com.intermaps.skiarlberg`), Google Play 2.9★ (286 reviews, 100K+ downloads). https://play.google.com/store/search?q=ski%20arlberg&c=apps
- **Trail map:** yes, interactive Intermaps https://skiarlberg.intermaps.com/skiarlberg

## 6. Livigno (Italy)

- **Piste km / lifts (official):** "over 115 km", 31 lifts, 74 slopes. https://www.skipasslivigno.com/en/lifts-and-slopes/
- **Piste km / lifts (skiresort):** 115 km (easy 32, intermediate 63, difficult 20), 32 lifts. https://www.skiresort.com/en/ski-resort/livigno/
- **Season:** generally early November to late May (skiresort wording); current season 2026-11-28 to 2027-05-02. Same source.
- **Live status:** yes. https://www.skipasslivigno.com/en/lifts-and-slopes/. **Format:** server-rendered HTML listing all 31 lifts with "Current status" and "Season status", plus capacity and connected slopes. A second official page, "Infolive Lifts": https://www.livigno.eu/en/lifts (HTML with open/closed states).
- **Official app:** "Livigno Skipassion" (Skipass Livigno, built on **Skitude**, `com.skitude.livigno`), Google Play 4.1★ (147 reviews, 10K+). https://play.google.com/store/search?q=livigno%20skipassion&c=apps. App Store id1550579648 (rating unverified). App page: https://www.skipasslivigno.com/en/livigno-skipassion-app/
- **Trail map:** yes, a "Download Livigno ski map" PDF linked on https://www.skipasslivigno.com/en/lifts-and-slopes/

## 7. Val di Fassa / Canazei, Dolomiti Superski (Italy)

- **Piste km / lifts (official):** unverified. dolomitisuperski.com returned 403 (Cloudflare) to fetches, and fassa.com shows no totals.
- **Piste km / lifts (skiresort, Belvedere/Col Rodella/Ciampac/Buffaure, the Canazei area):** 55.2 km (easy 7.3, intermediate 40.7, difficult 7.2), 35 lifts. https://www.skiresort.com/en/ski-resort/belvedere-col-rodella-ciampac-buffaure-canazei-campitello-alba-pozza-di-fassa/. The whole Val di Fassa valley is larger (third-party figures of about 190 km; unverified officially).
- **Season:** generally early December to early April; current season 2026-12-03 to 2027-04-04. Same source.
- **Live status:** Dolomiti Superski live-info page per area, https://www.dolomitisuperski.com/en/live-info/lifts/val-di-fassa-carezza (title "Lifts in Val di Fassa"; search snippet showed an open-lift count). **Format:** HTML page; per-item structure unverified (blocked by Cloudflare for automated fetch).
- **Official app:** "MyDolomiti" (Dolomiti Superski, `com.dolomitisuperski.skiapp`), Google Play 4.5★ (2.89K reviews, 500K+). https://play.google.com/store/search?q=dolomiti%20superski&c=apps
- **Trail map:** unverified (official map page not reachable from here).

## 8. Madonna di Campiglio, Skiarea Campiglio Dolomiti di Brenta (Italy)

- **Piste km / lifts (official):** 155 km, 104 pistes, 58 lifts. https://www.ski.it/ (funiviecampiglio.it redirects there)
- **Piste km / lifts (skiresort):** 155 km (easy 50, intermediate 72, difficult 33), 58 lifts. https://www.skiresort.com/en/ski-resort/madonna-di-campiglio-pinzolo-folgarida-marilleva/
- **Season:** generally mid November to mid April; current season 2026-11-21 to 2027-04-11. Same source.
- **Live status:** yes. https://www.ski.it/it/live/impianti-aperti. **Format:** server-rendered HTML with per-item state (186 "chiuso" occurrences in the off-season HTML). Feratel references are in the page. Skirama also has a summary-only page: https://www.skirama.it/en/inverno/slopes-and-lifts-skirama (counts only).
- **Official app:** "Skiarea Madonna di Campiglio" (Funivie Folgarida Marilleva, built on **Skitude**, `com.skitudeservices.SkiareaCampiglio`), Google Play 4.1★ (461 reviews, 50K+). https://play.google.com/store/search?q=madonna%20di%20campiglio&c=apps. App page: https://www.ski.it/it/live/app-skiarea
- **Trail map:** yes. Interactive map https://www.ski.it/it/live/mappa-interattiva and PDF https://www.ski.it/ski/documenti-file/live/Ski_Map_Skiarea.pdf

## 9. Val Thorens / Les 3 Vallées (France)

- **Piste km / lifts (official):** Val Thorens–Orelle 150 km; Les 3 Vallées 600 km. https://www.valthorens.com/en/ski/plan/ (via search snippet). Official lift count unverified.
- **Piste km / lifts (skiresort, Les 3 Vallées):** 600 km plus 50 km of ski routes, 159 lifts. https://www.skiresort.com/en/ski-resort/les-3-vallees-val-thorens-les-menuires-meribel-courchevel/
- **Season:** generally mid November to early May; current season 2026-11-21 to 2027-05-02. Same source.
- **Live status:** yes. https://www.les3vallees.com/en/live/lifts-and-trails-opening (per resort, for example `/val-thorens`). **Format:** JS/embedded. The HTML has counter placeholders, and per-item status comes from an embedded **Lumiplan "Lumiplay"** interactive map (`https://lumiplay.link/interactive-map/les-3-vallees/en`). Val Thorens has its own live map at https://www.valthorens.com/en/ski/plan/ (Cloudflare-blocked to curl).
- **Official app:** "Les 3 Vallées" (Assoc. Les Trois Vallées; Play package `com.cronostechnologies.skiplan`). Google Play 4.0★ (3,550 reviews, 100K+): https://play.google.com/store/search?q=les%203%20vallees&c=apps. App Store 4.7★ (957 ratings): https://apps.apple.com/app/id344348247
- **Trail map:** yes, interactive (Lumiplay, above) and the Val Thorens plan page.

## 10. Tignes – Val d'Isère (France)

- **Piste km / lifts (official):** 300 km of marked slopes. https://www.tignes.net/ (also stated on https://www.valdisere.com/en/). Official lift count unverified.
- **Piste km / lifts (skiresort):** 300 km plus 20 km of ski routes, 80 lifts (the fetched page; a search snippet showed 84). https://www.skiresort.com/en/ski-resort/tignes-val-disere/
- **Season:** generally late November to early May (plus glacier skiing mid June to mid August); current season 2026-11-28 to 2027-05-02. Same source.
- **Live status:** yes. https://www.tignes.net/ski/domaine-skiable/ouvertures-temps-reel ("real-time openings", updated by ALTTA/STVI). **Format:** JS-loaded. The off-season HTML only shows "no piste or lift currently open". Provider unverified. Val d'Isère: https://www.valdisere.com/en/live/ski-map/
- **Official apps:** "Tignes" (Openium, `fr.openium.tignes`), Google Play 2.8★ (415 reviews, 100K+): https://play.google.com/store/search?q=tignes&c=apps. "Val d'Isère Ski" (`com.youstiti.valdisere`), Google Play 4.3★ (779 reviews, 50K+): https://play.google.com/store/search?q=val%20d%27isere&c=apps
- **Trail map:** yes. https://www.tignes.net/ski/domaine-skiable/plan-des-pistes and https://www.valdisere.com/en/live/ski-map/

## 11. Alpe d'Huez (France)

- **Piste km / lifts (official):** "more than 250 km of slopes", 135 runs. Official lift count unverified. https://www.alpedhuez.com/en/
- **Piste km / lifts (skiresort):** 250 km (easy 70, intermediate 130, difficult 50), 68 lifts. https://www.skiresort.com/en/ski-resort/alpe-dhuez/
- **Season:** generally early December to late April; current season 2026-12-05 to 2027-04-18. Same source.
- **Live status:** https://skipass.alpedhuez.com/hiver/en/weather-conditions/. **Format:** no per-item data in the fetched HTML (JS-loaded; provider unverified). The app is built by Lumiplan (see below).
- **Official app:** "Alpe d'Huez" (SATA Group, `com.lumiplan.montagne.alpedhuez`), Google Play 4.2★ (1.24K reviews, 50K+). https://play.google.com/store/search?q=alpe%20d%27huez&c=apps. App page: https://skipass.alpedhuez.com/hiver/en/alpe-dhuez-app/
- **Trail map:** yes, interactive: https://explore.alpedhuez.com/en/map

## 12. Grandvalira (Andorra)

- **Piste km / lifts (official):** 215 km; the open-slopes page shows totals of 142 slopes and 73 facilities. https://www.grandvalira.com/en/resort/open-slopes
- **Piste km / lifts (skiresort):** 215 km (easy 100, intermediate 82, difficult 33), 75 lifts. https://www.skiresort.com/en/ski-resort/grandvalira-pas-de-la-casa-grau-roig-soldeu-el-tarter-canillo-encamp/
- **Season:** generally early December to mid April; current season 2026-12-05 to 2027-04-04. Same source.
- **Live status:** yes. https://www.grandvalira.com/en/resort/open-slopes. **Format:** server-rendered HTML listing named lifts (for example "TSD6 Pla de les Pedres") and slopes by sector with open/closed state, plus totals by colour (page about 840 KB).
- **Official app:** "Grandvalira Resorts" (`com.grandvalira.Grandvalira`), Google Play 3.3★ (1.24K reviews, 100K+): https://play.google.com/store/search?q=grandvalira&c=apps. App Store 3.8★ (23 ratings): https://apps.apple.com/us/app/grandvalira-resorts/id777328587
- **Trail map:** yes. https://www.grandvalira.com/en/resort/ski-map

## 13. Jasná Nízke Tatry – Chopok (Slovakia)

- **Piste km / lifts (official):** 51 km of pistes, 19 cable cars, chairlifts and ski lifts. Official 2026 press kit: https://www.jasna.sk/fileadmin/resort_upload/jasna/jasna_presskit_A4_2026_EN.pdf
- **Piste km / lifts (skiresort):** 52.1 km, 20 lifts. https://www.skiresort.com/en/ski-resort/jasna-nizke-tatry-chopok/
- **Season:** generally early December to mid May (skiresort wording); current season 2026-11-28 to 2027-05-02. Same source.
- **Live status:** https://www.jasna.sk/en/resort/resort-info/lifts-and-slopes. **Format:** in the off season the fetched page held only operating notices, no per-item list. Per-item list in season unverified. Per the resort, the GOPASS app shows the list of open cableways and slopes (https://www.jasna.sk/en/cenniky/gopass/gopass-app).
- **Official app:** "Gopass.travel" (developer GOPASS SE; the GOPASS system of Tatry Mountain Resorts), App Store 4.7★ (1.5k ratings): https://apps.apple.com/sk/app/gopass-travel/id1531706379. Google Play: package `gopass.travel.mobile`, 4.8★ on the search card, review count unverified. https://play.google.com/store/search?q=gopass%20jasna&c=apps
- **Trail map:** yes, maps section: https://www.jasna.sk/en/resort/resort-info/maps (page found via the homepage; map content not inspected).

## 14. Kopaonik (Serbia)

- **Piste km / lifts (official):** "about 55 km of runs for alpine skiing" (plus 12 km cross-country). The official page lists lift types and over 32,000 skiers/h capacity, but no single lift total. https://www.skijalistasrbije.rs/en/node/622
- **Piste km / lifts (skiresort):** 55 km (easy 30, intermediate 19, difficult 6), 26 lifts. https://www.skiresort.com/en/ski-resort/kopaonik/
- **Season:** generally early December to early April; current season 2026-12-03 to 2027-04-11. Same source.
- **Live status:** yes. "Ski info" on the state operator Skijališta Srbije: https://www.skijalistasrbije.rs/en/node/1312. **Format:** server-rendered HTML table per installation (name, type, capacity, "Open: Ne/Da") and per run (number, name, difficulty), with a last-update timestamp. It also covers Stara planina and Tornik.
- **Official app:** not found. Third-party apps exist: "Kopaonik – infoKOP" (Click Internet Solutions, `com.infokop`, 4.7★) and "Ski Serbia" (Flixeron, `com.neoapps.skiserbia`, 4.9★). Review counts unverified. https://play.google.com/store/search?q=kopaonik&c=apps
- **Trail map:** yes. https://www.skijalistasrbije.rs/en/map-ski-resort-kopaonik (linked from the official page; map content not inspected).

## 15. Erciyes (Turkey)

- **Piste km / lifts (official):** inconsistent. The official article says 102 km, 34 runs, 18 lifts including 2 gondolas (https://www.erciyeskayakmerkezi.com/kesfet/erciyes-kayak-merkezi-pist-durumu). The homepage says 33 pistes and 18 lifts (https://www.erciyeskayakmerkezi.com). Press and Wikipedia figures of 112 to 120 km are not used.
- **Piste km / lifts (skiresort):** 55.1 km (easy 19.8, intermediate 23, difficult 12.3), 14 lifts. https://www.skiresort.com/en/ski-resort/erciyes-kayseri/
- **Season:** generally early December to late April (skiresort). Current-season dates not shown.
- **Live status:** not found on the website. The "pist durumu" page is a descriptive article, not live. Per its listings, the official app shows run and lift status (below).
- **Official app:** "Erciyes Mobil", produced by Erciyes Kayak Merkezi, shows run and lift status and live cams. Source: https://www.tamindir.com/indir/erciyes-mobil/ (third-party download site). Not found under that name in a Google Play search; store ratings unverified.
- **Trail map:** yes. https://www.erciyeskayakmerkezi.com/kayak-haritasi-ski-map

## 16. Cerro Catedral, Catedral Alta Patagonia (Argentina)

- **Piste km / lifts (official):** unverified. The official homepage mentions "58 pistas" but no km or lift total. https://www.catedralaltapatagonia.com/
- **Piste km / lifts (skiresort):** 48 km (easy 30, intermediate 16, difficult 2), 29 lifts. https://www.skiresort.com/en/ski-resort/catedral-alta-patagonia/
- **Season:** generally late June to early October; 2026 season 2026-07-06 to 2026-10-12. Same source.
- **Live status:** daily report page https://catedralaltapatagonia.com/parte-de-nieve/. The fetched HTML held only a legend (Normal, Conditional, Closed, Descent only), with no named items. Per-item data is probably JS-loaded or app-only (unverified). The resort tells skiers to check the daily status of lifts in the official app (per a search snippet).
- **Official app:** "CatedralApp", described as the "Oficial App of Cerro Catedral" (developer listed as Busplus, `com.pmf.catedralapp`), Google Play 4.7★ (438 reviews, 10K+): https://play.google.com/store/search?q=cerro%20catedral&c=apps. App Store id6480464952 (rating unverified); links from https://www.catedralaltapatagonia.com/
- **Trail map:** official map unverified. Third-party: https://www.onthesnow.com/argentina/cerro-catedral-alta-patagonia/trailmap.html

## 17. Valle Nevado (Chile)

- **Piste km / lifts (official):** totals not stated as km. The mountain report shows denominators "Open Slopes x of 45" and "Open Ski Lifts x of 13" (the homepage shows different live counts). https://www.vallenevado.com/en/mountain-report/
- **Piste km / lifts (skiresort):** 40 km (easy 4, intermediate 14.4, difficult 21.6), 17 lifts. https://www.skiresort.com/en/ski-resort/valle-nevado/
- **Season:** generally mid June to late October; 2026 season 2026-06-19 to 2026-10-18. Same source.
- **Live status:** https://www.vallenevado.com/en/mountain-report/. **Format:** HTML with aggregate counts only, no named lifts or runs. Slopes page: https://www.vallenevado.com/en/slopes/ (not inspected). Per-item lift status is in the app.
- **Official app:** "Valle Nevado" (Valle Nevado S.A., built on **Skitude**), App Store 4.8★ (35 ratings): https://apps.apple.com/app/id548238945. It provides lift status and slope conditions. Google Play listing not found in search (unverified).
- **Trail map:** yes. https://www.vallenevado.com/en/winter-map/

---

## Cross-cutting observations

- **Intermaps** powers the interactive maps and the official apps of Sölden, Ischgl, Skicircus Saalbach and Ski Arlberg. Its winter maps are at `winter.intermaps.com/<area>` or `<area>.intermaps.com`.
- **Skitude** is behind the official apps of Livigno, Madonna di Campiglio and Valle Nevado. **Lumiplan** is behind the Les 3 Vallées interactive map and the Alpe d'Huez app.
- Server-rendered per-item status pages were found for Sölden, Ischgl, Ski Arlberg, Mayrhofen, Livigno, Madonna di Campiglio (ski.it), Grandvalira and Kopaonik. JS or embedded only: Saalbach, Les 3 Vallées, Tignes, Alpe d'Huez. Counts only: Valle Nevado. Not found: Erciyes.
- No documented public JSON API was found for any of the 17.
- Official app ratings on Google Play range from 2.8★ (Tignes) to 4.7★ (Catedral). The big Alpine lift-company apps (Ischgl 3.1, Arlberg 2.9, Sölden 3.4, Skicircus 3.4) are rated notably low.
