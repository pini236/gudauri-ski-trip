#!/usr/bin/env python3
"""Round 7, part 3: the horizontal pass (A+B), after Pini chose it.

Two whole passes, outbound and return, one behind the other. Tapping the one behind
shuffles them: the front pass slides out and down, the back one comes forward, with a
paper swish and a soft tap. Tapping the stub tears it along the perforation, tick by tick,
with a paper-tearing sound and a matching vibration, then it falls and comes back.
AB5 by day, AB6 at night. Sounds are made in the browser (Web Audio), no files.
Run from the repo root: python3 design/round7/build_pair.py"""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from build import O, R, TRIP, DISP, plane, d, ridge_barcode, frame  # noqa: E402
from build_mix import page, grain, notch_css, pal, L  # noqa: E402

root = pathlib.Path(__file__).resolve().parent.parent.parent

# the torn edge of the stub: teeth along the perforation (the stub's right edge)
TEETH = ', '.join(f'{100 if i % 2 else 93}% {i * 100 / 24:.2f}%' for i in range(25))
TORN_CLIP = f'polygon(0 0, {TEETH}, 0 100%)'

JS = r'''class Component extends DCLogic {
ac() {
  try {
    if (!this.audio) { const A = window.AudioContext || window.webkitAudioContext; if (!A) return null; this.audio = new A(); }
    if (this.audio.state === 'suspended') this.audio.resume();
    return this.audio;
  } catch (e) { return null; }
}
noise(ac) {
  if (!this.nbuf) {
    const n = ac.sampleRate, b = ac.createBuffer(1, n, ac.sampleRate), ch = b.getChannelData(0);
    for (let i = 0; i < n; i++) ch[i] = Math.random() * 2 - 1;
    this.nbuf = b;
  }
  return this.nbuf;
}
burst(ac, t, dur, f, q, g, f2) {
  const s = ac.createBufferSource(); s.buffer = this.noise(ac);
  const bp = ac.createBiquadFilter(); bp.type = 'bandpass'; bp.Q.value = q;
  bp.frequency.setValueAtTime(f, t); if (f2) bp.frequency.exponentialRampToValueAtTime(f2, t + dur);
  const v = ac.createGain();
  v.gain.setValueAtTime(0.0001, t); v.gain.exponentialRampToValueAtTime(g, t + Math.min(0.004, dur / 3)); v.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  s.connect(bp); bp.connect(v); v.connect(ac.destination);
  s.start(t, Math.random() * 0.6, dur + 0.05);
}
buzz(p) { try { if (navigator.vibrate) navigator.vibrate(p); } catch (e) {} }
tearSound() {
  const ac = this.ac(); if (!ac) return;
  const t0 = ac.currentTime + 0.01;
  /* the rip itself: a fibrous hiss that rises as the tear runs down */
  this.burst(ac, t0, 0.44, 1300, 0.9, 0.16, 3600);
  /* one tick per hole of the perforation, a little faster each time */
  let t = t0;
  for (let i = 0; i < 15; i++) {
    this.burst(ac, t, 0.016 + Math.random() * 0.012, 2000 + Math.random() * 3000, 1.4, 0.32 + Math.random() * 0.3);
    t += 0.031 - i * 0.0011;
  }
  /* the last fibres let go */
  this.burst(ac, t + 0.01, 0.07, 1700, 0.8, 0.55, 900);
}
swishSound() {
  const ac = this.ac(); if (!ac) return;
  const t0 = ac.currentTime + 0.01;
  /* card sliding over card */
  this.burst(ac, t0, 0.24, 700, 0.7, 0.22, 2600);
  /* the edge flicks past */
  this.burst(ac, t0 + 0.2, 0.035, 3200, 1.2, 0.3);
  /* and lands on the pile: a soft paper tap */
  const o = ac.createOscillator(), v = ac.createGain(), t1 = t0 + 0.27;
  o.type = 'sine'; o.frequency.setValueAtTime(150, t1); o.frequency.exponentialRampToValueAtTime(70, t1 + 0.09);
  v.gain.setValueAtTime(0.0001, t1); v.gain.exponentialRampToValueAtTime(0.28, t1 + 0.006); v.gain.exponentialRampToValueAtTime(0.0001, t1 + 0.12);
  o.connect(v); v.connect(ac.destination); o.start(t1); o.stop(t1 + 0.14);
  this.burst(ac, t1, 0.05, 1500, 0.8, 0.18);
}
renderVals() {
  const s = this.state || {};
  const front = s.front || 'out', phase = s.phase || 0, torn = s.torn || 0;
  const night = %(night)s;
  const days = Math.max(0, Math.ceil((new Date('2027-01-10T16:00:00+02:00') - new Date()) / 864e5));
  const FRONT = 'translate(0px, 0px) rotate(-2deg) scale(1)';
  const BACK = 'translate(-12px, -58px) rotate(2.6deg) scale(.955)';
  const OUT = 'translate(26px, 84px) rotate(-7deg) scale(1)';
  const MID = 'translate(-4px, -26px) rotate(.6deg) scale(.985)';
  const pos = (k) => {
    const f = front === k;
    return {
      t: phase === 1 ? (f ? OUT : MID) : (f ? FRONT : BACK),
      z: f ? 2 : 1,
      dim: f ? '0' : (night ? '.28' : '.10'),
      back: !f
    };
  };
  const po = pos('out'), pr = pos('ret');
  const stub = (k) => {
    if (front !== k || !torn) return { t: 'none', o: '1', clip: 'none', ease: 'transform .35s ease-out, opacity .3s' };
    if (torn === 1) return { t: 'rotate(-9deg) translate(-4px, 2px)', o: '1', clip: '%(clip)s', ease: 'transform .38s cubic-bezier(.4,.1,.6,1)' };
    return { t: 'translate(-58px, 210px) rotate(-34deg)', o: '0', clip: '%(clip)s', ease: 'transform .62s cubic-bezier(.45,0,.8,.6), opacity .5s ease-in .15s' };
  };
  const so = stub('out'), sr = stub('ret');
  return {
    days: days, word: night ? 'לילות' : 'ימים',
    outT: po.t, outZ: po.z, outDim: po.dim, outBack: po.back,
    retT: pr.t, retZ: pr.z, retDim: pr.dim, retBack: pr.back,
    outStubT: so.t, outStubO: so.o, outStubClip: so.clip, outStubEase: so.ease,
    retStubT: sr.t, retStubO: sr.o, retStubClip: sr.clip, retStubEase: sr.ease,
    swap: () => {
      if (this.busy) return; this.busy = true;
      this.swishSound(); this.buzz(8);
      this.setState({ phase: 1, torn: 0 });
      setTimeout(() => this.setState({ front: (this.state || {}).front === 'ret' ? 'out' : 'ret', phase: 2 }), 250);
      setTimeout(() => { this.buzz(12); }, 280);
      setTimeout(() => { this.setState({ phase: 0 }); this.busy = false; }, 640);
    },
    tear: () => {
      if (this.busy) return; this.busy = true;
      this.tearSound(); this.buzz([6, 24, 6, 24, 6, 24, 6, 24, 6, 24, 30]);
      this.setState({ torn: 1 });
      setTimeout(() => this.setState({ torn: 2 }), 420);
      setTimeout(() => { this.setState({ torn: 0 }); this.busy = false; }, 2600);
    }
  };
}
}'''


def card(p, k, f):
    """One whole pass. k is 'out' or 'ret'; f is the flight in trip.json."""
    out = k == 'out'
    fc, tc = ('TLV', 'TBS') if out else ('TBS', 'TLV')
    if out:
        stub_body = (f'{L("עוד", p)}<b class="{p["g"]}" style="{DISP} font-size: 60px; line-height: .8; color: {p["acc"]}">{{{{days}}}}</b>{L("{{word}} לטיסה", p)}')
    else:
        stub_body = (f'{L("קודם", p)}<b class="{p["g"]}" style="{DISP} font-size: 60px; line-height: .8; color: {p["acc"]}">4</b>{L("ימי סקי", p)}')
    note = 'הלוך' if out else 'חזור · בלילה'
    return (f'<div style="position: absolute; top: 60px; right: 0; left: 0; height: 252px; transform: {{{{{k}T}}}}; z-index: {{{{{k}Z}}}}; transition: transform .34s cubic-bezier(.25,.8,.3,1); transform-origin: 50% 60%; '
            f'filter: drop-shadow(0 10px 14px rgba(0,0,0,{.42 if p["pc"] != "paper" else .2}))">'
            f'<div style="position: relative; display: flex; height: 100%">'
            # main part
            f'<div class="nl {p["pc"]}" style="position: relative; flex: 1; min-width: 0; background-color: {p["paper"]}; display: flex; flex-direction: column">{grain(.09 if p["pc"] == "paper" else .07, p["pc"] != "paper")}'
            f'<div style="background: {p["strip"]}; color: {p["onstrip"]}; display: flex; justify-content: space-between; align-items: center; padding: 7px 16px; font-size: 12px; font-weight: 700; letter-spacing: .04em">'
            f'<span>{TRIP["airline"]} · כרטיס עלייה · {note}</span><span>{d(f["date"])}</span></div>'
            f'<div style="padding: 10px 16px 12px; display: flex; flex-direction: column; gap: 10px">'
            f'<div style="display: flex; align-items: flex-end; justify-content: space-between; gap: 6px">'
            f'<div>{L("מ", p)}<div class="{p["g"]}" style="{DISP} font-size: 50px; line-height: .8; color: {p["ink"]}" dir="ltr">{fc}</div><span style="font-size: 12.5px; font-weight: 600; color: {p["ink"]}">{f["from"]}</span></div>'
            f'<div class="{p["gs"]}" style="align-self: center">{plane(28, p["acc"])}</div>'
            f'<div style="text-align: left">{L("אל", p)}<div class="{p["g"]}" style="{DISP} font-size: 50px; line-height: .8; color: {p["ink"]}" dir="ltr">{tc}</div><span style="font-size: 12.5px; font-weight: 600; color: {p["ink"]}">{f["to"]}</span></div></div>'
            f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {p["rule"]}; padding-top: 8px">'
            f'<div>{L("טיסה", p)}<b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}" dir="ltr">{f["flight"]}</b></div>'
            f'<div>{L("המראה", p)}<b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}">{f["departs"]}</b></div><div>{L("נחיתה", p)}<b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}">{f["arrives"]}</b></div></div>'
            f'<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; border-top: 1px solid {p["rule"]}; padding-top: 8px">'
            f'<div>{L("נוסעים", p)}<b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}">6 החבר׳ה</b></div><div>{L("כבודה", p)}<b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}">תיק יד</b></div>'
            f'<div>{L("ימי סקי", p)}<b class="{p["g"]}" style="font-size: 14px; color: {p["ink"]}">11–14.1</b></div></div></div></div>'
            # stub: an empty slot behind it, and the stub itself
            f'<div style="position: relative; width: 100px; flex: none">'
            f'<div style="position: absolute; inset: 0; border: 2px dashed {p["dash"]}; border-right: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; font-size: 12px; color: {p["muted"]}">נתלש!<br>נתראה בשדה</div>'
            f'<button class="tap nr {p["pc"]}" onClick="{{{{tear}}}}" aria-label="לתלוש את הספח" style="position: relative; width: 100%; height: 100%; background-color: {p["paper2"]}; border-right: 2px dashed {p["dash"]}; '
            f'display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 6px 10px; box-sizing: border-box; text-align: center; '
            f'transform: {{{{{k}StubT}}}}; opacity: {{{{{k}StubO}}}}; clip-path: {{{{{k}StubClip}}}}; transition: {{{{{k}StubEase}}}}; transform-origin: 100% 100%">'
            f'{grain(.09 if p["pc"] == "paper" else .07, p["pc"] != "paper")}{stub_body}'
            f'<span class="{p["g"]}" style="{DISP} font-size: 20px; line-height: 1; margin-top: 4px; color: {p["ink"]}" dir="ltr">{fc} › {tc}</span>'
            f'<span class="{p["gs"]}" style="margin-top: auto">{ridge_barcode(80, 28, p["ink"])}</span></button></div>'
            # the pass behind is a little in shadow, and the whole of it is the button that brings it forward
            f'<span style="position: absolute; inset: 0; pointer-events: none; background: #000; opacity: {{{{{k}Dim}}}}; transition: opacity .3s"></span>'
            f'<sc-if value="{{{{{k}Back}}}}" hint-placeholder-val="{{{{ {"false" if out else "true"} }}}}">'
            f'<button class="tap" onClick="{{{{swap}}}}" aria-label="להביא קדימה את כרטיס ה{"הלוך" if out else "חזור"}" style="position: absolute; inset: 0; background: transparent; z-index: 3"></button></sc-if>'
            f'</div></div>')


def pair(p):
    return (f'<div style="position: relative; height: 330px">{card(p, "out", O)}{card(p, "ret", R)}</div>'
            f'<div style="margin-top: 14px; font-size: 12px; color: {p["muted"]}; text-align: center">נגיעה בכרטיס שמאחור מחליפה · נגיעה בספח תולשת</div>')


CSS = notch_css('nl', 'left') + notch_css('nr', 'right')
out = {
    'AB5-PairDay.dc.html': page('הלרוחב: שני כרטיסים', frame(pair(pal(False)), top_card=150, signs_top=550), JS % dict(night='false', clip=TORN_CLIP), CSS),
    'AB6-PairNight.dc.html': page('הלרוחב: שני כרטיסים בלילה', frame(pair(pal(True)), top_card=150, night=True, signs_top=550), JS % dict(night='true', clip=TORN_CLIP), CSS, night=True),
}

if __name__ == '__main__':
    dst = root / 'design/canvas/project'
    for n, s in out.items():
        (dst / n).write_text(s, encoding='utf-8')
    print({n: len(s) for n, s in out.items()})
