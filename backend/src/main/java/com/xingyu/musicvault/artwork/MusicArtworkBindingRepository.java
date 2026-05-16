package com.xingyu.musicvault.artwork;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class MusicArtworkBindingRepository implements PanacheRepository<MusicArtworkBinding> {
    public MusicArtworkBinding findPrimaryTrackCoverByMusicId(Long musicId) {
        return find("musicId = ?1 and relationType = ?2 and isPrimary = true", musicId, ArtworkService.TRACK_COVER).firstResult();
    }

    public MusicArtworkBinding findTrackCoverByMusicIdAndArtworkId(Long musicId, Long artworkId) {
        return find("musicId = ?1 and artworkId = ?2 and relationType = ?3", musicId, artworkId, ArtworkService.TRACK_COVER)
                .firstResult();
    }

    public List<MusicArtworkBinding> findPrimaryTrackCoversByMusicIds(List<Long> musicIds) {
        if (musicIds == null || musicIds.isEmpty()) {
            return List.of();
        }
        return list("musicId in ?1 and relationType = ?2 and isPrimary = true", musicIds, ArtworkService.TRACK_COVER);
    }

    public List<MusicArtworkBinding> findPrimaryTrackCoversByArtworkId(Long artworkId) {
        return list("artworkId = ?1 and relationType = ?2 and isPrimary = true", artworkId, ArtworkService.TRACK_COVER);
    }

    public List<Object[]> countPrimaryTrackCoversByArtworkIds(List<Long> artworkIds) {
        if (artworkIds == null || artworkIds.isEmpty()) {
            return List.of();
        }
        return getEntityManager()
                .createQuery("""
                        select binding.artworkId, count(binding)
                        from MusicArtworkBinding binding
                        where binding.artworkId in :artworkIds
                          and binding.relationType = :relationType
                          and binding.isPrimary = true
                        group by binding.artworkId
                        """, Object[].class)
                .setParameter("artworkIds", artworkIds)
                .setParameter("relationType", ArtworkService.TRACK_COVER)
                .getResultList();
    }
}
