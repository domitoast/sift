package dev.sift.fetch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface FetchJobRepository extends JpaRepository<FetchJob, Long> {

    /**
     * 這個來源目前有沒有還沒結束的任務。
     *
     * <p>用來擋掉「上一輪卡住了，這一輪又建一筆」。
     * 對應資料庫的 {@code uq_fetch_job_active}（V5）——
     * 這是程式端的第一道防線，給友善的 log；
     * 那個 partial unique index 才是真正的保證。
     */
    boolean existsBySourceIdAndStatusIn(Long sourceId, Collection<FetchStatus> statuses);
}
