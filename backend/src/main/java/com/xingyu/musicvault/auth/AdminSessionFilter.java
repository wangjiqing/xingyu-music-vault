package com.xingyu.musicvault.auth;

import com.xingyu.musicvault.common.ErrorResponse;
import com.xingyu.musicvault.config.MusicVaultConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class AdminSessionFilter implements ContainerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String GET = "GET";

    @Inject
    AdminSessionService sessionService;

    @Inject
    AdminAuthService authService;

    @Inject
    AdminAuthConfig adminAuthConfig;

    @Inject
    MusicVaultConfig musicVaultConfig;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (!path.startsWith("api/") || isPublicPath(requestContext, path) || isOpenApiPath(path)) {
            return;
        }

        if (testLegacyTokenAllowed(requestContext)) {
            return;
        }

        jakarta.ws.rs.core.Cookie sessionCookie = requestContext.getCookies().get(AdminSessionService.COOKIE_NAME);
        boolean loggedIn = sessionService.findUserId(sessionCookie == null ? null : sessionCookie.getValue())
                .flatMap(authService::findEnabledUser)
                .isPresent();
        if (loggedIn) {
            return;
        }

        requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("unauthorized", "未登录或登录已过期"))
                .build());
    }

    private boolean isPublicPath(ContainerRequestContext requestContext, String path) {
        // Keep this allowlist deliberately explicit. Any new unauthenticated
        // management endpoint must be reviewed here so it is not opened by
        // broad path matching by accident.
        return "api/health".equals(path)
                || "api/admin/auth/setup-status".equals(path)
                || "api/admin/auth/setup".equals(path)
                || "api/admin/auth/login".equals(path)
                || "api/admin/auth/logout".equals(path)
                || isPublicArtworkFile(requestContext, path)
                || path.startsWith("q/")
                || "q".equals(path);
    }

    private boolean isPublicArtworkFile(ContainerRequestContext requestContext, String path) {
        return GET.equals(requestContext.getMethod())
                && path.matches("api/artworks/\\d+/file");
    }

    private boolean isOpenApiPath(String path) {
        return "api/open/v1".equals(path) || path.startsWith("api/open/v1/");
    }

    private boolean testLegacyTokenAllowed(ContainerRequestContext requestContext) {
        if (!adminAuthConfig.testLegacyToken().enabled()) {
            return false;
        }
        String authorization = requestContext.getHeaderString("Authorization");
        return (BEARER_PREFIX + musicVaultConfig.apiToken()).equals(authorization);
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }
}
