#!/usr/bin/env python3
"""Feeds the emulator's GPS with one real-shaped descent, once a second, with speed and altitude (13.11.2026).
The emulator's "geo fix" has no speed, so the live screen showed 0 km/h. Here the longest fast stretch of the reference
tool's synthetic day (tools/fixtures/dayrec-logs/full_day.jsonl: made from the real Tatra lines, made-up times) goes out as
NMEA (GGA with the altitude, RMC with speed and course). Usage: gpsreplay.py <full_day.jsonl>. Runs until killed."""
import json, subprocess, sys, time, math
from datetime import datetime, timezone

rows = [r for r in map(json.loads, open(sys.argv[1])) if "la" in r]
best, cur = [], []
for i, r in enumerate(rows):
    if (r.get("s") or 0) > 6: cur.append(i)
    else:
        if len(cur) > len(best): best = cur
        cur = []
if len(cur) > len(best): best = cur
seq = rows[max(0, best[0] - 4): best[-1] + 1]

def ddmm(v, lat):
    a = abs(v); d = int(a); m = (a - d) * 60
    return ("%02d%07.4f" if lat else "%03d%07.4f") % (d, m), ("N" if v >= 0 else "S") if lat else ("E" if v >= 0 else "W")

def sent(body):
    c = 0
    for ch in body: c ^= ord(ch)
    return "$%s*%02X" % (body, c)

def send(s):
    subprocess.run(["adb", "emu", "geo", "nmea", s], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

i = 0
while True:
    r = seq[min(i, len(seq) - 1)]
    nxt = seq[min(i + 1, len(seq) - 1)]
    now = datetime.now(timezone.utc)
    hms = now.strftime("%H%M%S") + ".00"
    la, ns = ddmm(r["la"], True); lo, ew = ddmm(r["lo"], False)
    dy = (nxt["la"] - r["la"]) * 111000.0
    dx = (nxt["lo"] - r["lo"]) * 111000.0 * math.cos(math.radians(r["la"]))
    course = (math.degrees(math.atan2(dx, dy)) + 360) % 360 if (dx or dy) else 0.0
    kn = (r.get("s") or 0) * 1.943844
    send(sent("GPGGA,%s,%s,%s,%s,%s,1,10,0.8,%.1f,M,0.0,M,," % (hms, la, ns, lo, ew, r["a"])))
    send(sent("GPRMC,%s,A,%s,%s,%s,%s,%.2f,%.1f,%s,,,A" % (hms, la, ns, lo, ew, kn, course, now.strftime("%d%m%y"))))
    i += 1
    time.sleep(1)
