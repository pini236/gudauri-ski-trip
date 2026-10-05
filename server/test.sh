#!/usr/bin/env bash
# Runs the server tests: the database rules (supabase/tests/*.test.sql,
# pgTAP) and the server's actions (tests/api.test.ts, Deno).
#
#   server/test.sh            against $DB_URL, which already has the migrations
#                             (on GitHub: the real Supabase database from
#                             `supabase db start`)
#   server/test.sh --fresh    builds a new database on a plain Postgres
#                             ($DB_URL points at any database there; needs
#                             pgTAP and pgcrypto), with a stand-in for what
#                             Supabase provides (tests/shim/supabase.sql),
#                             then applies the migrations and runs the tests
#
# Every test file runs in its own transaction, which is rolled back.
set -euo pipefail
cd "$(dirname "$0")"

DB_URL="${DB_URL:-postgresql://postgres:postgres@127.0.0.1:54322/postgres}"
PSQL=(psql -X -q -v ON_ERROR_STOP=1 --no-psqlrc)

if [[ "${1:-}" == "--fresh" ]]; then
  "${PSQL[@]}" "$DB_URL" -c 'drop database if exists gudauri_test' -c 'create database gudauri_test'
  DB_URL="$(python3 -c 'import sys,urllib.parse as u;p=u.urlsplit(sys.argv[1]);print(u.urlunsplit(p._replace(path="/gudauri_test")))' "$DB_URL")"
  "${PSQL[@]}" "$DB_URL" -f tests/shim/supabase.sql
  for m in supabase/migrations/*.sql; do
    echo "migration: $m"
    "${PSQL[@]}" "$DB_URL" -f "$m"
  done
fi

"${PSQL[@]}" "$DB_URL" -c 'create extension if not exists pgtap with schema extensions' >/dev/null

failed=0
for t in supabase/tests/*.test.sql; do
  out="$({
    echo 'begin;'
    echo 'set local search_path = public, extensions, tests;'
    cat tests/helpers.sql
    cat "$t"
    echo 'rollback;'
  } | "${PSQL[@]}" -t -A "$DB_URL" 2>&1)" || { echo "$out"; echo "FAILED (error): $t"; failed=1; continue; }
  if grep -qE '^not ok|^# Looks like' <<<"$out"; then
    grep -E '^not ok|^#' <<<"$out"
    echo "FAILED: $t"
    failed=1
  else
    echo "ok: $t ($(grep -c '^ok' <<<"$out") checks)"
  fi
done

# The server's actions (supabase/functions/api/), with Deno.
echo "server actions:"
if ! DB_URL="$DB_URL" deno test -A --quiet --config supabase/functions/api/deno.json tests/api.test.ts tests/weather.test.ts; then
  failed=1
fi

exit $failed
