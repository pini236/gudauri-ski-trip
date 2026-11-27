# Share video (P.26): report

Built on `claude/android-share-video-2` (not merged):
- DV1: the first tap now plays the film at once in the card. `StoryVideo.scene()` exposes the encoder's own `Frame`; the card draws it on a Compose canvas scaled to the card, with its own clock (60 s, frame-driven). The VideoView is gone. The encoder still runs behind it (`withVideo()`), and "share" / "save" queue on it exactly as before.
- Video-mode card: 9:16, as large as the free height allows (`StoryRules.cardSize`, measured from the buttons panel and the link row), centred, dark background; the still picture is not shown once the film starts. Picture mode stays 4:5.
- Pause, clock, progress line, replay and the "see on the mountain" link unchanged. No new strings. Unit tests: `ShareCardSizeTest`.

Not verified: Gradle not run here (CI only); not tested on a phone.
Guesses / check on phone: frame rate of drawing the scene on the main thread (the mountain picture may be heavy: if it janks, move to a pre-rendered bitmap sequence); the free-height arithmetic (panel height + 30 dp margin); before the tap the still is cropped to 9:16 (center crop), fine?
