// Tests for the MTA lift status parser (supabase/functions/api/mta_status.ts), against the facts file
// tools/fixtures/mta-status-gudauri-2026-06-08.json. No database: deno test -A tests/mta_status.test.ts
//
// The page itself was not kept (only facts), so the test pages are BUILT from those facts in the structure the facts
// file describes (flat objects inside self.__next_f.push). They prove the parser and the mapping; they do not prove
// the real markup, which is checked on the live page in December.

import { buildStatus, extractLifts, isClosed, MTA_GAPS, MTA_TO_OURS } from "../supabase/functions/api/mta_status.ts";

const fx = JSON.parse(Deno.readTextFileSync(new URL("../../tools/fixtures/mta-status-gudauri-2026-06-08.json", import.meta.url)));
const site = JSON.parse(Deno.readTextFileSync(new URL("../../site/data/runs-and-lifts.json", import.meta.url)));
const NOW = new Date("2027-01-12T07:05:00Z");

function eq(a: unknown, e: unknown, what: string) {
  if (JSON.stringify(a) !== JSON.stringify(e)) throw new Error(`${what}: expected ${JSON.stringify(e)}, got ${JSON.stringify(a)}`);
}

// A page in the described structure: the lift objects split over several push calls, once in a nested list.
function page(lifts: unknown[]): string {
  const body = `{"children":["$","div",null,{"lifts":${JSON.stringify(lifts)}}]}`;
  const half = Math.floor(body.length / 2);
  const push = (s: string) => `<script>self.__next_f.push([1,${JSON.stringify(s)}])</script>`;
  return `<html><body>${push(body.slice(0, half))}${push(body.slice(half))}</body></html>`;
}

Deno.test("both zones: every mapped lift is closed, names are ours, pistes empty", () => {
  const { status, unknown } = buildStatus({ gudauri: page(fx.zones.gudauri.lifts), kobi: page(fx.zones.kobi.lifts) }, NOW);
  eq(status!.updated, "2027-01-12T07:05:00.000Z", "updated is the fetch time");
  eq(status!.pistes, {}, "no trail status on the page");
  eq(Object.keys(status!.lifts).sort(), [...Object.values(MTA_TO_OURS.gudauri), ...Object.values(MTA_TO_OURS.kobi)].sort(), "lifts");
  for (const v of Object.values(status!.lifts)) eq(v, { open: false }, "closed, no reason guessed");
  eq(unknown.map((u) => u.split("/")[1]).sort(), [...MTA_GAPS].sort(), "left out are exactly the gap list");
});

Deno.test("the mapping: our lift exists, and length agrees with ours", () => {
  const ours = new Map<string, { len: number; dur?: number }>(site.lifts.filter((l: { name: string }) => l.name).map((l: { name: string }) => [l.name, l]));
  for (const zone of ["gudauri", "kobi"] as const) {
    const byName = new Map(fx.zones[zone].lifts.map((l: { name: string }) => [l.name, l]));
    for (const [mta, name] of Object.entries(MTA_TO_OURS[zone])) {
      const m = byName.get(mta) as { length: number; time: number } | undefined;
      const o = ours.get(name);
      if (!m) throw new Error(`${mta} is not on the MTA page (${zone})`);
      if (!o) throw new Error(`${name} is not a lift in site/data/runs-and-lifts.json`);
      if (Math.abs(m.length - o.len) / o.len > 0.12) throw new Error(`${mta} -> ${name}: length ${m.length} vs ${o.len}`);
      // travel time is not compared: MTA's and ours differ for known reasons (Kikilo 7 vs 5, see research/lifts.md)
    }
  }
});

Deno.test("every lift on the MTA page is mapped or on the gap list", () => {
  for (const zone of ["gudauri", "kobi"] as const) {
    for (const l of fx.zones[zone].lifts) {
      if (!(l.name in MTA_TO_OURS[zone]) && !MTA_GAPS.includes(l.name)) throw new Error(`${zone}/${l.name} has no mapping and is not in the gaps`);
    }
  }
});

Deno.test("an unseen status is not guessed: left out, never open or closed", () => {
  const lifts = [{ ...fx.zones.gudauri.lifts[0], status: 3 }, { ...fx.zones.gudauri.lifts[1], status: "On" }, fx.zones.gudauri.lifts[2]];
  const { status, unknown } = buildStatus({ gudauri: page(lifts) }, NOW);
  eq(Object.keys(status!.lifts), ["Goodaura"], "only the one with a seen status");
  eq(unknown.length, 2, "two reported");
  eq([isClosed(1), isClosed(2), isClosed(0), isClosed(true), isClosed(undefined)], [true, true, false, false, false], "isClosed");
});

Deno.test("an unknown structure gives no answer", () => {
  for (const html of ["", "<html>500 Internal Server Error</html>", "<script>self.__next_f.push([1,\"hello\"])</script>", page([]), page([{ name: "Pirveli" }])]) {
    eq(extractLifts(html), null, "extract: " + html.slice(0, 30));
    eq(buildStatus({ gudauri: html }, NOW).status, null, "build");
  }
  eq(buildStatus({}, NOW).status, null, "no pages");
  eq(buildStatus({ gudauri: "<script>self.__next_f.push([1,\"{broken\"])</script><script>self.__next_f.push([1,\"x]\"])" }, NOW).status, null, "broken");
});

Deno.test("values as strings with spaces (Shino's bottom) and repeated objects", () => {
  const shino = { ...fx.zones.gudauri.lifts.find((l: { name: string }) => l.name === "Shino"), bottom: " 2187" };
  const got = extractLifts(page([shino, shino]))!;
  eq(got.length, 1, "deduplicated by id");
  eq(got[0].bottom, 2187, "trimmed number");
});
