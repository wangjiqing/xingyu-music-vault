package com.xingyu.musicvault.auth;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "xingyu.admin.auth")
public interface AdminAuthConfig {
    Cookie cookie();

    Session session();

    TestLegacyToken testLegacyToken();

    interface Cookie {
        @WithDefault("false")
        boolean secure();

        @WithDefault("Lax")
        String sameSite();
    }

    interface Session {
        @WithDefault("1440")
        long ttlMinutes();
    }

    interface TestLegacyToken {
        @WithDefault("false")
        boolean enabled();
    }
}
