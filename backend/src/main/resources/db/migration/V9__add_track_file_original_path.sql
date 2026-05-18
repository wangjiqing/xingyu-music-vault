alter table track_files add column original_path text;

update track_files
set original_path = file_path
where delete_status = 'trashed'
  and original_path is null;
