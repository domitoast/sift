package dev.sift.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Source 的資料存取層。
 *
 * <p>與 {@code DocumentRepository} 相同：<b>每個方法都帶 userId</b>，
 * 而且不提供「只用 id 查」的方法（ADR-013）。
 */
public interface SourceRepository extends JpaRepository<Source, Long> {

    Optional<Source> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /**
     * 列出某使用者的所有未刪除來源。
     *
     * <p><b>沒有分頁</b>：訂閱來源是使用者手動一個一個加的，
     * 幾十個就算多了。這是「答得出上限」的清單。
     */
    List<Source> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    boolean existsByUrlAndUserIdAndDeletedAtIsNull(String url, Long userId);

    /**
     * 排程要抓的所有來源：啟用中、未刪除。
     *
     * <p><b>這是全系統唯一一個不帶 userId 的查詢方法</b>，
     * 因為排程不代表任何使用者——它要抓的是所有人的來源。
     *
     * <p>對應 V1 的 {@code idx_source_scheduling (deleted_at, enabled)}。
     *
     * <p>⚠️ 沒有分頁。目前來源數量以「幾十個」為量級，
     * 若日後成長到數萬筆，這裡要改成分批處理。
     */
    List<Source> findAllByEnabledTrueAndDeletedAtIsNull();
}
