package dev.sift.fetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Hands a queued job to the background pool.
 *
 * This lives in its own bean because @Async only takes effect on calls that go
 * through the Spring proxy; a self-call would silently run inline.
 */
@Component
public class FetchRunner {
    private static final Logger log = LoggerFactory.getLogger(FetchRunner.class);

    private final FetchService fetchService;

    public FetchRunner(FetchService fetchService) {
        this.fetchService = fetchService;
    }

    @Async("fetchExecutor")
    public void run(Long sourceId, Long jobId) {
        log.debug("背景抓取開始 jobId={} sourceId={} thread={}",
                jobId, sourceId, Thread.currentThread().getName());

        fetchService.runJob(sourceId, jobId);
    }
}
