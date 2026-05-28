package com.xingyu.musicvault.openapi;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "xingyu.openapi")
public interface OpenApiSecurityConfig {
    Auth auth();

    RateLimit rateLimit();

    AccessLog accessLog();

    interface Auth {
        @WithDefault("false")
        boolean enabled();

        Optional<String> token();
    }

    interface RateLimit {
        @WithDefault("false")
        boolean enabled();

        @WithDefault("120")
        int requestsPerMinute();
    }

    interface AccessLog {
        @WithDefault("true")
        boolean enabled();
    }
}
