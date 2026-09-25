package com.aiworkbench.controller;

import com.aiworkbench.dto.account.AccountResponse;
import com.aiworkbench.entity.account.AccountRow;
import com.aiworkbench.events.WorkbenchSocketSessions;
import com.aiworkbench.security.CurrentUser;
import com.aiworkbench.security.LoginSessionService;
import com.aiworkbench.security.SessionActivity;
import com.aiworkbench.security.SessionRevoker;
import com.aiworkbench.security.WorkbenchPrincipal;
import com.aiworkbench.security.WorkbenchAuthentications;
import com.aiworkbench.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final LoginSessionService logins;
    private final SessionActivity activity;
    private final SessionRevoker revoker;
    private final WorkbenchSocketSessions sockets;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AccountService accounts, LoginSessionService logins,
                          SessionActivity activity, SessionRevoker revoker, WorkbenchSocketSessions sockets) {
        this.accounts = accounts;
        this.logins = logins;
        this.activity = activity;
        this.revoker = revoker;
        this.sockets = sockets;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/login")
    public AccountResponse login(@RequestBody LoginRequest body, HttpServletRequest request,
                                 HttpServletResponse response) {
        String username = body == null ? null : body.username();
        String password = body == null ? null : body.password();
        return logins.login(username, password, request, response);
    }

    @GetMapping("/me")
    public AccountResponse me() {
        return AccountResponse.from(accounts.require(CurrentUser.requireId()));
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            activity.remove(session.getId());
            sockets.closeSession(session.getId());
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @PostMapping("/activity")
    public Map<String, Boolean> active(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !activity.touch(session.getId())) {
            if (session != null) {
                sockets.closeSession(session.getId());
                session.invalidate();
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "会话已失效，请重新登录");
        }
        return Map.of("active", true);
    }

    @PostMapping("/password")
    public AccountResponse changePassword(@RequestBody ChangePasswordRequest body,
                                          HttpServletRequest request, HttpServletResponse response) {
        WorkbenchPrincipal current = CurrentUser.require();
        if (body == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码请求不能为空");
        }
        AccountRow account = accounts.changeOwnPassword(current.userId(),
                current.authVersion(), body.currentPassword(), body.newPassword());
        WorkbenchPrincipal updated = WorkbenchAuthentications.principal(account);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(WorkbenchAuthentications.authentication(updated));
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(false);
        contextRepository.saveContext(context, request, response);
        revoker.revokeOlder(current.userId(), updated.authVersion(), session.getId());
        // The HTTP Session remains valid, but its existing socket still has the old immutable Principal.
        sockets.closeSession(session.getId());
        return AccountResponse.from(account);
    }

    public record LoginRequest(String username, String password) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }
}
