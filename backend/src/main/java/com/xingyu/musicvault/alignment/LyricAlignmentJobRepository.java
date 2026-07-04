package com.xingyu.musicvault.alignment;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class LyricAlignmentJobRepository implements PanacheRepositoryBase<LyricAlignmentJob, String> {
    public List<LyricAlignmentJob> findSynchronizableJobs() {
        return list("status in ?1 order by createdAt asc", List.of("CREATING", "QUEUED", "RUNNING"));
    }

    public LyricAlignmentJob findActiveDraftJobForSong(Long songId) {
        return find(
                "songId = ?1 and taskType = ?2 and status in ?3 order by createdAt desc",
                songId,
                "LYRIC_DRAFT_EXTRACTION",
                List.of("CREATING", "QUEUED", "RUNNING")
        ).firstResult();
    }
}
