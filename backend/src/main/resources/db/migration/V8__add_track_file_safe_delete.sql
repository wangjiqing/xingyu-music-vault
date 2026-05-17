alter table track_files add column deleted_at timestamp;
alter table track_files add column trash_path text;
alter table track_files add column delete_status varchar(32) not null default 'active';

create index if not exists idx_track_files_delete_status on track_files(delete_status);
create index if not exists idx_track_files_deleted_at on track_files(deleted_at);
