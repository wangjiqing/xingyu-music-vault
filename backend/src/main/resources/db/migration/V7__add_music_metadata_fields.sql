-- title is nullable so stored metadata can distinguish an explicitly maintained
-- title from the API/display fallback derived from the file name.
-- SQLite cannot drop the NOT NULL constraint that V2 placed on tracks.title
-- with ALTER TABLE, so this migration rebuilds tracks while preserving the
-- existing columns, indexes, and constraints. The new metadata columns could
-- be added with ALTER TABLE, but rebuilding once keeps the title semantic fix
-- and the v0.7.0 metadata fields in one consistent schema change.
create table tracks_v7 (
    id integer primary key autoincrement,
    title text,
    normalized_title text,
    artist text,
    album text,
    album_artist text,
    duration bigint,
    year integer,
    track_no integer,
    genre text,
    metadata_status varchar(32) not null default 'pending',
    lyrics_status varchar(32) not null default 'pending',
    artwork_status varchar(32) not null default 'pending',
    metadata_updated_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

insert into tracks_v7 (
    id,
    title,
    normalized_title,
    artist,
    album,
    album_artist,
    duration,
    metadata_status,
    lyrics_status,
    artwork_status,
    created_at,
    updated_at
)
select
    id,
    title,
    normalized_title,
    artist,
    album,
    album_artist,
    duration,
    metadata_status,
    lyrics_status,
    artwork_status,
    created_at,
    updated_at
from tracks;

drop table tracks;
alter table tracks_v7 rename to tracks;

create index if not exists idx_tracks_normalized_title on tracks(normalized_title);
create index if not exists idx_tracks_created_at on tracks(created_at);
create index if not exists idx_tracks_artist on tracks(artist);
create index if not exists idx_tracks_album on tracks(album);
create index if not exists idx_tracks_genre on tracks(genre);
