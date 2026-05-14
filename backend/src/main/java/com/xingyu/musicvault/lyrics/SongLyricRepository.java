package com.xingyu.musicvault.lyrics;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class SongLyricRepository implements PanacheRepository<SongLyric> {
    public SongLyric findPrimaryBySongId(Long songId) {
        return find("songId = ?1 and isPrimary = true", songId).firstResult();
    }

    public SongLyric findBySongIdAndLyricId(Long songId, Long lyricId) {
        return find("songId = ?1 and lyricId = ?2", songId, lyricId).firstResult();
    }

    public List<SongLyric> findPrimaryBySongIds(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return List.of();
        }
        return list("songId in ?1 and isPrimary = true", songIds);
    }
}
