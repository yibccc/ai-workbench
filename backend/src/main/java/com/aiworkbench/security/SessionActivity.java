package com.aiworkbench.security;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Business idle time is independent of Spring Session's transport access time. */
@Component
public class SessionActivity {
    public static final long IDLE_MILLIS = Duration.ofDays(7).toMillis();
    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
            local last = redis.call('GET', KEYS[1])
            if not last then return 0 end
            if tonumber(ARGV[1]) - tonumber(last) >= tonumber(ARGV[2]) then
              redis.call('DEL', KEYS[1])
              return 0
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> TOUCH = new DefaultRedisScript<>("""
            local last = redis.call('GET', KEYS[1])
            if not last then return 0 end
            if tonumber(ARGV[1]) - tonumber(last) >= tonumber(ARGV[2]) then
              redis.call('DEL', KEYS[1])
              return 0
            end
            if tonumber(ARGV[1]) > tonumber(last) then
              redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    public SessionActivity(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public void initialize(String sessionId) {
        redis.opsForValue().set(key(sessionId), Long.toString(clock.millis()), Duration.ofDays(7));
    }

    public boolean isLive(String sessionId) {
        return Long.valueOf(1).equals(redis.execute(CHECK, List.of(key(sessionId)),
                Long.toString(clock.millis()), Long.toString(IDLE_MILLIS)));
    }

    public boolean touch(String sessionId) {
        return Long.valueOf(1).equals(redis.execute(TOUCH, List.of(key(sessionId)),
                Long.toString(clock.millis()), Long.toString(IDLE_MILLIS)));
    }

    public void remove(String sessionId) {
        redis.delete(key(sessionId));
    }

    private static String key(String id) {
        return "workbench:auth:activity:" + id;
    }
}
