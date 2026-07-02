package com.xingyu.musicvault.config;

import io.smallrye.config.ConfigMapping;

import java.util.List;

@ConfigMapping(prefix = "music-vault")
public interface MusicVaultConfig {
    String dataDir();

    String configDir();

    List<String> musicDirs();

    List<String> lyricDirs();

    String alignmentJobsDir();

    String alignmentWorkerMusicDir();

    String alignmentWorkerJobsDir();

    int alignmentStatusSyncIntervalSeconds();

    String alignmentStatusSyncInterval();

    String dbPath();

    String apiToken();

    String ffprobePath();

    String ffmpegPath();
}
