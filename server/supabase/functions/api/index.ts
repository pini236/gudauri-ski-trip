// The server (decision 40): one Supabase Edge Function for all actions,
// so one warm instance serves them all (fewer cold starts).
//
//   POST /functions/v1/api/<action>   JSON body, session token in Authorization
//
// The actions are in actions.ts. Here: CORS for the website, who is
// calling (the session token is verified with Supabase Auth), and one log
// line per request (Edge Functions > api > Logs; kept one day on the free
// plan; who did what is also kept in private.audit_log).

import postgres from "postgres";
import { createClient } from "@supabase/supabase-js";
import { clientIp, handle } from "./actions.ts";

// Provided by Supabase to every Edge Function.
const sql = postgres(Deno.env.get("SUPABASE_DB_URL")!, { prepare: false, max: 4, idle_timeout: 20 });
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false, autoRefreshToken: false },
});

const deps = {
  sql,
  deleteAuthUser: async (id: string) => {
    const { error } = await admin.auth.admin.deleteUser(id);
    if (error && error.status !== 404) throw error;
  },
};

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info, x-region",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

function reply(status: number, body: unknown, cache?: string) {
  return new Response(JSON.stringify(body), {
    status,
    // Errors and everything without a cache setting are never cached.
    headers: { ...CORS, "Content-Type": "application/json", "Cache-Control": status === 200 && cache ? cache : "no-store" },
  });
}

// The scheduled fetches carry a secret in x-fetch-secret (FETCH_SECRET, set in the dashboard; never in the repo).
// Compared in constant time. No secret set: nothing is authorized.
function fetchAuthorized(req: Request): boolean {
  const want = Deno.env.get("FETCH_SECRET") ?? "";
  const got = req.headers.get("x-fetch-secret") ?? "";
  if (!want || want.length !== got.length) return false;
  let diff = 0;
  for (let i = 0; i < want.length; i++) diff |= want.charCodeAt(i) ^ got.charCodeAt(i);
  return diff === 0;
}

// The two public reads are GET, with no session; every other action is POST.
const PUBLIC_READS = ["weather", "status"];

// The caller's network address as a keyed hash that changes every day: enough to count wrong code guesses per address
// (R-13), and nothing that can be turned back into the address, or linked across days. The key is derived from the
// service key ("ip-v1"), which only the server has, so replacing one never touches the other.
//
// Which header: Cloudflare's and the proxy's own first; else the LAST entry of x-forwarded-for, the one the proxy
// added (the first is whatever the caller sent). No trusted address: not counted per address (logged), never put in
// a shared "unknown" bucket, which would let anyone close joining for all again. Checked after deploying:
// server/README.md, "פריסה".
async function hmac(key: Uint8Array<ArrayBuffer>, text: string): Promise<Uint8Array<ArrayBuffer>> {
  const k = await crypto.subtle.importKey("raw", key, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return new Uint8Array(await crypto.subtle.sign("HMAC", k, new TextEncoder().encode(text)));
}
const ipSecret = hmac(new TextEncoder().encode(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "local"), "ip-v1");

// Counting per address keeps a keyed hash of the address for two days: personal data, so it is on only once Pini has
// approved the line about it in the privacy policy (docs/PRIVACY.md, the architect's review of R-13). Off: no address
// is read or kept, and wrong guesses are limited per person and by protection mode only.
const COUNT_BY_ADDRESS = false;

async function ipKey(req: Request, action: string): Promise<string | undefined> {
  if (!COUNT_BY_ADDRESS) return undefined;
  const ip = clientIp(req.headers);
  if (!ip) {
    if (["invite_preview", "join_group", "request_reclaim"].includes(action)) console.log(JSON.stringify({ action, no_client_ip: true }));
    return undefined;
  }
  const day = new Date().toISOString().slice(0, 10);
  const mac = await hmac(await ipSecret, `${day}|${ip}`);
  return [...mac].slice(0, 12).map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function caller(req: Request): Promise<string | null> {
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const { data, error } = await admin.auth.getUser(token);
  return error || !data.user ? null : data.user.id;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS });
  const started = Date.now();
  const action = new URL(req.url).pathname.split("/").filter(Boolean).pop() ?? "";
  // R-13, temporary: only the NAMES of the request headers (never values), on the health check, to learn which header
  // carries the caller's address. Removed once clientIp is fixed.
  if (action === "keepalive") console.log(JSON.stringify({ header_names: [...req.headers.keys()].sort() }));
  const isRead = PUBLIC_READS.includes(action);
  if (req.method !== (isRead ? "GET" : "POST")) return reply(405, { error: isRead ? "use_get" : "use_post" });

  let user: string | null = null;
  let status = 500;
  let error: string | undefined;
  try {
    const text = isRead ? "" : await req.text();
    let body: Record<string, unknown> = {};
    try {
      body = text ? JSON.parse(text) : {};
    } catch {
      status = 400;
      error = "invalid_json";
      return reply(status, { error });
    }
    user = action === "keepalive" || isRead || action.startsWith("fetch_") ? null : await caller(req);
    const r = await handle(deps, user, action, body, { ipKey: await ipKey(req, action), fetchAuthorized: action.startsWith("fetch_") && fetchAuthorized(req) });
    status = r.status;
    error = (r.body as { error?: string } | null)?.error;
    return reply(r.status, r.body, r.cache);
  } catch (e) {
    console.error(JSON.stringify({ action, user, error: String(e) }));
    error = "server_error";
    return reply(500, { error });
  } finally {
    // No names, codes or tokens in the log: the action, the user id, the result.
    console.log(JSON.stringify({ action, user, status, error, ms: Date.now() - started }));
  }
});
