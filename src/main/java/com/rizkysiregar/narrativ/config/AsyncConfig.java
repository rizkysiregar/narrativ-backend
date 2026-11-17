package com.rizkysiregar.narrativ.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

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
        executor.initialize();
        return executor;
    }

    @Bean(name = "postEventExecutor")
    public Executor postEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("post-event-");
        executor.initialize();
        return executor;
    }
}
