package com.xingyu.musicvault.config;

import io.smallrye.config.ConfigMapping;

import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "music-vault")
public interface MusicVaultConfig {
    String dataDir();

    String configDir();

    List<String> musicDirs();

    List<String> lyricDirs();

    String alignmentJobsDir();

    String alignmentWorkerMusicDir();

    String alignmentWorkerJobsDir();

    String alignmentAssetsDir();

    Optional<String> alignmentLyricsRoot();

    String alignmentLyricsSubdir();

    String alignmentDraftDefaultAsrModel();

    boolean alignmentDraftDefaultSkipSeparation();

    boolean alignmentDraftDefaultVadFilter();

    int alignmentDraftMaxTextBytes();

    int alignmentStatusSyncIntervalSeconds();

    String alignmentStatusSyncInterval();

    String dbPath();

    String apiToken();

    String ffprobePath();

    String ffmpegPath();
}
