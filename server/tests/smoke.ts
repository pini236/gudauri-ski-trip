// Calls the running server the way an app does: a guest signs in
// (anonymous identity), then calls actions over HTTP with its session.
// Used on GitHub against the local Supabase (.github/workflows/server.yml):
//   deno run -A tests/smoke.ts <api url> <anon key>

const [api, key] = Deno.args;
if (!api || !key) throw new Error("usage: smoke.ts <api url> <anon key>");

async function post(path: string, body: unknown, token?: string) {
  const r = await fetch(`${api}${path}`, {
    method: "POST",
    headers: { apikey: key, "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  });
  const text = await r.text();
  let json: unknown = text;
  try { json = JSON.parse(text); } catch { /* keep the text */ }
  return { status: r.status, json: json as Record<string, unknown> };
}

function expect(ok: boolean, what: string, got: unknown) {
  if (!ok) throw new Error(`${what}: got ${JSON.stringify(got)}`);
  console.log(`ok: ${what}`);
}

const keep = await post("/functions/v1/api/keepalive", {});
expect(keep.status === 200 && keep.json.ok === true, "keepalive works without a session", keep);

const none = await post("/functions/v1/api/my_account", {});
expect(none.status === 401 && none.json.error === "not_signed_in", "an action needs a session", none);

const fake = await post("/functions/v1/api/my_account", {}, "not-a-token");
expect(fake.status === 401, "a fake session is refused", fake);

const signIn = await post("/auth/v1/signup", { data: {} });
const token = (signIn.json as { access_token?: string }).access_token;
expect(signIn.status === 200 && !!token, "a guest signs in (anonymous identity)", signIn.status);

const me = await post("/functions/v1/api/my_account", {}, token);
expect(me.status === 200 && me.json.is_anonymous === true, "the server knows who is calling", me);

const create = await post("/functions/v1/api/create_group", { name: "x", display_name: "x" }, token);
expect(create.status === 403 && create.json.error === "must_register", "a guest cannot create a group", create);

const join = await post("/functions/v1/api/join_group", { code: "AAAAAA", display_name: "Guest" }, token);
expect(join.status === 200 && join.json.status === "invalid_code", "a wrong code is refused", join);

const del = await post("/functions/v1/api/delete_my_account", {}, token);
expect(del.status === 200, "a guest deletes their account (through the Auth admin API)", del);

const after = await post("/functions/v1/api/my_account", {}, token);
expect(after.status === 401 || after.status === 404, "the deleted session no longer works", after);
