package dev.sift.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pool for background fetching.
 *
 * A dedicated pool rather than the shared one: fetching blocks on other people's
 * servers, and anything sharing a pool with it would block too.
 *
 * The queue is bounded and the rejection policy aborts rather than running on the
 * caller thread, which would push the work back onto the HTTP request thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Configuration
    @ConditionalOnProperty(name = "sift.async.enabled", havingValue = "true", matchIfMissing = true)
    static class PooledExecutorConfig {
        @Bean("fetchExecutor")
        Executor fetchExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

            executor.setCorePoolSize(2);
            executor.setMaxPoolSize(4);
            executor.setQueueCapacity(50);

            executor.setThreadNamePrefix("fetch-");

            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(30);

            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

            executor.initialize();

            log.info("抓取執行緒池：core={} max={} queue={}",
                    executor.getCorePoolSize(),
                    executor.getMaxPoolSize(),
                    50);

            return executor;
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "sift.async.enabled", havingValue = "false")
    static class SyncExecutorConfig {
        @Bean("fetchExecutor")
        Executor fetchExecutor() {
            log.info("抓取採同步執行（sift.async.enabled=false）");
            return new SyncTaskExecutor();
        }
    }
}
