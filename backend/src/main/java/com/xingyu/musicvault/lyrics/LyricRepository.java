package com.xingyu.musicvault.lyrics;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class LyricRepository implements PanacheRepository<Lyric> {
    public Lyric findLocalByContentHash(String contentHash) {
        return find("sourceType = ?1 and contentHash = ?2", "LOCAL_FILE", contentHash).firstResult();
    }

    public List<Lyric> findLocalBySourcePath(String sourcePath) {
        return list("sourceType = ?1 and sourcePath = ?2 order by id asc", "LOCAL_FILE", sourcePath);
    }

    public Lyric findAlignmentBySourceTaskId(String sourceTaskId) {
        return find("sourceType = ?1 and sourceTaskId = ?2", "ALIGNMENT", sourceTaskId).firstResult();
    }

    public Lyric findDraftConfirmedBySourceTaskId(String sourceTaskId) {
        return find("sourceType = ?1 and sourceTaskId = ?2", "DRAFT_CONFIRMED", sourceTaskId).firstResult();
    }
}
