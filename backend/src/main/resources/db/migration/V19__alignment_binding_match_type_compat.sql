-- Keep alignment-confirmed primary bindings compatible with existing clients
-- that recognize title/artist matched lyrics but do not know internal import actions.
update song_lyrics
set match_type = 'TITLE_ARTIST'
where match_type = 'ALIGNMENT_APPROVED'
  and lyric_id in (
      select id
      from lyrics
      where source_type = 'ALIGNMENT'
  );
