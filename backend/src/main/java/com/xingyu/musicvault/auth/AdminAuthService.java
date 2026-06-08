package com.xingyu.musicvault.auth;

import com.xingyu.musicvault.common.ConflictException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAuthorizedException;

import java.time.LocalDateTime;
import java.util.Optional;

@ApplicationScoped
public class AdminAuthService {
    private static final int MIN_PASSWORD_LENGTH = 8;

    @Inject
    PasswordHashService passwordHashService;

    public boolean initialized() {
        return AdminUser.count() > 0;
    }

    @Transactional
    public AdminUser setup(String username, String password) {
        if (initialized()) {
            throw new ConflictException("管理员账号已初始化");
        }
        String normalizedUsername = normalizeUsername(username);
        validatePassword(password);

        AdminUser user = new AdminUser();
        user.username = normalizedUsername;
        user.passwordHash = passwordHashService.hash(password);
        user.role = AdminUser.ROLE_ADMIN;
        user.enabled = true;
        user.persist();
        return user;
    }

    @Transactional
    public AdminUser login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        if (password == null || password.isBlank()) {
            throw invalidCredentials();
        }

        Optional<AdminUser> user = AdminUser.find("username", normalizedUsername).firstResultOptional();
        if (user.isEmpty() || !user.get().enabled || !passwordHashService.verify(password, user.get().passwordHash)) {
            throw invalidCredentials();
        }

        AdminUser adminUser = user.get();
        adminUser.lastLoginAt = LocalDateTime.now();
        return adminUser;
    }

    public Optional<AdminUser> findEnabledUser(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<AdminUser> user = AdminUser.findByIdOptional(id);
        return user.filter(adminUser -> adminUser.enabled);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new BadRequestException("用户名不能为空");
        }
        return username.trim();
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("密码不能为空");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("密码至少需要 8 位");
        }
    }

    private NotAuthorizedException invalidCredentials() {
        return new NotAuthorizedException("用户名或密码错误");
    }
}
