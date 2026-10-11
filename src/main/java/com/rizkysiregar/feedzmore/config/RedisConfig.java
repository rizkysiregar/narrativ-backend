package com.rizkysiregar.feedzmore.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Configuration
public class RedisConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(
                GenericJacksonJsonRedisSerializer.builder().build()
        );
        return template;
    }

    // raw lettuce connection for Bucket4j - rate limit
    // @Lazy: do not open this connection during Tomcat onStartup (it is pulled in eagerly
    // by RateLimitFilter -> RateLimiterService -> ProxyManager). Initializing here would block
    // web-server startup and, with Micrometer Tracing/Brave context-propagation active, can
    // deadlock the blocking RedisClient.connect() call.
    @Lazy
    @Bean
    public StatefulRedisConnection<String, byte[]> bucket4jRedisConnection(
            RedisConnectionFactory connectionFactory) {

        LettuceConnectionFactory lettuceConnectionFactory = (LettuceConnectionFactory) connectionFactory;
        RedisClient nativeClient = (RedisClient) lettuceConnectionFactory.getNativeClient();

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "bucket4j-redis-connect");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<StatefulRedisConnection<String, byte[]>> f = executor.submit(
                    () -> nativeClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)));
            return f.get(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to establish Bucket4j Lettuce Redis connection within "
                            + CONNECT_TIMEOUT + " (host=" + lettuceConnectionFactory.getHostName()
                            + ", port=" + lettuceConnectionFactory.getPort() + ")",
                    e);
        } finally {
            executor.shutdownNow();
        }
    }

    @Lazy
    @Bean
    public ProxyManager<String> lettuceProxyManager(
            StatefulRedisConnection<String, byte[]> bucket4jRedisConnection) {
        return LettuceBasedProxyManager
                .builderFor(bucket4jRedisConnection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2))
                ).build();
    }


}
