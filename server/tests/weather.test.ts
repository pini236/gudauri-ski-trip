// Tests for /api/weather and /api/status (supabase/functions/api/weather.ts and the read and fetch actions in actions.ts).
//
//   DB_URL=... deno test -A --config supabase/functions/api/deno.json tests/weather.test.ts

import postgres from "postgres";
import { handle, type Deps } from "../supabase/functions/api/actions.ts";
import { buildAnswer, gudauriClock, POINTS, riskOf } from "../supabase/functions/api/weather.ts";

const sql = postgres(Deno.env.get("DB_URL") ?? "postgresql://postgres:postgres@127.0.0.1:54322/postgres", { onnotice: () => {} });
const T = { low_max: 40, medium_max: 60 };
const NOW = new Date("2027-01-12T07:05:00Z"); // 11:05 in Gudauri

function eq(actual: unknown, expected: unknown, what: string) {
  const a = JSON.stringify(actual), e = JSON.stringify(expected);
  if (a !== e) throw new Error(`${what}: expected ${e}, got ${a}`);
}

// An Open-Meteo answer: hours from 2027-01-11T00:00 for 16 days (past_days=1 and 15 forecast days), Gudauri time.
// `wind700(i)` and `gust(i)` choose the ridge numbers for hour i; everything else is a simple pattern.
function raw(opts: { wind700?: (i: number) => number | null; gust?: (i: number) => number | null; snow?: (i: number) => number | null } = {}) {
  const n = 16 * 24;
  const time = Array.from({ length: n }, (_, i) => {
    const d = new Date(Date.UTC(2027, 0, 11) + i * 3600 * 1000).toISOString();
    return d.slice(0, 16);
  });
  const dates = Array.from({ length: 16 }, (_, i) => new Date(Date.UTC(2027, 0, 11 + i)).toISOString().slice(0, 10));
  return POINTS.map((p, k) => ({
    hourly: {
      time,
      temperature_2m: time.map(() => -5 - k),
      wind_speed_10m: time.map(() => 10 + k),
      wind_gusts_10m: time.map((_, i) => (p.id === "sadzele" ? (opts.gust ? opts.gust(i) : 20) : 15)),
      wind_direction_10m: time.map(() => 290),
      snowfall: time.map((_, i) => (opts.snow ? opts.snow(i) : 0.25)),
      visibility: time.map(() => 8000),
      weather_code: time.map(() => 3),
      wind_speed_700hPa: time.map((_, i) => (opts.wind700 ? opts.wind700(i) : 30)),
    },
    daily: {
      time: dates,
      temperature_2m_min: dates.map(() => -8), temperature_2m_max: dates.map(() => -2), snowfall_sum: dates.map(() => 3),
      wind_speed_10m_max: dates.map(() => 20), wind_gusts_10m_max: dates.map(() => 30), wind_direction_10m_dominant: dates.map(() => 300),
    },
  }));
}

Deno.test("risk thresholds: up to the first number low, up to the second medium, above high", () => {
  eq([riskOf(40, T), riskOf(40.1, T), riskOf(60, T), riskOf(60.1, T), riskOf(null, T)], ["low", "medium", "medium", "high", null], "levels");
});

Deno.test("Gudauri clock is UTC+4", () => {
  eq(gudauriClock(NOW), "2027-01-12T11:05", "clock");
  eq(gudauriClock(new Date("2027-01-12T20:30:00Z")), "2027-01-13T00:30", "after midnight");
});

Deno.test("the answer: 72 hours from the hour that has begun, 15 days from today, snow24 from the day before", () => {
  // deno-lint-ignore no-explicit-any
  const a = buildAnswer(raw(), NOW, T) as any;
  eq(a.schema, 1, "schema");
  eq(a.updated, "2027-01-12T07:05:00Z", "updated");
  eq(a.points.map((p: { id: string }) => p.id), ["village", "goodaura", "sadzele"], "points");
  const v = a.points[0];
  eq(v.hourly.time.length, 72, "72 hours");
  eq(v.hourly.time[0], "2027-01-12T11:00", "starts at the hour that has begun");
  eq(v.daily.date.length, 15, "15 days");
  eq(v.daily.date[0], "2027-01-12", "starts today");
  eq(v.snow24, 6, "24 hours of 0.25 cm before 11:00");
  eq(Object.keys(v.hourly), ["time", "temp", "wind", "gust", "dir", "snow", "vis", "code"], "hourly keys");
  eq(a.ridge.hourly.time.length, 72, "ridge hours");
  eq(a.ridge.daily.date.length, 15, "ridge days");
});

Deno.test("hourly risk: the stronger of the 700 hPa wind and the gusts at Sadzele, the same for both lifts", () => {
  // deno-lint-ignore no-explicit-any
  const a = buildAnswer(raw({ wind700: (i) => (i === 36 ? 65 : 30), gust: (i) => (i === 37 ? 45 : 20) }), NOW, T) as any;
  // index 35 is 2027-01-12T11:00, so ridge hour 1 is index 36 and hour 2 is index 37
  eq(a.ridge.hourly.risk.Sadzele.slice(0, 3), ["low", "high", "medium"], "Sadzele");
  eq(a.ridge.hourly.risk.Kudebi.slice(0, 3), ["low", "high", "medium"], "Kudebi");
  eq(a.ridge.hourly.wind700.slice(0, 2), [30, 65], "wind700 published");
});

Deno.test("daily risk: the highest in lift hours (09:00 to 17:00), not in 24 hours", () => {
  // Midnight gale on 2027-01-13 (index 24*2+0), calm in lift hours: low. A strong hour at 16:00 on 2027-01-14: high.
  const night = 24 * 2 + 0, late = 24 * 3 + 16;
  // deno-lint-ignore no-explicit-any
  const a = buildAnswer(raw({ wind700: (i) => (i === night ? 90 : i === late ? 75 : 30) }), NOW, T) as any;
  const day = (d: string) => a.ridge.daily.risk.Sadzele[a.ridge.daily.date.indexOf(d)];
  eq(day("2027-01-13"), "low", "a night gale is not lift hours");
  eq(day("2027-01-14"), "high", "16:00 is");
  eq(day("2027-01-20"), "low", "a day beyond the 72 published hours still has a value");
});

Deno.test("null is allowed in any value and becomes no data", () => {
  // deno-lint-ignore no-explicit-any
  const a = buildAnswer(raw({ wind700: () => null, gust: () => null, snow: (i) => (i === 20 ? null : 0.25) }), NOW, T) as any;
  eq(a.ridge.hourly.risk.Sadzele[0], null, "no wind numbers, no risk");
  eq(a.ridge.daily.risk.Sadzele[0], null, "nothing in lift hours");
  eq(a.points[0].snow24, null, "a missing hour in the window: no snow24");
});

Deno.test("a partial answer throws (never half an answer)", () => {
  const short = raw().map((p) => ({ ...p, hourly: { ...p.hourly, time: p.hourly.time.slice(0, 40) } }));
  let threw = false;
  try { buildAnswer(short, NOW, T); } catch { threw = true; }
  eq(threw, true, "too few hours");
  threw = false;
  try { buildAnswer(raw().slice(0, 2), NOW, T); } catch { threw = true; }
  eq(threw, true, "a missing point");
});

// --- The actions, against a database ---------------------------------

function deps(fetchJson: Deps["fetchJson"]): Deps {
  return { sql, deleteAuthUser: async () => {}, fetchJson, now: () => NOW };
}

Deno.test("reads: 404 before the first fetch; the fetch needs the secret; a failed fetch keeps the old answer", async () => {
  await sql`delete from private.weather_cache`;
  const good = deps(async () => raw());
  const r0 = await handle(good, null, "weather", {});
  eq([r0.status, r0.cache], [404, undefined], "no answer yet: 404 without a cache");

  eq((await handle(good, null, "fetch_weather", {})).status, 401, "no secret");
  const ok = await handle(good, null, "fetch_weather", {}, { fetchAuthorized: true });
  eq(ok.status, 200, "fetch");

  const r1 = await handle(good, null, "weather", {});
  eq([r1.status, r1.cache], [200, "public, s-maxage=600"], "stored answer, cached ten minutes");
  eq((r1.body as { updated: string }).updated, "2027-01-12T07:05:00Z", "updated");

  // A failing network, and a partial answer: 502, and the stored answer is the same.
  const down = deps(async () => { throw new Error("offline"); });
  eq((await handle(down, null, "fetch_weather", {}, { fetchAuthorized: true })).status, 502, "network down");
  const partial = deps(async () => raw().slice(0, 1));
  eq((await handle(partial, null, "fetch_weather", {}, { fetchAuthorized: true })).status, 502, "partial answer");
  eq(((await handle(good, null, "weather", {})).body as { updated: string }).updated, "2027-01-12T07:05:00Z", "old answer kept");
});

Deno.test("status: 404 until the parser exists (December); its fetch says not implemented", async () => {
  await sql`delete from private.lift_status_cache`;
  const d = deps(async () => raw());
  const r = await handle(d, null, "status", {});
  eq([r.status, r.cache], [404, undefined], "no data is a 404, not an empty 200");
  eq((await handle(d, null, "fetch_status", {}, { fetchAuthorized: true })).status, 501, "no parser yet");
  eq((await handle(d, null, "fetch_status", {})).status, 401, "and not without the secret");
});

Deno.test("a threshold change is written to the audit log", async () => {
  await sql`update private.weather_thresholds set kmh = 45 where key = 'low_max'`;
  const [r] = await sql`select details from private.audit_log where action = 'weather_threshold_changed' order by at desc limit 1`;
  eq([r.details.key, r.details.from, r.details.to], ["low_max", 40, 45], "logged");
  await sql`update private.weather_thresholds set kmh = 40 where key = 'low_max'`;
});

Deno.test("close", async () => { await sql.end(); });
