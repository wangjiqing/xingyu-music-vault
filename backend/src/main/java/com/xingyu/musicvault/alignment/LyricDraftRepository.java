package com.xingyu.musicvault.alignment;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LyricDraftRepository implements PanacheRepository<LyricDraft> {
    public LyricDraft findByJobId(String jobId) {
        return find("jobId", jobId).firstResult();
    }

    public LyricDraft findLatestConfirmedBySongId(Long songId) {
        return find(
                "musicId = ?1 and draftStatus = ?2 and confirmedTrustedLyricsId is not null order by confirmedAt desc, updatedAt desc",
                songId,
                "CONFIRMED"
        ).firstResult();
    }
}
