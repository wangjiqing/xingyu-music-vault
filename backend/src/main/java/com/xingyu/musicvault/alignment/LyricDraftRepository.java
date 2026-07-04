package com.xingyu.musicvault.alignment;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LyricDraftRepository implements PanacheRepository<LyricDraft> {
    public LyricDraft findByJobId(String jobId) {
        return find("jobId", jobId).firstResult();
    }
}
