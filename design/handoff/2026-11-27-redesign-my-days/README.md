# Handoff: no-descent recordings are not saved; "ימי הגלישה שלי" and "השיאים שלך" (P.23, P.24)

Status: **waiting for Pini** (rule 7). Nothing here is built. Boards: canvas page "ימי הגלישה שלי והשיאים (27.11)", `MY00`, `NS1`-`NS3`, `MY1`-`MY3`, `RS1`-`RS3`. Source: `design/redesign/tools/md_boards.py` (`--preview <dir> <blobs dir>` for local pages). Designed by `session_017QrNw6fSnaro3p4JhH4YLR` (could not push); published and committed by `session_017QrNw9vE7tZwtoB5accqAi`.

## 1. P.23: a recording with no real descent is never saved

**The rule (for the builder).** At stop, the analyzer's result is kept only if it has at least one *real descent*:

- a descent segment of the analyzer with a height drop of at least `min_descent_drop_m` (20 m, `tools/fixtures/dayrec-params.json`); a "descent" that passed only on distance (`min_descent_m`, flat walking) does **not** count. This is the "0 m down, 0.5 km" day in Pini's screenshot;
- inside Gudauri: the descent's points within `WhereRules.atResort` (2.5 km of a run or lift);
- a part of a descent counts (a cut day, a recording started mid-run): same rule on what was recorded.

Otherwise nothing is written to the days store: no day, no records, no share. No "save?" question (Pini rejected it).

**The end state (`NS1`-`NS3`).** The approved empty end sheet (`EM`, `design/handoff/2026-11-25-redesign-empty-end/`) with three changes:

| | EM (approved) | NS (new) |
|---|---|---|
| Title | "הקלטה בלי ירידות" | "לא נרשמה גלישה בהר" |
| Under it | "היום · {time}" | a crossed-save icon and "ההקלטה לא נשמרה" |
| Buttons | "חזרה להר" + "מחיקה" | "חזרה להר" only, full width |

Unchanged: the three facts (recorded time, location found or not, where), the sample-day card that opens the sample day, the X, the day lines behind. `NS1` outside Gudauri, `NS2` in Gudauri after riding a lift and no descent (38:10), `NS3` night without location.

**Junk days already saved on test phones:** recommendation, a one-time silent cleanup on the update that ships this: every stored day that fails the rule is deleted; the sample day stays. Pini decides (question 3 on `MY00`).

## 2. The name

"ימי הגלישה שלי" as the page title, "ימי גלישה" on the menu tile (short). Content may propose another; the boards use these.

## 3. P.24: the days list (`MY1`-`MY3`)

- **Background:** the painted mountain under a light veil (the v91 rule: the picture fills the screen).
- **Trip card on top:** "גודאורי · ינואר 2027", four numbers (days, descents, m down, km skiing), one column per day (height = m down, newest day bold), then a row "השיאים שלך" with the two headline records as chips and a chevron to the records page.
- **One card per day, newest first:** a 104 x 104 window on the painted mountain framing that day's descents in the speed colours over the resort's runs in faint white (as `DM1`); "יום ה׳ · 14.1" and the hours; three numbers (descents, m down, km); one speed strip over the descents only (no lifts); a tag when the day holds a record ("שיא מהירות") or was cut ("נקטע ב-{time}"). A tap opens the day.
- **The sample day** sits at the end under "יום לדוגמה, לא נספר בשיאים", dimmed (`MY2`).
- **Before the first day** (`MY3`): "עוד אין ימי גלישה", a large window of the sample day that opens it, and "להקליט יום גלישה" at the thumb.
- Window framing: fit the day's descents with 25% margin, scale at least what covers the window (never past the picture's edge).

## 4. P.24: the records (`RS1`-`RS3`)

- **The headline record big:** the top speed on a window of the mountain with that descent and its point, the number over the picture ("68.9 קמ״ש"), the run, the date and "הערכה".
- **Four tiles, each with its own drawing:** the longest descent (km, its line on a small window, from-to runs), the biggest day (m down, the day's ridge), most descents (one bar per descent, height = drop), the steepest (degrees, a wedge at the true angle, the run).
- "חדש" on a record set on the most recent day (none in the boards: all sample records are from 11.1).
- A tap on a tile opens that day at that moment. The privacy line stays short: "רק מול עצמך. נשאר בטלפון."
- `RS3`: no records yet: the four tiles as ghosts with "—", and "להקליט יום גלישה".

## 5. Strings (to content after approval)

Existing: "חזרה להר", "דוגמה", "הוקלט", "מיקום", "נמצא", "לא נמצא", "איפה", "בגודאורי", "מחוץ לגודאורי", "השיאים שלך", "עוד אין שיאים", the record names, units.
New: "לא נרשמה גלישה בהר", "ההקלטה לא נשמרה", "ימי הגלישה שלי", "ימי גלישה" (tile), "עוד אין ימי גלישה", "להקליט יום גלישה", "יום לדוגמה, לא נספר בשיאים", "נקטע ב-{time}", "{n} ימים", "ק״מ גלישה", "הכי תלול", "הקטע התלול", "רק מול עצמך. נשאר בטלפון.", a screen-reader label for a day card ("{day}, {n} ירידות, {m} מטר למטה").
Plurals: "ירידה אחת" / "{n} ירידות", "יום אחד" / "{n} ימים" (Pini's shot had "1 ירידות").

## 6. Honesty

Numbers, run names and records come from the sample day, the analyzer and the moments engine. The four trip days in `MY1`/`MY2` are illustrative: cut from the sample day's descents, tagged "דוגמה". Checked in the design tool only, not on a phone.
