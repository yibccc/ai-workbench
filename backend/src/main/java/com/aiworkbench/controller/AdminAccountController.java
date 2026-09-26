package com.aiworkbench.controller;

import com.aiworkbench.dto.account.AccountResponse;
import com.aiworkbench.entity.account.AccountRow;
import com.aiworkbench.security.SessionRevoker;
import com.aiworkbench.service.AccountService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/users")
public class AdminAccountController {
    private final AccountService accounts;
    private final SessionRevoker revoker;

    public AdminAccountController(AccountService accounts, SessionRevoker revoker) {
        this.accounts = accounts;
        this.revoker = revoker;
    }

    @GetMapping
    public List<AccountResponse> list() {
        return accounts.list().stream().map(AccountResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@RequestBody CreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账号请求不能为空");
        }
        return AccountResponse.from(accounts.create(request.username(), request.password(), request.role()));
    }

    @PatchMapping("/{id}/role")
    public AccountResponse role(@PathVariable UUID id, @RequestBody RoleRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色请求不能为空");
        }
        AccountRow account = accounts.changeRole(id, request.role());
        revoker.revokeOlder(id, account.authVersion(), null);
        return AccountResponse.from(account);
    }

    @PatchMapping("/{id}/enabled")
    public AccountResponse enabled(@PathVariable UUID id, @RequestBody EnabledRequest request) {
        if (request == null || request.enabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "启用状态不能为空");
        }
        AccountRow account = accounts.changeEnabled(id, request.enabled());
        revoker.revokeOlder(id, account.authVersion(), null);
        return AccountResponse.from(account);
    }

    @PostMapping("/{id}/reset-password")
    public AccountResponse resetPassword(@PathVariable UUID id, @RequestBody PasswordRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码请求不能为空");
        }
        AccountRow account = accounts.resetPassword(id, request.password());
        revoker.revokeOlder(id, account.authVersion(), null);
        return AccountResponse.from(account);
    }

    public record CreateRequest(String username, String password, String role) {
    }

    public record RoleRequest(String role) {
    }

    public record EnabledRequest(Boolean enabled) {
    }

    public record PasswordRequest(String password) {
    }
}
