package com.rizkysiregar.feedzmore.service.ratelimit;

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

    @Value("${app.rate-limit.interaction}")
    private int interactionLimit;

    @Value("${app.rate-limit.default}")
    private int defaultLimit;

    @Value("${app.rate-limit.auth}")
    private int authLimit;

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
            case "interaction" -> interactionLimit;
            case "auth" -> authLimit;
            default -> defaultLimit;
        };

        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofMinutes(1)))
                .build();
    }
}
