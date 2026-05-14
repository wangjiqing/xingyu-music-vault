package com.xingyu.musicvault.lyrics;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LyricRepository implements PanacheRepository<Lyric> {
    public Lyric findByContentHash(String contentHash) {
        return find("contentHash", contentHash).firstResult();
    }
}
