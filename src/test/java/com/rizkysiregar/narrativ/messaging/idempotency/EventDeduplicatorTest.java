package com.rizkysiregar.narrativ.messaging.idempotency;

import com.rizkysiregar.narrativ.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDeduplicatorTest {

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private EventDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deduplicator, "ttlSeconds", 60L);
    }

    @Test
    void tryReserveUsesNamespacedKeyAndTtl() {
        when(redisUtil.setIfAbsent("mq:processed:email:post-1", "1", 60L)).thenReturn(true);

        assertTrue(deduplicator.tryReserve("email", "post-1"));

        verify(redisUtil).setIfAbsent("mq:processed:email:post-1", "1", 60L);
    }

    @Test
    void tryReserveReturnsFalseWhenKeyExists() {
        when(redisUtil.setIfAbsent("mq:processed:email:post-1", "1", 60L)).thenReturn(false);

        assertFalse(deduplicator.tryReserve("email", "post-1"));
    }

    @Test
    void releaseDeletesNamespacedKey() {
        deduplicator.release("notification", "post-1:user-2");

        verify(redisUtil).delete("mq:processed:notification:post-1:user-2");
    }
}
