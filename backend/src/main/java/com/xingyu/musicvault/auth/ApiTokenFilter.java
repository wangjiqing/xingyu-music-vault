package com.xingyu.musicvault.auth;

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
public class ApiTokenFilter implements ContainerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    @Inject
    MusicVaultConfig config;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (isPublicPath(path) || !path.startsWith("api/")) {
            return;
        }

        String authorization = requestContext.getHeaderString("Authorization");
        String expected = BEARER_PREFIX + config.apiToken();
        if (!expected.equals(authorization)) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("unauthorized"))
                    .build());
        }
    }

    private boolean isPublicPath(String path) {
        return "api/health".equals(path)
                || path.startsWith("q/")
                || "q".equals(path);
    }

    public record ErrorResponse(String error) {
    }
}
