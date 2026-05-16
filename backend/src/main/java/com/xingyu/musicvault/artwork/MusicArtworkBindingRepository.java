package com.xingyu.musicvault.artwork;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class MusicArtworkBindingRepository implements PanacheRepository<MusicArtworkBinding> {
    public MusicArtworkBinding findPrimaryTrackCoverByMusicId(Long musicId) {
        return find("musicId = ?1 and relationType = ?2 and isPrimary = true", musicId, ArtworkService.TRACK_COVER).firstResult();
    }

    public List<MusicArtworkBinding> findPrimaryTrackCoversByMusicIds(List<Long> musicIds) {
        if (musicIds == null || musicIds.isEmpty()) {
            return List.of();
        }
        return list("musicId in ?1 and relationType = ?2 and isPrimary = true", musicIds, ArtworkService.TRACK_COVER);
    }
}
