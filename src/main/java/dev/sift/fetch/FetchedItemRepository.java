package dev.sift.fetch;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FetchedItemRepository extends JpaRepository<FetchedItem, Long> {

    /**
     * 撈出待處理的文章，舊的先做。
     *
     * <p>對應 V1 的 {@code idx_fetched_item_processing (status, next_retry_at)}。
     *
     * <p><b>為什麼要 {@code Limit}</b>：不限制的話，第一次啟動時
     * 資料庫裡幾百篇會一次全部送去呼叫 LLM——那是幾百次網路請求、幾百次付費，
     * 而且中間掛掉的話狀態會很亂。一次做十篇，做不完下一輪再繼續。
     *
     * <p>⚠️ 沒有 userId 條件——排程不代表任何使用者。
     * 呼叫端必須自己找出每一篇屬於誰。
     */
    List<FetchedItem> findByStatusOrderByCreatedAtAsc(FetchedItemStatus status, Limit limit);

    /**
     * 撈出「可以現在處理」的文章——擁有者必須已經設定 API key。
     *
     * <h2>為什麼要用原生 SQL</h2>
     *
     * 這個查詢要跨三張表：
     * {@code fetched_item → source → app_user}。
     *
     * <p>但 ADR-012 決定「跨聚合只存 id，不建立 JPA 關聯」，
     * 所以 JPQL 沒辦法 join——<b>這是 ADR-012 的代價第一次具體出現。</b>
     *
     * <p>選擇原生 SQL 而不是分三次查詢，理由是：
     * ADR-012 說的是「Java 物件之間不要互相持有」，不是「資料庫不准 join」。
     * 資料表本來就有外鍵，join 是它們的天職。
     *
     * <h2>為什麼不能撈出來才過濾</h2>
     *
     * Day 18 的做法是撈最舊的 10 筆，發現沒 key 就跳過。結果：
     *
     * <pre>
     * 每一輪：撈同樣的 10 筆 → 全部沒 key → 全部跳過 → 什麼都沒做
     * </pre>
     *
     * 那 10 筆佔著名額，後面的文章永遠輪不到。
     * 這叫 <b>head-of-line blocking</b>——排在最前面的堵住了整條隊伍。
     *
     * <p>真實情境：某個使用者刪掉自己的 key，他的文章就永遠卡在隊伍前面，
     * 害所有人的文章都處理不到。
     *
     * <p><b>原則：能在資料庫過濾掉的，就不要撈回來再過濾。</b>
     *
     * <h2>next_retry_at 的條件</h2>
     *
     * <pre>{@code AND (fi.next_retry_at IS NULL OR fi.next_retry_at <= now())}</pre>
     *
     * <ul>
     *   <li>{@code IS NULL} — 從來沒失敗過，隨時可以處理</li>
     *   <li>{@code <= now()} — 失敗過，但等待時間已經到了</li>
     * </ul>
     *
     * 少了這個條件，剛失敗的項目下一輪（30 秒後）就會被撿走——
     * exponential backoff 等於沒做。
     *
     * <p>對應 V1 的 {@code idx_fetched_item_processing (status, next_retry_at)}。
     *
     * @param status 要撈的狀態，<b>傳字串</b>——原生 SQL 不認得 Java 的 enum
     */
    @Query(value = """
            SELECT fi.* FROM fetched_item fi
            JOIN source   s ON s.id = fi.source_id
            JOIN app_user u ON u.id = s.user_id
            WHERE fi.status = :status
              AND s.deleted_at IS NULL
              AND u.deleted_at IS NULL
              AND u.llm_api_key_encrypted IS NOT NULL
              AND (fi.next_retry_at IS NULL OR fi.next_retry_at <= now())
            ORDER BY fi.created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<FetchedItem> findProcessable(@Param("status") String status, @Param("limit") int limit);

    /**
     * 某個來源目前存了幾篇。測試與統計用。
     *
     * <p>⚠️ 沒有 userId 條件——與 {@code findBySourceIdOrderByCreatedAtDesc}
     * 一樣，權限必須由呼叫端先驗證。
     */
    long countBySourceId(Long sourceId);
}
