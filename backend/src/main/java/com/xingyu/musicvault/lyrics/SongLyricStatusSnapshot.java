package com.xingyu.musicvault.lyrics;

public record SongLyricStatusSnapshot(
        Long musicId,
        SongLyricStatus lyricStatus,
        Long lyricId,
        boolean hasLrc,
        boolean hasSwlrc
) {
    public boolean missingSwlrc() {
        return lyricStatus == SongLyricStatus.LRC_READY || lyricStatus == SongLyricStatus.NO_LYRICS;
    }
}
