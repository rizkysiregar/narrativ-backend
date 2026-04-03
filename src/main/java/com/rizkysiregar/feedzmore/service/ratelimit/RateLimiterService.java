package com.rizkysiregar.feedzmore.service.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

@Service
public class RateLimiterService {

    @Value("${rate-limit.feed}")
    private int feedLimit;

    @Value("${rate-limit.post-create}")
    private int postCreateLimit;

    @Value("${rate-limit.interaction}")
    private int interactionLimit;

    @Value("${rate-limit.default}")
    private int defaultLimit;

    private final ProxyManager<String> proxyManager;

    public RateLimiterService(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }

    public Bucket resolveBucket(String userId, String endpointType) {
        String key = "rate-limit:" + userId + ":" + endpointType;
        Supplier<BucketConfiguration> configSupplier = () -> buildConfig(endpointType);
        return proxyManager.builder().build(key, configSupplier);
    }

    private BucketConfiguration buildConfig(String endpointType) {
        int capacity = switch (endpointType) {
            case "feed" -> feedLimit;
            case "post-create" -> postCreateLimit;
            case "interaction" -> interactionLimit;
            default -> defaultLimit;
        };

        return BucketConfiguration.builder()
                .addLimit(limit -> limit
                        .capacity(capacity)
                        .refillGreedy(capacity, Duration.ofMinutes(1)))
                .build();
    }
}
