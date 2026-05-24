alter table tracks add column metadata_extracted_at timestamp;
alter table tracks add column metadata_source text;

create table music_metadata_sync_audit (
    id integer primary key autoincrement,
    batch_id text,
    music_id integer not null,
    file_path text,
    direction text not null,
    source_type text not null,
    target_type text not null,
    mode text not null,
    operation_type text not null,
    before_database_json text,
    after_database_json text,
    before_file_json text,
    after_file_json text,
    changed_fields_json text,
    status text not null,
    error_message text,
    rollback_status text default 'NOT_ROLLED_BACK',
    rollback_of_audit_id integer,
    created_at timestamp not null,
    created_by text,
    foreign key (music_id) references track_files(id)
);

create index idx_metadata_sync_audit_music_id on music_metadata_sync_audit(music_id);
create index idx_metadata_sync_audit_batch_id on music_metadata_sync_audit(batch_id);
create index idx_metadata_sync_audit_created_at on music_metadata_sync_audit(created_at);
