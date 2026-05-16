create table if not exists artworks (
    id integer primary key autoincrement,
    file_path text not null,
    file_name text not null,
    file_ext varchar(16) not null,
    mime_type varchar(64) not null,
    file_size bigint not null,
    width integer,
    height integer,
    hash varchar(64) not null,
    source_type varchar(32) not null,
    source_path text,
    title text,
    description text,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index if not exists idx_artworks_file_path on artworks(file_path);
create unique index if not exists idx_artworks_hash on artworks(hash);
create index if not exists idx_artworks_source_type on artworks(source_type);
create index if not exists idx_artworks_file_name on artworks(file_name);

create table if not exists music_artwork_bindings (
    id integer primary key autoincrement,
    -- v0.6 uses track_files.id as the public music id. A future songs/tracks table can migrate this FK.
    music_id bigint not null,
    artwork_id bigint not null,
    relation_type varchar(32) not null,
    is_primary boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null,
    foreign key (music_id) references track_files(id),
    foreign key (artwork_id) references artworks(id)
);

create index if not exists idx_music_artwork_bindings_music_id on music_artwork_bindings(music_id);
create index if not exists idx_music_artwork_bindings_artwork_id on music_artwork_bindings(artwork_id);
create unique index if not exists idx_music_artwork_bindings_music_artwork_relation
    on music_artwork_bindings(music_id, artwork_id, relation_type);
create unique index if not exists idx_music_artwork_bindings_primary_music_relation
    on music_artwork_bindings(music_id, relation_type) where is_primary = true;
