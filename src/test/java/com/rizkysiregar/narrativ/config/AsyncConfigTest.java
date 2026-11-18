package com.rizkysiregar.narrativ.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncConfigTest {

    private final AsyncConfig config = new AsyncConfig();

    @Test
    void queuedEventsAreStillPublishedWhenTheAppStops() throws Exception {
        assertQueuedTasksFinishOnShutdown((ThreadPoolTaskExecutor) config.postEventExecutor());
    }

    @Test
    void queuedMediaDeletionsStillRunWhenTheAppStops() throws Exception {
        assertQueuedTasksFinishOnShutdown((ThreadPoolTaskExecutor) config.mediaCleanupExecutor());
    }

    private static void assertQueuedTasksFinishOnShutdown(ThreadPoolTaskExecutor executor) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger done = new AtomicInteger();
        int tasks = 10; // more than the pool, so most of them wait in the queue
        for (int i = 0; i < tasks; i++) {
            executor.execute(() -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                done.incrementAndGet();
            });
        }

        // the tasks are released while shutdown is already waiting for them
        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                // released right away then
            }
            release.countDown();
        });
        releaser.start();
        executor.shutdown();
        releaser.join();

        assertEquals(tasks, done.get());
    }
}
