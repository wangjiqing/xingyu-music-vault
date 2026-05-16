package com.xingyu.musicvault.artwork;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ArtworkRepository implements PanacheRepository<Artwork> {
    public Artwork findByFilePath(String filePath) {
        return find("filePath", filePath).firstResult();
    }

    public Artwork findByHash(String hash) {
        return find("hash", hash).firstResult();
    }
}
