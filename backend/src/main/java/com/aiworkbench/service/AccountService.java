package com.aiworkbench.service;

import com.aiworkbench.entity.account.AccountRow;
import com.aiworkbench.mapper.AccountMapper;
import com.aiworkbench.security.WorkbenchPrincipal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "password", "password1", "password123", "12345678", "123456789", "1234567890",
            "qwerty123", "qwertyuiop", "admin123", "letmein123", "welcome123", "iloveyou",
            "11111111", "00000000", "abc12345", "passw0rd", "workbench");

    private final AccountMapper mapper;
    private final PasswordEncoder encoder;

    public AccountService(AccountMapper mapper, PasswordEncoder encoder) {
        this.mapper = mapper;
        this.encoder = encoder;
    }

    public AccountRow findById(UUID id) {
        return mapper.findById(id);
    }

    public AccountRow findByUsername(String username) {
        return mapper.findByUsername(username);
    }

    public AccountRow require(UUID id) {
        AccountRow account = findById(id);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在");
        }
        return account;
    }

    public List<AccountRow> list() {
        return mapper.list();
    }

    @Transactional
    public AccountRow create(String username, String password, String role) {
        validateUsername(username);
        validatePassword(password);
        validateRole(role);
        mapper.lockAdministration();
        requireLiveAdminIfPresent();
        AccountRow row = new AccountRow(UUID.randomUUID(), username, encoder.encode(password), role,
                true, 0L, null);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在", exception);
        }
        return require(row.id());
    }

    @Transactional
    public AccountRow bootstrap(String username, String password) {
        mapper.lockAdministration();
        if (mapper.count() != 0) {
            return null;
        }
        validateUsername(username);
        validatePassword(password);
        AccountRow row = new AccountRow(UUID.randomUUID(), username, encoder.encode(password), "ADMIN",
                true, 0L, null);
        mapper.insert(row);
        return require(row.id());
    }

    @Transactional
    public AccountRow resetPassword(UUID id, String password) {
        validatePassword(password);
        mapper.lockAdministration();
        requireLiveAdminIfPresent();
        require(id);
        mapper.updatePassword(id, encoder.encode(password));
        return require(id);
    }

    @Transactional
    public AccountRow changeOwnPassword(UUID id, long expectedVersion, String currentPassword, String newPassword) {
        mapper.lockAdministration();
        AccountRow current = require(id);
        if (!current.enabled() || current.authVersion() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "会话已失效，请重新登录");
        }
        if (currentPassword == null || !encoder.matches(currentPassword, current.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码错误");
        }
        validatePassword(newPassword);
        if (mapper.updatePasswordIfVersion(id, expectedVersion, encoder.encode(newPassword)) != 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "会话已失效，请重新登录");
        }
        return require(id);
    }

    @Transactional
    public AccountRow changeRole(UUID id, String role) {
        validateRole(role);
        mapper.lockAdministration();
        requireLiveAdminIfPresent();
        AccountRow current = require(id);
        if (current.role().equals(role)) {
            return current;
        }
        if (current.enabled() && current.role().equals("ADMIN") && mapper.enabledAdminCount() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "必须保留至少一名启用的管理员");
        }
        mapper.updateRole(id, role);
        return require(id);
    }

    @Transactional
    public AccountRow changeEnabled(UUID id, boolean enabled) {
        mapper.lockAdministration();
        requireLiveAdminIfPresent();
        AccountRow current = require(id);
        if (current.enabled() == enabled) {
            return current;
        }
        if (!enabled && current.role().equals("ADMIN") && mapper.enabledAdminCount() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "必须保留至少一名启用的管理员");
        }
        mapper.updateEnabled(id, enabled);
        return require(id);
    }

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.codePointCount(0, password.length()) > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码须为 8～64 个字符");
        }
        if (WEAK_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码过于常见，请更换密码");
        }
    }

    private static void validateRole(String role) {
        if (!"ADMIN".equals(role) && !"USER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色只能是 ADMIN 或 USER");
        }
    }

    private void requireLiveAdminIfPresent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof WorkbenchPrincipal principal)) {
            return; // Bootstrap and service-level tests do not enter through HTTP.
        }
        AccountRow actor = mapper.findById(principal.userId());
        if (actor == null || !actor.enabled() || !"ADMIN".equals(actor.role())
                || actor.authVersion() != principal.authVersion()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员身份已失效");
        }
    }
}
