package com.xingyu.musicvault.lyrics;

public enum LyricStatus {
    // Legacy binding/detail API states. Song lists, dashboards, and recommendations use SongLyricStatus.
    BOUND,
    NO_LYRIC,
    // Scan-level state: a lyric file was imported, but no song candidate was found.
    UNMATCHED,
    PARSE_FAILED,
    MISSING_FILE
}
