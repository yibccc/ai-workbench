package com.aiworkbench.security;

import com.aiworkbench.dto.account.AccountResponse;
import com.aiworkbench.entity.account.AccountRow;
import com.aiworkbench.mapper.AccountMapper;
import com.aiworkbench.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Serializes credential validation and Session creation with administrator account changes. */
@Service
public class LoginSessionService {
    private final AccountMapper mapper;
    private final AccountService accounts;
    private final PasswordEncoder passwords;
    private final LoginLimiter limiter;
    private final SessionActivity activity;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public LoginSessionService(AccountMapper mapper, AccountService accounts, PasswordEncoder passwords,
                               LoginLimiter limiter, SessionActivity activity) {
        this.mapper = mapper;
        this.accounts = accounts;
        this.passwords = passwords;
        this.limiter = limiter;
        this.activity = activity;
    }

    @Transactional
    public AccountResponse login(String username, String password, HttpServletRequest request,
                                 HttpServletResponse response) {
        if (username == null || password == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        // reset/disable/role/self-password use the same transaction lock before changing authVersion.
        mapper.lockAdministration();
        AccountRow account = accounts.findByUsername(username);
        String limiterKey = account == null ? username : account.username();
        if (limiter.isBlocked(limiterKey)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过多，请稍后再试");
        }
        if (account == null || !account.enabled() || !passwords.matches(password, account.passwordHash())) {
            limiter.failure(limiterKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        limiter.success(limiterKey);

        HttpSession session = request.getSession(true);
        String previousId = session.getId();
        request.changeSessionId();
        if (!previousId.equals(session.getId())) {
            activity.remove(previousId);
        }
        WorkbenchPrincipal principal = WorkbenchAuthentications.principal(account);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(WorkbenchAuthentications.authentication(principal));
        SecurityContextHolder.setContext(context);
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal.getName());
        activity.initialize(session.getId());
        contexts.saveContext(context, request, response);
        return AccountResponse.from(account);
    }
}
