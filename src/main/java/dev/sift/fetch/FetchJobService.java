package dev.sift.fetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fetch job state transitions. Each method is one short transaction and flushes
 * explicitly, because the active-job unique index depends on these writes being
 * visible immediately.
 */
@Service
public class FetchJobService {
    private static final Logger log = LoggerFactory.getLogger(FetchJobService.class);

    private static final List<FetchStatus> ACTIVE =
            List.of(FetchStatus.PENDING, FetchStatus.RUNNING);

    private final FetchJobRepository fetchJobRepository;

    public FetchJobService(FetchJobRepository fetchJobRepository) {
        this.fetchJobRepository = fetchJobRepository;
    }

    public Long enqueue(Long sourceId) {
        if (fetchJobRepository.existsBySourceIdAndStatusIn(sourceId, ACTIVE)) {
            log.info("跳過：這個來源已有進行中的任務 sourceId={}", sourceId);
            return null;
        }

        FetchJob job = new FetchJob(sourceId);

        try {
            Long jobId = fetchJobRepository.saveAndFlush(job).getId();
            log.info("任務排隊 jobId={} sourceId={}", jobId, sourceId);
            return jobId;
        } catch (DataIntegrityViolationException e) {
            log.warn("這個來源已有進行中的任務（撞到唯一約束）sourceId={}", sourceId);
            return null;
        }
    }

    @Transactional
    public void start(Long jobId) {
        FetchJob job = load(jobId);
        job.start();
        fetchJobRepository.flush();

        log.info("任務開始 jobId={}", jobId);
    }

    @Transactional
    public void succeed(Long jobId, int newItemCount) {
        FetchJob job = load(jobId);
        job.succeed(newItemCount);
        fetchJobRepository.flush();

        log.info("任務成功 jobId={} 新增={}", jobId, newItemCount);
    }

    @Transactional
    public void fail(Long jobId, FailureType failureType, String reason) {
        FetchJob job = load(jobId);
        job.fail(failureType, reason);
        fetchJobRepository.flush();

        log.warn("任務失敗 jobId={} 類型={} 原因={}", jobId, failureType, reason);
    }

    private FetchJob load(Long jobId) {
        return fetchJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("找不到任務 jobId=" + jobId));
    }
}
