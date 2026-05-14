package com.xingyu.musicvault.lyrics;

public enum LyricStatus {
    BOUND,
    NO_LYRIC,
    // Scan-level state: a lyric file was imported, but no song candidate was found.
    UNMATCHED,
    PARSE_FAILED,
    MISSING_FILE
}
