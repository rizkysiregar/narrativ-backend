package com.rizkysiregar.narrativ.messaging.idempotency;

import com.rizkysiregar.narrativ.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guards RabbitMQ consumers against duplicate delivery.
 *
 * <p>RabbitMQ guarantees at-least-once delivery, so a message can be delivered
 * more than once (e.g. the consumer processed it but crashed before ack, or a
 * retry after a partial failure). Each consumer calls {@link #tryReserve} before
 * doing side effects and {@link #release} if the side effect failed and the
 * event must be retried.
 */
@Component
@RequiredArgsConstructor
public class EventDeduplicator {

    private static final String KEY_PREFIX = "mq:processed:";
    private static final String MARKER = "1";

    private final RedisUtil redisUtil;

    @Value("${app.messaging.idempotency-ttl-seconds}")
    private long ttlSeconds;

    /**
     * Atomically reserves the (consumer, eventKey) pair.
     *
     * @return {@code true} if it is the first time this pair is seen (the caller
     *         should process the event), {@code false} if it was already handled
     *         (the caller should skip it).
     */
    public boolean tryReserve(String consumer, String eventKey) {
        return redisUtil.setIfAbsent(key(consumer, eventKey), MARKER, ttlSeconds);
    }

    /**
     * Releases a reservation so a failed event can be retried on redelivery.
     */
    public void release(String consumer, String eventKey) {
        redisUtil.delete(key(consumer, eventKey));
    }

    private String key(String consumer, String eventKey) {
        return KEY_PREFIX + consumer + ":" + eventKey;
    }
}
