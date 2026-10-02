-- A meetup made without signal waits in the phone's queue and may be sent twice (the first answer never came back).
-- The phone now chooses the meetup's id, and sends it with "Prefer: resolution=ignore-duplicates": the second send finds
-- the same row instead of making another. Only the id column is added to what a member may set on insert; the
-- default (a random uuid) stays for everyone who does not send one, and the row rules are the same.
grant insert (id, group_id, station, meet_at, note) on public.meetups to authenticated;
