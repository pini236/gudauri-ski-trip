# Gudauri 2027: the preparation site

A preparation site for a ski holiday in Gudauri, Georgia, for Pini and friends. Flight on 10.1.2027.
The UI is Hebrew, RTL, phone first. (The Hebrew original of this file is `docs/archive/CLAUDE-he.md`, for reference only; this file is the one in force.)

## Language (Pini, 25.11.2026, token saving)

- **Everything is written in English**: messages between sessions, prompts for new sessions, STATUS log lines, commit messages, new docs, code comments.
- **The only exception: replies to Pini are in Hebrew.** When Pini asks something, or when you report to him (the manager session does most of this), answer in plain native Hebrew and translate what you saw or collected from other sessions.
- Product text (the app and site UI, `i18n/strings.json`, store listings, the privacy policy) stays as the product needs it (Hebrew, English, Russian, Georgian).
- Do not mix Hebrew and English in one sentence in replies to Pini (RTL/LTR garbles the terminal). Put terms, file names and commands on a separate line or in a code block, and describe them in Hebrew in the sentence.
- Existing Hebrew docs are not translated in bulk. Docs that every session loads (this file, `docs/STATUS.md`) are translated; the rest are read on demand, and anything new written in them is English.

## Mandatory for everyone working in the repo

1. **Before starting:** read @docs/STATUS.md. It says what is done, in progress, open, and which decisions were made.
2. **When finishing work, before the last commit:** update `docs/STATUS.md`: mark stages and tasks, add a log line, and update "Open now".
3. **An idea or a plan for the future:** write it in `docs/ROADMAP.md`, not only in the conversation. **Even a small passing mention** (in conversation, in a comment, in parentheses) is recorded there immediately, in the section "small items not to miss". Nothing may be missed.
4. **A structure change or a new decision:** update `README.md` and the decisions in `docs/STATUS.md` too.
5. Work that finished without a `docs/STATUS.md` update is considered unfinished.
6. **At the end of every stage:** after the commit, merge the branch into `main` (fast-forward when possible) and push, so Vercel deploys. Pini approved this as a standing permission.
7. **Every change to how the site looks or behaves is shown first on the design canvas and waits for Pini's approval** (1.10.2026). Only after approval is it implemented in code. Even a small change.
8. **Monday board** (decision 24, 1.10.2026): at the end of every stage, after the push, update the board "Gudauri 2027" (id `5105298795`, workspace AI Projects): status and date on the existing item, a new item for a new task or a decision waiting for Pini, and a link to the document in the repo. **The repo stays the source of truth:** do not copy documentation there, and do not read the board to learn the state (that is what `docs/STATUS.md` is for). **Every action is documented** (Pini, 6.11.2026: "every time something is done it must be documented, whether you do it or one of the sessions"): both what a session does and what the manager session does or hands out is recorded, without exception: a log line in `docs/STATUS.md`, and a Monday update (existing or new item) the same day. A session that finished work sends the manager a short summary, and if the board was not updated the manager completes it; when assigning a task to a session, open a board item for it at that moment.
9. **Do not check the deployment every time.** Pini checks it himself. Only update him when a stage is finished and pushed.
10. **Schedule (Pini, 1.10.2026, decision 29; "remember this well"):** by the start of the ski season there is a complete app, on Android and iPhone, approved in the stores, with everything planned, including four languages and all the games. **Nothing is postponed to "after the trip"**, and no schedules in weeks are proposed: AI tools do the work. What delays is only Pini's approvals, phone tests and external gates (stores, accounts, people), and those are started early.
11. **Several sessions in parallel (Pini, 2.10.2026, decision 45):**
    - **Who works on what:** before starting on an area (site, Android, server, canvas, etc.), check the table "who works on what now" at the top of `docs/STATUS.md`. Area taken: do not enter it; send a message to the session that holds it. Area free: register the session in it and push the row to the main branch **before** starting, so other sessions see it. When done, return the row to "free".
    - **System architect (decision 51):** before code, a design review with the architect session (id in the table) is mandatory when changing a contract, database structure or permissions; adding a service, library or external tool; building a capability that touches more than one platform; touching security, privacy, authentication or signing; or changing the release and test process. The proposal follows the template in `docs/ROLE-ARCHITECT.md`. He recommends, Pini decides.
    - **Decision numbering:** during work a new decision is recorded without a number ("new decision"), also in commit messages. The number is assigned only on merge to the main branch: after merging the main branch into your branch, take the next free number in the table.
12. **Token saving (Pini, 25.11.2026):**
    - Only `docs/STATUS.md` is auto-loaded. `FEATURES`, `ROADMAP`, `HANDOFF` and the archive (`docs/STATUS-ARCHIVE.md`) are read only when the task needs them, and only the relevant section (grep, do not read the whole file).
    - A log line is at most three lines. Detail stays in the commit, the handoff, or the area's own doc.
    - At the end of every week the manager session moves the log older than the last week to the archive.
    - Report to the manager session only at the end of a task or when stuck, not on every step.
    - Model: Sonnet by default; Opus or Fable only when Pini decides.
    - No research agents and no repeated design reviews when there is no real need.
    - **A session per task:** a new session for each task, closed (archived) when it ends, not a session that lives for weeks: every step re-reads the whole conversation. A session that grew past about 500K tokens is replaced by a new one that starts from `docs/STATUS.md`. Standing role sessions (architect, server, content, quality) stay, but dormant until they have a task.
    - **Effort level:** medium or low for routine work (implementing to a spec, log lines, relaying messages); high only for new graphic design and architecture decisions.
    - **Images:** look only at screenshots of the screen that changed, not the whole set.
    - **Monday update:** once a day and at the end of a stage, not after every step.
    - **Code search:** broad searches go to an Explore agent or a cheap model; do not read whole files.
    - **Tests (Pini, 25.11.2026):** do not overdo tests. Only the app's core flows get a test on every push (one short emulator part); everything else runs manually, and a full run is required before any store upload.

## Planning documents

- What is done and what remains: @docs/STATUS.md
- Detailed instructions for stages 1 to 5 (history): `docs/HANDOFF.md`
- Feature spec (and the working process with design): `docs/FEATURES.md`
- Ideas and future plans: `docs/ROADMAP.md`
- History (stages, closed decisions, log before 24.11.2026): `docs/STATUS-ARCHIVE.md`
- System structure, contracts, security, risks and technical decisions: `docs/ARCHITECTURE.md` (the system architect's; his role is in `docs/ROLE-ARCHITECT.md`).
- The professional team (product, design, branding, marketing, stores, data, content, community, legal, business, ski expert, games, quality, alignment, infrastructure): `docs/TEAM.md`, whom to approach and when (decision 52). **Who is active now:** a very lean team (decision 54, there).
- Operating the live services (Vercel, Supabase, GitHub Actions, Sentry, PostHog): what to check, what to do when a component fails, keys and expiry, and the incident log: `docs/OPERATIONS.md` (owned by cloud infrastructure and operations).
- Alignment between the site, Android, iPhone and the server (every capability, where it exists, and the gaps with owners): `docs/PARITY.md`. **A new capability on one side adds a row there in the same commit, and a closed gap is marked there.**
- The native apps (Android and iPhone): `docs/APP-NATIVE.md`. Users, sign-in and groups: `docs/USERS.md`. Growth: `docs/GROWTH.md`.
- Google Play (account, signing, closed test and publishing): `docs/PLAY.md`. Apple (developer account, Sign in with Apple, signing, TestFlight and App Store): `docs/APPLE.md`. Draft privacy policy: `docs/PRIVACY.md`.
- **No duplicates:** every item lives in one document. Spec in `FEATURES.md`, tracking in `STATUS.md`, platform gaps in `PARITY.md`, ideas in `ROADMAP.md`. When adding an item, check first that it does not exist in another.

## Current state (2.10.2026)

- The site was built in claude.ai as an artifact and moved to this repo. **From now on the repo is the source of truth.**
- Site structure (since stage 1):
  - `site/index.html`: a template only, no code and no data.
  - `site/css/site.css`: all the design and the tokens.
  - `site/js/relief.js`: the library `GudRelief`: loading the model, `stats()` for heights and slopes, `View3D` (three.js r128 served from the site itself, `site/js/vendor/`; all libraries there, pinned, because of the content security policy in `site/vercel.json`).
  - `site/js/i18n.js` and `site/i18n/`: four languages (since 2.10.2026). Every text on the site comes from `i18n/strings.json` through `T('key')`, `data-i18n` in the page, or `L('key','Hebrew')` in the games. **New text goes first into `i18n/strings.json`**, then `python3 tools/build-site-strings.py`. Details in `i18n/README.md`.
  - `site/js/app.js`: the main script. `main()` loads the runs file with `fetch` and builds everything: navigation `#home` / `#map`, the 2D map (SVG), the details panel, filtering, the flight card, videos. **The elevation model (`terrain.json`) loads in the background and does not delay the home page** (R-11): the relief, the meeting point and the 3D are built when it arrives (`onTerrain()`), and a link to the map or the meeting waits for it. three.js loads only when entering the map. **Tests:** `loaded()` also waits for the model (`html[data-model]`) and the 3D.
  - `site/data/terrain.json`: elevation model, relief, contour lines, surroundings, peaks.
  - `site/data/runs-and-lifts.json`: runs and lifts.
  - `site/img/pano/`: the view from the village for the home page, seven hours of the day, for phone and desktop. Built by `design/round3/panorama.py`.
  - `site/js/resort.js` and `site/data/resorts.json`: **several ski resorts** (prototype, decision 68, 6.10.2026; today Sölden and Sella Ronda). **A new resort is built only by the process in `.claude/skills/new-resort/SKILL.md`** (sources and an official list before code, a matching table, a self-check, and one report to Pini). Gudauri is the default and stays in `site/data/`; every other resort lives in `site/data/resorts/<id>/` in the same schema, built by `tools/build-resort.py` from the open map and an elevation model (the accuracy rules apply). `?resort=<id>` in the URL wins, and the choice is saved in the browser. **A resort marked `hidden` in the list (today Sölden and Sella Ronda) is in testing:** an outside visitor sees only Gudauri and no selector; it opens only with `?resort=`, or for everyone in the selector with `?preview=1` (saved in the browser; `?preview=0` cancels), and is always `noindex` (Pini, 25.10.2026). Capabilities of Gudauri only (trip, meeting, lift status, weather, Kobi) are marked in the page with `data-feature`, and in code with `has('<capability>')`.
  - `site/js/buildings3d.js` and `site/data/buildings.json`: buildings, roads and street lamps in 3D in New Gudauri, **a POC behind `?b3d=1`** (decision 77). The data is built with `python3 tools/build-buildings.py`; outlines from the open map, and every building has `hsrc` and `lsrc` (where the height and the look came from). The apps do not read the file before an architect review.
  - `site/js/account.js`: accounts and groups on the site (joining, group page, account deletion, `/account`), against the server. Loaded only when there is a connection, so a guest does not contact the server at all.
  - `site/js/snow.js`: the snow on signs and cards, drawn to the width of each (`data-snow`), also on the privacy page. Details in `design/README.md`.
  - `site/js/telemetry.js`: usage measurement (PostHog) and errors (Sentry), without identifying details and with a kill switch in settings (decisions 32 and 37). Events follow the contract in `docs/GROWTH.md`.
  - `site/app.html`: the short app link (`/app`, decision 75), for sharing: WhatsApp shows an image (a Google Play link does not), and the page redirects to the store. Details in `site/README.md`.
  - `site/support.html`: the support page (`/support`, round 33), built from the `support.*` keys in `i18n/strings.json` with `python3 tools/build-support.py`; a test fails if they do not match. The `Support URL` of the App Store.
  - `site/privacy.html`: the privacy page (`/privacy`), built from `docs/PRIVACY.md` with `python3 tools/build-privacy.py`; a test fails if they do not match. **The policy text was approved by Pini and changes only with his approval.**
  - **Running locally needs a server** (the command `npm start`), because data is loaded with `fetch` and does not work from a file opened directly.
- **An instruction file per project (decision 34):** the session working on Android has extra instructions in `app/android/CLAUDE.md`, and the server session in `server/CLAUDE.md`. Instructions that concern only one project are written in its folder, not here.
- **Server (stage 13.5, decisions 27, 28, 40):** `server/`, a Supabase project on the free plan (Frankfurt), with all actions as `TypeScript` code in one function. **The contract for anyone talking to it (apps, site, sessions) is `server/CONTRACT.md`;** explanation and deployment in `server/README.md`. A daily GitHub job prevents the free plan from pausing. **Actions on the live server (deploying a function, a migration) only with Pini's approval.**
- **Four languages (decisions 32, 39, 41):** Hebrew, English, Russian, Georgian, from `i18n/strings.json` (site, games and app). Tests: `python3 i18n/check.py` and `python3 tools/check-site-i18n.py` (also run on GitHub).
- **Apps (since 1.10.2026, decision 25):** `app/android/` (`Kotlin` with `Jetpack Compose`) and `app/ios/` (`Swift` with `SwiftUI`), fully native, no shared code and no change in `site/` for them. **`chatgpt/` belongs to ChatGPT:** do not touch it or import from it.
- The live artifact: https://claude.ai/artifact/1uDUdt45A2c77zooPZBGYK (you have no access to it; it is a mirror only).
- The design canvas: https://claude.ai/artifact/TVdeDbFBhvFDZdhCKjcys4 (a copy in `design/canvas/`, previews in `design/previews/`). Includes a page "new features" (`F1` to `F7`).
- The original features document: https://claude.ai/artifact/FeAJgxBa6ZkDHqjxppVMkV (a snapshot from 30.9.2026; the repo is the source of truth).

## Flight, videos and server

No dependency on claude.ai. The map data and the videos are files in the repo, read-only. What concerns accounts and groups (names, shared flights, meetings, records) lives on the server (`server/`), and only someone who joined a group or signed in sends anything there:

- **"Your trip"** (since round 12, decision 42): the card on the home page is built from the viewer's own flight, saved only in the browser (`#trip`, `MYTRIP` in `site/js/app.js`). The flight and the friends' names were removed from the site (Pini, 2.10.2026); a copy for the canvas builders is in `design/data/trip.json`, which is not deployed. **No passport numbers, birth dates, phones or emails.**
- `site/data/videos-seed.json`: an array of videos `{piste, url, title, by, at, channel, length, published, confidence, evidence}` (`piste` = the run key, `at` = epoch ms). Shown in the run details (`vidList()` and `vidBlock()`) as a thumbnail that opens an embedded YouTube player (`youtube-nocookie.com`), with a link below. **The file is generated automatically**: edit `tools/videos-curated.json` and run `python3 tools/build-videos.py`, which also produces `research/videos.md`. Rule: a video enters only if the title or description names the run with its number.
- The videos file loads softly: if loading fails, the site keeps working without videos.
- Users, sign-in and groups: `docs/USERS.md` (decisions) and `server/CONTRACT.md` (contract).

## Run data format (`site/data/runs-and-lifts.json`)

- Coordinates are **`[lat, lon]`**. In GeoJSON the order is reversed: `[lon, lat]`.
- `pistes[]`: `key, name, color (green|blue|red|black), named, len (m, horizontal), osmDiff[], refs[], groom[], lit[], segs[{id, area, g}], fromLifts[], toLifts[], joins[], fromPistes[]`.
  - Optional: `kind` (`run` | `ski-way` | `beginner-area`), `research {conf, status, notes, sources[], gps, osmIds[], historical, partial}`.
- `lifts[]`: `id, name, kind, len, g, cap, dur, occ, year, rise, status...`
- Connections are computed from proximity of ends: up to 200 m to a lift station, up to 60 m to a run. Start and end points by height from the elevation model.

## Accuracy rules (the most important in the project)

1. **Do not invent geometry.** A line enters the map only from a real source: OSM (including history), GPS recordings, or a measurement. With no source, mark it missing and do not draw.
2. **The official MTA map is a protected illustration.** It may be used only to identify a run's name, color, start and end. Do not follow it, copy it, or store it in the repo.
3. **Colors follow the official map**, even when another source says otherwise. For example Kudebi 1 is red. Shino is a connecting road (Ski Way), not a run.
4. **Every new line is checked** against the elevation model (`site/data/terrain.json`) and the lift positions:
   - descends continuously (accumulated counter-climb of up to about 10 m);
   - starts and ends near the right lifts or runs.
5. **For every completion:** a confidence level (`high` / `medium` / `low`), a source and a rationale, in the data (the run's `research`). **They are no longer shown to the user in the panel** (Pini, 6.10.2026, decision 64, as part of round 20), but remain mandatory in the data, and rules 1 to 4 do not change: a line with no source does not enter the map.

## Design

Details in `design/README.md`. In short:

- Fonts: Karantina (headings, 700) and IBM Plex Sans Hebrew (text, in Hebrew). In the other languages (decision 36, `FT1`): Russian headings in Oswald, Georgian in Noto Sans Georgian (also for text), and English and Russian text in IBM Plex Sans.
- Tokens in `:root`. Dark mode is defined both in `@media (prefers-color-scheme: dark)` and in `:root[data-theme="dark"]`.
- Components: sign (clip-path with a left arrow), sign post, chips (44px, straight corners), view switcher, a flight card tilted 2 degrees.
- 3D terrain is always snowy, so the 3D labels have a fixed light palette (`.r3-labels`).
- Touch targets of at least 44px. No sideways scroll on a phone.

## Tests

- Playwright, four modes: desktop 1280x800 and phone 390x844, light and dark.
- Walk through: home page, `#map`, 3D, top view, run selection, filtering, going back home.
- Zero console errors. If three.js does not load, the site must fall back to top view and hide the view switcher.
- Compare with the screenshots in `design/screenshots/` after every change that touches the look.
- **The full test runs on GitHub, not in the cloud environment** (Pini, 2.10.2026). There the four modes run in parallel, in about a minute and a half; in the cloud environment the same run takes about 18 minutes. Therefore:
  1. Locally only the tests of what changed, in one mode: `npm run test:quick -- tests/<file>.spec.ts` (you can add `-g "<test name>"`).
  2. Push to the branch, and GitHub runs all the tests in the four modes (`.github/workflows/tests.yml`).
  3. Merge into `main` only after the branch run is green. Check it with the GitHub tool, without waiting idle: continue other work meanwhile.

## Work

- **Parallel work (Pini, 1.10.2026):** during tests or long operations do not wait idle. Continue work that does not touch the files under test (design, canvas, docs), and work on site code is done in a separate copy of the repo (`git worktree`) on its own branch, so as not to change files mid-run.
- **Vercel deployments (7.10.2026):** only `main` and branches whose name starts with `preview/` are deployed (`git.deploymentEnabled` in `site/vercel.json`), because of the limit of 100 deployments a day. A session branch gets no preview; when Pini needs to check before a merge, also push to `preview/<topic>`.
- A separate commit for every stage, with a clear message. Stop for Pini's approval at the end of each stage in HANDOFF.
- Do not store secrets or friends' email addresses in the repo. Supabase's anon key is public by definition and allowed.
- Update `README.md` and the relevant README files when the structure changes.

## Spectacular, text and power (Pini, 25.11.2026, permanent rules)

- **The bar is "spectacular in every respect".** When you feel that reaching it needs a stronger model (for example Fable), a tool we do not have, or more power, **raise a flag to Pini** instead of compromising. Pini will handle it.
- **No explanatory lines when the UI and UX are good.** A sentence like "stops by itself at the bottom, and gives a card of the descent" clutters and feels like AI text. If you need to explain, fix the design first.
- **App text: a native Hebrew speaker's Hebrew, clear and informative, short, not poetic.** Not "the phone in the pocket. The mountain draws the day", but what it is and what it does, in plain words. The same in the other languages.

## Reply style

- **Replies to Pini are always in Hebrew** (and, per the language section above, everything else is English). Short in-progress messages to Pini ("checking...", "running tests...") are in Hebrew too. English only in code, file names and commands.
- Do not mix Hebrew and English in one sentence, because in the terminal it garbles and is unreadable (RTL/LTR directionality).
- Terms, file names and commands in English: move them to a separate line or a code block, and in the Hebrew sentence use a Hebrew description ("the settings file", "the command below").
- Only file names and commands inside `code`, and on their own line when possible.
