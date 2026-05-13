create table if not exists track_files (
    id integer primary key autoincrement,
    track_id bigint,
    file_path text not null unique,
    file_name text not null,
    file_ext varchar(16) not null,
    file_size bigint not null default 0,
    last_modified_at timestamp,
    scan_job_id bigint,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_track_files_track_id on track_files(track_id);
create index if not exists idx_track_files_scan_job_id on track_files(scan_job_id);
create index if not exists idx_track_files_file_ext on track_files(file_ext);
