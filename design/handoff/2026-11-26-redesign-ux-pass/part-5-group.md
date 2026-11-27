# Part 5: my group (items 7, 8). Sonnet

Boards `GR1`-`GR4` (`ref/GR*.jpg`). **Not before Pini approves.** The group's rules, the server calls and the four tabs do not change; base spec: `design/handoff/2026-11-23-redesign-people/README.md`, sections 3 and 4.

## 1. The name

"החבר׳ה" becomes "הקבוצה שלי" everywhere it is a title or an entry (`app.grp_title`): the menu tile, the row under "אני", the hints. Inside a group the title is the group's own name, as today.

## 2. With no group (`GR1`, `GR2`): replaces `GateSheet`

Today the sheet leads with six code boxes, which most people do not have, and "יצירת קבוצה" is the secondary button.

- **Backdrop:** the mountain under a veil `rgba(40,62,110,.22)` (not the village). A round back button at the top start.
- **Sheet,** padding 10 / 16 / 16, gap 12:
  1. "נוסעים עם חברים?" (21 / 700) and under it "קבוצה אחת לכל החברים בטיול" (13.5 `ink2`).
  2. Three small pictures in a row (each 108 high, radius 18, background `tile`, border 1 `line`; the drawing 52 high, the caption 12.5 / 500 under it). Not tappable:
     - a plane and three avatars: "מי טס מתי"
     - a flag on a small slope: "איפה נפגשים"
     - three bars, the middle one tall and `acc`: "מי מוביל במשחקים"
  3. **"קבוצה חדשה"** (primary, with a plus): for a guest the sign-in (`R08`) and then the form; signed in, the form. As today.
  4. **"יש לי קוד הזמנה"** (secondary, height 52): the sheet changes to `GR2`.
- **`GR2`:** the same head and pictures; "קוד ההזמנה" (14 / 700), the six boxes with the keyboard open, "חזרה" (secondary) and "הצטרפות" (primary, 45% until six letters). All the states of the code (checking, wrong, too many tries, full, offline) as in the people handoff, section 3.
- The line `app.g_rest_works` is removed.
- A link that opens the app with a code still goes straight to `JoinScreen`.

## 3. Loading without a jump (`GR3`, `GR4`)

Three places jump today; one rule fixes them: **show what the phone already knows, at the final size, and never change the frame when the answer arrives.**

**Save the groups on the phone.** After every successful load, store the list (id, name, member count, dates, my role, today's next meetup) in the app's private storage. Every screen below first draws from this copy, then refreshes in the background and cross-fades changed text in place (150 ms). Cleared on sign-out and on account deletion.

| Where | Today | New |
|---|---|---|
| The group entry (the village row today; the menu tile after part 1) | says "none" while loading, then changes | the saved state at once. First ever load: a 90 x 12 skeleton bar; a tap during it waits for the answer (the tile shows a pressed state), it does not open the gate |
| "אני" (`MeScreen`) | shows the sign-in box and "החבר׳ה", then swaps to "הקבוצות שלי" | the saved list at once. First ever load for a signed-in user: the head "הקבוצות שלי" and two skeleton rows of the real row height (64). Rows that arrive beyond two are added below; nothing above them moves |
| The group sheet (`GroupSheet`) | the top is 292 or 254 by tab; the tab is unknown until the group loads, so the sheet jumps 38 and the skeleton is replaced | **the top is always 254**, for every tab; a shorter tab leaves space above its bottom button. The opening tab is decided before the sheet opens, from the saved copy (the same rule as today); with no saved copy it is "מפגשים", and it does not switch by itself after the load |

**The skeleton of the group sheet (`GR3`)** has the real frame: the head (two bars, 150 x 22 and 110 x 13, in a 48-high row; "הזמנה" at 45%), the real tabs with the opening tab already marked, a card of the real height (150) with three bars, two rows of 60 with two bars each, and the bottom button at 45%. When the group arrives (`GR4`) every element is replaced where it stands, in a 150 ms cross-fade.

- Skeleton bar: radius 8, `line`, with a light sweep every 1.4 s; reduced motion: no sweep.
- A load that fails: the saved copy stays, with `app.err_offline` in a pill at the top of the sheet; with nothing saved, the existing error state inside the same frame.
- Stored on the phone only; nothing new is sent. No contract change.
