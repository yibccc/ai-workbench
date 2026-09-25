package com.aiworkbench.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class LoginLimiter {
    private static final DefaultRedisScript<Long> FAILURE = new DefaultRedisScript<>("""
            local failures = redis.call('INCR', KEYS[1])
            if failures == 1 then redis.call('EXPIRE', KEYS[1], 900) end
            if failures >= 5 then redis.call('SET', KEYS[2], '1', 'EX', 60) end
            return failures
            """, Long.class);

    private final StringRedisTemplate redis;

    public LoginLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isBlocked(String username) {
        return Boolean.TRUE.equals(redis.hasKey(key(username) + ":wait"));
    }

    public void failure(String username) {
        String key = key(username);
        redis.execute(FAILURE, List.of(key + ":count", key + ":wait"));
    }

    public void success(String username) {
        String key = key(username);
        redis.delete(List.of(key + ":count", key + ":wait"));
    }

    private static String key(String username) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(username.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return "workbench:auth:login:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
