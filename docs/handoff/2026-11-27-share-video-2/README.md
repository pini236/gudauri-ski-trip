# Share video: nothing visible for a minute, and a narrow card (P.26)

Pini, 27.11.2026 16:16 (Israel), phone test of build 1091. His words: "בשיתוף כשלוחצים על כפתור ה play של הסרטון לא רואים כלום אבל מאחורי הקלעים זה מכין את הסרטון ומתחיל ל ג'ון אחרי כדקה וגם אחרי שזה מתחיל זה לא נראה טוב כי זה צר" (the odd word is probably dictation for "לנגן", to play).

Screenshots (his phone, 1544 px tall, scaled to 540 px wide here):
- `shot-1-before-play.jpg`: share page, tile "סרטון של דקה" chosen; the card shows the still picture (4:5) with a play circle. Under it the link "לראות את היום על ההר"; about 230 px of the screen (of 1544) is unused between the link and the buttons panel.
- `shot-2-playing-narrow.jpg`: after the tap and a wait of about a minute: "0:10 / 0:56", the pause button, the progress line. The film is a narrow 9:16 strip (about 410 of the card's 585 px) in the middle, and the still picture's numbers peek out at both card edges ("12 ירידות" on the right, "69 קמ״ש" on the left). It looks broken.

## What to build (branch `claude/android-share-video-2`, from current origin/main)

1. **DV1, the live preview in the card (approved, never built).** Spec: `design/handoff/2026-11-27-redesign-day-pages/README.md` section 4: "The card plays in place. The preview shows the same frames as the file (`StoryVideo` drawing the film into the card), so it plays at once, without waiting for the encoder." Today (part 2, `docs/handoff/2026-11-27-story-share-2.md`, "Not built") the first tap runs the encoder and the card plays the encoded file only when it is done. Make the tap play at once by drawing the film's frames into the card with the same drawing the encoder uses; the encoder keeps running in the background for "שיתוף" and "שמירה" (buttons fill and queue exactly as today). Pause, clock, progress line, replay and the "לראות את היום על ההר" link keep working. No new text.
2. **The card's shape in video mode.** The film is 9:16, the picture card is 4:5. In video mode the card takes the film's aspect (9:16), as large as the free height allows (use the unused height; the top bar, the link and the buttons panel keep their size), centred, solid dark background, and no still picture behind or beside the film. Picture mode (tile "תמונה") stays 4:5 as it is. Pause, clock and progress keep their place on the card.

Do not change the sheet (tiles, buttons, saved state), the story page or the player. If the card-size rule is plain arithmetic, add a unit test for it.

## Process

Rules are in `CLAUDE.md` and `app/android/CLAUDE.md`; read `docs/STATUS.md` first. Register your row in the STATUS area table and push it to main before starting. Gradle does not run in the cloud: CI (`android`, unit tests and compile) is the only compiler; push, poll the run of your branch with the status call, read only the failing step, and do not end your turn while it runs. Do not merge to main. Report to the manager session `session_01SJ69hvPgsv988fpVSzFq8z` with `send_message`: at most 10 lines (built, not built, guesses, what to check on a phone); one STATUS log line of at most three lines; the long version in `docs/handoff/2026-11-27-share-video-2/report.md`. Everything you write is English. If a usage limit stops you, say so in your last message.
