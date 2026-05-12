create table tracks_new (
    id integer primary key autoincrement,
    title text not null,
    normalized_title text,
    metadata_status varchar(32) not null default 'pending',
    lyrics_status varchar(32) not null default 'pending',
    artwork_status varchar(32) not null default 'pending',
    created_at timestamp not null,
    updated_at timestamp not null
);

insert into tracks_new (
    id,
    title,
    normalized_title,
    metadata_status,
    lyrics_status,
    artwork_status,
    created_at,
    updated_at
)
select
    id,
    coalesce(title, 'Untitled'),
    normalized_title,
    metadata_status,
    lyrics_status,
    artwork_status,
    created_at,
    updated_at
from tracks;

drop table tracks;
alter table tracks_new rename to tracks;

create index if not exists idx_tracks_normalized_title on tracks(normalized_title);
create index if not exists idx_tracks_created_at on tracks(created_at);

create table scan_jobs_new (
    id integer primary key autoincrement,
    job_type varchar(64) not null,
    status varchar(32) not null,
    music_dirs text,
    total_files bigint not null default 0,
    scanned_files bigint not null default 0,
    new_files bigint not null default 0,
    updated_files bigint not null default 0,
    skipped_files bigint not null default 0,
    error_files bigint not null default 0,
    error_message text,
    started_at timestamp,
    finished_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

insert into scan_jobs_new (
    id,
    job_type,
    status,
    music_dirs,
    total_files,
    scanned_files,
    new_files,
    updated_files,
    skipped_files,
    error_files,
    error_message,
    started_at,
    finished_at,
    created_at,
    updated_at
)
select
    id,
    job_type,
    status,
    music_dirs,
    total_files,
    scanned_files,
    new_files,
    updated_files,
    skipped_files,
    error_files,
    error_message,
    started_at,
    finished_at,
    created_at,
    updated_at
from scan_jobs;

drop table scan_jobs;
alter table scan_jobs_new rename to scan_jobs;

create index if not exists idx_scan_jobs_status on scan_jobs(status);
create index if not exists idx_scan_jobs_created_at on scan_jobs(created_at);
