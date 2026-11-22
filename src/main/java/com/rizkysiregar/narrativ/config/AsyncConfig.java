package com.rizkysiregar.narrativ.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // on shutdown queued tasks still run, for at most this long: an event that is never published is lost for good
    static final int AWAIT_TERMINATION_SECONDS = 15;

    /**
     * Deletes object storage files after the request that asked for it (account deletion can mean hundreds of
     * files). A file that is not removed, because the queue is full or the app stops, no longer belongs to any
     * post_media row, so the orphan cleanup finds it later.
     */
    @Bean(name = "mediaCleanupExecutor")
    public Executor mediaCleanupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("media-cleanup-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

    /** Freezes and unfreezes chats after a block changes; see ChatBlockSync. Failures are logged, not retried. */
    @Bean(name = "chatSyncExecutor")
    public Executor chatSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

    /**
     * Publishes the RabbitMQ events (notifications, video thumbnail requests) after the request returned. A video
     * whose thumbnail request is dropped keeps no thumbnail, so a pending publish is waited for on shutdown.
     */
    @Bean(name = "postEventExecutor")
    public Executor postEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("post-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }
}
