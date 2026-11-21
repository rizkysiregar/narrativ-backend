package com.rizkysiregar.narrativ.service.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class RateLimiterService {

    @Value("${app.rate-limit.feed}")
    private int feedLimit;

    @Value("${app.rate-limit.post-create}")
    private int postCreateLimit;

    // a post can carry up to 5 media and each one asks for its own upload url
    @Value("${app.rate-limit.post-upload:60}")
    private int postUploadLimit;

    @Value("${app.rate-limit.interaction}")
    private int interactionLimit;

    @Value("${app.rate-limit.default}")
    private int defaultLimit;

    @Value("${app.rate-limit.auth}")
    private int authLimit;

    // anonymous session upkeep (refresh, logout, Google sign-in config), per client IP per minute: a client does it on
    // every start, so it gets more room than the login attempts of the "auth" bucket
    @Value("${app.rate-limit.auth-session:60}")
    private int authSessionLimit;

    // endpoints that prove ownership with a password (account deletion): few tries, slowly refilled, so a stolen
    // access token cannot be used to guess the password
    @Value("${app.rate-limit.sensitive:5}")
    private int sensitiveLimit;

    public static final String SENSITIVE = "sensitive";
    public static final String AUTH_SESSION = "auth-session";
    private static final Duration SENSITIVE_WINDOW = Duration.ofMinutes(15);

    private final ProxyManager<String> proxyManager;

    public RateLimiterService(@Lazy ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    public Bucket resolveBucket(String userId, String endpointType) {
        return bucketFor("rate-limit:" + userId + ":" + endpointType, endpointType);
    }

    // anonymous callers (login, register, otp, ...) are limited per client IP. Separate key prefix so an IP
    // can never share a bucket with a username.
    public Bucket resolveIpBucket(String ip, String endpointType) {
        return bucketFor("rate-limit-ip:" + ip + ":" + endpointType, endpointType);
    }

    private Bucket bucketFor(String key, String endpointType) {
        Supplier<BucketConfiguration> configSupplier = () -> buildConfig(endpointType);
        return proxyManager.builder().build(key, configSupplier);
    }

    private BucketConfiguration buildConfig(String endpointType) {
        int capacity = switch (endpointType) {
            case "feed" -> feedLimit;
            case "post-create" -> postCreateLimit;
            case "post-upload" -> postUploadLimit;
            case "interaction" -> interactionLimit;
            case "auth" -> authLimit;
            case AUTH_SESSION -> authSessionLimit;
            case SENSITIVE -> sensitiveLimit;
            default -> defaultLimit;
        };
        Duration window = SENSITIVE.equals(endpointType) ? SENSITIVE_WINDOW : Duration.ofMinutes(1);

        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(capacity)
                        .refillGreedy(capacity, window))
                .build();
    }
}
