package com.xingyu.musicvault.auth;

import com.xingyu.musicvault.auth.AdminAuthDtos.AdminUserResponse;
import com.xingyu.musicvault.auth.AdminAuthDtos.LoginRequest;
import com.xingyu.musicvault.auth.AdminAuthDtos.SetupRequest;
import com.xingyu.musicvault.auth.AdminAuthDtos.SetupStatusResponse;
import com.xingyu.musicvault.common.ErrorResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

@Path("/api/admin/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AdminAuthResource {
    @Inject
    AdminAuthService authService;

    @Inject
    AdminSessionService sessionService;

    @Inject
    AdminAuthConfig config;

    @GET
    @Path("/setup-status")
    public SetupStatusResponse setupStatus() {
        return new SetupStatusResponse(authService.initialized());
    }

    @POST
    @Path("/setup")
    public Response setup(SetupRequest request) {
        AdminUser user = authService.setup(request == null ? null : request.username(), request == null ? null : request.password());
        return Response.status(Response.Status.CREATED)
                .entity(AdminUserResponse.from(user))
                .build();
    }

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        AdminUser user = authService.login(request == null ? null : request.username(), request == null ? null : request.password());
        String sessionId = sessionService.createSession(user);
        return Response.ok(AdminUserResponse.from(user))
                .cookie(sessionCookie(sessionId, sessionService.cookieMaxAgeSeconds()))
                .build();
    }

    @POST
    @Path("/logout")
    @Consumes(MediaType.WILDCARD)
    public Response logout(@Context HttpHeaders headers) {
        jakarta.ws.rs.core.Cookie cookie = headers.getCookies().get(AdminSessionService.COOKIE_NAME);
        if (cookie != null) {
            sessionService.invalidate(cookie.getValue());
        }
        return Response.ok()
                .cookie(sessionCookie("", 0))
                .build();
    }

    @GET
    @Path("/me")
    public Response me(@Context HttpHeaders headers) {
        return sessionService.findUserId(headers)
                .flatMap(authService::findEnabledUser)
                .map(user -> Response.ok(AdminUserResponse.from(user)).build())
                .orElseGet(() -> Response.status(Response.Status.UNAUTHORIZED)
                        .entity(new ErrorResponse("unauthorized", "未登录或登录已过期"))
                        .build());
    }

    private NewCookie sessionCookie(String sessionId, int maxAge) {
        return new NewCookie.Builder(AdminSessionService.COOKIE_NAME)
                .value(sessionId)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(config.cookie().secure())
                .sameSite(sameSite())
                .build();
    }

    private NewCookie.SameSite sameSite() {
        try {
            return NewCookie.SameSite.valueOf(config.cookie().sameSite().toUpperCase());
        } catch (RuntimeException ex) {
            return NewCookie.SameSite.LAX;
        }
    }
}
