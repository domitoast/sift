package dev.sift.fetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily fetch of every enabled source.
 */
@Component
public class FetchScheduler {
    private static final Logger log = LoggerFactory.getLogger(FetchScheduler.class);

    private final FetchService fetchService;

    public FetchScheduler(FetchService fetchService) {
        this.fetchService = fetchService;
    }

    @Scheduled(cron = "${sift.fetch.cron}")
    public void fetchAll() {
        long startedAt = System.currentTimeMillis();

        fetchService.fetchAll();

        log.info("本輪耗時 {} ms", System.currentTimeMillis() - startedAt);
    }
}
