// The lift status page of MTA (status.mta.ski) -> our /api/status answer. OFF AND NOT WIRED: `fetch_status` still says
// 501 (actions.ts), nothing here is deployed, and nothing calls it yet.
//
// What is known about the page (tools/fixtures/mta-status-gudauri-2026-06-08.json, a copy from 8.6.2026, out of
// season): a Next.js page whose data is inside `self.__next_f.push([1,"..."])` as flat objects with `id, name, type,
// status, bottom, top, time, length`. The HTML tables were not available to us, so this reads only the embedded data.
// What is NOT known: how an open lift looks. Only `status` 1 and 2 were seen, and both are shown "Off". So:
//   * 1 and 2 mean closed; every other value means "no information" and the lift is left out (never open, never closed);
//   * a page whose structure is not recognised gives `null` (nothing is stored, the answer stays 404, clients say
//     "no recent information");
//   * a lift name we have no mapping for is reported in `unknown` and left out.
// The page has no "updated" line and its open/close dates are wrong, so `updated` is the time WE fetched, and no
// `reason` is guessed. The page lists no status for trails, so `pistes` is empty (the clients then derive a run from
// the lifts that go up to it, `runOpen` in site/js/app.js).

export type MtaLift = { id: number; name: string; type: string; status: unknown; bottom: number; top: number; time: number; length: number };
export type Zone = "gudauri" | "kobi";

// MTA name -> our lift name (`name` in site/data/runs-and-lifts.json, the key of `lifts` in /api/status).
// Checked against length and travel time in tests/mta_status.test.ts (within 12 percent of our `len`).
export const MTA_TO_OURS: Record<Zone, Record<string, string>> = {
  gudauri: {
    "Pirveli": "Pirveli",
    "Soliko": "Soliko",
    "Goodaura": "Goodaura",
    "New Goodaura": "New Goodaura",
    "Kikilo": "Kikilo",
    "Kudebi": "Kudebi",
    "Snow Park": "Snow Park",
    "Shino": "Shino",
    "Zuma": "Zuma",
    "Khada": "Khada",
    "Sadzele": "Sadzele",
  },
  kobi: {
    // Since the 1.11.2026 data fix our lift names equal the MTA names. MTA "Kobi" is 2271 m and 6.3 min; ours is
    // 2285 m and 6.3 min. MTA "Truso" is 2837 m and 8 min; ours is 2790 m (research/lifts.md). Medium confidence.
    "Kobi": "Kobi",
    "Truso": "Truso",
    "Firni": "Firni",
  },
};

// On the MTA page and not in our data under a name: Alpina, Baby, Bombora (T-bars we do not have), Khalicha 1 to 3
// (carpets: we have two unnamed ones, 37 m and 29 m, against MTA's 60, 27 and 90 m, so no safe match). A gap list for
// Pini; they are reported in `unknown` and not shown.
export const MTA_GAPS = ["Alpina", "Baby", "Bombora", "Khalicha 1", "Khalicha 2", "Khalicha 3"];

const PUSH = /self\.__next_f\.push\(\[1,("(?:[^"\\]|\\.)*")\]\)/g;

// The flat objects with `name` and `status` inside the page's data; null when there is no data at all.
export function extractLifts(html: string): MtaLift[] | null {
  let text = "";
  for (const m of html.matchAll(PUSH)) {
    try {
      text += JSON.parse(m[1]);
    } catch {
      return null;
    }
  }
  if (!text) return null;
  const byId = new Map<number, MtaLift>();
  for (const m of text.matchAll(/\{[^{}]*"status"[^{}]*\}/g)) {
    let o: Record<string, unknown>;
    try {
      o = JSON.parse(m[0]);
    } catch {
      continue;
    }
    const num = (v: unknown) => (typeof v === "number" ? v : typeof v === "string" && v.trim() !== "" ? Number(v.trim()) : NaN);
    const id = num(o.id);
    const bottom = num(o.bottom), top = num(o.top), time = num(o.time), length = num(o.length);
    if (typeof o.name !== "string" || ![id, bottom, top, time, length].every(Number.isFinite)) continue;
    byId.set(id, { id, name: o.name.trim(), type: String(o.type ?? ""), status: o.status, bottom, top, time, length });
  }
  return byId.size ? [...byId.values()] : null;
}

// Closed is only what was seen to be closed (1 and 2 are both "Off"); anything else is unknown.
export function isClosed(status: unknown): boolean {
  return status === 1 || status === 2 || status === "1" || status === "2";
}

export type Status = { updated: string; lifts: Record<string, { open: boolean }>; pistes: Record<string, never> };

// Both pages (or one, if the other failed) -> the answer, or null when nothing could be read. `unknown` lists what
// was left out: names with no mapping, and statuses we have not seen.
export function buildStatus(pages: Partial<Record<Zone, string>>, now: Date): { status: Status | null; unknown: string[] } {
  const lifts: Status["lifts"] = {};
  const unknown: string[] = [];
  for (const zone of Object.keys(MTA_TO_OURS) as Zone[]) {
    const html = pages[zone];
    if (html === undefined) continue;
    const found = extractLifts(html);
    if (!found) continue;
    for (const l of found) {
      const ours = MTA_TO_OURS[zone][l.name];
      if (!ours) {
        unknown.push(`${zone}/${l.name}`);
        continue;
      }
      if (!isClosed(l.status)) {
        unknown.push(`${zone}/${l.name}: status ${JSON.stringify(l.status)}`);
        continue;
      }
      lifts[ours] = { open: false };
    }
  }
  if (!Object.keys(lifts).length) return { status: null, unknown };
  return { status: { updated: now.toISOString(), lifts, pistes: {} }, unknown };
}
