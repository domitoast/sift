package dev.sift.summarize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically summarizes articles that are waiting.
 */
@Component
public class SummarizeScheduler {
    private static final Logger log = LoggerFactory.getLogger(SummarizeScheduler.class);

    private final SummarizeService summarizeService;

    public SummarizeScheduler(SummarizeService summarizeService) {
        this.summarizeService = summarizeService;
    }

    @Scheduled(
            fixedDelayString = "${sift.summarize.interval-ms}",
            initialDelayString = "${sift.summarize.initial-delay-ms}")
    public void summarize() {
        try {
            summarizeService.summarizePending();
        } catch (Exception e) {
            log.error("摘要排程發生預期外的錯誤", e);
        }
    }
}
