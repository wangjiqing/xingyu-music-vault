create table if not exists tracks (
    id integer primary key autoincrement,
    title text,
    normalized_title text,
    metadata_status varchar(32) not null default 'pending',
    lyrics_status varchar(32) not null default 'pending',
    artwork_status varchar(32) not null default 'pending',
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_tracks_normalized_title on tracks(normalized_title);
create index if not exists idx_tracks_created_at on tracks(created_at);

create table if not exists scan_jobs (
    id integer primary key autoincrement,
    job_type varchar(64) not null,
    status varchar(32) not null,
    music_dirs text,
    total_files integer not null default 0,
    scanned_files integer not null default 0,
    new_files integer not null default 0,
    updated_files integer not null default 0,
    skipped_files integer not null default 0,
    error_files integer not null default 0,
    error_message text,
    started_at timestamp,
    finished_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_scan_jobs_status on scan_jobs(status);
create index if not exists idx_scan_jobs_created_at on scan_jobs(created_at);
