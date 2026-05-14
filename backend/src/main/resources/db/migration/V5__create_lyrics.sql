create table if not exists lyrics (
    id integer primary key autoincrement,
    title text,
    artist text,
    album text,
    language varchar(16),
    release_year integer,
    source_type varchar(32) not null,
    source_path text,
    content text not null,
    content_hash varchar(64) not null,
    format varchar(16) not null,
    parse_status varchar(32) not null,
    parse_message text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index if not exists idx_lyrics_content_hash on lyrics(content_hash);
create index if not exists idx_lyrics_source_path on lyrics(source_path);
create index if not exists idx_lyrics_title_artist on lyrics(title, artist);
create index if not exists idx_lyrics_parse_status on lyrics(parse_status);

create table if not exists song_lyrics (
    id integer primary key autoincrement,
    -- v0.5 uses track_files.id as the public song id. A future songs table can migrate this FK.
    song_id bigint not null,
    lyric_id bigint not null,
    match_type varchar(32) not null,
    match_score integer not null default 0,
    is_primary boolean not null default false,
    created_at timestamp not null,
    updated_at timestamp not null,
    foreign key (song_id) references track_files(id),
    foreign key (lyric_id) references lyrics(id)
);

create index if not exists idx_song_lyrics_song_id on song_lyrics(song_id);
create index if not exists idx_song_lyrics_lyric_id on song_lyrics(lyric_id);
create unique index if not exists idx_song_lyrics_song_lyric on song_lyrics(song_id, lyric_id);
create unique index if not exists idx_song_lyrics_primary_song on song_lyrics(song_id) where is_primary = true;
