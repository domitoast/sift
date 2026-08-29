package dev.sift.fetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * fetch_job 的資料庫操作。<b>每個方法都是一個很短的 transaction。</b>
 *
 * <h2>為什麼要獨立成一個類別</h2>
 *
 * {@code @Transactional} 是靠 proxy（代理物件）實作的：
 * Spring 在外面包一層，呼叫進來時先開 transaction，回去時再提交。
 *
 * <p><b>但同一個類別內部的呼叫不會經過那層 proxy。</b>
 * 如果這些方法和 {@code FetchService} 寫在一起，
 * {@code FetchService} 呼叫 {@code this.succeed(...)} 時，
 * {@code @Transactional} 完全不會生效——而且沒有任何錯誤訊息。
 *
 * <p>這叫 self-invocation，是 Spring 最常見的陷阱之一。
 * 拆成兩個 bean 之後，呼叫一定會經過 proxy。
 */
@Service
public class FetchJobService {

    private static final Logger log = LoggerFactory.getLogger(FetchJobService.class);

    /** 還沒結束的狀態。與 V5 的 uq_fetch_job_active 條件一致。 */
    private static final List<FetchStatus> ACTIVE =
            List.of(FetchStatus.PENDING, FetchStatus.RUNNING);

    private final FetchJobRepository fetchJobRepository;

    public FetchJobService(FetchJobRepository fetchJobRepository) {
        this.fetchJobRepository = fetchJobRepository;
    }

    /**
     * 為一個來源建立任務並標記為 RUNNING。
     *
     * @return 新任務的 id；若這個來源已經有進行中的任務則回傳 {@code null}
     */
    @Transactional
    public Long startJob(Long sourceId) {

        // 第一道防線：給友善的 log
        if (fetchJobRepository.existsBySourceIdAndStatusIn(sourceId, ACTIVE)) {
            log.info("跳過：這個來源已有進行中的任務 sourceId={}", sourceId);
            return null;
        }

        FetchJob job = new FetchJob(sourceId);

        /*
         * 建立之後立刻 start()，所以資料庫裡不會真的看到 PENDING 這個狀態。
         *
         * 那 PENDING 還有存在的必要嗎？有——
         * 等 Day 17 把抓取改成非同步（排程只負責「排隊」，另一批執行緒負責「執行」），
         * 排隊中的任務就會停在 PENDING。現在只是還沒走到那一步。
         */
        job.start();

        try {
            Long jobId = fetchJobRepository.saveAndFlush(job).getId();
            log.info("任務開始 jobId={} sourceId={}", jobId, sourceId);
            return jobId;

        } catch (DataIntegrityViolationException e) {
            // 第二道防線：uq_fetch_job_active。兩個排程同時跑時才會走到這裡
            log.warn("這個來源已有進行中的任務（撞到唯一約束）sourceId={}", sourceId);
            return null;
        }
    }

    @Transactional
    public void succeed(Long jobId) {

        FetchJob job = load(jobId);
        job.succeed();

        log.info("任務成功 jobId={}", jobId);
    }

    @Transactional
    public void fail(Long jobId, FailureType failureType, String reason) {

        FetchJob job = load(jobId);
        job.fail(failureType, reason);

        log.warn("任務失敗 jobId={} 類型={} 原因={}", jobId, failureType, reason);
    }

    private FetchJob load(Long jobId) {
        return fetchJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("找不到任務 jobId=" + jobId));
    }
}
