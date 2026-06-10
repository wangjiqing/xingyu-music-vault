package com.xingyu.musicvault.openapi;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

@ConfigMapping(prefix = "xingyu.openapi")
public interface OpenApiSecurityConfig {
    Auth auth();

    RateLimit rateLimit();

    AccessLog accessLog();

    Credential credential();

    Hmac hmac();

    /**
     * @deprecated v1.1.3 起 OpenAPI 不再使用静态 Token，保留该配置仅用于兼容旧部署配置。
     */
    @Deprecated(since = "1.1.3", forRemoval = false)
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

    interface Credential {
        Optional<String> masterKey();
    }

    interface Hmac {
        @WithDefault("300")
        long timestampWindowSeconds();

        @WithDefault("600")
        long nonceTtlSeconds();

        @WithDefault("1048576")
        long maxBodyBytes();
    }
}
