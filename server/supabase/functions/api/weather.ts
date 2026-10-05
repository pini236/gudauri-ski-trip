// /api/weather (decision 58, M-6; the contract is server/CONTRACT.md, "מזג אוויר"). The pure part: turning Open-Meteo's
// answer into the one answer the clients read, and the closing-risk rule. The fetching and storing are in actions.ts.
//
// Rules from the architect's review: a failed or partial fetch never replaces the stored answer (everything here
// throws instead of returning half); null is allowed in any value of an array; times are Gudauri time (UTC+4, no
// daylight saving), without a zone; the risk is computed here only, from thresholds the caller reads from the database.

export const POINTS = [
  { id: "village", lat: 42.471394, lon: 44.49246, elevation: 2170 },
  { id: "goodaura", lat: 42.492379, lon: 44.494273, elevation: 2710 },
  { id: "sadzele", lat: 42.508985, lon: 44.503209, elevation: 3240 },
] as const;

// Both lifts read the ridge at the top of Sadzele (the architect's rule); two names so that a live report on one lift
// replaces the estimate for that lift only.
export const RISK_LIFTS = ["Sadzele", "Kudebi"] as const;

export const HOURS = 72;
export const DAYS = 15;
export const LIFT_HOURS = [9, 17] as const; // the daily risk is the highest in these hours (inclusive)

export type Level = "low" | "medium" | "high";
export interface Thresholds {
  low_max: number;
  medium_max: number;
}

export function openMeteoUrl(): string {
  const q = new URLSearchParams({
    latitude: POINTS.map((p) => p.lat).join(","),
    longitude: POINTS.map((p) => p.lon).join(","),
    elevation: POINTS.map((p) => p.elevation).join(","),
    hourly: "temperature_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m,snowfall,visibility,weather_code,wind_speed_700hPa",
    daily: "temperature_2m_min,temperature_2m_max,snowfall_sum,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant",
    models: "ecmwf_ifs025",
    timezone: "Asia/Tbilisi",
    wind_speed_unit: "kmh",
    past_days: "1",
    forecast_days: String(DAYS),
  });
  return `https://api.open-meteo.com/v1/forecast?${q}`;
}

// 0 low, 1 medium, 2 high; null when there is no number.
export function riskOf(kmh: number | null, t: Thresholds): Level | null {
  if (kmh === null) return null;
  return kmh <= t.low_max ? "low" : kmh <= t.medium_max ? "medium" : "high";
}

const RANK: Record<Level, number> = { low: 0, medium: 1, high: 2 };
function highest(levels: Array<Level | null>): Level | null {
  let best: Level | null = null;
  for (const l of levels) if (l && (best === null || RANK[l] > RANK[best])) best = l;
  return best;
}
function maxOf(a: number | null, b: number | null): number | null {
  return a === null ? b : b === null ? a : Math.max(a, b);
}

type Series = Array<number | null>;
interface RawPoint {
  hourly: { time: string[]; [k: string]: unknown };
  daily: { time: string[]; [k: string]: unknown };
}

function num(v: unknown): number | null {
  return typeof v === "number" && Number.isFinite(v) ? v : null;
}
function series(o: { [k: string]: unknown }, key: string, length: number): Series {
  const a = o[key];
  if (!Array.isArray(a) || a.length !== length) throw new Error(`open-meteo: ${key} is missing or the wrong length`);
  return a.map(num);
}

// Gudauri's wall clock for a moment: "2027-01-12T11:05".
export function gudauriClock(now: Date): string {
  return new Date(now.getTime() + 4 * 3600 * 1000).toISOString().slice(0, 16);
}

const r1 = (v: number) => Math.round(v * 10) / 10;

// Open-Meteo's answer (one object per point, in the order of POINTS) -> the answer of /api/weather.
export function buildAnswer(raw: unknown, now: Date, t: Thresholds): Record<string, unknown> {
  if (!Array.isArray(raw) || raw.length !== POINTS.length) throw new Error("open-meteo: expected one answer per point");
  const hourStart = gudauriClock(now).slice(0, 13) + ":00";
  const today = hourStart.slice(0, 10);
  const ridge: { wind700: Series; gust: Series; time: string[] } = { wind700: [], gust: [], time: [] };

  const points = (raw as RawPoint[]).map((r, i) => {
    const p = POINTS[i];
    const time = r.hourly?.time;
    if (!Array.isArray(time)) throw new Error("open-meteo: no hourly times");
    const at = time.indexOf(hourStart);
    if (at < 24 || at + HOURS > time.length) throw new Error("open-meteo: the hours around now are missing");
    const h = (k: string) => series(r.hourly, k, time.length);
    const snow = h("snowfall");
    const past = snow.slice(at - 24, at);
    const snow24 = past.some((v) => v === null) ? null : r1(past.reduce<number>((s, v) => s + (v as number), 0));
    const win = (a: Series) => a.slice(at, at + HOURS);

    const dTime = r.daily?.time;
    if (!Array.isArray(dTime)) throw new Error("open-meteo: no daily dates");
    const d0 = dTime.indexOf(today);
    if (d0 < 0 || d0 + DAYS > dTime.length) throw new Error("open-meteo: the days from today are missing");
    const d = (k: string) => series(r.daily, k, dTime.length).slice(d0, d0 + DAYS);

    if (p.id === "sadzele") {
      ridge.wind700 = h("wind_speed_700hPa").slice(at);
      ridge.gust = h("wind_gusts_10m").slice(at);
      ridge.time = time.slice(at);
    }
    return {
      id: p.id, lat: p.lat, lon: p.lon, elevation: p.elevation, snow24,
      hourly: {
        time: time.slice(at, at + HOURS),
        temp: win(h("temperature_2m")), wind: win(h("wind_speed_10m")), gust: win(h("wind_gusts_10m")),
        dir: win(h("wind_direction_10m")), snow: win(snow), vis: win(h("visibility")), code: win(h("weather_code")),
      },
      daily: {
        date: dTime.slice(d0, d0 + DAYS),
        tmin: d("temperature_2m_min"), tmax: d("temperature_2m_max"), snow: d("snowfall_sum"),
        wind: d("wind_speed_10m_max"), gust: d("wind_gusts_10m_max"), dir: d("wind_direction_10m_dominant"),
      },
    };
  });

  // The ridge: for every hour, the stronger of the 700 hPa wind and the gusts at the top of Sadzele (null when both
  // are missing); the daily value is the highest in lift hours. Daily needs more than 72 hours, so it reads the whole
  // series even though only 72 hours are published.
  const level: Array<Level | null> = ridge.time.map((_, i) => riskOf(maxOf(ridge.wind700[i], ridge.gust[i]), t));
  const dates = (points[0].daily.date as string[]);
  const daily = dates.map((date) => {
    const hours = ridge.time.flatMap((tm, i) => {
      const hh = Number(tm.slice(11, 13));
      return tm.startsWith(date) && hh >= LIFT_HOURS[0] && hh <= LIFT_HOURS[1] ? [level[i]] : [];
    });
    return highest(hours);
  });
  const each = <T>(v: T) => Object.fromEntries(RISK_LIFTS.map((l) => [l, v]));

  return {
    schema: 1,
    updated: now.toISOString().replace(/\.\d+Z$/, "Z"),
    model: "ecmwf_ifs025",
    run: null,
    points,
    ridge: {
      hourly: { time: ridge.time.slice(0, HOURS), wind700: ridge.wind700.slice(0, HOURS), risk: each(level.slice(0, HOURS)) },
      daily: { date: dates, risk: each(daily) },
    },
  };
}
