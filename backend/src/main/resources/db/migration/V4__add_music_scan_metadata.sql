alter table tracks add column artist text;
alter table tracks add column album text;
alter table tracks add column album_artist text;
alter table tracks add column duration bigint;

create index if not exists idx_tracks_artist on tracks(artist);
