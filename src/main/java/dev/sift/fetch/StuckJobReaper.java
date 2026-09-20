package dev.sift.fetch;

import dev.sift.config.FetchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Fails jobs and summaries that have been running too long.
 *
 * Without this, a restart mid-fetch leaves a job stuck in RUNNING forever, and
 * the active-job unique index then blocks that source permanently.
 *
 * The condition is the age of the row rather than a lifecycle event, so it stays
 * correct when more than one instance is running.
 */
@Component
public class StuckJobReaper {
    private static final Logger log = LoggerFactory.getLogger(StuckJobReaper.class);

    private static final List<FetchStatus> ACTIVE =
            List.of(FetchStatus.PENDING, FetchStatus.RUNNING);

    private final FetchJobRepository fetchJobRepository;
    private final FetchedItemRepository fetchedItemRepository;
    private final FetchProperties properties;

    public StuckJobReaper(FetchJobRepository fetchJobRepository,
                          FetchedItemRepository fetchedItemRepository,
                          FetchProperties properties) {
        this.fetchJobRepository = fetchJobRepository;
        this.fetchedItemRepository = fetchedItemRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${sift.fetch.reap-interval-ms:300000}")
    @Transactional
    public void reap() {
        Instant cutoff = Instant.now().minus(properties.stuckTimeout());

        int jobs = reapStuckJobs(cutoff);
        int items = reapStuckItems(cutoff);

        if (jobs > 0 || items > 0) {
            log.warn("回收卡住的項目：抓取任務 {} 筆、摘要中文章 {} 筆（逾時門檻 {}）",
                    jobs, items, properties.stuckTimeout());
        }
    }

    private int reapStuckJobs(Instant cutoff) {
        List<FetchJob> stuck =
                fetchJobRepository.findByStatusInAndCreatedAtBefore(ACTIVE, cutoff);

        for (FetchJob job : stuck) {
            log.warn("任務卡在 {} 太久，判定為中斷 jobId={} sourceId={} 建立於={}",
                    job.getStatus(), job.getId(), job.getSourceId(), job.getCreatedAt());

            job.fail(FailureType.TRANSIENT, "執行中斷（可能是程式重新啟動），未完成");
        }

        return stuck.size();
    }

    private int reapStuckItems(Instant cutoff) {
        List<FetchedItem> stuck = fetchedItemRepository
                .findByStatusAndUpdatedAtBefore(FetchedItemStatus.SUMMARIZING, cutoff);

        for (FetchedItem item : stuck) {
            log.warn("文章卡在 SUMMARIZING 太久，判定為中斷 itemId={} 最後更新={}",
                    item.getId(), item.getUpdatedAt());

            item.failSummarization(
                    FailureType.TRANSIENT, "摘要執行中斷（可能是程式重新啟動），可重新摘要");
        }

        return stuck.size();
    }
}
