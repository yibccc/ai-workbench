package com.aiworkbench.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("workbench.storage")
public record StorageProperties(String provider, URI endpoint, String region, String bucket,
        String accessKey, String secretKey, boolean pathStyle, Duration connectTimeout, Duration readTimeout,
        Duration apiCallAttemptTimeout, Duration apiCallTimeout, Duration reservationDuration) {
    public StorageProperties {
        if (!"rustfs".equals(provider) || endpoint == null || !java.util.Set.of("http", "https").contains(endpoint.getScheme())
                || endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                || endpoint.getFragment() != null || region == null || region.isBlank() || bucket == null || bucket.isBlank()
                || !pathStyle || connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()
                || apiCallAttemptTimeout == null || apiCallAttemptTimeout.compareTo(readTimeout) < 0
                || apiCallTimeout == null || apiCallTimeout.compareTo(apiCallAttemptTimeout) < 0
                || reservationDuration == null || reservationDuration.compareTo(apiCallTimeout) <= 0) {
            throw new IllegalArgumentException("Invalid workbench storage configuration");
        }
    }
}
