"""The test cases of the day analysis (docs/ARCHITECTURE.md m-8, condition 4), built on the real resort data.

Each case is a function that builds a synthetic Day; build.py turns it into a raw log and the
expected analysis. The same plan with other seeds makes the "stress" days of check.py.
"""
import synth

# The run lines used here (key, OSM way), all from site/data/runs-and-lifts.json
G1 = ('Goodaura 1', 668794714)
T1 = ('Tatra 1', 472374255)
T2 = ('Tatra 2', 668794706)
S1 = ('Soliko 1', 158744057)
BOMBORA = ('Bombora', 158817286)
K2_TOP = ('Kudebi 2', 1483001620)
K2_LOW = ('Kudebi 2', 1235084637)
SNOWPARK = ('Snow Park', 158744071)
SNOWPARK_TOP = ('Snow Park', 1259783830)
SADZELE1 = ('Sadzele 1', 1478541348)
KOBI = ('Kobi', 668794718)
FIRNI1 = ('Firni 1', 668794719)
FIRNI2 = ('Firni 2', 668794719)

# a station that is NOT in the data: only to exercise lifts[].mid (docs/ARCHITECTURE.md m-8, answer 4)
MID_PATCH_U = 0.55


def mid_patch(net):
    l = next(x for x in net.lifts if x.name == 'Goodaura')
    (x, y), _ = l.line.at(l.length * MID_PATCH_U)
    la, lo = net.proj.ll(x, y)
    return {'lifts': {l.id: {'mid': [{'pos': [round(la, 6), round(lo, 6)], 'osm': 'node/0', 'name': 'TEST ONLY'}]}}}


def start(d, lift):
    l = d.lift(lift)
    return d.at(*l.line.p[0])


# ---------- the cases ----------

def lift_basic(d):
    start(d, 'Goodaura').stand(150).ride('Goodaura').stand(40)


def lift_stopped(d):
    """The Soliko chair lift runs 40 % faster than the data says, and stops for two minutes half way."""
    start(d, 'Soliko').stand(45).ride('Soliko', speed=1.4, pause=(0.5, 120)).stand(40)


def lift_mid_station(d):
    """Board at the bottom, get off at the middle station (a test-only station), ski down from there."""
    start(d, 'Goodaura').stand(40).ride('Goodaura', 0.0, MID_PATCH_U).stand(45).ski(*G1)


def lift_unmapped_stop(d):
    """The same ride without the station: the app must say 'not a mapped station', not invent one."""
    lift_mid_station(d)


def run_single(d):
    start(d, 'Goodaura').stand(30).ride('Goodaura').stand(20).ski(*G1).stand(40)


def run_to_run(d):
    """Kudebi 2 (both lines) into Soliko 1, down to the Soliko lift."""
    start(d, 'Kudebi').stand(30).ride('Kudebi').stand(15).ski(*K2_TOP, frm='start').ski(*K2_LOW).ski(*S1).stand(40)


def shared_tatra(d):
    """Shino lift, Tatra 1 to the fork, Tatra 2 down; the lower part is shared with Tatra 1."""
    start(d, 'Shino').stand(30).ride('Shino').stand(15).ski(*T1, until=T2).ski(*T2).stand(40)


def offpiste(d):
    start(d, 'Goodaura').stand(30).ride('Goodaura').stand(15)
    d.offpiste(*G1, 0.25, 0.6, 160)
    d.stand(40)


def queue_and_hut(d):
    """A long queue at the bottom, then a long break (a hut) at the top, then a descent with a rest on the way."""
    start(d, 'Goodaura').stand(420).ride('Goodaura').stand(25 * 60).ski(*G1, stop=(0.5, 90)).stand(40)


def gps_spike(d):
    run_single(d)


def signal_loss(d):
    run_single(d)


def crash_resume(d):
    start(d, 'Goodaura').stand(30).ride('Goodaura').stand(20).ski(*G1).stand(30).ride('Goodaura').stand(20).ski(*G1).stand(30)


def full_day(d):
    g = d.lift('Goodaura')
    d.at(*g.line.p[0]).stand(200)
    d.ride('Goodaura').stand(20).ski(*G1)                                    # 1
    d.stand(60).ride('Goodaura').stand(20).offpiste(*G1, 0.25, 0.6, 160)     # 2, off-piste
    d.stand(45).ride('Goodaura').stand(30).ski(*T1)                          # 3, Tatra 1 to Shino
    d.stand(30).ride('Shino', speed=0.7).stand(15).ski(*T1, until=T2).ski(*T2)  # 4, Tatra 2 (shared); a slow lift
    d.stand(30).ride('Shino').stand(15).ski(*G1, stop=(0.4, 70))             # 5, Goodaura 1 with a rest
    d.stand(90).ride('Goodaura').stand(20)                                   # 6
    d.walk_to_lift('Firni').ride('Firni').stand(30).ski(*KOBI)               # Kobi
    d.ride('Kobi 2').stand(35 * 60)                                          # lunch at the top
    d.walk_to(*d.run_line(*FIRNI1).p[0]).ski(*FIRNI1, frm='start').ski(*FIRNI2)
    d.walk_to_lift('Snow Park').stand(60).ride('Snow Park').stand(15)
    d.marks['spike'] = d.t + 40000                                           # 7, one reading of 100 km/h
    d.ski(*SNOWPARK, frm='start')
    d.stand(40).ride('Snow Park').stand(15).ski(*SNOWPARK_TOP, frm='start')  # 8, to the Sadzele lift
    d.walk_to_lift('Sadzele').stand(90).ride('Sadzele').stand(20).ski(*SADZELE1, frm='start').ski(*SNOWPARK)
    d.walk_to(*d.run_line(*G1).p[0]).stand(30)
    d.marks['loss'] = d.t + 100000                                           # 9, a minute without signal on the way
    d.ski(*G1, frm='start')
    d.walk_to_lift('Soliko').stand(120).ride('Soliko', pause=(0.6, 90)).stand(20).ski(*BOMBORA, frm='start')
    d.walk_to_lift('Kudebi').stand(60).ride('Kudebi').stand(20).ski(*K2_TOP, frm='start').ski(*K2_LOW).ski(*S1)
    d.stand(60)


CASES = [
    # name, builder, about, options
    ('lift_basic', lift_basic, 'A long queue, then the Goodaura gondola from the bottom to the top station.', {}),
    ('lift_stopped', lift_stopped, 'The Soliko chair lift, 40 % faster than the data, stopped for two minutes half way: one ride.', {}),
    ('lift_mid_station', lift_mid_station, 'Goodaura gondola, off at a middle station that exists only in this test (network_patch), then Goodaura 1.', {'patch': True}),
    ('lift_unmapped_stop', lift_unmapped_stop, 'The same ride and descent, without the test station: the stop is not a mapped station.', {}),
    ('run_single', run_single, 'One descent on one run: Goodaura 1.', {}),
    ('run_single_nobaro', run_single, 'The same, without a barometer (iPhone without the motion permission, simulator).', {'baro': False}),
    ('run_to_run', run_to_run, 'From run to run: Kudebi 2 (two lines) into Soliko 1.', {}),
    ('shared_tatra', shared_tatra, 'Tatra 1 to the fork, then Tatra 2; the lower stretch is shared with Tatra 1 and is shown as both.', {}),
    ('offpiste', offpiste, 'Goodaura 1, with 160 m off the runs and back.', {}),
    ('queue_and_hut', queue_and_hut, 'A 7 minute queue, a 25 minute break at the top, and a rest in the middle of the run.', {}),
    ('gps_spike', gps_spike, 'One reading of 100 km/h with a jump of the position, in the middle of the descent.', {'spike': 0.45}),
    ('signal_loss', signal_loss, 'No readings for a minute in the middle of the descent.', {'loss': (0.4, 60)}),
    ('crash_resume', crash_resume, 'The app was killed: the last line is cut in half, 90 seconds are missing, then a resume event.', {'crash': 0.5}),
    ('full_day', full_day, 'A full day: 13 rides on 8 lifts (one slow, one stopped), Tatra 2 with the shared stretch, off-piste, Kobi, lunch, a minute without signal.', {}),
]
