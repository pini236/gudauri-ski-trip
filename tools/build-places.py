#!/usr/bin/env python3
"""Builds the places-near-stations data file (round 31, food and services).

Inputs (all in the repo, so the build is repeatable and offline):
  tools/places-curated.json     hand-picked places (certainty a or b only), hubs, exclusions
  tools/places-osm-cache.json   what the open map says about each picked object
  site/data/runs-and-lifts.json lift ends (the stations)
Output (default: research/places/places.json; the proposed home is site/data/places.json,
after the architect's review):
  stations[]  one per hub of lift ends, with the places within maxDistance metres
  places[]    one per place, with kind, certainty and sources

Commands:
  python3 tools/build-places.py            build the output
  python3 tools/build-places.py --check    build in memory and fail if the output differs
  python3 tools/build-places.py --refresh  re-download the picked objects from the open map
                                           (api.openstreetmap.org) into the cache

Rules (research/places.md, round 31): certainty a or b only; a needs a second source;
b needs an edit from 2024 or later, unless the entry carries an explicit "override" text;
no phone numbers, no opening hours, no promises. A station with no places is "none",
which a client shows as "no information", not "nothing there".
"""
import json, math, sys, urllib.request, xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CURATED = ROOT / "tools" / "places-curated.json"
CACHE = ROOT / "tools" / "places-osm-cache.json"
LIFTS = ROOT / "site" / "data" / "runs-and-lifts.json"
DEFAULT_OUT = ROOT / "research" / "places" / "places.json"
# The lean file is what clients get (architect, 1.11.2026). Until round 31 is approved it
# stays here; on approval the target is site/data/places.json.
DEFAULT_LEAN = ROOT / "research" / "places" / "places.lean.json"
LEAN_DROP = ("sources", "note", "override", "alsoOsm", "nameLocal", "edited", "checked", "sub", "osm")


def lean(out):
    res = json.loads(json.dumps(out))
    res.pop("notes", None)
    for p in res["places"]:
        for k in LEAN_DROP:
            p.pop(k, None)
    return res

KEEP_TAGS = ("name", "name:en", "amenity", "cuisine", "seasonal", "check_date", "status", "building", "tourism")
KIND_BY_AMENITY = {
    "restaurant": "food", "fast_food": "food", "bar": "food", "pub": "food",
    "cafe": "cafe",
    "rescue_station": "aid", "mountain_rescue": "aid",
    "toilets": "wc",
}
LAT_M = 111320.0
MIN_B_YEAR = 2024


def dist_m(a, b):
    return math.hypot((a[0] - b[0]) * LAT_M, (a[1] - b[1]) * LAT_M * math.cos(math.radians(42.49)))


def fail(msg):
    sys.exit("build-places: " + msg)


def api_get(path):
    req = urllib.request.Request("https://api.openstreetmap.org/api/0.6/" + path,
                                 headers={"User-Agent": "gudauri-ski-trip/places (research)"})
    with urllib.request.urlopen(req, timeout=90) as r:
        return ET.fromstring(r.read())


def tags_of(el):
    return {t.get("k"): t.get("v") for t in el.findall("tag") if t.get("k") in KEEP_TAGS}


def refresh():
    cur = json.loads(CURATED.read_text())
    ids = [p["osm"] for p in cur["places"]] + \
          [x for p in cur["places"] for x in p.get("alsoOsm", [])] + [e["osm"] for e in cur["excluded"]]
    ids = sorted(set(ids))
    nodes = [i.split("/")[1] for i in ids if i.startswith("node/")]
    ways = [i.split("/")[1] for i in ids if i.startswith("way/")]
    objs = {}
    for k in range(0, len(nodes), 100):
        root = api_get("nodes?nodes=" + ",".join(nodes[k:k + 100]))
        for n in root.iter("node"):
            objs["node/" + n.get("id")] = dict(
                v=int(n.get("version")), ts=n.get("timestamp")[:10],
                ll=[float(n.get("lat")), float(n.get("lon"))], tags=tags_of(n))
    for w in ways:
        root = api_get("way/%s/full" % w)
        nd = {n.get("id"): (float(n.get("lat")), float(n.get("lon"))) for n in root.iter("node")}
        way = next(root.iter("way"))
        pts = [nd[r.get("ref")] for r in way.findall("nd")]
        objs["way/" + w] = dict(
            v=int(way.get("version")), ts=way.get("timestamp")[:10],
            ll=[round(sum(p[0] for p in pts) / len(pts), 6), round(sum(p[1] for p in pts) / len(pts), 6)],
            tags=tags_of(way), centroid=True)
    missing = [i for i in ids if i not in objs]
    if missing:
        fail("not found on the open map (deleted?): " + ", ".join(missing))
    CACHE.write_text(json.dumps(dict(
        fetched=__import__("datetime").date.today().isoformat(),
        source="api.openstreetmap.org/api/0.6",
        license="ODbL 1.0, (c) OpenStreetMap contributors",
        objects=dict(sorted(objs.items()))), ensure_ascii=False, indent=1) + "\n")
    print("cache written: %d objects" % len(objs))


def build():
    cur = json.loads(CURATED.read_text())
    cache = json.loads(CACHE.read_text())
    lifts = {str(l["id"]): l for l in json.loads(LIFTS.read_text())["lifts"]}
    objs = cache["objects"]
    max_d = cur.get("maxDistance", 250)

    # ends -> hubs
    end_ll = {}
    for lid, l in lifts.items():
        end_ll[lid + ":bottom"] = l["g"][0]
        end_ll[lid + ":top"] = l["g"][-1]
    seen = {}
    stations = []
    for h in cur["hubs"]:
        pts = []
        for e in h["ends"]:
            if e not in end_ll:
                fail("hub %s: unknown lift end %s" % (h["id"], e))
            if e in seen:
                fail("lift end %s is in two hubs (%s, %s)" % (e, seen[e], h["id"]))
            seen[e] = h["id"]
            pts.append(end_ll[e])
        cen = [round(sum(p[0] for p in pts) / len(pts), 6), round(sum(p[1] for p in pts) / len(pts), 6)]
        spread = max(dist_m(p, cen) for p in pts)
        if spread > 100:
            fail("hub %s spreads %.0f m (max 100)" % (h["id"], spread))
        stations.append(dict(id=h["id"], label=h["label"], ll=cen, ends=[
            dict(lift=lifts[e.split(":")[0]]["name"], liftId=int(e.split(":")[0]), end=e.split(":")[1])
            for e in h["ends"]], places=[]))
    uncovered = sorted(set(end_ll) - set(seen))
    if uncovered:
        fail("lift ends not in any hub: " + ", ".join(uncovered))

    places = []
    seen_osm = set()
    for p in cur["places"]:
        o = objs.get(p["osm"])
        if not o:
            fail("%s is not in the cache; run --refresh" % p["osm"])
        if p["osm"] in seen_osm:
            fail("duplicate place " + p["osm"])
        seen_osm.add(p["osm"])
        tg = o["tags"]
        if tg.get("status") in ("inactive", "disused", "closed") or any(k.startswith(("disused:", "was:")) for k in tg):
            fail("%s is marked inactive on the open map" % p["osm"])
        kind = KIND_BY_AMENITY.get(tg.get("amenity"))
        if kind != p["kind"]:
            fail("%s: kind %s does not match amenity %s" % (p["osm"], p["kind"], tg.get("amenity")))
        cert = p["cert"]
        if cert not in ("a", "b"):
            fail("%s: only certainty a or b is allowed" % p["osm"])
        sec = p.get("sec", [])
        edited = o["ts"]
        checked = tg.get("check_date")
        latest = max(edited, checked or "")
        if cert == "a" and not sec:
            fail("%s: certainty a needs a second source" % p["osm"])
        if cert == "b" and int(latest[:4]) < MIN_B_YEAR and not p.get("override"):
            fail("%s: certainty b needs an edit from %d or later (last %s); add 'override' with a reason"
                 % (p["osm"], MIN_B_YEAR, latest))
        name = p.get("name") or tg.get("name:en") or tg.get("name")
        if kind in ("food", "cafe") and not name:
            fail("%s: a food or cafe place needs a name" % p["osm"])
        pid = p["osm"][0] + p["osm"].split("/")[1]
        sources = [dict(t="osm", url="https://www.openstreetmap.org/" + p["osm"], date=edited)] + sec
        place = dict(id=pid, osm=p["osm"], kind=kind, sub=tg.get("amenity"),
                     name=name, nameLocal=(tg.get("name") if tg.get("name") != name else None),
                     ll=[round(o["ll"][0], 6), round(o["ll"][1], 6)], cert=cert,
                     edited=edited, seasonal=tg.get("seasonal") in ("yes", "winter"),
                     slope=bool(p.get("slope")), sources=sources)
        if checked:
            place["checked"] = checked
        if p.get("alsoOsm"):
            place["alsoOsm"] = p["alsoOsm"]
        if p.get("override"):
            place["override"] = p["override"]
        if p.get("note") or p.get("nameNote"):
            place["note"] = " ".join(x for x in (p.get("note"), p.get("nameNote")) if x)
        if p.get("lift"):
            place["liftId"] = int(p["lift"])
        places.append(place)

    by_id = {s["id"]: s for s in stations}
    for pl in places:
        near = []
        for s in stations:
            d = dist_m(pl["ll"], s["ll"])
            if d <= max_d:
                near.append((round(d), s["id"]))
        for d, sid in near:
            by_id[sid]["places"].append(dict(p=pl["id"], d=d))
    for s in stations:
        s["places"].sort(key=lambda x: (x["d"], x["p"]))
        s["info"] = "some" if s["places"] else "none"

    places.sort(key=lambda x: x["id"])
    return dict(
        schema=1, kinds=["food", "cafe", "aid", "wc"], maxDistance=max_d,
        osmFetched=cache["fetched"],
        attribution="(c) OpenStreetMap contributors, ODbL 1.0, https://www.openstreetmap.org/copyright",
        notes="aid = a rescue point as mapped; no hours, phone or promise of service. info=none means no information, not nothing there.",
        stations=stations, places=places)


def main():
    if "--refresh" in sys.argv:
        return refresh()
    out = build()
    text = json.dumps(out, ensure_ascii=False, indent=1) + "\n"
    lean_text = json.dumps(lean(out), ensure_ascii=False, separators=(",", ":")) + "\n"
    dst = DEFAULT_OUT
    if "--out" in sys.argv:
        dst = Path(sys.argv[sys.argv.index("--out") + 1])
    lean_dst = DEFAULT_LEAN if "--out" not in sys.argv else dst.with_name(dst.stem + ".lean.json")
    if "--check" in sys.argv:
        for f, t in ((dst, text), (lean_dst, lean_text)):
            if not f.exists() or f.read_text() != t:
                fail("%s is out of date; run tools/build-places.py" % f)
        print("ok")
        return
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(text)
    lean_dst.write_text(lean_text)
    some = sum(1 for s in out["stations"] if s["info"] == "some")
    print("%d places, %d stations (%d with places, %d no information) -> %s"
          % (len(out["places"]), len(out["stations"]), some, len(out["stations"]) - some, dst))


if __name__ == "__main__":
    main()
