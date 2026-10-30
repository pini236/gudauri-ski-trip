"""Unit tests of the day analysis: python3 -m unittest discover -s tools/dayrec"""
import json, math, os, sys, unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import analyze, build, cases, net as netmod, rawlog, synth  # noqa: E402

P = build.params()
BASE = build.load_base(P)


def pt(t, s=None, sa=0.5, la=42.48, lo=44.49, **kw):
    q = {'t': t, 'la': la, 'lo': lo, 'a': None, 'p': None, 's': s, 'sa': sa, 'ha': 5.0, 'va': 8.0, 'b': None, 'm': None}
    q.update(kw)
    return q


def straight(speeds, t0=0):
    """Points on a line going east at the given speeds, one a second, positions that agree with them."""
    pts, xy, x = [], [], 0.0
    for i, s in enumerate(speeds):
        if i:
            x += (speeds[i - 1] + s) / 2
        la, lo = BASE.proj.ll(x, 0.0)
        pts.append(pt(t0 + i * 1000, s=s, la=la, lo=lo, b=90.0))
        xy.append((x, 0.0))
    return pts, xy


class RawLog(unittest.TestCase):
    def test_cut_lines_and_events(self):
        txt = '\n'.join([
            '{"v":1,"resort":"gudauri"}',
            '{"t":1000,"la":42.48,"lo":44.49,"s":1.0,"new_field":3}',
            '{"t":2000,"la":42.48,"lo":44.4',          # cut by a crash
            '{"ev":"resume","t":5000}',
            '{"t":5000,"la":42.48,"lo":44.49}',
            '{"t":5000,"la":42.49,"lo":44.49}',        # the same second twice: the first wins
            '{"t":6000,"la":42.48,"lo":44.4',          # cut at the end
        ])
        h, pts, evs, bad = rawlog.read(txt)
        self.assertEqual(h['resort'], 'gudauri')
        self.assertEqual([q['t'] for q in pts], [1000, 5000])
        self.assertEqual(pts[1]['la'], 42.48)
        self.assertNotIn('new_field', pts[0])
        self.assertEqual(evs, [{'ev': 'resume', 't': 5000}])
        self.assertEqual(bad, 2)


class MaxSpeed(unittest.TestCase):
    def test_held_two_readings(self):
        pts, xy = straight([10, 11, 12, 13, 20, 14, 13, 12, 11, 10])
        ok = analyze.valid_speeds(pts, xy, P)
        v, at = analyze.max_speed(pts, ok, range(len(pts)), P)
        self.assertEqual(v, 14)  # 20 is a jump of 6-7 m/s, allowed, but held for one reading: 20 and 14 hold 14

    def test_spike_is_refused(self):
        pts, xy = straight([15] * 10)
        pts[5]['s'] = 27.8  # 100 km/h, accuracy looks fine
        ok = analyze.valid_speeds(pts, xy, P)
        self.assertFalse(ok[5])
        v, _ = analyze.max_speed(pts, ok, range(len(pts)), P)
        self.assertEqual(v, 15)

    def test_inaccurate_reading_is_refused(self):
        pts, xy = straight([15] * 10)
        for q in pts[4:7]:
            q['s'] = 18; q['sa'] = 1.5
        ok = analyze.valid_speeds(pts, xy, P)
        self.assertEqual(analyze.max_speed(pts, ok, range(len(pts)), P)[0], 15)

    def test_speed_that_disagrees_with_positions(self):
        pts, xy = straight([10] * 12)
        for q in pts:
            q['s'] = 14  # the positions move 10 m a second; 14 is 40 % more
        ok = analyze.valid_speeds(pts, xy, P)
        self.assertIsNone(analyze.max_speed(pts, ok, range(len(pts)), P))

    def test_gap_breaks_the_window(self):
        pts, xy = straight([12] * 10)
        for q in pts[5:]:
            q['t'] += 30000
        ok = analyze.valid_speeds(pts, xy, P)
        self.assertFalse(ok[4]); self.assertFalse(ok[5])


class Altitude(unittest.TestCase):
    def test_drop_hysteresis(self):
        h = [100, 101, 99, 100, 98, 97, 90, 91, 89.5, 80]
        # noise under 3 m does not count; 100 -> 97 -> 90 -> 80 is 20 m
        self.assertAlmostEqual(analyze.drop(h, range(len(h)), P), 20.0)

    def test_barometer_wins_when_most_readings_have_it(self):
        pts = [pt(i * 1000, a=2000.0 + i, p=780.0) for i in range(4)] + [pt(4000, a=2004.0)]
        src, baro = analyze.alt_source(pts)
        self.assertTrue(baro)
        self.assertAlmostEqual(src[4], src[3])  # held from the last reading
        pts = [pt(i * 1000, a=2000.0 + i) for i in range(4)]
        src, baro = analyze.alt_source(pts)
        self.assertFalse(baro)
        self.assertEqual(src, [2000.0, 2001.0, 2002.0, 2003.0])

    def test_calibration_jump_guard(self):
        A = P['alt']
        offs = analyze.offsets([(1, 100.0), (2, 102.0), (3, 140.0), (4, 103.0)], A)
        self.assertEqual([t for t, _ in offs], [1, 2, 4])          # 140 is a jump of 38 m: refused
        self.assertEqual(offs[-1][1], 102.0)                       # median of 100, 102, 103
        offs = analyze.offsets([(1, 100.0), (2, 140.0), (3, 141.0)], A)
        self.assertEqual([t for t, _ in offs], [1, 3])             # two that agree win over one bad station
        self.assertEqual(offs[-1][1], 120.5)


class Network(unittest.TestCase):
    def test_mid_station_far_from_the_cable_fails(self):
        g = next(l for l in BASE.lifts if l.name == 'Goodaura')
        (x, y), (ux, uy) = g.line.at(g.length / 2)
        la, lo = BASE.proj.ll(x - uy * 80, y + ux * 80)
        with self.assertRaises(ValueError):
            netmod.Net(BASE.raw_rl, BASE.raw_tj, P, {'lifts': {g.id: {'mid': [{'pos': [la, lo], 'osm': 'node/0'}]}}})

    def test_mid_station_from_the_patch(self):
        n = netmod.Net(BASE.raw_rl, BASE.raw_tj, P, cases.mid_patch(BASE))
        g = next(l for l in n.lifts if l.name == 'Goodaura')
        self.assertEqual([s['id'] for s in g.stations], ['bottom', 'mid1', 'top'])
        self.assertAlmostEqual(g.stations[1]['u'], g.length * cases.MID_PATCH_U, delta=1)
        # the patch never leaks into the base network
        self.assertEqual(len(next(l for l in BASE.lifts if l.name == 'Goodaura').stations), 2)

    def test_no_mid_station_in_the_data(self):
        # OpenStreetMap, 30.10.2026: every aerialway=station in Gudauri is at an end of its lift (tools/dayrec/README.md)
        self.assertFalse(any(l.get('mid') for l in BASE.raw_rl['lifts']))

    def test_shared_stretch_of_tatra(self):
        t2 = next(q for q in BASE.pistes if q.key == 'Tatra 2')
        end = t2.samples[-20][1]
        self.assertEqual(end, ('Tatra 1', 'Tatra 2'))      # the lower stretch is shared
        self.assertEqual(t2.samples[30][1], ('Tatra 2',))  # in the middle, Tatra 2 alone

    def test_lifts_bottom_to_top(self):
        for l in BASE.lifts:
            self.assertLess(l.stations[0]['ele'], l.stations[-1]['ele'], l.name)

    def test_projection_matches_the_site(self):
        x, y = BASE.proj.xy(42.5, 44.5)
        self.assertAlmostEqual(x, 0.005 * 111320 * math.cos(math.radians(42.51)), places=6)
        self.assertAlmostEqual(y, 0.01 * 111320, places=6)


class Day(unittest.TestCase):
    def analyse(self, d, seed=7, baro=True):
        synth.truth_labels(BASE, d.rows)
        pts = synth.sense(BASE, d, seed, baro=baro)
        txt = synth.write_log(pts, {'v': 1, 'resort': 'gudauri'})
        h, p, e, _ = rawlog.read(txt)
        a = analyze.Analyzer(BASE, P)
        return a.final(h, p, e), a.live(h, p, e)

    def test_ride_and_descent(self):
        for baro in (True, False):
            d = synth.Day(BASE, 3)
            cases.run_single(d)
            fin, liv = self.analyse(d, baro=baro)
            types = [s['type'] for s in fin['segments']]
            # the wait, the ride, the stop at the top, the descent: each edge where it happened
            self.assertEqual(types[:4], ['stop', 'lift', 'stop', 'descent'], baro)
            lift = fin['segments'][1]
            self.assertLessEqual(abs(lift['t0'] - d.rides[0]['t0']), 2000)
            self.assertLessEqual(abs(lift['t1'] - d.rides[0]['t1']), 2000)
            self.assertEqual((lift['name'], lift['from'], lift['to']), ('Goodaura', {'station': 'bottom'}, {'station': 'top'}))
            desc = fin['segments'][3]
            self.assertIn('Goodaura 1', desc['legs'][0]['runs'])
            self.assertAlmostEqual(desc['drop_m'], 545, delta=25)
            self.assertAlmostEqual(desc['top_m'], lift_top(), delta=15)
            self.assertEqual(fin['baro'], baro)

    def test_standing_is_not_walking(self):
        d = synth.Day(BASE, 4)
        cases.start(d, 'Goodaura').stand(600)
        fin, liv = self.analyse(d)
        self.assertEqual([s['type'] for s in fin['segments']], ['stop'])
        self.assertEqual(fin['segments'][0]['kind'], 'rest')
        self.assertEqual(fin['stats']['max_kmh'], None)

    def test_off_piste_is_not_named(self):
        d = synth.Day(BASE, 5)
        cases.offpiste(d)
        fin, _ = self.analyse(d)
        desc = next(s for s in fin['segments'] if s['type'] == 'descent')
        off = [lg for lg in desc['legs'] if lg['kind'] in ('off', 'unmatched')]
        self.assertTrue(off)
        self.assertGreater(sum(lg['dist_m'] for lg in off), 250)

    def test_falls(self):
        d = synth.Day(BASE, 6)
        cases.falls(d)
        synth.truth_labels(BASE, d.rows)
        pts = synth.sense(BASE, d, 6)
        import random
        rng = random.Random(1)
        evs = [synth.impact_window(i, rng, P) for i in d.impacts if i['peak_g'] >= P['fall']['trigger_g']]
        txt = synth.write_log(pts, {'v': 1}, evs)
        h, p, e, _ = rawlog.read(txt)
        a = analyze.Analyzer(BASE, P)
        fin = a.final(h, p, e)
        # one real fall among a jump, the phone in the pocket, a bump, a hard stop and a bump on the gondola
        self.assertEqual(len(d.falls), 1)
        self.assertEqual(len(fin['falls']), 1)
        self.assertLessEqual(abs(fin['falls'][0]['t'] - d.falls[0]), 3000)
        live = a.live(h, p, e)['falls']
        self.assertEqual([f['t'] for f in live], [f['t'] for f in fin['falls']])
        self.assertGreater(live[0]['at'], live[0]['t'])
        # "I did not fall": the window is gone, a summary line stays, and it never counts
        e2 = e + [{'ev': 'impact', 't': int(d.falls[0]), 'dismissed': True}]  # a second line, append-only
        self.assertEqual(a.final(h, p, e2)['falls'], [])
        # closing the day deletes the dismissed window for real, and nothing else
        txt2 = txt + rawlog.line({'ev': 'impact', 't': int(d.falls[0]), 'dismissed': True}) + '\n'
        closed = rawlog.compact(txt2)
        h3, p3, e3, _ = rawlog.read(closed)
        self.assertEqual(p3, p)
        self.assertFalse(any(x.get('t') == int(d.falls[0]) and 'acc' in x for x in e3))
        self.assertEqual(len([x for x in e3 if 'acc' in x]), len([x for x in e if 'acc' in x]) - 1)
        self.assertEqual(a.final(h3, p3, e3)['falls'], [])
        # the hard stop looks like a fall to the Doppler speed alone
        self.assertGreaterEqual(len(analyze.falls_doppler(p, [q['t'] for q in p], a._state, P)), 2)

    def test_offpiste_event(self):
        for builder, n in ((cases.offpiste, 1), (cases.offpiste_short, 0)):
            d = synth.Day(BASE, 8)
            builder(d)
            fin, _ = self.analyse(d)
            ev = [o for s in fin['segments'] if s['type'] == 'descent' for o in s['offpiste']]
            self.assertEqual(len(ev), n, builder.__name__)
            for o in ev:
                self.assertGreaterEqual(o['drop_m'], P['offpiste']['drop_m'])

    def test_driving_is_not_skiing(self):
        for builder in (cases.drive_down, cases.drive_up):
            d = synth.Day(BASE, 11)
            builder(d)
            fin, liv = self.analyse(d)
            car = [r['t'] for r in d.rows if r['act'] == 'drive']
            drv = [s for s in fin['segments'] if s['type'] == 'drive']
            self.assertEqual(len(drv), 1, builder.__name__)
            self.assertLessEqual(abs(drv[0]['t0'] - car[0]), 2000)
            self.assertLessEqual(abs(drv[0]['t1'] - car[-1]), 2000)
            self.assertGreater(drv[0]['dist_m'], 3000)
            # nothing of the car in a descent or in the skiing figures: one descent, the run on Pirveli
            ds = [s for s in fin['segments'] if s['type'] == 'descent']
            self.assertEqual(len(ds), 1)
            self.assertFalse(any(ds[0]['t0'] <= t <= ds[0]['t1'] for t in car))
            self.assertEqual(fin['stats']['descents'], 1)
            # live: "drive" once the stretch is long enough, and before the car stops
            t = next(e['t'] for e in liv['events'] if e['act'] == 'drive')
            self.assertLessEqual(t - car[0], (P['drive']['min_s'] + 10) * 1000)

    def test_empty_log(self):
        h, p, e, _ = rawlog.read('{"v":1}\n')
        a = analyze.Analyzer(BASE, P)
        self.assertEqual(a.final(h, p)['segments'], [])
        self.assertEqual(a.live(h, p)['events'], [])


def lift_top():
    return next(l for l in BASE.lifts if l.name == 'Goodaura').stations[-1]['ele']


if __name__ == '__main__':
    unittest.main()
