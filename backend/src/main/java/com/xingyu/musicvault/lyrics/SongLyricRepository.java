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

    public List<SongLyric> findByLyricIds(List<Long> lyricIds) {
        if (lyricIds == null || lyricIds.isEmpty()) {
            return List.of();
        }
        return list("lyricId in ?1 order by isPrimary desc, id asc", lyricIds);
    }

    public List<Long> findDistinctBoundLyricIds() {
        return getEntityManager()
                .createQuery("select distinct songLyric.lyricId from SongLyric songLyric", Long.class)
                .getResultList();
    }

    public boolean hasBindings(Long lyricId) {
        if (lyricId == null) {
            return false;
        }
        return count("lyricId", lyricId) > 0;
    }
}
