# First-run hints: gentle motion between hints (P.19, 27.11.2026)

**Status: waiting for Pini** (canvas https://claude.ai/artifact/8NrAKy97KWRj3JWcxx41yQ, page "רמזים: תנועה רכה בין רמז לרמז", boards `HM00`-`HM4`). Do not build before approval (rule 7). No new strings. Source: `design/redesign/tools/hm_boards.py` (the numbers below are its constants).

## Why it jumps today (`redesign/HintSequence.kt`)
- The ring snaps to 1.18x and shrinks in 200 ms on every step (`shrink`): reads as a pop, not motion.
- `bw`/`bh` are `remember(step)`: the bubble is alpha 0 until measured, then appears at once; when its side changes (the "mode" hint sits beside the column) it teleports.
- The run hint retargets `animateRectAsState` every 100 ms while the mountain draws: the ring jitters.
- A missing target waits 900 ms with the ring in the old place and no bubble, then skips.

## The spec (recommended: ring glides, bubble cross-fades)
| Element | Start | Duration | Easing | What |
|---|---|---|---|---|
| Old bubble | tap | 120 ms | `cubic-bezier(0.3,0,1,1)` | opacity 1 to 0, scale 1 to 0.98 |
| Ring (hole in the dim) | tap | `clamp(300 + 0.4 * distance_dp, 360, 560)` ms | `cubic-bezier(0.2,0,0,1)` (emphasized decelerate) | x, y, w, h and corner radius together, straight line; the run's white fill fades with it |
| New bubble | 55% of the glide | 220 ms | `cubic-bezier(0,0,0.2,1)` | opacity 0 to 1, slides 6 dp away from its target to rest |
| Breath | 80% of the glide | 420 ms | decelerate | a white halo leaves the ring 0 to 10 dp, alpha 0.6 to 0 (replaces the 1.18 shrink) |
| Hint timer (13 s) | when the new bubble is fully in | | | the X ring starts draining only then |

- The dim never changes; only its hole moves. The bubble never travels.
- Taps during a move are ignored until the new bubble starts (55%); after that a tap goes on from where the ring is (interruptible animation).
- The next target is resolved at tap time; a missing one is skipped before the glide (no 900 ms wait).
- Run hint: the ring follows the run point with a critically damped spring (about 250 ms settle), not a new tween every 100 ms.
- Start: dim fades in 240 ms, the ring appears with the breath, then the bubble. End (X or last): bubble out 120 ms, ring and dim out together 240 ms.
- Bubble size: measure off screen (or keep the old size until the new one is measured) so the new bubble never flashes.

## Reduced motion (`Motion.reduced`)
No glide, no slide, no breath: old ring and bubble fade out 100 ms, new ones fade in place 150 ms. Animator scale 0: instant. Screen reader: unchanged; the new hint is announced when its bubble appears.

## Honestly
Checked in the design tool only (CSS loops in `HM1`, `HM3`, `HM4`, frames in `HM2`), not on a phone. Durations and curves are my values. The boards use the hint dock of `HN1`-`HN6`; the menu dock of the UX pass does not change the motion.
