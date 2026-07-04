package com.xingyu.musicvault.api;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ApplicationInfo {
    @ConfigProperty(name = "quarkus.application.version")
    String version;

    public String version() {
        return version;
    }
}
