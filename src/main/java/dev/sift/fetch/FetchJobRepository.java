package dev.sift.fetch;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

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

    /**
     * 某來源最近幾次的抓取紀錄，新的在前（FR-2.4）。
     *
     * <p>對應 V1 的 {@code idx_fetch_job_source_created (source_id, created_at DESC)}——
     * 那個索引 Day 3 就為了這個查詢而建，今天才第一次被用到。
     *
     * <p><b>{@code Limit} 而不是 {@code Pageable}</b>：
     * 這裡不需要頁碼、不需要總筆數，只要「最近 N 筆」。
     * 用 {@code Pageable} 會多一次 {@code COUNT(*)}，而那個數字沒有人要看。
     *
     * <p><b>沒有 userId 條件</b>——這是全專案唯一一個違反 ADR-013 的查詢。
     * 理由：{@code fetch_job} 依 ADR-012 只存 {@code source_id}，沒有 {@code user_id}。
     * 因此權限必須由呼叫端先驗證 source 的歸屬，再呼叫這個方法。
     * <b>這個方法不可以被 Controller 直接使用。</b>
     */
    List<FetchJob> findBySourceIdOrderByCreatedAtDesc(Long sourceId, Limit limit);
}
