package com.xingyu.musicvault.auth;

public final class AdminAuthDtos {
    private AdminAuthDtos() {
    }

    public record SetupStatusResponse(boolean initialized) {
    }

    public record SetupRequest(String username, String password) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record AdminUserResponse(Long id, String username, String role) {
        public static AdminUserResponse from(AdminUser user) {
            return new AdminUserResponse(user.id, user.username, user.role);
        }
    }
}
