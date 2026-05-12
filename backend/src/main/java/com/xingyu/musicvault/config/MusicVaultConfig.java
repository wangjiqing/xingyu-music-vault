package com.xingyu.musicvault.config;

import io.smallrye.config.ConfigMapping;

import java.util.List;

@ConfigMapping(prefix = "music-vault")
public interface MusicVaultConfig {
    String dataDir();

    String configDir();

    List<String> musicDirs();

    String dbPath();

    String apiToken();

    String ffprobePath();

    String ffmpegPath();
}
