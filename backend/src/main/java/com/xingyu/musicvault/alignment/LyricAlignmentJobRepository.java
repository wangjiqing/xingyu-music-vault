package com.xingyu.musicvault.alignment;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LyricAlignmentJobRepository implements PanacheRepositoryBase<LyricAlignmentJob, String> {
}
