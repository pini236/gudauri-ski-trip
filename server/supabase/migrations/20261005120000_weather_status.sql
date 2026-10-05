-- /api/weather and /api/status (decision 58, M-6 and M-2; server/CONTRACT.md). One stored answer for each, read by the
-- public read actions and replaced only by the scheduled fetch actions. Nothing here is reachable by the apps' own
-- keys: the private schema is closed to them, and the server (which runs as the owner) is the only reader and writer.

create table private.weather_cache (
  id int primary key check (id = 1),
  body jsonb not null,
  fetched_at timestamptz not null
);

create table private.lift_status_cache (
  id int primary key check (id = 1),
  body jsonb not null,
  fetched_at timestamptz not null
);

-- The closing-risk thresholds (km/h of the stronger of the ridge wind at 700 hPa and the gusts at the top of Sadzele):
-- up to low_max is low, up to medium_max is medium, above it high. Starting numbers, calibrated at the start of the
-- season against the real lift status. A table, not code: changing them needs no deploy and no new app version.
create table private.weather_thresholds (
  key text primary key check (key in ('low_max', 'medium_max')),
  kmh int not null check (kmh > 0)
);
insert into private.weather_thresholds (key, kmh) values ('low_max', 40), ('medium_max', 60);

-- Every change of a threshold is written to the audit log.
create function private.audit_weather_threshold() returns trigger language plpgsql set search_path = '' as $$
begin
  insert into private.audit_log (user_id, action, details)
  values (null, 'weather_threshold_changed', jsonb_build_object('key', new.key, 'from', old.kmh, 'to', new.kmh));
  return new;
end $$;
create trigger weather_threshold_audit after update on private.weather_thresholds
  for each row when (old.kmh is distinct from new.kmh) execute function private.audit_weather_threshold();

-- The schedule (pg_cron with pg_net, calling fetch_weather every hour and fetch_status every 10 minutes during lift
-- hours, with the secret in the x-fetch-secret header) is set up when deploying, not here: it carries the secret.
-- See server/README.md, "Weather and lift status".
